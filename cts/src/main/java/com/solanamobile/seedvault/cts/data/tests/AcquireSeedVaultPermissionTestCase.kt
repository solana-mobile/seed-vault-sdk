/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.solanamobile.seedvault.SeedVault
import com.solanamobile.seedvault.SeedVault.AccessType
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.ActivityLauncherTestCase
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.DoesNotHaveSeedVaultPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject

internal class AcquireSeedVaultPermissionTestCase @Inject constructor(
    doesNotHaveSeedVaultPermissionChecker: DoesNotHaveSeedVaultPermissionChecker,
    @ApplicationContext private val ctx: Context,
    private val logger: TestSessionLogger
) : TestCaseImpl(
    preConditions = listOf(doesNotHaveSeedVaultPermissionChecker)
), ActivityLauncherTestCase {
    override val id: String = "asvp"
    override val description: String =
        "Verify that ${WalletContractV1.PERMISSION_ACCESS_SEED_VAULT} can be acquired"
    override val instructions: String =
        "If a permission dialog is presented, grant the Seed Vault permission to this app"

    private lateinit var launcher: ActivityResultLauncher<String>
    private var completionSignal: CompletableDeferred<Boolean>? = null

    override suspend fun doExecute(): TestResult {
        val signal = CompletableDeferred<Boolean>()
        assert(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal
        launcher.launch(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT)

        val granted = signal.await()
        if (!granted) {
            logger.warn("$id: Seed Vault permission not granted")
            return TestResult.FAIL
        }

        val held = ctx.checkSelfPermission(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT)
        if (held != PackageManager.PERMISSION_GRANTED) {
            logger.warn("$id: Seed Vault permission not held")
            return TestResult.FAIL
        }

        if (SeedVault.getAccessType(ctx) != AccessType.STANDARD) {
            logger.warn("$id: Seed Vault access type is not STANDARD")
            return TestResult.FAIL
        }

        return TestResult.PASS
    }

    override fun registerActivityLauncher(arc: ActivityResultCaller) {
        launcher = arc.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted: Boolean ->
            completionSignal?.run {
                completionSignal = null
                complete(granted)
            }
        }
    }
}