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
import com.solanamobile.seedvault.cts.data.conditioncheckers.AuthorizedSeedsChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasSeedVaultPermissionChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed12AuthorizedChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed24AuthorizedChecker
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed12
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed24
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal abstract class DeauthorizeSeedTestCase(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    private val knownSeedAuthorizedChecker: AuthorizedSeedsChecker,
    private val knownSeed: KnownSeed,
    private val ctx: Context,
    private val logger: TestSessionLogger
) : TestCaseImpl(
    preConditions = listOf(hasSeedVaultPermissionChecker, knownSeedAuthorizedChecker)
) {
    override suspend fun doExecute(): TestResult {
        @AuthToken val authToken = knownSeedAuthorizedChecker.findMatchingSeed()
        if (authToken == null) {
            logger.warn("$id: Failed locating seed '${knownSeed.SEED_NAME}' to deauthorize")
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

        @AuthToken val postDeleteAuthToken = knownSeedAuthorizedChecker.findMatchingSeed()
        if (postDeleteAuthToken != null) {
            logger.warn("$id: Seed '${knownSeed.SEED_NAME}' still present after deauthorize")
            return TestResult.FAIL
        }

        return TestResult.PASS
    }
}

internal class DeauthorizeSeed12TestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    @KnownSeed12 private val knownSeed12: KnownSeed,
    @ApplicationContext private val ctx: Context,
    logger: TestSessionLogger
) : DeauthorizeSeedTestCase(
    hasSeedVaultPermissionChecker,
    knownSeed12AuthorizedChecker,
    knownSeed12,
    ctx,
    logger
) {
    override val id: String = "ds12"
    override val description: String = "Deauthorize the previously authorized seed '${knownSeed12.SEED_NAME}'"
    override val instructions: String = ""
}

internal class DeauthorizeSeed24TestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    knownSeed24AuthorizedChecker: KnownSeed24AuthorizedChecker,
    @KnownSeed24 knownSeed24: KnownSeed,
    @ApplicationContext ctx: Context,
    logger: TestSessionLogger
) : DeauthorizeSeedTestCase(
    hasSeedVaultPermissionChecker,
    knownSeed24AuthorizedChecker,
    knownSeed24,
    ctx,
    logger
) {
    override val id: String = "ds24"
    override val description: String = "Deauthorize the previously authorized seed '${knownSeed24.SEED_NAME}'"
    override val instructions: String = ""
}