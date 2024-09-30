/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.conditioncheckers

import android.content.ContentUris
import android.content.Context
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.ConditionCheckerImpl
import com.solanamobile.seedvault.cts.data.TestResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal abstract class UnauthorizedSeedsChecker(
    private val hasUnauthorizedSeeds: Boolean,
    private val ctx: Context
) : ConditionCheckerImpl() {
    override suspend fun doCheck(): TestResult {
        return ctx.contentResolver.query(
            ContentUris.withAppendedId(
                WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION.toLong()
            ), arrayOf(WalletContractV1.UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS), null, null, null
        )?.use {
            it.takeIf { it.moveToFirst() }?.getShort(0)
                ?.let { c -> if (c == hasUnauthorizedSeeds.compareTo(false).toShort()) TestResult.PASS else TestResult.FAIL }
        } ?: TestResult.FAIL
    }
}

@Singleton
internal class NoUnauthorizedSeedsChecker @Inject constructor(
    @ApplicationContext ctx: Context
) : UnauthorizedSeedsChecker(false, ctx) {
    override val id: String = "nusc"
    override val description: String = "No seeds are available for authorization"
}

@Singleton
internal class HasUnauthorizedSeedsChecker @Inject constructor(
    @ApplicationContext ctx: Context
) : UnauthorizedSeedsChecker(true, ctx) {
    override val id: String = "husc"
    override val description: String = "One or more seeds are available for authorization"
}