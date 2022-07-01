/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.contentprovider

import android.content.ContentProvider
import android.content.ContentResolver.CURSOR_DIR_BASE_TYPE
import android.content.ContentResolver.CURSOR_ITEM_BASE_TYPE
import android.content.ContentResolver.NOTIFY_DELETE
import android.content.ContentResolver.NOTIFY_INSERT
import android.content.ContentResolver.NOTIFY_UPDATE
import android.content.ContentResolver.QUERY_ARG_SQL_SELECTION
import android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import com.solanamobile.seedvault.BipDerivationPath
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.WalletContractV1.AUTHORITY_WALLET_PROVIDER
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.model.Authorization
import com.solanamobile.seedvaultimpl.usecase.Base58EncodeUseCase
import com.solanamobile.seedvaultimpl.usecase.RequestLimitsUseCase
import com.solanamobile.seedvaultimpl.usecase.normalize
import com.solanamobile.seedvaultimpl.usecase.toBip32DerivationPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class WalletContentProvider : ContentProvider() {

    private val seedRepository: SeedRepository by inject()
    private val contentProviderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isSubscriptionStarted = false
        @Synchronized
        get
        @Synchronized
        set

    override fun onCreate(): Boolean {
        // NOTE: this occurs before the Application instance is created, so we can't do our
        // dependency injection here
        return true
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            AUTHORIZED_SEEDS -> CURSOR_DIR_BASE_TYPE + WalletContractV1.AUTHORIZED_SEEDS_MIME_SUBTYPE
            AUTHORIZED_SEEDS_ID -> CURSOR_ITEM_BASE_TYPE + WalletContractV1.AUTHORIZED_SEEDS_MIME_SUBTYPE
            UNAUTHORIZED_SEEDS -> CURSOR_ITEM_BASE_TYPE + WalletContractV1.UNAUTHORIZED_SEEDS_MIME_SUBTYPE
            UNAUTHORIZED_SEEDS_ID -> CURSOR_ITEM_BASE_TYPE + WalletContractV1.UNAUTHORIZED_SEEDS_MIME_SUBTYPE
            ACCOUNTS -> CURSOR_DIR_BASE_TYPE + WalletContractV1.ACCOUNTS_MIME_SUBTYPE
            ACCOUNTS_ID -> CURSOR_ITEM_BASE_TYPE + WalletContractV1.ACCOUNTS_MIME_SUBTYPE
            IMPLEMENTATION_LIMITS -> CURSOR_DIR_BASE_TYPE + WalletContractV1.IMPLEMENTATION_LIMITS_MIME_SUBTYPE
            IMPLEMENTATION_LIMITS_ID -> CURSOR_ITEM_BASE_TYPE + WalletContractV1.IMPLEMENTATION_LIMITS_MIME_SUBTYPE
            else -> null
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        return when (method) {
            WalletContractV1.RESOLVE_BIP32_DERIVATION_PATH_METHOD ->
                callResolveBip32DerivationPath(arg, extras)
            else -> {
                Log.w(TAG, "Method $method is not defined")
                throw IllegalArgumentException("Method $method is not defined")
            }
        }
    }

    private fun callResolveBip32DerivationPath(arg: String?, extras: Bundle?): Bundle {
        require(arg != null) { "arg must be defined" }
        val uri = Uri.parse(arg)
        require(extras != null) { "extras must be defined" }
        val purpose = extras.getInt(WalletContractV1.EXTRA_PURPOSE, -1)
        val purposeAsEnum = Authorization.Purpose.fromWalletContractConstant(purpose)
        val derivationPath = try {
            BipDerivationPath.fromUri(uri)
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing BIP derivation path '$uri'", e)
            throw IllegalArgumentException("Failed parsing BIP derivation path '$uri'", e)
        }
        val resolvedDerivationPath = derivationPath
            .toBip32DerivationPath(purposeAsEnum)
            .normalize(purposeAsEnum)
        val result = Bundle()
        result.putParcelable(
            WalletContractV1.EXTRA_RESOLVED_BIP32_DERIVATION_PATH,
            resolvedDerivationPath.toUri()
        )
        return result
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        throw NotImplementedError("Legacy ContentProvider interface methods are not implemented")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?
    ): Cursor {
        checkSubscriptionStarted()

        val match = uriMatcher.match(uri)
        val uid = requireContext().packageManager.getPackageUid(callingPackage!!, 0)

        return when (match) {
            AUTHORIZED_SEEDS -> {
                queryAuthorizedSeeds(uid, null, projection, queryArgs)
            }
            AUTHORIZED_SEEDS_ID -> {
                queryAuthorizedSeeds(uid, ContentUris.parseId(uri), projection, queryArgs)
            }
            UNAUTHORIZED_SEEDS -> {
                queryUnauthorizedSeeds(uid, null, projection, queryArgs)
            }
            UNAUTHORIZED_SEEDS_ID -> {
                queryUnauthorizedSeeds(uid, ContentUris.parseId(uri).toInt(), projection, queryArgs)
            }
            ACCOUNTS -> {
                val authToken = queryArgs?.getLong(WalletContractV1.EXTRA_AUTH_TOKEN, -1) ?: -1
                queryAccounts(uid, authToken, null, projection, queryArgs)
            }
            ACCOUNTS_ID -> {
                val authToken = queryArgs?.getLong(WalletContractV1.EXTRA_AUTH_TOKEN, -1) ?: -1
                queryAccounts(uid, authToken, ContentUris.parseId(uri), projection, queryArgs)
            }
            IMPLEMENTATION_LIMITS -> {
                queryImplementationLimits(null, projection, queryArgs)
            }
            IMPLEMENTATION_LIMITS_ID -> {
                queryImplementationLimits(ContentUris.parseId(uri).toInt(), projection, queryArgs)
            }
            else -> {
                Log.w(TAG, "Query not supported for $uri")
                throw IllegalArgumentException("Query not supported for $uri")
            }
        }
    }

    private fun makeQueryParser(
        queryableColumns: Collection<String>,
        queryArgs: Bundle?
    ): SimpleQueryParser? {
        return queryArgs?.let { bundle ->
            val selection = bundle.getString(QUERY_ARG_SQL_SELECTION) ?: return@let null
            val selectionArgs = bundle.getStringArray(QUERY_ARG_SQL_SELECTION_ARGS)
                ?: throw IllegalArgumentException("Selection args required when selection is provided")
            try {
                SimpleQueryParser(queryableColumns, selection, selectionArgs)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Unable to apply selection args '$selection'/$selectionArgs", e)
                throw e
            }
        }
    }

    private fun queryAuthorizedSeeds(
        uid: Int,
        @WalletContractV1.AuthToken authToken: Long?,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        val defaultProjection = WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS.toList()
        val queryParser = makeQueryParser(defaultProjection, queryArgs)
        val filteredProjection = projection?.intersect(defaultProjection) ?: defaultProjection
        val cursor = MatrixCursor(filteredProjection.toTypedArray())


        runBlocking {
            seedRepository.delayUntilDataValid()
        }

        seedRepository.seeds.value.values.forEach { seed ->
            seed.authorizations.forEach { auth ->
                // Note: must be in the same order as defaultProjection
                val values = arrayOf(
                    auth.authToken,                             // WalletContractV1.AUTHORIZED_SEEDS_AUTH_TOKEN
                    auth.purpose.toWalletContractConstant(),    // WalletContractV1.AUTHORIZED_SEEDS_AUTH_PURPOSE
                    seed.details.name
                        ?: ""                     // WalletContractV1.AUTHORIZED_SEEDS_SEED_NAME
                )

                if (auth.uid == uid
                    && (authToken == null || auth.authToken == authToken)
                    && queryParser?.match(*values) != false
                ) {
                    val rowBuilder = cursor.newRow()
                    for (item in defaultProjection.zip(values)) {
                        rowBuilder.add(item.first, item.second)
                    }
                }
            }
        }

        return cursor
    }

    private fun queryUnauthorizedSeeds(
        uid: Int,
        @WalletContractV1.Purpose purpose: Int?,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        val purposeAsEnum = purpose?.let { Authorization.Purpose.fromWalletContractConstant(it) }
        val defaultProjection = WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS.toList()
        val queryParser = makeQueryParser(defaultProjection, queryArgs)
        val filteredProjection = projection?.intersect(defaultProjection) ?: defaultProjection
        val cursor = MatrixCursor(filteredProjection.toTypedArray())


        runBlocking {
            seedRepository.delayUntilDataValid()
        }

        val seeds = seedRepository.seeds.value.values
        val seedsAuthorizedPurposeCounts = seeds.flatMap { seed ->
            seed.authorizations.filter { auth ->
                auth.uid == uid
            }.map { auth ->
                auth.purpose
            }
        }.groupBy { p ->
            p
        }

        Authorization.Purpose.values().forEach { p ->
            val seedPurposeCount = seedsAuthorizedPurposeCounts[p]?.size ?: 0

            // NOTE: must be in the same order as defaultProjection
            val values = arrayOf(
                p.toWalletContractConstant(),                                   // WalletContractV1.UNAUTHORIZED_SEEDS_AUTH_PURPOSE
                if (seedPurposeCount < seeds.size) 1.toShort() else 0.toShort() // WalletContractV1.UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS
            )

            if ((purposeAsEnum == null || p == purposeAsEnum)
                && queryParser?.match(*values) != false
            ) {
                val rowBuilder = cursor.newRow()
                for (item in defaultProjection.zip(values)) {
                    rowBuilder.add(item.first, item.second)
                }
            }
        }

        return cursor
    }

    private fun queryAccounts(
        uid: Int,
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId accountId: Long?,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        val defaultProjection = WalletContractV1.ACCOUNTS_ALL_COLUMNS.toList()
        val queryParser = makeQueryParser(defaultProjection, queryArgs)
        val filteredProjection = projection?.intersect(defaultProjection) ?: defaultProjection
        val cursor = MatrixCursor(filteredProjection.toTypedArray())


        runBlocking {
            seedRepository.delayUntilDataValid()
        }

        val authKey = SeedRepository.AuthorizationKey(uid, authToken)
        seedRepository.authorizations.value[authKey]?.let { seed ->
            seed.accounts.forEach { account ->
                // NOTE: must be in the same order as defaultProjection
                val values = arrayOf(
                    account.id,                                             // WalletContractV1.ACCOUNTS_ACCOUNT_ID
                    account.bip32DerivationPathUri.toString(),              // WalletContractV1.ACCOUNTS_BIP32_DERIVATION_PATH
                    account.publicKey,                                      // WalletContractV1.ACCOUNTS_PUBLIC_KEY_RAW
                    Base58EncodeUseCase(account.publicKey),                 // WalletContractV1.ACCOUNTS_PUBLIC_KEY_ENCODED
                    account.name
                        ?: "",                                     // WalletContractV1.ACCOUNTS_ACCOUNT_NAME
                    if (account.isUserWallet) 1.toShort() else 0.toShort(), // WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET
                    if (account.isValid) 1.toShort() else 0.toShort()       // WalletContractV1.ACCOUNTS_ACCOUNT_IS_VALID
                )

                if ((accountId == null || account.id == accountId)
                    && queryParser?.match(*values) != false
                ) {
                    val rowBuilder = cursor.newRow()
                    for (item in defaultProjection.zip(values)) {
                        rowBuilder.add(item.first, item.second)
                    }
                }
            }
        } ?: throw IllegalArgumentException("authToken $authToken is not a valid auth token")

        return cursor
    }

    private fun queryImplementationLimits(
        @WalletContractV1.Purpose purpose: Int?,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        val purposeAsEnum = purpose?.let { Authorization.Purpose.fromWalletContractConstant(it) }
        val defaultProjection = WalletContractV1.IMPLEMENTATION_LIMITS_ALL_COLUMNS.toList()
        val queryParser = makeQueryParser(defaultProjection, queryArgs)
        val filteredProjection = projection?.intersect(defaultProjection) ?: defaultProjection
        val cursor = MatrixCursor(filteredProjection.toTypedArray())

        // Currently, all Purposes have the same set of implementation limits
        for (p in Authorization.Purpose.values()) {
            // NOTE: must be in the same order as defaultProjection
            val values = arrayOf(
                p.toWalletContractConstant(),                               // WalletContractV1.IMPLEMENTATION_LIMITS_AUTH_PURPOSE
                RequestLimitsUseCase.MAX_SIGNING_REQUESTS.toShort(),        // WalletContractV1.IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS
                RequestLimitsUseCase.MAX_REQUESTED_SIGNATURES.toShort(),    // WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES
                RequestLimitsUseCase.MAX_REQUESTED_PUBLIC_KEYS.toShort(),   // WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS
            )

            if ((purposeAsEnum == null || p == purposeAsEnum)
                && queryParser?.match(*values) != false
            ) {
                val rowBuilder = cursor.newRow()
                for (item in defaultProjection.zip(values)) {
                    rowBuilder.add(item.first, item.second)
                }
            }
        }

        return cursor
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?
    ): Uri? {
        throw NotImplementedError("Legacy ContentProvider interface methods are not implemented")
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
        extras: Bundle?
    ): Uri? {
        Log.w(TAG, "Insert not supported for $uri")
        throw IllegalArgumentException("Insert not supported for $uri")
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw NotImplementedError("Legacy ContentProvider interface methods are not implemented")
    }

    override fun delete(
        uri: Uri,
        extras: Bundle?
    ): Int {
        checkSubscriptionStarted()

        val match = uriMatcher.match(uri)
        val uid = requireContext().packageManager.getPackageUid(callingPackage!!, 0)

        return when (match) {
            AUTHORIZED_SEEDS_ID -> {
                deleteAuthorizedSeed(uid, ContentUris.parseId(uri))
            }
            else -> {
                Log.w(TAG, "Delete not supported for $uri")
                throw IllegalArgumentException("Delete not supported for $uri")
            }
        }
    }

    private fun deleteAuthorizedSeed(
        uid: Int,
        @WalletContractV1.AuthToken authToken: Long
    ): Int {

        runBlocking {
            seedRepository.delayUntilDataValid()
        }

        val authKey = SeedRepository.AuthorizationKey(uid, authToken)
        var deauthorized = false
        seedRepository.authorizations.value[authKey]?.let { seed ->
            runBlocking {
                try {
                    seedRepository.deauthorizeSeed(seed.id, authToken)
                    deauthorized = true
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Failed to delete AuthToken $authToken", e)
                }
            }
        }

        return if (deauthorized) 1 else 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw NotImplementedError("Legacy ContentProvider interface methods are not implemented")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        extras: Bundle?
    ): Int {
        checkSubscriptionStarted()

        val match = uriMatcher.match(uri)
        val uid = requireContext().packageManager.getPackageUid(callingPackage!!, 0)

        return when (match) {
            ACCOUNTS_ID -> {
                val authToken = extras?.getLong(WalletContractV1.EXTRA_AUTH_TOKEN, -1) ?: -1
                updateAccount(uid, authToken, ContentUris.parseId(uri), values)
            }
            else -> {
                Log.w(TAG, "Update not supported for $uri")
                throw IllegalArgumentException("Update not supported for $uri")
            }
        }
    }

    private fun updateAccount(
        uid: Int,
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId accountId: Long,
        values: ContentValues?
    ): Int {

        runBlocking {
            seedRepository.delayUntilDataValid()
        }

        val authKey = SeedRepository.AuthorizationKey(uid, authToken)
        var updated = false
        seedRepository.authorizations.value[authKey]?.let { seed ->
            seed.accounts.firstOrNull { account ->
                account.id == accountId
            }?.let { account ->
                val updatedName =
                    if (values?.containsKey(WalletContractV1.ACCOUNTS_ACCOUNT_NAME) == true) {
                        values.getAsString(WalletContractV1.ACCOUNTS_ACCOUNT_NAME)
                    } else {
                        account.name
                    }

                val updatedIsUserWallet =
                    if (values?.containsKey(WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET) == true) {
                        values.getAsInteger(WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET) == 1
                    } else {
                        account.isUserWallet
                    }

                val updatedIsValid =
                    if (values?.containsKey(WalletContractV1.ACCOUNTS_ACCOUNT_IS_VALID) == true) {
                        values.getAsInteger(WalletContractV1.ACCOUNTS_ACCOUNT_IS_VALID) == 1
                    } else {
                        account.isValid
                    }

                runBlocking {
                    try {
                        seedRepository.updateKnownAccountForSeed(
                            seed.id, account.copy(
                                name = updatedName,
                                isUserWallet = updatedIsUserWallet,
                                isValid = updatedIsValid
                            )
                        )
                        updated = true
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Failed to update account ${account.id} for seed ${seed.id}", e)
                    }
                }
            }
        } ?: throw IllegalArgumentException("authToken $authToken is not a valid auth token")

        return if (updated) 1 else 0
    }

    private fun checkSubscriptionStarted() {
        if (!isSubscriptionStarted) {
            observeSeedRepositoryChanges()
        }
    }

    private fun observeSeedRepositoryChanges() {
        contentProviderScope.launch {
            seedRepository.changes.collect { change ->
                // NOTE: this is overeager; we aren't checking, e.g., if deleting a particular seed
                // will affect an observer watching a particular account.
                val uris: List<Uri> = when (change.category) {
                    SeedRepository.ChangeNotification.Category.SEED -> {
                        when (change.type) {
                            SeedRepository.ChangeNotification.Type.CREATE ->
                                listOf(WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI)
                            SeedRepository.ChangeNotification.Type.UPDATE ->
                                listOf(WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI)
                            SeedRepository.ChangeNotification.Type.DELETE ->
                                listOf(
                                    WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
                                    WalletContractV1.ACCOUNTS_CONTENT_URI
                                )
                        }
                    }
                    SeedRepository.ChangeNotification.Category.AUTHORIZATION -> {
                        when (change.type) {
                            SeedRepository.ChangeNotification.Type.CREATE ->
                                listOf(
                                    WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI,
                                    ContentUris.withAppendedId(
                                        WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
                                        change.id!!.toLong()
                                    )
                                )
                            SeedRepository.ChangeNotification.Type.UPDATE ->
                                throw AssertionError("Authorizations are not expected to be updated")
                            SeedRepository.ChangeNotification.Type.DELETE ->
                                listOf(
                                    WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI,
                                    ContentUris.withAppendedId(
                                        WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
                                        change.id!!.toLong()
                                    ),
                                    WalletContractV1.ACCOUNTS_CONTENT_URI
                                )
                        }
                    }
                    SeedRepository.ChangeNotification.Category.ACCOUNT ->
                        when (change.type) {
                            SeedRepository.ChangeNotification.Type.CREATE,
                            SeedRepository.ChangeNotification.Type.UPDATE ->
                                listOf(
                                    ContentUris.withAppendedId(
                                        WalletContractV1.ACCOUNTS_CONTENT_URI,
                                        change.id!!.toLong()
                                    )
                                )
                            SeedRepository.ChangeNotification.Type.DELETE ->
                                throw AssertionError("Accounts are not expected to be deleted")
                        }
                }

                val flags = when (change.type) {
                    SeedRepository.ChangeNotification.Type.CREATE -> NOTIFY_INSERT
                    SeedRepository.ChangeNotification.Type.UPDATE -> NOTIFY_UPDATE
                    SeedRepository.ChangeNotification.Type.DELETE -> NOTIFY_DELETE
                }

                requireContext().contentResolver.notifyChange(uris, null, flags)
            }
        }
    }

    companion object {
        private val TAG = WalletContentProvider::class.simpleName

        private const val AUTHORIZED_SEEDS = 1
        private const val AUTHORIZED_SEEDS_ID = 2
        private const val UNAUTHORIZED_SEEDS = 3
        private const val UNAUTHORIZED_SEEDS_ID = 4
        private const val ACCOUNTS = 5
        private const val ACCOUNTS_ID = 6
        private const val IMPLEMENTATION_LIMITS = 7
        private const val IMPLEMENTATION_LIMITS_ID = 8

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(
                AUTHORITY_WALLET_PROVIDER,
                WalletContractV1.AUTHORIZED_SEEDS_TABLE,
                AUTHORIZED_SEEDS
            )
            addURI(
                AUTHORITY_WALLET_PROVIDER,
                WalletContractV1.AUTHORIZED_SEEDS_TABLE + "/#",
                AUTHORIZED_SEEDS_ID
            )
            addURI(
                AUTHORITY_WALLET_PROVIDER,
                WalletContractV1.UNAUTHORIZED_SEEDS_TABLE,
                UNAUTHORIZED_SEEDS
            )
            addURI(
                AUTHORITY_WALLET_PROVIDER,
                WalletContractV1.UNAUTHORIZED_SEEDS_TABLE + "/#",
                UNAUTHORIZED_SEEDS_ID
            )
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.ACCOUNTS_TABLE, ACCOUNTS)
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.ACCOUNTS_TABLE + "/#", ACCOUNTS_ID)
            addURI(
                AUTHORITY_WALLET_PROVIDER,
                WalletContractV1.IMPLEMENTATION_LIMITS_TABLE,
                IMPLEMENTATION_LIMITS
            )
            addURI(
                AUTHORITY_WALLET_PROVIDER,
                WalletContractV1.IMPLEMENTATION_LIMITS_TABLE + "/#",
                IMPLEMENTATION_LIMITS_ID
            )
        }
    }
}