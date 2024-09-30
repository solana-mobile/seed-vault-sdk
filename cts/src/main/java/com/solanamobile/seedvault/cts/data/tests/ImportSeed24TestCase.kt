/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import com.solanamobile.seedvault.Wallet
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.ActivityLauncherTestCase
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasSeedVaultPermissionChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed24NotAuthorizedChecker
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed24
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject

internal class ImportSeed24TestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    knownSeed24NotAuthorizedChecker: KnownSeed24NotAuthorizedChecker,
    @ApplicationContext private val ctx: Context,
    private val logger: TestSessionLogger
) : TestCaseImpl(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed24NotAuthorizedChecker)
), ActivityLauncherTestCase {
    override val id: String = "is24"
    override val description: String = "Test importing a 24-word seed phrase"
    override val instructions: String = "When the Import Seed workflow begins, import the following 24-word seed phrase: '${KnownSeed24.SEED_PHRASE}'. Name the seed '${KnownSeed24.SEED_NAME}', Set the PIN to '${KnownSeed24.SEED_PIN}', and do not enable biometrics for this seed."
    
    private class ImportSeedIntentContract : ActivityResultContract<Int, Result<Long>>() {
        override fun createIntent(context: Context, @WalletContractV1.Purpose input: Int): Intent =
            Wallet.importSeed(input)

        override fun parseResult(resultCode: Int, intent: Intent?): Result<Long> {
            return when (resultCode) {
                Activity.RESULT_OK -> {
                    intent!!.extras?.getLong(WalletContractV1.EXTRA_AUTH_TOKEN, -1)
                        ?.takeUnless { it == -1L }?.let { authToken -> Result.success(authToken) }
                        ?: Result.failure(NoSuchElementException("Result Intent does not contain extra '${WalletContractV1.EXTRA_AUTH_TOKEN}'"))
                }
                WalletContractV1.RESULT_INVALID_PURPOSE -> Result.failure(IllegalArgumentException("Unsupported value for extra `${WalletContractV1.EXTRA_PURPOSE}`"))
                else -> Result.failure(IllegalStateException("Received an unexpected result"))
            }
        }
    }

    private lateinit var launcher: ActivityResultLauncher<Int>
    private var completionSignal: CompletableDeferred<Long>? = null

    override fun registerActivityLauncher(arc: ActivityResultCaller) {
        launcher = arc.registerForActivityResult(ImportSeedIntentContract()) { authToken ->
            completionSignal?.run {
                completionSignal = null
                authToken.fold(
                    onSuccess = { complete(it) },
                    onFailure = { completeExceptionally(it) })
            }
        }
    }

    override suspend fun doExecute(): TestResult {
        var result = TestResult.PASS

        if (!testWalletHelperConstructsExpectedIntent()) {
            logger.warn("$id: wallet helper constructed unexpected Intent")
            result = TestResult.FAIL
        }

        if (!testImportSeed()) {
            logger.warn("$id: unexpected result while importing seed")
            result = TestResult.FAIL
        }

        if (!testImportSeedWithUnknownPurpose()) {
            logger.warn("$id: unexpected error while testing import seed with unknown purpose")
            result = TestResult.FAIL
        }

        return result
    }

    private fun testWalletHelperConstructsExpectedIntent(): Boolean {
        val helperIntent = Wallet.importSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        val directIntent = Intent(WalletContractV1.ACTION_IMPORT_SEED).putExtra(
            WalletContractV1.EXTRA_PURPOSE,
            WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
        )
        return directIntent.filterEquals(helperIntent)
    }

    private suspend fun testImportSeed(): Boolean {
        val signal = CompletableDeferred<Long>()
        assert(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        launcher.launch(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)

        @WalletContractV1.AuthToken val authToken = try {
            signal.await()
        } catch (_: Exception) {
            return false
        }

        if (authToken < 0) {
            return false
        }

        // Ensure that the auth token is listed in the accounts table
        ctx.contentResolver.query(
            ContentUris.withAppendedId(
                WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI, authToken
            ), WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS, null, null
        )?.use { c ->
            if (c.count != 1 ||
                !c.moveToNext() ||
                c.getLong(0) != authToken ||
                c.getInt(1) != WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION ||
                c.getString(2) != KnownSeed24.SEED_NAME
            ) return false // unexpected values
        } ?: return false // authToken not found

        // Ensure that all 4 known seed derivation paths are pre-derived in the accounts table
        val found = BooleanArray(4)
        ctx.contentResolver.query(
            WalletContractV1.ACCOUNTS_CONTENT_URI,
            WalletContractV1.ACCOUNTS_ALL_COLUMNS,
            Bundle().apply { putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken) },
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val derivationPath = Uri.parse(c.getString(1))
                when (derivationPath) {
                    KnownSeed24.DERIVATION_PATH_0 -> found[0] = checkAccountValues(
                        c,
                        KnownSeed24.DERIVATION_PATH_0_PUBLIC_KEY,
                        KnownSeed24.DERIVATION_PATH_0_PUBLIC_KEY_BASE58
                    )
                    KnownSeed24.DERIVATION_PATH_1 -> found[1] = checkAccountValues(
                        c,
                        KnownSeed24.DERIVATION_PATH_1_PUBLIC_KEY,
                        KnownSeed24.DERIVATION_PATH_1_PUBLIC_KEY_BASE58
                    )
                    KnownSeed24.DERIVATION_PATH_2 -> found[2] = checkAccountValues(
                        c,
                        KnownSeed24.DERIVATION_PATH_2_PUBLIC_KEY,
                        KnownSeed24.DERIVATION_PATH_2_PUBLIC_KEY_BASE58
                    )
                    KnownSeed24.DERIVATION_PATH_3 -> found[3] = checkAccountValues(
                        c,
                        KnownSeed24.DERIVATION_PATH_3_PUBLIC_KEY,
                        KnownSeed24.DERIVATION_PATH_3_PUBLIC_KEY_BASE58
                    )
                }
            }
        } ?: return false // accounts for authToken not found
        return found.all { it }
    }

    private suspend fun testImportSeedWithUnknownPurpose(): Boolean {
        val signal = CompletableDeferred<Long>()
        assert(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        launcher.launch(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION + 1)

        return try {
            signal.await()
            throw IllegalStateException("Expected an error for import seed with unknown purpose")
        } catch (e: IllegalArgumentException) {
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private fun checkAccountValues(
            c: Cursor,
            publicKey: ByteArray,
            publicKeyBase58: String
        ): Boolean {
            return c.getBlob(2).contentEquals(publicKey) && c.getString(3) == publicKeyBase58
        }
    }
}