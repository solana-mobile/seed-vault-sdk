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
import com.solanamobile.seedvault.cts.data.ActivityLauncherTestCase
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasSeedVaultPermissionChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed12AuthorizedChecker
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed12
import com.solanamobile.seedvault.cts.data.tests.ShowSeedSettingsTestCase.ShowSeedSettingsIntentContract
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.time.withTimeout
import java.time.Duration
import javax.inject.Inject

internal class ShowSeedSettingsTestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    private val knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    private val logger: TestSessionLogger
) : TestCaseImpl(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker)
), ActivityLauncherTestCase {
    override val id: String = "sss"
    override val description: String = "Show seed settings UI for an authorized seed"
    override val instructions: String =
        "When the seed settings UI appears, press the back key (or make the back gesture) to dismiss the settings UI"

    private class ShowSeedSettingsIntentContract : ActivityResultContract<Intent, Result<Any>>() {
        override fun createIntent(
            context: Context, @WalletContractV1.AuthToken input: Intent
        ): Intent = input

        override fun parseResult(resultCode: Int, intent: Intent?): Result<Any> {
            return when (resultCode) {
                Activity.RESULT_OK, Activity.RESULT_CANCELED -> Result.success(Unit)
                WalletContractV1.RESULT_INVALID_AUTH_TOKEN -> Result.failure(
                    IllegalArgumentException("Unknown or no value for extra ${WalletContractV1.EXTRA_AUTH_TOKEN}")
                )

                else -> Result.failure(IllegalStateException("Received an unexpected result $resultCode"))
            }
        }
    }

    private lateinit var launcher: ActivityResultLauncher<Intent>
    private var completionSignal: CompletableDeferred<Any>? = null

    override fun registerActivityLauncher(arc: ActivityResultCaller) {
        launcher = arc.registerForActivityResult(ShowSeedSettingsIntentContract()) { result ->
            completionSignal?.run {
                completionSignal = null
                result.fold(onSuccess = { complete(it) }, onFailure = { completeExceptionally(it) })
            }
        }
    }

    override suspend fun doExecute(): TestResult {
        var result = TestResult.PASS

        if (!testShowSettingsForUnknownSeedReturnsError()) {
            logger.warn("testShowSettingsForUnknownSeedReturnsError failed")
            result = TestResult.FAIL
        }

        if (!testShowSettingsForNoSeedReturnsError()) {
            logger.warn("testShowSettingsForNoSeedReturnsError failed")
            result = TestResult.FAIL
        }

        if (!testShowSettingsForInvalidSeedReturnsError()) {
            logger.warn("testShowSettingsForInvalidSeedReturnsError failed")
            result = TestResult.FAIL
        }

        if (!testShowSettingsSuccess()) {
            logger.warn("testShowSettingsSuccess failed")
            result = TestResult.FAIL
        }

        if (!testWalletHelperConstructsCorrectIntents()) {
            logger.warn("testWalletHelperConstructsCorrectIntents failed")
            result = TestResult.FAIL
        }

        if (!testWalletHelperGeneratesExpectedResultCodeResponses()) {
            logger.warn("testWalletHelperGeneratesExpectedResultCodeResponses failed")
            result = TestResult.FAIL
        }

        return result
    }

    private suspend fun testShowSettingsForUnknownSeedReturnsError(): Boolean {
        val signal = CompletableDeferred<Any>()
        check(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        return try {
            launcher.launch(
                Intent().setAction(WalletContractV1.ACTION_SEED_SETTINGS)
                    .putExtra(WalletContractV1.EXTRA_AUTH_TOKEN, UNKNOWN_AUTH_TOKEN)
            )
            withTimeout(Duration.ofMillis(200)) { // Should complete nearly instantaneously
                signal.await()
            }
            logger.warn("Result was success; expected ${WalletContractV1.RESULT_INVALID_AUTH_TOKEN}")
            false
        } catch (e: IllegalArgumentException) {
            true
        } catch (e: Exception) {
            logger.warn("Unexpected failure for unknown auth token", e)
            false
        }
    }

    private suspend fun testShowSettingsForNoSeedReturnsError(): Boolean {
        val signal = CompletableDeferred<Any>()
        check(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        return try {
            launcher.launch(Intent().setAction(WalletContractV1.ACTION_SEED_SETTINGS))
            withTimeout(Duration.ofMillis(200)) { // Should complete nearly instantaneously
                signal.await()
            }
            logger.warn("Result was success; expected ${WalletContractV1.RESULT_INVALID_AUTH_TOKEN}")
            false
        } catch (e: IllegalArgumentException) {
            true
        } catch (e: Exception) {
            logger.warn("Unexpected failure for no auth token", e)
            false
        }
    }

    private suspend fun testShowSettingsForInvalidSeedReturnsError(): Boolean {
        val signal = CompletableDeferred<Any>()
        check(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        return try {
            launcher.launch(
                Intent().setAction(WalletContractV1.ACTION_SEED_SETTINGS)
                    .putExtra(WalletContractV1.EXTRA_AUTH_TOKEN, -1)
            )
            withTimeout(Duration.ofMillis(200)) { // Should complete nearly instantaneously
                signal.await()
            }
            logger.warn("Result was success; expected ${WalletContractV1.RESULT_INVALID_AUTH_TOKEN}")
            false
        } catch (e: IllegalArgumentException) {
            true
        } catch (e: Exception) {
            logger.warn("Unexpected failure for invalid auth token", e)
            false
        }
    }

    private suspend fun testShowSettingsSuccess(): Boolean {
        val signal = CompletableDeferred<Any>()
        check(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        val authToken = knownSeed12AuthorizedChecker.findMatchingSeed()
        if (authToken == null) {
            logger.warn("Cannot find auth token for seed ${KnownSeed12.SEED_NAME}")
            return false
        }

        return try {
            launcher.launch(
                Intent().setAction(WalletContractV1.ACTION_SEED_SETTINGS)
                    .putExtra(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
            )
            signal.await()
            true
        } catch (e: Exception) {
            logger.warn("Unexpected failure when showing seed settings", e)
            false
        }
    }

    private fun testWalletHelperConstructsCorrectIntents(): Boolean {
        return Wallet.showSeedSettings(UNKNOWN_AUTH_TOKEN).let { intent ->
            intent.action == WalletContractV1.ACTION_SEED_SETTINGS && intent.extras?.size() == 1 && intent.getLongExtra(
                WalletContractV1.EXTRA_AUTH_TOKEN, -1L
            ) == UNKNOWN_AUTH_TOKEN && intent.component == null

        }
    }

    private fun testWalletHelperGeneratesExpectedResultCodeResponses(): Boolean {
        try {
            Wallet.onShowSeedSettingsResult(Activity.RESULT_OK, Intent())
        } catch (e: Exception) {
            logger.warn("Unexpected exception for result ${Activity.RESULT_OK}", e)
            return false
        }

        try {
            Wallet.onShowSeedSettingsResult(Activity.RESULT_CANCELED, Intent())
        } catch (e: Exception) {
            logger.warn("Unexpected exception for result ${Activity.RESULT_CANCELED}", e)
            return false
        }

        try {
            Wallet.onShowSeedSettingsResult(WalletContractV1.RESULT_INVALID_AUTH_TOKEN, Intent())
            logger.warn("Expected an exception for result ${WalletContractV1.RESULT_INVALID_AUTH_TOKEN}")
            return false
        } catch (_: Wallet.ActionFailedException) {
            // No-op; fall through to next test case
        } catch (e: Exception) {
            logger.warn(
                "Unexpected exception for result ${WalletContractV1.RESULT_INVALID_AUTH_TOKEN}", e
            )
            return false
        }

        try {
            Wallet.onShowSeedSettingsResult(Activity.RESULT_OK, null)
            logger.warn("Expected an exception when intent is null")
            return false
        } catch (_: Wallet.ActionFailedException) {
            // No-op; fall through to next test case
        } catch (e: Exception) {
            logger.warn("Unexpected exception when intent is null", e)
            return false
        }

        return true
    }

    companion object {
        private const val UNKNOWN_AUTH_TOKEN =
            7394872231938472276L // if this random value matches, ¯\_(ツ)_/¯
    }
}

internal class CannotShowSeedSettingsTestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    private val knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    private val logger: TestSessionLogger
) : TestCaseImpl(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker)
), ActivityLauncherTestCase {
    override val id: String = "csss"
    override val description: String = "Seed settings UI cannot be invoked by non-privileged app"
    override val instructions: String = ""

    private class ShowSeedSettingsIntentContract : ActivityResultContract<Intent, Result<Any>>() {
        override fun createIntent(
            context: Context, @WalletContractV1.AuthToken input: Intent
        ): Intent = input

        override fun parseResult(resultCode: Int, intent: Intent?): Result<Any> {
            // No intent should ever actually be sent, so any result is a failure
            return Result.failure(IllegalStateException("Unexpectedly received activity result"))
        }
    }

    private lateinit var launcher: ActivityResultLauncher<Intent>
    private var completionSignal: CompletableDeferred<Any>? = null

    override fun registerActivityLauncher(arc: ActivityResultCaller) {
        launcher = arc.registerForActivityResult(ShowSeedSettingsIntentContract()) { result ->
            completionSignal?.run {
                completionSignal = null
                result.fold(onSuccess = { complete(it) }, onFailure = { completeExceptionally(it) })
            }
        }
    }

    override suspend fun doExecute(): TestResult {
        val signal = CompletableDeferred<Any>()
        check(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal

        val authToken = knownSeed12AuthorizedChecker.findMatchingSeed()
        if (authToken == null) {
            logger.warn("Cannot find auth token for seed ${KnownSeed12.SEED_NAME}")
            return TestResult.FAIL
        }

        return try {
            launcher.launch(
                Intent().setAction(WalletContractV1.ACTION_SEED_SETTINGS)
                    .putExtra(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
            )
            signal.await()
            logger.warn("Unexpected success while starting activity for ${WalletContractV1.ACTION_SEED_SETTINGS}")
            TestResult.FAIL
        } catch (_: SecurityException) {
            TestResult.PASS
        } catch (e: Exception) {
            logger.warn("Unexpected exception while starting activity for ${WalletContractV1.ACTION_SEED_SETTINGS}")
            TestResult.FAIL
        }
    }
}