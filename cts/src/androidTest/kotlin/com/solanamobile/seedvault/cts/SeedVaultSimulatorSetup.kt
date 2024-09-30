/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.solanamobile.seedvault.SeedVault
import com.solanamobile.seedvault.WalletContractV1
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeedVaultSimulatorSetup {
    companion object {
        // NOTE: provided only by Seed Vault simulator
        private const val RESET_SEED_VAULT_SIMULATOR_METHOD = "ResetSeedVaultSimulator"

        @AfterClass
        @JvmStatic
        fun resetSeedVaultState() {
            val ctx = ApplicationProvider.getApplicationContext<Application>()
            ctx.contentResolver.call(
                WalletContractV1.WALLET_PROVIDER_CONTENT_URI_BASE,
                RESET_SEED_VAULT_SIMULATOR_METHOD,
                null,
                null
            )

            ctx.revokeSelfPermissionOnKill(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT)
        }
    }

    @get:Rule
    val runtimePermissionRule =
        GrantPermissionRule.grant(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT)!!

    @Test
    fun verifySeedVaultSimulatorInstalled() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(SeedVault.isAvailable(ctx, true))
        assertFalse(SeedVault.isAvailable(ctx, false))
    }
}