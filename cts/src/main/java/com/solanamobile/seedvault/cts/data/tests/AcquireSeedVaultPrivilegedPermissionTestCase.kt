/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.ActivityLauncherTestCase
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.DoesNotHaveSeedVaultPrivilegedPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject

internal class AcquireSeedVaultPrivilegedPermissionTestCase @Inject constructor(
    doesNotHaveSeedVaultPrivilegedPermissionChecker: DoesNotHaveSeedVaultPrivilegedPermissionChecker,
    @ApplicationContext private val ctx: Context,
    private val logger: TestSessionLogger
) : TestCaseImpl(
    preConditions = listOf(doesNotHaveSeedVaultPrivilegedPermissionChecker)
), ActivityLauncherTestCase {
    override val id: String = "asvpp"
    override val description: String =
        "Verify that ${WalletContractV1.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED} can not be acquired by unknown signed builds"
    override val instructions: String = ""

    private lateinit var launcher: ActivityResultLauncher<String>
    private var completionSignal: CompletableDeferred<Boolean>? = null

    override suspend fun doExecute(): TestResult {
        val signal = CompletableDeferred<Boolean>()
        assert(completionSignal == null) { "Completion signal non-null" }
        completionSignal = signal
        launcher.launch(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED)

        val granted = signal.await()
        if (granted) {
            logger.warn("$id: Seed Vault privileged permission granted")
            return TestResult.FAIL
        }

        val held = ctx.checkSelfPermission(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED)
        if (held == PackageManager.PERMISSION_GRANTED) {
            logger.warn("$id: Seed Vault privileged permission held")
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