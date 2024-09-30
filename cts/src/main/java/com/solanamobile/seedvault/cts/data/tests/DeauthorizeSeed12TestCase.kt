/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.content.ContentUris
import android.content.Context
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.WalletContractV1.AuthToken
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasSeedVaultPermissionChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed12AuthorizedChecker
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed12
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class DeauthorizeSeed12TestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    private val knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    @ApplicationContext private val ctx: Context,
    private val logger: TestSessionLogger
) : TestCaseImpl(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeed12AuthorizedChecker)
) {
    override val id: String = "ds12"
    override val description: String = "Deauthorize the previously authorized seed '${KnownSeed12.SEED_NAME}'"
    override val instructions: String = ""

    override suspend fun doExecute(): TestResult {
        @AuthToken val authToken = knownSeed12AuthorizedChecker.findMatchingSeed()
        if (authToken == null) {
            logger.warn("$id: Failed locating seed '${KnownSeed12.SEED_NAME}' to deauthorize")
            return TestResult.FAIL
        }

        val count = ctx.contentResolver.delete(
            ContentUris.withAppendedId(
                WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
                authToken
            ), null
        )
        if (count != 1) {
            logger.warn("$id: Expected to deauthorize 1 seed, but got $count")
            return TestResult.FAIL
        }

        @AuthToken val postDeleteAuthToken = knownSeed12AuthorizedChecker.findMatchingSeed()
        if (postDeleteAuthToken != null) {
            logger.warn("$id: Seed '${KnownSeed12.SEED_NAME}' still present after deauthorize")
            return TestResult.FAIL
        }

        return TestResult.PASS
    }
}
