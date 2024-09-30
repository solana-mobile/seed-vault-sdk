/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.conditioncheckers

import android.content.Context
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.ConditionCheckerImpl
import com.solanamobile.seedvault.cts.data.TestResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal abstract class SeedNameChecker(
    private val seedName: String,
    private val ctx: Context
) : ConditionCheckerImpl() {

    fun findMatchingSeed(): Long? {
        return ctx.contentResolver.query(
            WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
            arrayOf(
                WalletContractV1.AUTHORIZED_SEEDS_AUTH_TOKEN,
                WalletContractV1.AUTHORIZED_SEEDS_AUTH_PURPOSE
            ),
            "${WalletContractV1.AUTHORIZED_SEEDS_SEED_NAME}=?",
            arrayOf(seedName),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val purpose = c.getInt(1)
                if (purpose != WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION) continue

                val authToken = c.getLong(0)
                return authToken
            }

            null
        }
    }
}

@Singleton
internal class NewSeedDoesNotExistChecker @Inject constructor(
    @ApplicationContext private val ctx: Context
) : SeedNameChecker("NewSeed", ctx) {
    override val id = "nssn"
    override val description = "Seed 'NewSeed' is not authorized"

    override suspend fun doCheck(): TestResult {
        return if (findMatchingSeed() == null) TestResult.PASS else TestResult.FAIL
    }
}
