/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.conditioncheckers.NoAuthorizedSeedsChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.NoUnauthorizedSeedsChecker
import javax.inject.Inject

internal class InitialConditionsTestCase @Inject constructor(
    noUnauthorizedSeedsChecker: NoUnauthorizedSeedsChecker,
    noAuthorizedSeedsChecker: NoAuthorizedSeedsChecker
) : TestCaseImpl(
    preConditions = listOf(noUnauthorizedSeedsChecker, noAuthorizedSeedsChecker)
) {
    override val id: String = "ic"
    override val description: String = "Verify initial conditions before beginning the test suite"
    override val instructions: String = ""

    override suspend fun doExecute(): TestResult = TestResult.PASS
}