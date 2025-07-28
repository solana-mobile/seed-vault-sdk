/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.conditioncheckers

import android.content.Context
import android.content.pm.PackageManager
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.ConditionCheckerImpl
import com.solanamobile.seedvault.cts.data.TestResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DoesNotHaveSeedVaultPrivilegedPermissionChecker @Inject constructor(
    @ApplicationContext private val ctx: Context
) : ConditionCheckerImpl() {
    override val id = "dnhsvpp"
    override val description = "App does not have Seed Vault privileged permission"

    override suspend fun doCheck(): TestResult {
        val result = ctx.checkSelfPermission(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED)
        return if (result != PackageManager.PERMISSION_GRANTED) TestResult.PASS else TestResult.FAIL
    }
}