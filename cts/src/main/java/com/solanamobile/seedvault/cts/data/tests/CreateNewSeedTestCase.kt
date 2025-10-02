/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
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
import com.solanamobile.seedvault.cts.data.conditioncheckers.NewSeedDoesNotExistChecker
import com.solanamobile.seedvault.cts.data.testdata.ImplementationDetails
import com.solanamobile.seedvault.cts.data.testdata.NewSeed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject

internal class CreateNewSeedTestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    newSeedExistsChecker: NewSeedDoesNotExistChecker,
    newSeed: NewSeed,
    private val implementationDetails: ImplementationDetails,
    @ApplicationContext private val ctx: Context,
    private val logger: TestSessionLogger
) : TestCaseImpl(
    preConditions = listOf(hasSeedVaultPermissionChecker, newSeedExistsChecker)
), ActivityLauncherTestCase {
    override val id: String = "cns"
    override val description: String = "Create a new, random 12-word seed, and verify that it is created and properly enumerates pre-derived accounts"
    override val instructions: String = "When the Create Seed workflow begins, follow the steps to create a new seed. Name the seed '${newSeed.SEED_NAME}', Set the PIN to '${newSeed.SEED_PIN}', and enable biometrics for this seed."

    private class CreateSeedIntentContract : ActivityResultContract<Int, Result<Long>>() {
        override fun createIntent(context: Context, @WalletContractV1.Purpose input: Int): Intent =
            Wallet.createSeed(context, input)

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
        launcher = arc.registerForActivityResult(CreateSeedIntentContract()) { authToken ->
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

        if (!testCreateSeed()) {
            logger.warn("$id: unexpected result while creating seed")
            result = TestResult.FAIL
        }

        if (!testCreateSeedWithUnknownPurpose()) {
            logger.warn("$id: unexpected error while testing create seed with unknown purpose")
            result = TestResult.FAIL
        }

        return result
    }

    private fun testWalletHelperConstructsExpectedIntent(): Boolean {
        val helperIntent = Wallet.createSeed(ctx, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        val directIntent = Intent(WalletContractV1.ACTION_CREATE_SEED).putExtra(
            WalletContractV1.EXTRA_PURPOSE,
            WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
        ).setComponent(implementationDetails.ACTION_CREATE_SEED_COMPONENT_NAME)
        return directIntent.filterEquals(helperIntent)
    }

    private suspend fun testCreateSeed(): Boolean {
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
                c.getInt(1) != WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
            ) return false // unexpected values
        } ?: return false // authToken not found

        // Ensure that all derivation paths are pre-derived in the accounts table
        val found = Array(2) { BooleanArray(MIN_NUM_DEFAULT_DERIVED_ACCOUNTS) }
        ctx.contentResolver.query(
            WalletContractV1.ACCOUNTS_CONTENT_URI,
            WalletContractV1.ACCOUNTS_ALL_COLUMNS,
            Bundle().apply { putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken) },
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val derivationPath = c.getString(1)
                val indices = parsePathIndices(derivationPath)
                if (indices != null && indices.first < 2
                    && indices.second < MIN_NUM_DEFAULT_DERIVED_ACCOUNTS) {
                    found[indices.first][indices.second] = true
                }
            }
        } ?: return false // accounts for authToken not found
        return found.all { inner -> inner.all { it } }
    }

    private suspend fun testCreateSeedWithUnknownPurpose(): Boolean {
        val signal = CompletableDeferred<Long>()
        assert(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        launcher.launch(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION + 1)

        return try {
            signal.await()
            throw IllegalStateException("Expected an error for create seed with unknown purpose")
        } catch (e: IllegalArgumentException) {
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val MIN_NUM_DEFAULT_DERIVED_ACCOUNTS = 50

        private val derivationPathRegex = "bip32:/m/44'/501'/(?<index>[0-9]+)'(?<sub>/0')?".toRegex()

        private fun parsePathIndices(path: String): Pair<Int, Int>? =
            derivationPathRegex.find(path)?.let { matcher ->
                val index = matcher.groups["index"]?.value?.toInt() ?: return@let null
                Pair(if (matcher.groups["sub"]?.value != null) 1 else 0, index)
            }
    }
}