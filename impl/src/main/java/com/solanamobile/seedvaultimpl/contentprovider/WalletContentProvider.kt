/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.contentprovider

import android.content.*
import android.content.ContentResolver.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import com.solanamobile.seedvault.BipDerivationPath
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.WalletContractV1.AUTHORITY_WALLET_PROVIDER
import com.solanamobile.seedvaultimpl.ApplicationDependencyContainer
import com.solanamobile.seedvaultimpl.SeedVaultImplApplication
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.model.Authorization
import com.solanamobile.seedvaultimpl.usecase.Base58EncodeUseCase
import com.solanamobile.seedvaultimpl.usecase.normalize
import com.solanamobile.seedvaultimpl.usecase.toBip32DerivationPath
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class WalletContentProvider : ContentProvider() {
    private lateinit var dependencyContainer: ApplicationDependencyContainer

    override fun onCreate(): Boolean {
        // NOTE: this occurs before the Application instance is created, so we can't do our
        // dependency injection here
        return true
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            AUTHORIZED_SEEDS -> CURSOR_DIR_BASE_TYPE + WalletContractV1.WALLET_AUTHORIZED_SEEDS_MIME_SUBTYPE
            AUTHORIZED_SEEDS_ID -> CURSOR_ITEM_BASE_TYPE + WalletContractV1.WALLET_AUTHORIZED_SEEDS_MIME_SUBTYPE
            UNAUTHORIZED_SEEDS -> CURSOR_ITEM_BASE_TYPE + WalletContractV1.WALLET_UNAUTHORIZED_SEEDS_MIME_SUBTYPE
            ACCOUNTS -> CURSOR_DIR_BASE_TYPE + WalletContractV1.WALLET_ACCOUNTS_MIME_SUBTYPE
            ACCOUNTS_ID -> CURSOR_ITEM_BASE_TYPE + WalletContractV1.WALLET_ACCOUNTS_MIME_SUBTYPE
            else -> null
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            WalletContractV1.WALLET_RESOLVE_BIP32_DERIVATION_PATH_METHOD ->
                callResolveBip32DerivationPath(arg, extras)
            else -> {
                Log.w(TAG, "Method $method is not defined")
                throw IllegalArgumentException("Method $method is not defined")
            }
        }
    }

    private fun callResolveBip32DerivationPath(arg: String?, extras: Bundle?): Bundle {
        require(extras != null) { "extras must be defined for method '${WalletContractV1.WALLET_RESOLVE_BIP32_DERIVATION_PATH_METHOD}'" }
        val uri: Uri? = extras.getParcelable(WalletContractV1.BIP_DERIVATION_PATH)
        require(uri != null) { "BIP derivation path must be specified" }
        val purpose = extras.getInt(WalletContractV1.PURPOSE, -1)
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
            WalletContractV1.RESOLVED_BIP32_DERIVATION_PATH,
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
        checkDependencyInjection()

        val match = uriMatcher.match(uri)
        val uid = requireContext().packageManager.getPackageUid(callingPackage!!, 0)

        return when (match) {
            AUTHORIZED_SEEDS -> {
                queryAuthorizedSeeds(uid, null, projection, queryArgs)
            }
            AUTHORIZED_SEEDS_ID -> {
                queryAuthorizedSeeds(uid, ContentUris.parseId(uri).toInt(), projection, queryArgs)
            }
            UNAUTHORIZED_SEEDS -> {
                queryUnauthorizedSeeds(uid, null, projection, queryArgs)
            }
            UNAUTHORIZED_SEEDS_ID -> {
                queryUnauthorizedSeeds(uid, ContentUris.parseId(uri).toInt(), projection, queryArgs)
            }
            ACCOUNTS -> {
                val authToken = queryArgs?.getInt(
                    WalletContractV1.EXTRA_AUTH_TOKEN,
                    WalletContractV1.AUTH_TOKEN_INVALID) ?: WalletContractV1.AUTH_TOKEN_INVALID
                queryAccounts(uid, authToken, null, projection, queryArgs)
            }
            ACCOUNTS_ID -> {
                val authToken = queryArgs?.getInt(
                    WalletContractV1.EXTRA_AUTH_TOKEN,
                    WalletContractV1.AUTH_TOKEN_INVALID) ?: WalletContractV1.AUTH_TOKEN_INVALID
                queryAccounts(uid, authToken, ContentUris.parseId(uri).toInt(), projection, queryArgs)
            }
            else -> {
                Log.w(TAG, "Query not supported for $uri")
                throw IllegalArgumentException("Query not supported for $uri")
            }
        }
    }

    private fun makeQueryParser(queryableColumns: Collection<String>, queryArgs: Bundle?): SimpleQueryParser? {
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
        authToken: Int?,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        val defaultProjection = WalletContractV1.WALLET_AUTHORIZED_SEEDS_ALL_COLUMNS.toList()
        val queryParser = makeQueryParser(defaultProjection, queryArgs)
        val filteredProjection = projection?.intersect(defaultProjection) ?: defaultProjection
        val cursor = MatrixCursor(filteredProjection.toTypedArray())

        val seedRepository = dependencyContainer.seedRepository
        runBlocking {
            seedRepository.delayUntilDataValid()
        }

        seedRepository.seeds.value.values.forEach { seed ->
            seed.authorizations.forEach { auth ->
                // Note: must be in the same order as defaultProjection
                val values = arrayOf(
                    auth.authToken,                             // WalletContractV1.AUTH_TOKEN
                    auth.purpose.toWalletContractConstant(),    // WalletContractV1.AUTH_PURPOSE
                    seed.details.name ?: ""                     // WalletContractV1.SEED_NAME
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
        purpose: Int?,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        val purposeAsEnum = purpose?.let { Authorization.Purpose.fromWalletContractConstant(it) }
        val defaultProjection = WalletContractV1.WALLET_UNAUTHORIZED_SEEDS_ALL_COLUMNS.toList()
        val queryParser = makeQueryParser(defaultProjection, queryArgs)
        val filteredProjection = projection?.intersect(defaultProjection) ?: defaultProjection
        val cursor = MatrixCursor(filteredProjection.toTypedArray())

        val seedRepository = dependencyContainer.seedRepository
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
                p.toWalletContractConstant(),
                if (seedPurposeCount < seeds.size) 1 else 0
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
        authToken: Int,
        accountId: Int?,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        val defaultProjection = WalletContractV1.WALLET_ACCOUNTS_ALL_COLUMNS.toList()
        val queryParser = makeQueryParser(defaultProjection, queryArgs)
        val filteredProjection = projection?.intersect(defaultProjection) ?: defaultProjection
        val cursor = MatrixCursor(filteredProjection.toTypedArray())

        val seedRepository = dependencyContainer.seedRepository
        runBlocking {
            seedRepository.delayUntilDataValid()
        }

        val authKey = SeedRepository.AuthorizationKey(uid, authToken)
        seedRepository.authorizations.value[authKey]?.let { seed ->
            seed.accounts.forEach { account ->
                // NOTE: must be in the same order as defaultProjection
                val values = arrayOf(
                    account.id,                                 // WalletContractV1.ACCOUNT_ID
                    account.bip32DerivationPathUri.toString(),  // WalletContractV1.BIP32_DERIVATION_PATH
                    account.publicKey,                          // WalletContractV1.PUBLIC_KEY_RAW
                    Base58EncodeUseCase(account.publicKey),     // WalletContractV1.PUBLIC_KEY_BASE58
                    account.name ?: "",                         // WalletContractV1.ACCOUNT_NAME
                    if (account.isUserWallet) 1 else 0,         // WalletContractV1.ACCOUNT_IS_USER_WALLET
                    if (account.isValid) 1 else 0               // WalletContractV1.ACCOUNT_IS_VALID
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
        checkDependencyInjection()

        val match = uriMatcher.match(uri)
        val uid = requireContext().packageManager.getPackageUid(callingPackage!!, 0)

        return when (match) {
            AUTHORIZED_SEEDS_ID -> {
                deleteAuthorizedSeed(uid, ContentUris.parseId(uri).toInt())
            }
            else -> {
                Log.w(TAG, "Delete not supported for $uri")
                throw IllegalArgumentException("Delete not supported for $uri")
            }
        }
    }

    private fun deleteAuthorizedSeed(
        uid: Int,
        authToken: Int
    ): Int {
        val seedRepository = dependencyContainer.seedRepository
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
        checkDependencyInjection()

        val match = uriMatcher.match(uri)
        val uid = requireContext().packageManager.getPackageUid(callingPackage!!, 0)

        return when (match) {
            ACCOUNTS_ID -> {
                val authToken = extras?.getInt(
                    WalletContractV1.EXTRA_AUTH_TOKEN,
                    WalletContractV1.AUTH_TOKEN_INVALID) ?: WalletContractV1.AUTH_TOKEN_INVALID
                updateAccount(uid, authToken, ContentUris.parseId(uri).toInt(), values)
            }
            else -> {
                Log.w(TAG, "Update not supported for $uri")
                throw IllegalArgumentException("Update not supported for $uri")
            }
        }
    }

    private fun updateAccount(
        uid: Int,
        authToken: Int,
        accountId: Int,
        values: ContentValues?
    ): Int {
        val seedRepository = dependencyContainer.seedRepository
        runBlocking {
            seedRepository.delayUntilDataValid()
        }

        val authKey = SeedRepository.AuthorizationKey(uid, authToken)
        var updated = false
        seedRepository.authorizations.value[authKey]?.let { seed ->
            seed.accounts.firstOrNull { account ->
                account.id == accountId
            }?.let { account ->
                val updatedName = if (values?.containsKey(WalletContractV1.ACCOUNT_NAME) == true) {
                    values.getAsString(WalletContractV1.ACCOUNT_NAME)
                } else {
                    account.name
                }

                val updatedIsUserWallet = if (values?.containsKey(WalletContractV1.ACCOUNT_IS_USER_WALLET) == true) {
                    values.getAsInteger(WalletContractV1.ACCOUNT_IS_USER_WALLET) == 1
                } else {
                    account.isUserWallet
                }

                val updatedIsValid = if (values?.containsKey(WalletContractV1.ACCOUNT_IS_VALID) == true) {
                    values.getAsInteger(WalletContractV1.ACCOUNT_IS_VALID) == 1
                } else {
                    account.isValid
                }

                runBlocking {
                    try {
                        seedRepository.updateKnownAccountForSeed(seed.id, account.copy(
                            name = updatedName, isUserWallet = updatedIsUserWallet, isValid = updatedIsValid
                        ))
                        updated = true
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Failed to update account ${account.id} for seed ${seed.id}", e)
                    }
                }
            }
        }

        return if (updated) 1 else 0
    }

    private fun checkDependencyInjection() {
        // Note: this can be executed in an arbitrary thread context. Use double-checked locking
        // pattern to safely initialize it.
        if (!this::dependencyContainer.isInitialized) {
            val didInitialization = synchronized(this::dependencyContainer) {
                if (!this::dependencyContainer.isInitialized) {
                    dependencyContainer =
                        (requireContext().applicationContext as SeedVaultImplApplication)
                            .dependencyContainer
                    true
                } else {
                    false
                }
            }

            if (didInitialization) {
                observeSeedRepositoryChanges()
            }
        }
    }

    private fun observeSeedRepositoryChanges() {
        dependencyContainer.applicationScope.launch {
            dependencyContainer.seedRepository.changes.collect { change ->
                // NOTE: this is overeager; we aren't checking, e.g., if deleting a particular seed
                // will affect an observer watching a particular account.
                val uris: List<Uri> = when (change.category) {
                    SeedRepository.ChangeNotification.Category.SEED -> {
                        when (change.type) {
                            SeedRepository.ChangeNotification.Type.CREATE ->
                                listOf(WalletContractV1.WALLET_UNAUTHORIZED_SEEDS_CONTENT_URI)
                            SeedRepository.ChangeNotification.Type.UPDATE ->
                                listOf(WalletContractV1.WALLET_AUTHORIZED_SEEDS_CONTENT_URI)
                            SeedRepository.ChangeNotification.Type.DELETE ->
                                listOf(
                                    WalletContractV1.WALLET_AUTHORIZED_SEEDS_CONTENT_URI,
                                    WalletContractV1.WALLET_ACCOUNTS_CONTENT_URI
                                )
                        }
                    }
                    SeedRepository.ChangeNotification.Category.AUTHORIZATION -> {
                        when (change.type) {
                            SeedRepository.ChangeNotification.Type.CREATE ->
                                listOf(
                                    WalletContractV1.WALLET_UNAUTHORIZED_SEEDS_CONTENT_URI,
                                    ContentUris.withAppendedId(
                                        WalletContractV1.WALLET_AUTHORIZED_SEEDS_CONTENT_URI,
                                        change.id!!.toLong()
                                    )
                                )
                            SeedRepository.ChangeNotification.Type.UPDATE ->
                                throw AssertionError("Authorizations are not expected to be updated")
                            SeedRepository.ChangeNotification.Type.DELETE ->
                                listOf(
                                    WalletContractV1.WALLET_UNAUTHORIZED_SEEDS_CONTENT_URI,
                                    ContentUris.withAppendedId(
                                        WalletContractV1.WALLET_AUTHORIZED_SEEDS_CONTENT_URI,
                                        change.id!!.toLong()
                                    ),
                                    WalletContractV1.WALLET_ACCOUNTS_CONTENT_URI
                                )
                        }
                    }
                    SeedRepository.ChangeNotification.Category.ACCOUNT ->
                        when (change.type) {
                            SeedRepository.ChangeNotification.Type.CREATE,
                            SeedRepository.ChangeNotification.Type.UPDATE ->
                                listOf(
                                    ContentUris.withAppendedId(
                                        WalletContractV1.WALLET_ACCOUNTS_CONTENT_URI,
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

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.WALLET_AUTHORIZED_SEEDS_TABLE, AUTHORIZED_SEEDS)
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.WALLET_AUTHORIZED_SEEDS_TABLE + "/#", AUTHORIZED_SEEDS_ID)
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.WALLET_UNAUTHORIZED_SEEDS_TABLE, UNAUTHORIZED_SEEDS)
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.WALLET_UNAUTHORIZED_SEEDS_TABLE + "/#", UNAUTHORIZED_SEEDS_ID)
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.WALLET_ACCOUNTS_TABLE, ACCOUNTS)
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.WALLET_ACCOUNTS_TABLE + "/#", ACCOUNTS_ID)
        }
    }
}