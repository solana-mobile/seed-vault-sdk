/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import com.solanamobile.seedvault.Wallet
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.ConditionChecker
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasSeedVaultPermissionChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasUnauthorizedSeedsChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.NoUnauthorizedSeedsChecker
import com.solanamobile.seedvault.cts.data.testdata.ImplementationDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal abstract class UnauthorizedSeedsContentProviderTestCase(
    private val hasUnauthorizedSeeds: Boolean,
    preConditions: List<ConditionChecker>,
    private val ctx: Context,
    private val logger: TestSessionLogger,
    private val implementationDetails: ImplementationDetails
) : TestCaseImpl(
    preConditions = preConditions
) {
    override suspend fun doExecute(): TestResult {
        var result = TestResult.PASS

        val resultTestTableMimeType = testTableMimeType()
        if (!resultTestTableMimeType) {
            logger.warn("$id: Unexpected MIME type")
            result = TestResult.FAIL
        }

        if (!testTable()) {
            logger.warn("$id: Unfiltered table returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelper()) {
            logger.warn("$id: Unfiltered Wallet helper query returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableIdFilterPurpose()) {
            logger.warn("$id: ID-filtered table returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterPurpose()) {
            logger.warn("$id: Query-filtered table by purpose returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperFilterPurpose()) {
            logger.warn("$id: Wallet helper query filtered by purpose returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableIdFilterUnknownPurpose()) {
            logger.warn("$id: ID-filtered table with unknown purpose returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterUnknownPurpose()) {
            logger.warn("$id: Query-filtered table with unknown purpose returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperFilterUnknownPurpose()) {
            logger.warn("$id: Wallet helper query filtered with unknown purpose returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterAvailableSeeds()) {
            logger.warn("$id: Query-filtered table by available seeds returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperFilterAvailableSeeds()) {
            logger.warn("$id: Wallet helper query filtered by available seeds returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperHasUnauthorizedSeeds()) {
            logger.warn("$id: Wallet helper check for unauthorized seeds returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperHasUnauthorizedSeedsUnknownPurpose()) {
            logger.warn("$id: Wallet helper check for unauthorized seeds with unknown purpose returned unexpected value")
            result = TestResult.FAIL
        }

        return result
    }

    private fun testTableMimeType(): Boolean {
        val expectedTableMimeType = if (implementationDetails.IS_LEGACY_IMPLEMENTATION) {
            "${ContentResolver.CURSOR_ITEM_BASE_TYPE}${WalletContractV1.UNAUTHORIZED_SEEDS_MIME_SUBTYPE}"
        } else {
            "${ContentResolver.CURSOR_DIR_BASE_TYPE}/${WalletContractV1.UNAUTHORIZED_SEEDS_MIME_SUBTYPE}"
        }
        val expectedItemMimeType = if (implementationDetails.IS_LEGACY_IMPLEMENTATION) {
            "${ContentResolver.CURSOR_ITEM_BASE_TYPE}${WalletContractV1.UNAUTHORIZED_SEEDS_MIME_SUBTYPE}"
        } else {
            "${ContentResolver.CURSOR_ITEM_BASE_TYPE}/${WalletContractV1.UNAUTHORIZED_SEEDS_MIME_SUBTYPE}"
        }

        return ctx.contentResolver.getType(WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI) == expectedTableMimeType && ctx.contentResolver.getType(
            ContentUris.withAppendedId(
                WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION.toLong()
            )
        ) == expectedItemMimeType
    }

    private fun validateAllRowsCursor(c: Cursor): Boolean {
        return c.count == 1 && // Current implementations only define a single PURPOSE_* value
                c.moveToFirst() &&
                c.getInt(0) == WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION &&
                c.getShort(1) == hasUnauthorizedSeeds.compareTo(false).toShort()
    }

    private fun testTable(): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI,
            WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS,
            null,
            null,
            null
        )?.use { c -> validateAllRowsCursor(c) } ?: false
    }

    private fun testWalletHelper(): Boolean {
        return Wallet.getUnauthorizedSeeds(
            ctx,
            WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS
        )?.use { c -> validateAllRowsCursor(c) } ?: false
    }

    private fun validatePurposeFilteredCursor(c: Cursor): Boolean {
        return c.count == 1 && // Current implementations only define a single PURPOSE_* value
                c.moveToFirst() &&
                c.getInt(0) == WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION &&
                c.getShort(1) == hasUnauthorizedSeeds.compareTo(false).toShort()
    }

    private fun testTableIdFilterPurpose(): Boolean {
        return ctx.contentResolver.query(
            ContentUris.withAppendedId(
                WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION.toLong()
            ),
            WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS,
            null,
            null,
            null
        )?.use { c -> validatePurposeFilteredCursor(c) } ?: false
    }

    private fun testTableExpressionFilterPurpose(): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI,
            WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS,
            "${WalletContractV1.UNAUTHORIZED_SEEDS_AUTH_PURPOSE}=?",
            arrayOf(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION.toString()),
            null
        )?.use { c -> validatePurposeFilteredCursor(c) } ?: false
    }

    private fun testWalletHelperFilterPurpose(): Boolean {
        return Wallet.getUnauthorizedSeeds(
            ctx,
            WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS,
            WalletContractV1.UNAUTHORIZED_SEEDS_AUTH_PURPOSE,
            WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION.toString()
        )?.use { c -> validatePurposeFilteredCursor(c) } ?: false
    }

    private fun testTableIdFilterUnknownPurpose(): Boolean {
        return try {
            ctx.contentResolver.query(
                ContentUris.withAppendedId(
                    WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI,
                    (WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION + 1).toLong()
                ),
                WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS,
                null,
                null,
                null
            )?.close()
            false // Unexpected result - should throw IllegalArgumentException
        } catch (e: IllegalArgumentException) {
            true // Expected
        }
    }

    private fun validateUnknownPurposeFilteredCursor(c: Cursor): Boolean {
        return c.count == 0
    }

    private fun testTableExpressionFilterUnknownPurpose(): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI,
            WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS,
            "${WalletContractV1.UNAUTHORIZED_SEEDS_AUTH_PURPOSE}=?",
            arrayOf((WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION + 1).toString()),
            null
        )?.use { c -> validateUnknownPurposeFilteredCursor(c) } ?: false
    }

    private fun testWalletHelperFilterUnknownPurpose(): Boolean {
        return Wallet.getUnauthorizedSeeds(
            ctx,
            WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS,
            WalletContractV1.UNAUTHORIZED_SEEDS_AUTH_PURPOSE,
            (WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION + 1).toString()
        )?.use { c -> validateUnknownPurposeFilteredCursor(c) } ?: false
    }

    private fun validateAvailableSeedsFilteredCursor(c: Cursor): Boolean {
        return if (hasUnauthorizedSeeds) {
            c.count == 1 &&
                    c.moveToFirst() &&
                    c.getInt(0) == WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION &&
                    c.getShort(1) == 1.toShort()
        } else {
            c.count == 0
        }
    }

    private fun testTableExpressionFilterAvailableSeeds(): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.UNAUTHORIZED_SEEDS_CONTENT_URI,
            WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS,
            "${WalletContractV1.UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS}=?",
            arrayOf("1"),
            null
        )?.use { c -> validateAvailableSeedsFilteredCursor(c) } ?: false
    }

    private fun testWalletHelperFilterAvailableSeeds(): Boolean {
        return Wallet.getUnauthorizedSeeds(
            ctx,
            WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS,
            WalletContractV1.UNAUTHORIZED_SEEDS_HAS_UNAUTHORIZED_SEEDS,
            "1"
        )?.use { c -> validateAvailableSeedsFilteredCursor(c) } ?: false
    }

    private fun testWalletHelperHasUnauthorizedSeeds(): Boolean {
        return Wallet.hasUnauthorizedSeedsForPurpose(
            ctx,
            WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
        ) == hasUnauthorizedSeeds
    }

    private fun testWalletHelperHasUnauthorizedSeedsUnknownPurpose(): Boolean {
        return try {
            Wallet.hasUnauthorizedSeedsForPurpose(
                ctx,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION + 1
            )
            false
        } catch (e: IllegalArgumentException) {
            true
        }
    }
}

internal class NoUnauthorizedSeedsContentProviderTestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    noUnauthorizedSeedsChecker: NoUnauthorizedSeedsChecker,
    @ApplicationContext ctx: Context,
    logger: TestSessionLogger,
    implementationDetails: ImplementationDetails
) : UnauthorizedSeedsContentProviderTestCase(
    false,
    listOf(hasSeedVaultPermissionChecker, noUnauthorizedSeedsChecker),
    ctx,
    logger,
    implementationDetails
) {
    override val id: String = "nuascp"
    override val description: String =
        "Verify content provider behavior for ${WalletContractV1.UNAUTHORIZED_SEEDS_TABLE} when no seeds are available for authorization"
    override val instructions: String = ""
}

internal class HasUnauthorizedSeedsContentProviderTestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    hasUnauthorizedSeedsChecker: HasUnauthorizedSeedsChecker,
    @ApplicationContext ctx: Context,
    logger: TestSessionLogger,
    implementationDetails: ImplementationDetails
) : UnauthorizedSeedsContentProviderTestCase(
    true,
    listOf(hasSeedVaultPermissionChecker, hasUnauthorizedSeedsChecker),
    ctx,
    logger,
    implementationDetails
) {
    override val id: String = "huascp"
    override val description: String =
        "Verify content provider behavior for ${WalletContractV1.UNAUTHORIZED_SEEDS_TABLE} when one seed is available for authorization"
    override val instructions: String = ""
}