/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import com.solanamobile.seedvault.Wallet
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.WalletContractV1.AuthToken
import com.solanamobile.seedvault.cts.data.ActivityLauncherTestCase
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasSeedVaultPermissionChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasUnauthorizedSeedsChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed24NotAuthorizedChecker
import com.solanamobile.seedvault.cts.data.testdata.ImplementationDetails
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed24
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject

internal class AuthorizeSeed24SagaTestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    hasUnauthorizedSeedsChecker: HasUnauthorizedSeedsChecker,
    knownSeed24NotAuthorizedChecker: KnownSeed24NotAuthorizedChecker,
    @KnownSeed24 knownSeed24: KnownSeed,
    private val logger: TestSessionLogger,
    private val implementationDetails: ImplementationDetails
): ReauthorizeSeed24TestCase(
    hasSeedVaultPermissionChecker,
    hasUnauthorizedSeedsChecker,
    knownSeed24NotAuthorizedChecker,
    knownSeed24,
    logger
) {
    override val id: String = "as24saga"
    override val description: String = "Authorize the '${knownSeed24.SEED_NAME}'"
    override val instructions: String = "On your Saga device rename the previous created seed to '${knownSeed24.SEED_NAME}' before pressing execute\n\nWhen prompted, authorize seed ${knownSeed24.SEED_NAME} using pin ${knownSeed24.SEED_PIN}"

    override suspend fun doExecute(): TestResult {
        if (!implementationDetails.IS_LEGACY_IMPLEMENTATION) {
            logger.warn("Running Saga only test on non-saga device.")
            return TestResult.FAIL
        }
        return super.doExecute()
    }
}

internal open class ReauthorizeSeed24TestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    hasUnauthorizedSeedsChecker: HasUnauthorizedSeedsChecker,
    private val knownSeed24NotAuthorizedChecker: KnownSeed24NotAuthorizedChecker,
    @KnownSeed24 private val knownSeed24: KnownSeed,
    private val logger: TestSessionLogger
) : TestCaseImpl(
    preConditions = listOf(hasSeedVaultPermissionChecker, hasUnauthorizedSeedsChecker, knownSeed24NotAuthorizedChecker)
), ActivityLauncherTestCase {
    override val id: String = "rs24"
    override val description: String = "Reauthorize the previously deauthorized seed '${knownSeed24.SEED_NAME}'"
    override val instructions: String = "When prompted, authorize seed ${knownSeed24.SEED_NAME} using pin ${knownSeed24.SEED_PIN}"

    private class AuthorizeSeedIntentContract : ActivityResultContract<Int, Result<Long>>() {
        override fun createIntent(context: Context, @WalletContractV1.Purpose input: Int): Intent =
            Wallet.authorizeSeed(input)

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
        launcher = arc.registerForActivityResult(AuthorizeSeedIntentContract()) { authToken ->
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

        // NOTE: run this test first, as it is undefined what is returned when both the purpose is
        // invalid AND there are no seeds available for authorization
        if (!testAuthorizeSeedWithInvalidPurpose()) {
            logger.warn("$id: unexpected error while testing import seed with unknown purpose")
            result = TestResult.FAIL
        }

        if (!testReauthorizeSeed()) {
            logger.warn("$id: seed ${knownSeed24.SEED_NAME} was not reauthorized")
            result = TestResult.FAIL
        }

        return result
    }

    private fun testWalletHelperConstructsExpectedIntent(): Boolean {
        val helperIntent = Wallet.authorizeSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        val directIntent = Intent(WalletContractV1.ACTION_AUTHORIZE_SEED_ACCESS)
            .setPackage(WalletContractV1.PACKAGE_SEED_VAULT)
            .putExtra(
                WalletContractV1.EXTRA_PURPOSE,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
            )
        return directIntent.filterEquals(helperIntent)
    }

    private suspend fun testReauthorizeSeed(): Boolean {
        @AuthToken val preAuthorizeAuthToken = knownSeed24NotAuthorizedChecker.findMatchingSeed()
        if (preAuthorizeAuthToken != null) {
            return false
        }

        val signal = CompletableDeferred<Long>()
        assert(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        launcher.launch(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)

        @AuthToken val authToken = try {
            signal.await()
        } catch (_: Exception) {
            return false
        }

        if (authToken < 0) {
            return false
        }

        @AuthToken val postAuthorizeAuthToken = knownSeed24NotAuthorizedChecker.findMatchingSeed()

        return (authToken == postAuthorizeAuthToken)
    }

    private suspend fun testAuthorizeSeedWithInvalidPurpose(): Boolean {
        val signal = CompletableDeferred<Long>()
        assert(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        launcher.launch(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION + 1)

        return try {
            signal.await()
            throw IllegalStateException("Expected an error for authorize seed with unknown purpose")
        } catch (_: IllegalArgumentException) {
            true
        } catch (_: Exception) {
            false
        }
    }
}