/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.conditioncheckers

import android.content.Context
import android.content.pm.PackageManager
import com.solanamobile.seedvault.SeedVault
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.ConditionCheckerImpl
import com.solanamobile.seedvault.cts.data.TestResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class HasSeedVaultPermissionChecker @Inject constructor(
    @ApplicationContext private val ctx: Context
) : ConditionCheckerImpl() {
    override val id = "hsvp"
    override val description = "App has Seed Vault or Seed Vault privileged permission"

    override suspend fun doCheck(): TestResult {
        if (ctx.checkSelfPermission(
                WalletContractV1.PERMISSION_ACCESS_SEED_VAULT
            ) != PackageManager.PERMISSION_GRANTED
            && ctx.checkSelfPermission(
                WalletContractV1.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return TestResult.FAIL
        }

        if (!SeedVault.getAccessType(ctx).isGranted) {
            return TestResult.FAIL
        }

        return TestResult.PASS
    }
}