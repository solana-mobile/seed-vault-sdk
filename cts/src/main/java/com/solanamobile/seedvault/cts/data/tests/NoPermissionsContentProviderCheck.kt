/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.content.Context
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.DoesNotHaveSeedVaultPermissionChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed12AuthorizedChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class NoPermissionsContentProviderCheck @Inject constructor(
    private val knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    doesNotHaveSeedVaultPermissionChecker: DoesNotHaveSeedVaultPermissionChecker,
    @ApplicationContext private val ctx: Context,
    private val logger: TestSessionLogger,
) : TestCaseImpl(
    preConditions = listOf(doesNotHaveSeedVaultPermissionChecker)
) {
    override suspend fun doExecute(): TestResult {
        try {
            knownSeed12AuthorizedChecker.findMatchingSeedAndAccount()
            logger.error("Expected SecurityException while finding seed without permission")
            return TestResult.FAIL
        } catch (e: SecurityException) {
            // Expected exception.
        }

        try {
            ctx.contentResolver.query(
                WalletContractV1.ACCOUNTS_CONTENT_URI,
                WalletContractV1.ACCOUNTS_ALL_COLUMNS,
                null,
                null,
                null,
                null
            )?.close()
            logger.error("Expected SecurityException while querying accounts without permission")
            return TestResult.FAIL
        } catch (e: SecurityException) {
            // Expected exception.
        }

        try {
            ctx.contentResolver.delete(
                WalletContractV1.ACCOUNTS_CONTENT_URI,
                null,
            )
            logger.error("Expected SecurityException while deleting without permission")
            return TestResult.FAIL
        } catch (e: SecurityException) {
            // Expected exception.
        }

        try {
            ctx.contentResolver.call(
                WalletContractV1.ACCOUNTS_CONTENT_URI,
                "test",
                null,
                null
            )
            logger.error("Expected SecurityException while calling method without permission")
            return TestResult.FAIL
        } catch (e: SecurityException) {
            // Expected exception.
        }

        return TestResult.PASS
    }

    override val id: String = "npcpc"
    override val description: String =
        "Verify content provider behavior when seed vault permission is not granted"
    override val instructions: String = ""
}