/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.content.Context
import com.solanamobile.seedvault.cts.R
import com.solanamobile.seedvault.cts.data.ExternalActionTestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.NewSeedExistsChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.RenamedSeedDoesNotExistChecker
import com.solanamobile.seedvault.cts.data.testdata.NewSeed
import com.solanamobile.seedvault.cts.data.testdata.RenamedSeed
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class RenameExistingSeedTestCase @Inject constructor(
    private val newSeedExistsChecker: NewSeedExistsChecker,
    private val renamedSeedDoesNotExistChecker: RenamedSeedDoesNotExistChecker,
    private val newSeed: NewSeed,
    private val renamedSeed: RenamedSeed,
    @ApplicationContext ctx: Context,
    private val logger: TestSessionLogger,
) : ExternalActionTestCaseImpl(
    preConditions = listOf(newSeedExistsChecker, renamedSeedDoesNotExistChecker)
) {
    override val id: String = "res"
    override val description: String = "Test that an existing seed can be renamed"
    override val instructions: String = "After tapping '${ctx.getString(R.string.validate_execute)}', switch to Seed Vault settings, and rename '${newSeed.SEED_NAME}' to '${renamedSeed.SEED_NAME}'. Switch back to this CTS test, and tap '${ctx.getString(R.string.external_action_dialog_confirm)}'."

    override suspend fun doExecute(): TestResult {
        var result = TestResult.PASS

        val seedAuthToken = newSeedExistsChecker.findMatchingSeed()
        check(seedAuthToken != null) // the precondition check passed, so should never be null

        waitForExternalActionComplete()

        if (newSeedExistsChecker.findMatchingSeed() != null) {
            logger.warn("Seed '${newSeed.SEED_NAME}' still exists")
            result = TestResult.FAIL
        }

        val renamedSeedAuthToken = renamedSeedDoesNotExistChecker.findMatchingSeed()
        if (renamedSeedAuthToken == null) {
            logger.warn("Seed '${renamedSeed.SEED_NAME}' not found")
            result = TestResult.FAIL
        }

        if (renamedSeedAuthToken != seedAuthToken) {
            logger.warn("Seed identity changed during renaming")
            result = TestResult.FAIL
        }

        return result
    }
}