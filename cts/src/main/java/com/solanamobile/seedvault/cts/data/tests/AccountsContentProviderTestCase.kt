/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import androidx.core.content.contentValuesOf
import androidx.core.os.bundleOf
import com.solanamobile.seedvault.Wallet
import com.solanamobile.seedvault.Wallet.NotModifiedException
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.SagaChecker
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.AuthorizedSeedsChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasSeedVaultPermissionChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed12AuthorizedChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed24AuthorizedChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.accountId
import com.solanamobile.seedvault.cts.data.conditioncheckers.authToken
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed12
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed24
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import javax.inject.Inject

internal abstract class AccountsContentProviderTestCase(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    private val knownSeedAuthorizedChecker: AuthorizedSeedsChecker,
    @ApplicationContext private val ctx: Context,
    private val logger: TestSessionLogger,
    private val sagaChecker: SagaChecker
) : TestCaseImpl(
    preConditions = listOf(
        hasSeedVaultPermissionChecker,
        knownSeedAuthorizedChecker,
    )
) {

    override suspend fun doExecute(): TestResult {
        val accountWithAuthToken = knownSeedAuthorizedChecker.findMatchingSeedAndAccount()
        @WalletContractV1.AuthToken val authToken = accountWithAuthToken?.authToken
        if (authToken == null) {
            logger.warn("$id: Failed locating seed '${knownSeedAuthorizedChecker.seedName}' for accounts")
            return TestResult.FAIL
        }

        @WalletContractV1.AccountId val accountId = accountWithAuthToken.accountId
        val seedName = knownSeedAuthorizedChecker.seedName
        val verificationDerivationPath = knownSeedAuthorizedChecker.verificationDerivationPath
        val verificationPublicKeyBase58 = knownSeedAuthorizedChecker.verificationPublicKeyBase58

        var result = TestResult.PASS

        if (!testTable()) {
            logger.warn("$id: Unfiltered table returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableIdFilterAuthToken(authToken)) {
            logger.warn("$id: ID-filtered table with '$seedName' returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableIdFilterUnknownAuthToken()) {
            logger.warn("$id: ID-filtered table with unknown auth token returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableIdFilterUnknownAccountId(authToken)) {
            logger.warn("$id: ID-filtered table with unknown account ID returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterDerivationPath(authToken, verificationDerivationPath, verificationPublicKeyBase58)) {
            logger.warn("$id: Query-filtered table by derivation path returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterUnknownDerivationPath(authToken)) {
            logger.warn("$id: Query-filtered table with unknown derivation path returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterPublicKeyEncoded(authToken, verificationPublicKeyBase58)) {
            logger.warn("$id: Query-filtered table by public key for '$seedName' returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterUnknownPublicKey(authToken)) {
            logger.warn("$id: Query-filtered table with unknown public key returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableUpdateAccountIsUserWallet(authToken, accountId)) {
            logger.warn("$id: table update account is user wallet returned unexpected value")
            result = TestResult.FAIL
        }

        if (!sagaChecker.isSaga() && !testTableUpdateAccountIsUserWalletUnknownAuthToken()) {
            logger.warn("$id: table update account is user wallet with unknown auth token returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableUpdateAccountIsUserWalletUnknownId(authToken)) {
            logger.warn("$id: table update account is user wallet with unknown account ID returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableUpdateAccountIsValid(authToken, accountId)) {
            logger.warn("$id: table update account is valid returned unexpected value")
            result = TestResult.FAIL
        }

        if (!sagaChecker.isSaga() && !testTableUpdateAccountIsValidUnknownAuthToken()) {
            logger.warn("$id: table update account is valid with unknown auth token returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableUpdateAccountIsValidUnknownId(authToken)) {
            logger.warn("$id: table update account is valid with unknown account ID returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelper(authToken)) {
            logger.warn("$id: Unfiltered Wallet helper query returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperExpressionFilterPublicKeyEncoded(
                authToken,
                verificationPublicKeyBase58
            )
        ) {
            logger.warn("$id: Wallet helper query filtered by public key (encoded) returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperExpressionFilterUnknownPublicKey(authToken)) {
            logger.warn("$id: Wallet helper query filtered with unknown public key (encoded) returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperExpressionFilterDerivationPath(
                authToken,
                verificationDerivationPath,
                verificationPublicKeyBase58
            )
        ) {
            logger.warn("$id: Wallet helper query filtered by derivation path returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperExpressionFilterUnknownDerivationPath(authToken)) {
            logger.warn("$id: Wallet helper query filtered with unknown derivation path returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperUpdateAccountName(authToken, accountId)) {
            logger.warn("$id: Wallet helper update account name returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperUpdateAccountNameUnknownAuthToken()) {
            logger.warn("$id: Wallet helper update account is user wallet with unknown auth token returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperUpdateAccountNameUnknownId(authToken)) {
            logger.warn("$id: Wallet helper update account is user wallet with unknown account ID returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperUpdateAccountIsUserWallet(authToken, accountId)) {
            logger.warn("$id: Wallet helper update account is user wallet returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperUpdateAccountIsUserWalletUnknownAuthToken()) {
            logger.warn("$id: Wallet helper update account is user wallet with unknown auth token returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperUpdateAccountIsUserWalletUnknownId(authToken)) {
            logger.warn("$id: Wallet helper update account is user wallet with unknown account ID returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperUpdateAccountIsValid(authToken, accountId)) {
            logger.warn("$id: Wallet helper update account is valid returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperUpdateAccountIsValidUnknownAuthToken()) {
            logger.warn("$id: Wallet helper update account is valid with unknown auth token returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperUpdateAccountIsValidUnknownId(authToken)) {
            logger.warn("$id: Wallet helper update account is valid with unknown account ID returned unexpected value")
            result = TestResult.FAIL
        }

        return result
    }

    private fun testTable(): Boolean {
        return try {
            val cursor = ctx.contentResolver.query(
                WalletContractV1.ACCOUNTS_CONTENT_URI,
                WalletContractV1.ACCOUNTS_ALL_COLUMNS,
                null,
                null,
                null,
                null
            )
            if (sagaChecker.isSaga() && cursor?.count == 0) {
                throw IllegalArgumentException("No accounts found")
            }
            cursor?.close()
            false // Unexpected result - should throw IllegalArgumentException
        } catch (e: IllegalArgumentException) {
            true // Expected
        }
    }

    private fun testTableIdFilterAuthToken(@WalletContractV1.AuthToken authToken: Long): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.ACCOUNTS_CONTENT_URI,
            WalletContractV1.ACCOUNTS_ALL_COLUMNS,
            bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken),
            null
        )?.use { c ->
            while(c.moveToNext()) {
                try {
                    // check derivation path is uri
                    Uri.parse(c.getString(1))
                    // check public key is 32 bytes
                    if (c.getBlob(2).size != 32) return false
                    // check public key encoded is non-zero
                    if (c.getString(3).isNullOrEmpty()) return false
                    // check account name is not null
                    if (c.getString(4) == null) return false
                    // check isUserWallet flag is 0 or 1
                    if (c.getShort(5) !in 0..1) return false
                    // check isValid flag is 0 or 1
                    if (c.getShort(6) !in 0..1) return false
                } catch (_: Exception) {
                    return false
                }
            }
            c.count >= MIN_NUM_DEFAULT_DERIVED_ACCOUNTS
        } ?: false
    }

    private fun testTableIdFilterUnknownAuthToken(): Boolean {
        return try {
            val cursor = ctx.contentResolver.query(
                WalletContractV1.ACCOUNTS_CONTENT_URI,
                WalletContractV1.ACCOUNTS_ALL_COLUMNS,
                bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to -2975244312860459623L),
                null
            )
            if (sagaChecker.isSaga() && cursor?.count == 0) {
                throw IllegalArgumentException("No accounts found")
            }
            cursor?.close()
            false // Unexpected result - should throw IllegalArgumentException
        } catch (e: IllegalArgumentException) {
            true // Expected
        }
    }

    private fun testTableIdFilterUnknownAccountId(@WalletContractV1.AuthToken authToken: Long): Boolean {
        return ctx.contentResolver.query(
            ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, 925658928778235234L),
            WalletContractV1.ACCOUNTS_ALL_COLUMNS,
            bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken),
            null
        )?.use { c ->
            c.count == 0
        } ?: false
    }

    private fun testTableExpressionFilterDerivationPath(
        @WalletContractV1.AuthToken authToken: Long,
        derivationPath: String,
        publicKeyBase58: String
    ): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.ACCOUNTS_CONTENT_URI,
            arrayOf(WalletContractV1.ACCOUNTS_PUBLIC_KEY_ENCODED),
            bundleOf(
                WalletContractV1.EXTRA_AUTH_TOKEN to authToken,
                ContentResolver.QUERY_ARG_SQL_SELECTION to "${WalletContractV1.ACCOUNTS_BIP32_DERIVATION_PATH}=?",
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to arrayOf(derivationPath)
            ),
            null
        )?.use { c ->
            c.count == 1 && c.moveToFirst() && c.getString(0) == publicKeyBase58
        } ?: false
    }

    private fun testTableExpressionFilterUnknownDerivationPath(@WalletContractV1.AuthToken authToken: Long): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.ACCOUNTS_CONTENT_URI,
            WalletContractV1.ACCOUNTS_ALL_COLUMNS,
            bundleOf(
                WalletContractV1.EXTRA_AUTH_TOKEN to authToken,
                ContentResolver.QUERY_ARG_SQL_SELECTION to "${WalletContractV1.ACCOUNTS_BIP32_DERIVATION_PATH}=?",
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to arrayOf("invalid/derivation/path/123")
            ),
            null
        )?.use { it.count == 0 } ?: false
    }

    private fun testTableExpressionFilterPublicKeyEncoded(
        @WalletContractV1.AuthToken authToken: Long,
        publicKeyBase58: String
    ): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.ACCOUNTS_CONTENT_URI,
            arrayOf(WalletContractV1.ACCOUNTS_PUBLIC_KEY_ENCODED),
            bundleOf(
                WalletContractV1.EXTRA_AUTH_TOKEN to authToken,
                ContentResolver.QUERY_ARG_SQL_SELECTION to "${WalletContractV1.ACCOUNTS_PUBLIC_KEY_ENCODED}=?",
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to arrayOf(publicKeyBase58)
            ),
            null
        )?.use { c ->
            c.count == 1 && c.moveToFirst() && c.getString(0) == publicKeyBase58
        } ?: false
    }

    private fun testTableExpressionFilterUnknownPublicKey(@WalletContractV1.AuthToken authToken: Long): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.ACCOUNTS_CONTENT_URI,
            WalletContractV1.ACCOUNTS_ALL_COLUMNS,
            bundleOf(
                WalletContractV1.EXTRA_AUTH_TOKEN to authToken,
                ContentResolver.QUERY_ARG_SQL_SELECTION to "${WalletContractV1.ACCOUNTS_PUBLIC_KEY_ENCODED}=?",
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to arrayOf("11111111111111111111111111111111")
            ),
            null
        )?.use { it.count == 0 } ?: false
    }

    private suspend fun testTableUpdateAccountIsUserWallet(
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId accountId: Long
    ): Boolean {
        if (ctx.contentResolver.update(
            ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId),
            contentValuesOf(WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET to 0.toShort()),
            bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken)
        ) == 0) return false

        val signal = contentChangeSignal(ctx, ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId)) {
            ctx.contentResolver.query(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId),
                arrayOf(WalletContractV1.ACCOUNTS_ACCOUNT_ID, WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET),
                bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken),
                null
            )?.use { c ->
                c.count == 1
                        && c.moveToFirst()
                        && c.getLong(0) == accountId
                        && c.getShort(1) == 1.toShort()
            } ?: false
        }

        if (ctx.contentResolver.update(
            ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId),
            contentValuesOf(WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET to 1.toShort()),
            bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken)
        ) == 0) return false

        return try {
            signal.await()
        } catch (_: Exception) {
            false
        }
    }

    private fun testTableUpdateAccountIsUserWalletUnknownAuthToken(): Boolean {
        return try {
            ctx.contentResolver.update(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, 5638552912784834639L),
                contentValuesOf(WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET to 1.toShort()),
                bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to 1956124295627289475L)
            )
            false // Unexpected result - should throw IllegalArgumentException
        } catch (e: IllegalArgumentException) {
            true // Expected
        }
    }

    private fun testTableUpdateAccountIsUserWalletUnknownId(@WalletContractV1.AuthToken authToken: Long): Boolean {
        return ctx.contentResolver.update(
            ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, 1956124295627289475L),
            contentValuesOf(WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET to 1.toShort()),
            bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken)
        ) == 0
    }

    private suspend fun testTableUpdateAccountIsValid(
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId accountId: Long
    ): Boolean {
        if (ctx.contentResolver.update(
            ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId),
            contentValuesOf(WalletContractV1.ACCOUNTS_ACCOUNT_IS_VALID to 0.toShort()),
            bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken)
        ) == 0) return false

        val signal = contentChangeSignal(ctx, ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId)) {
            ctx.contentResolver.query(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId),
                arrayOf(WalletContractV1.ACCOUNTS_ACCOUNT_ID, WalletContractV1.ACCOUNTS_ACCOUNT_IS_VALID),
                bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken),
                null
            )?.use { c ->
                c.count == 1
                        && c.moveToFirst()
                        && c.getLong(0) == accountId
                        && c.getShort(1) == 1.toShort()
            } ?: false
        }

        if (ctx.contentResolver.update(
            ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId),
            contentValuesOf(WalletContractV1.ACCOUNTS_ACCOUNT_IS_VALID to 1.toShort()),
            bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken)
        ) == 0) return false

        return try {
            signal.await()
        } catch (_: Exception) {
            false
        }
    }

    private fun testTableUpdateAccountIsValidUnknownAuthToken(): Boolean {
        return try {
            ctx.contentResolver.update(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, 5638552912784834639L),
                contentValuesOf(WalletContractV1.ACCOUNTS_ACCOUNT_IS_VALID to 1.toShort()),
                bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to 1956124295627289475L)
            )
            false // Unexpected result - should throw IllegalArgumentException
        } catch (e: IllegalArgumentException) {
            true // Expected
        }
    }

    private fun testTableUpdateAccountIsValidUnknownId(@WalletContractV1.AuthToken authToken: Long): Boolean {
        return ctx.contentResolver.update(
            ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, 1956124295627289475L),
            contentValuesOf(WalletContractV1.ACCOUNTS_ACCOUNT_IS_VALID to 1.toShort()),
            bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken)
        ) == 0
    }

    private fun testWalletHelper(
        authToken: Long
    ): Boolean {
        return Wallet.getAccounts(
            ctx,
            authToken,
            WalletContractV1.ACCOUNTS_ALL_COLUMNS
        )?.use { c ->
            c.count >= MIN_NUM_DEFAULT_DERIVED_ACCOUNTS
            // TODO: what else should be checked here?
        } ?: false
    }

    private fun testWalletHelperExpressionFilterPublicKeyEncoded(
        authToken: Long,
        publicKeyBase58: String
    ): Boolean {
        return Wallet.getAccounts(
            ctx,
            authToken,
            arrayOf(WalletContractV1.ACCOUNTS_PUBLIC_KEY_ENCODED),
            WalletContractV1.ACCOUNTS_PUBLIC_KEY_ENCODED,
            publicKeyBase58
        )?.use { c ->
            c.count == 1 && c.moveToFirst() && c.getString(0) == publicKeyBase58
        } ?: false
    }

    private fun testWalletHelperExpressionFilterUnknownPublicKey(@WalletContractV1.AuthToken authToken: Long): Boolean {
        return Wallet.getAccounts(
            ctx,
            authToken,
            WalletContractV1.ACCOUNTS_ALL_COLUMNS,
            WalletContractV1.ACCOUNTS_PUBLIC_KEY_ENCODED,
            "Vote111111111111111111111111111111111111111"
        )?.use { it.count == 0 } ?: false
    }

    private fun testWalletHelperExpressionFilterDerivationPath(
        authToken: Long,
        derivationPath: String,
        publicKeyBase58: String
    ): Boolean {
        return Wallet.getAccounts(
            ctx,
            authToken,
            arrayOf(WalletContractV1.ACCOUNTS_PUBLIC_KEY_ENCODED),
            WalletContractV1.ACCOUNTS_BIP32_DERIVATION_PATH,
            derivationPath
        )?.use { c ->
            c.count == 1 && c.moveToFirst() && c.getString(0) == publicKeyBase58
        } ?: false
    }

    private fun testWalletHelperExpressionFilterUnknownDerivationPath(@WalletContractV1.AuthToken authToken: Long): Boolean {
        return Wallet.getAccounts(
            ctx,
            authToken,
            WalletContractV1.ACCOUNTS_ALL_COLUMNS,
            WalletContractV1.ACCOUNTS_BIP32_DERIVATION_PATH,
            "some/path/123'"
        )?.use { it.count == 0} ?: false
    }

    private suspend fun testWalletHelperUpdateAccountName(
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId accountId: Long
    ): Boolean {
        try {
            Wallet.updateAccountName(ctx, authToken, accountId, "")
        } catch (_: NotModifiedException) {}

        val signal = contentChangeSignal(ctx, ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId)) {
            ctx.contentResolver.query(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId),
                arrayOf(WalletContractV1.ACCOUNTS_ACCOUNT_ID, WalletContractV1.ACCOUNTS_ACCOUNT_NAME),
                bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken),
                null
            )?.use { c ->
                c.count == 1
                        && c.moveToFirst()
                        && c.getLong(0) == accountId
                        && c.getString(1) == "NewAccountName"
            } ?: false
        }

        Wallet.updateAccountName(ctx, authToken, accountId, "NewAccountName")

        return try {
            signal.await()
        } catch (_: Exception) {
            false
        }
    }

    private fun testWalletHelperUpdateAccountNameUnknownAuthToken(): Boolean {
        return try {
            Wallet.updateAccountName(ctx, 1956124295627289475L, 5638552912784834639L, "NewAccountName")
            false // Unexpected result - should throw IllegalArgumentException
        } catch (e: IllegalArgumentException) {
            true // Expected
        } catch (e: NotModifiedException) {
            // Only expected on saga.
            sagaChecker.isSaga()
        }
    }

    private fun testWalletHelperUpdateAccountNameUnknownId(@WalletContractV1.AuthToken authToken: Long): Boolean {
        return try {
            Wallet.updateAccountName(ctx, authToken, 1956124295627289475L, "NewAccountName")
            false // Unexpected result - should throw NotModifiedException
        } catch (e: NotModifiedException) {
            true // Expected
        }
    }

    private suspend fun testWalletHelperUpdateAccountIsUserWallet(
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId accountId: Long
    ): Boolean {
        try {
            Wallet.updateAccountIsUserWallet(ctx, authToken, accountId, false)
        } catch (_: NotModifiedException) {}

        val signal = contentChangeSignal(ctx, ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId)) {
            ctx.contentResolver.query(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId),
                arrayOf(WalletContractV1.ACCOUNTS_ACCOUNT_ID, WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET),
                bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken),
                null
            )?.use { c ->
                c.count == 1
                        && c.moveToFirst()
                        && c.getLong(0) == accountId
                        && c.getShort(1) == 1.toShort()
            } ?: false
        }

        Wallet.updateAccountIsUserWallet(ctx, authToken, accountId, true)

        return try {
            signal.await()
        } catch (_: Exception) {
            false
        }
    }

    private fun testWalletHelperUpdateAccountIsUserWalletUnknownAuthToken(): Boolean {
        return try {
            Wallet.updateAccountIsUserWallet(ctx, 1956124295627289475L, 5638552912784834639L, true)
            false // Unexpected result - should throw IllegalArgumentException
        } catch (e: IllegalArgumentException) {
            true // Expected
        } catch (e: NotModifiedException) {
            // Only expected on saga.
            sagaChecker.isSaga()
        }
    }

    private fun testWalletHelperUpdateAccountIsUserWalletUnknownId(@WalletContractV1.AuthToken authToken: Long): Boolean {
        return try {
            Wallet.updateAccountIsUserWallet(ctx, authToken, 1956124295627289475L, true)
            false // Unexpected result - should throw NotModifiedException
        } catch (e: NotModifiedException) {
            true // Expected
        }
    }

    private suspend fun testWalletHelperUpdateAccountIsValid(
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId accountId: Long
    ): Boolean {
        try {
            Wallet.updateAccountIsValid(ctx, authToken, accountId, false)
        } catch (_: NotModifiedException) {}

        val signal = contentChangeSignal(ctx, ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId)) {
            ctx.contentResolver.query(
                ContentUris.withAppendedId(WalletContractV1.ACCOUNTS_CONTENT_URI, accountId),
                arrayOf(WalletContractV1.ACCOUNTS_ACCOUNT_ID, WalletContractV1.ACCOUNTS_ACCOUNT_IS_VALID),
                bundleOf(WalletContractV1.EXTRA_AUTH_TOKEN to authToken),
                null
            )?.use { c ->
                c.count == 1
                        && c.moveToFirst()
                        && c.getLong(0) == accountId
                        && c.getShort(1) == 1.toShort()
            } ?: false
        }

        Wallet.updateAccountIsValid(ctx, authToken, accountId, true)

        return try { signal.await() } catch (_: Exception) { false }
    }

    private fun testWalletHelperUpdateAccountIsValidUnknownAuthToken(): Boolean {
        return try {
            Wallet.updateAccountIsValid(ctx, 1956124295627289475L, 5638552912784834639L, true)
            false // Unexpected result - should throw IllegalArgumentException
        } catch (e: IllegalArgumentException) {
            true // Expected
        } catch (e: NotModifiedException) {
            // Only expected on saga.
            sagaChecker.isSaga()
        }
    }

    private fun testWalletHelperUpdateAccountIsValidUnknownId(@WalletContractV1.AuthToken authToken: Long): Boolean {
        return try {
            Wallet.updateAccountIsValid(ctx, authToken, 1956124295627289475L, true)
            false // Unexpected result - should throw NotModifiedException
        } catch (e: NotModifiedException) {
            true // Expected
        }
    }

    companion object {
        private const val MIN_NUM_DEFAULT_DERIVED_ACCOUNTS = 50

        private fun checkAccountValues(
            c: Cursor,
            publicKey: ByteArray,
            publicKeyBase58: String
        ): Boolean {
            return c.getBlob(2).contentEquals(publicKey) && c.getString(3) == publicKeyBase58
        }

        private fun contentChangeSignal(ctx: Context, uri: Uri, onChange: () -> Boolean = { true }): Deferred<Boolean> {
            return CompletableDeferred<Boolean>().also { signal ->
                ctx.contentResolver.registerContentObserver(uri, false,
                    object : ContentObserver(Handler(ctx.mainLooper)) {
                        override fun onChange(selfChange: Boolean) {
                            super.onChange(selfChange)
                            ctx.contentResolver.unregisterContentObserver(this)
                            signal.complete(onChange())
                        }
                    })
            }
        }
    }
}

