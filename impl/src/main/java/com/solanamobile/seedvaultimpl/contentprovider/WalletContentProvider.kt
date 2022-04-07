/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.contentprovider

import android.content.ContentProvider
import android.content.ContentResolver.*
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.BaseColumns
import android.util.Log
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.WalletContractV1.AUTHORITY_WALLET_PROVIDER
import com.solanamobile.seedvaultimpl.ApplicationDependencyContainer
import com.solanamobile.seedvaultimpl.SeedVaultImplApplication
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.usecase.Base58EncodeUseCase
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
                queryUnauthorizedSeeds(uid, projection, queryArgs)
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

    private fun queryAuthorizedSeeds(
        uid: Int,
        authToken: Int?,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        val defaultProjection = WalletContractV1.WALLET_AUTHORIZED_SEEDS_ALL_COLUMNS.toSet()
        val filteredProjection = projection?.intersect(defaultProjection) ?: defaultProjection
        val cursor = MatrixCursor(filteredProjection.toTypedArray())

        // TODO: support queries with filter on Purpose

        val seedRepository = dependencyContainer.seedRepository
        runBlocking {
            seedRepository.delayUntilDataValid()
        }

        seedRepository.seeds.value.values.forEach { seed ->
            seed.authorizations.filter { auth ->
                auth.uid == uid && (authToken == null || auth.authToken == authToken)
            }.forEach { auth ->
                val rowBuilder = cursor.newRow()
                rowBuilder.add(BaseColumns._ID, auth.authToken)
                rowBuilder.add(WalletContractV1.AUTH_PURPOSE, auth.purpose.toWalletContractConstant())
                rowBuilder.add(WalletContractV1.SEED_NAME, seed.details.name ?: "")
            }
        }

        return cursor
    }

    private fun queryUnauthorizedSeeds(
        uid: Int,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        val defaultProjection = WalletContractV1.WALLET_UNAUTHORIZED_SEEDS_ALL_COLUMNS.toSet()
        val filteredProjection = projection?.intersect(defaultProjection) ?: defaultProjection
        val cursor = MatrixCursor(filteredProjection.toTypedArray())

        // TODO: support queries with filter on Purpose (otherwise, this returns seeds which remain
        // to be authorized for ANY purpose, which isn't very useful if the wallet is tied to one
        // chain)

        val seedRepository = dependencyContainer.seedRepository
        runBlocking {
            seedRepository.delayUntilDataValid()
        }

        val firstUnauthorizedSeed = seedRepository.seeds.value.values.firstOrNull { seed ->
            seed.authorizations.all { auth -> auth.uid != uid }
        }

        cursor.newRow().add(WalletContractV1.HAS_UNAUTHORIZED_SEEDS, if (firstUnauthorizedSeed != null) 1 else 0)

        return cursor
    }

    private fun queryAccounts(
        uid: Int,
        authToken: Int,
        accountId: Int?,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        val defaultProjection = WalletContractV1.WALLET_ACCOUNTS_ALL_COLUMNS.toSet()
        val filteredProjection = projection?.intersect(defaultProjection) ?: defaultProjection
        val cursor = MatrixCursor(filteredProjection.toTypedArray())

        // TODO: support queries with filters on most columns

        val seedRepository = dependencyContainer.seedRepository
        runBlocking {
            seedRepository.delayUntilDataValid()
        }

        val authKey = SeedRepository.AuthorizationKey(uid, authToken)
        seedRepository.authorizations.value[authKey]?.let { seed ->
            seed.accounts.forEach { account ->
                if (accountId == null || account.id == accountId) {
                    val rowBuilder = cursor.newRow()
                    rowBuilder.add(BaseColumns._ID, account.id)
                    rowBuilder.add(WalletContractV1.BIP32_DERIVATION_PATH, account.bip32DerivationPathUri.toString())
                    rowBuilder.add(WalletContractV1.PUBLIC_KEY_RAW, account.publicKey)
                    rowBuilder.add(WalletContractV1.PUBLIC_KEY_BASE58, Base58EncodeUseCase(account.publicKey))
                    rowBuilder.add(WalletContractV1.ACCOUNT_NAME, account.name ?: "")
                    rowBuilder.add(WalletContractV1.ACCOUNT_IS_USER_WALLET, if (account.isUserWallet) 1 else 0)
                    rowBuilder.add(WalletContractV1.ACCOUNT_IS_VALID, if (account.isValid) 1 else 0)
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
        private const val ACCOUNTS = 4
        private const val ACCOUNTS_ID = 5

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.WALLET_AUTHORIZED_SEEDS_TABLE, AUTHORIZED_SEEDS)
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.WALLET_AUTHORIZED_SEEDS_TABLE + "/#", AUTHORIZED_SEEDS_ID)
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.WALLET_UNAUTHORIZED_SEEDS_TABLE, UNAUTHORIZED_SEEDS)
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.WALLET_ACCOUNTS_TABLE, ACCOUNTS)
            addURI(AUTHORITY_WALLET_PROVIDER, WalletContractV1.WALLET_ACCOUNTS_TABLE + "/#", ACCOUNTS_ID)
        }
    }
}