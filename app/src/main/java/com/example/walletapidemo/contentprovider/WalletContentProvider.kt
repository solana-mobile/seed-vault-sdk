/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.contentprovider

import android.content.ContentProvider
import android.content.ContentResolver.CURSOR_DIR_BASE_TYPE
import android.content.ContentResolver.CURSOR_ITEM_BASE_TYPE
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
import com.example.seedvault.WalletContractV1
import com.example.seedvault.WalletContractV1.AUTHORITY_WALLET_PROVIDER
import com.example.walletapidemo.ApplicationDependencyContainer
import com.example.walletapidemo.WalletAPIDemoApplication
import com.example.walletapidemo.data.SeedRepository
import com.example.walletapidemo.usecase.Base58EncodeUseCase
import kotlinx.coroutines.runBlocking
import kotlin.IllegalArgumentException

class WalletContentProvider : ContentProvider() {
    private lateinit var dependencyContainer: ApplicationDependencyContainer

    // TODO: send notifications on Seed repository contents changed

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
    ): Cursor? {
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
        if (!this::dependencyContainer.isInitialized) {
            dependencyContainer = (requireContext().applicationContext as WalletAPIDemoApplication)
                .dependencyContainer
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