internal class KnownSeed12AccountsContentProviderTestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    @ApplicationContext private val ctx: Context,
    logger: TestSessionLogger,
    sagaChecker: SagaChecker
) : AccountsContentProviderTestCase(
    hasSeedVaultPermissionChecker,
    knownSeed12AuthorizedChecker,
    ctx,
    logger,
    sagaChecker
) {
    override val id: String = "ks12acp"
    override val description: String =
        "Verify content provider behavior for ${WalletContractV1.ACCOUNTS_TABLE} when seed '${KnownSeed12.SEED_NAME}' is authorized"
    override val instructions: String = ""
}

internal class KnownSeed24AccountsContentProviderTestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    knownSeed24AuthorizedChecker: KnownSeed24AuthorizedChecker,
    @ApplicationContext private val ctx: Context,
    logger: TestSessionLogger,
    sagaChecker: SagaChecker
) : AccountsContentProviderTestCase(
    hasSeedVaultPermissionChecker,
    knownSeed24AuthorizedChecker,
    ctx,
    logger,
    sagaChecker
) {
    override val id: String = "ks24acp"
    override val description: String =
        "Verify content provider behavior for ${WalletContractV1.ACCOUNTS_TABLE} when seed '${KnownSeed24.SEED_NAME}' is authorized"
    override val instructions: String = ""
}