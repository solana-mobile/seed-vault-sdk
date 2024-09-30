/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.tests

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import androidx.collection.arrayMapOf
import com.solanamobile.seedvault.Wallet
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.SagaChecker
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasSeedVaultPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class ImplementationLimitsContentProviderTestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    @ApplicationContext private val ctx: Context,
    private val logger: TestSessionLogger,
    private val sagaChecker: SagaChecker
) : TestCaseImpl(
    preConditions = listOf(hasSeedVaultPermissionChecker)
) {
    override val id: String = "ilcp"
    override val description: String = "Verify content provider behavior for ${WalletContractV1.IMPLEMENTATION_LIMITS_TABLE}"
    override val instructions: String = ""

    override suspend fun doExecute(): TestResult {
        var result = TestResult.PASS

        if (!testTableMimeType()) {
            logger.warn("$id: Unexpected MIME type")
            result = TestResult.FAIL
        }

        if (!testTable()) {
            logger.warn("$id: Unfiltered table returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableIdFilterPurpose()) {
            logger.warn("$id: ID-filtered table returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableIdFilterUnknownPurpose()) {
            logger.warn("$id: ID-filtered table with unknown purpose returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterPurpose()) {
            logger.warn("$id: Query-filtered table by purpose returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterUnknownPurpose()) {
            logger.warn("$id: Query-filtered table with unknown purpose returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperGetImplementationLimits()) {
            logger.warn("$id: Wallet helper check for implementation limits returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperGetImplementationLimitsUnknownPurpose()) {
            logger.warn("$id: Wallet helper check for implementation limits with unknown purpose returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelper()) {
            logger.warn("$id: Unfiltered Wallet helper query returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperExpressionFilterPurpose()) {
            logger.warn("$id: Wallet helper query filtered by purpose returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperExpressionFilterUnknownPurpose()) {
            logger.warn("$id: Wallet helper query filtered with unknown purpose returned unexpected value")
            result = TestResult.FAIL
        }

        return result
    }

    private fun testTableMimeType(): Boolean {
        val expectedTableMimeType = if (sagaChecker.isSaga()) {
            "${ContentResolver.CURSOR_DIR_BASE_TYPE}${WalletContractV1.IMPLEMENTATION_LIMITS_MIME_SUBTYPE}"
        } else {
            "${ContentResolver.CURSOR_DIR_BASE_TYPE}/${WalletContractV1.IMPLEMENTATION_LIMITS_MIME_SUBTYPE}"
        }
        val expectedItemMimeType = if (sagaChecker.isSaga()) {
            "${ContentResolver.CURSOR_ITEM_BASE_TYPE}${WalletContractV1.IMPLEMENTATION_LIMITS_MIME_SUBTYPE}"
        } else {
            "${ContentResolver.CURSOR_ITEM_BASE_TYPE}/${WalletContractV1.IMPLEMENTATION_LIMITS_MIME_SUBTYPE}"
        }

        return ctx.contentResolver.getType(WalletContractV1.IMPLEMENTATION_LIMITS_CONTENT_URI) == expectedTableMimeType && ctx.contentResolver.getType(
            ContentUris.withAppendedId(
                WalletContractV1.IMPLEMENTATION_LIMITS_CONTENT_URI,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION.toLong()
            )
        ) == expectedItemMimeType
    }

    private fun testTable(): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.IMPLEMENTATION_LIMITS_CONTENT_URI,
            WalletContractV1.IMPLEMENTATION_LIMITS_ALL_COLUMNS,
            null,
            null,
            null
        )?.use { c ->
            c.count == 1 && // Current implementations only define a single PURPOSE_* value
                    c.moveToFirst() &&
                    c.getInt(0) == WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION &&
                    c.getShort(1) >= MIN_ALLOWED_MAX_SIGNING_REQUESTS &&
                    c.getShort(2) >= MIN_ALLOWED_MAX_REQUESTED_SIGNATURES &&
                    c.getShort(3) >= MIN_ALLOWED_MAX_REQUESTED_PUBLIC_KEYS
        } ?: false
    }

    private fun testTableIdFilterPurpose(): Boolean {
        return ctx.contentResolver.query(
            ContentUris.withAppendedId(
                WalletContractV1.IMPLEMENTATION_LIMITS_CONTENT_URI,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION.toLong()
            ),
            WalletContractV1.IMPLEMENTATION_LIMITS_ALL_COLUMNS,
            null,
            null,
            null
        )?.use { c ->
            c.count == 1 && // Current implementations only define a single PURPOSE_* value
                    c.moveToFirst() &&
                    c.getInt(0) == WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION &&
                    c.getShort(1) >= MIN_ALLOWED_MAX_SIGNING_REQUESTS &&
                    c.getShort(2) >= MIN_ALLOWED_MAX_REQUESTED_SIGNATURES &&
                    c.getShort(3) >= MIN_ALLOWED_MAX_REQUESTED_PUBLIC_KEYS
        } ?: false
    }

    private fun testTableIdFilterUnknownPurpose(): Boolean {
        return try {
            ctx.contentResolver.query(
                ContentUris.withAppendedId(
                    WalletContractV1.IMPLEMENTATION_LIMITS_CONTENT_URI,
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

    private fun testTableExpressionFilterPurpose(): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.IMPLEMENTATION_LIMITS_CONTENT_URI,
            WalletContractV1.IMPLEMENTATION_LIMITS_ALL_COLUMNS,
            "${WalletContractV1.IMPLEMENTATION_LIMITS_AUTH_PURPOSE}=?",
            arrayOf(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION.toString()),
            null
        )?.use { c ->
            c.count == 1 && // Current implementations only define a single PURPOSE_* value
                    c.moveToFirst() &&
                    c.getInt(0) == WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION &&
                    c.getShort(1) >= MIN_ALLOWED_MAX_SIGNING_REQUESTS &&
                    c.getShort(2) >= MIN_ALLOWED_MAX_REQUESTED_SIGNATURES &&
                    c.getShort(3) >= MIN_ALLOWED_MAX_REQUESTED_PUBLIC_KEYS
        } ?: false
    }

    private fun testTableExpressionFilterUnknownPurpose(): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.IMPLEMENTATION_LIMITS_CONTENT_URI,
            WalletContractV1.IMPLEMENTATION_LIMITS_ALL_COLUMNS,
            "${WalletContractV1.IMPLEMENTATION_LIMITS_AUTH_PURPOSE}=?",
            arrayOf((WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION + 1).toString()),
            null
        )?.use { c ->
            c.count == 0
        } ?: false
    }

    private fun testWalletHelperGetImplementationLimits(): Boolean {
        return Wallet.getImplementationLimitsForPurpose(
            ctx,
            WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
        ) == arrayMapOf(
            WalletContractV1.IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS to MIN_ALLOWED_MAX_SIGNING_REQUESTS.toLong(),
            WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES to MIN_ALLOWED_MAX_REQUESTED_SIGNATURES.toLong(),
            WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS to MIN_ALLOWED_MAX_REQUESTED_PUBLIC_KEYS.toLong()
        )
    }

    private fun testWalletHelperGetImplementationLimitsUnknownPurpose(): Boolean {
        return try {
            Wallet.getImplementationLimitsForPurpose(
                ctx,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION + 1
            )
            false
        } catch (e: IllegalArgumentException) {
            true
        }
    }

    private fun testWalletHelper(): Boolean {
        return Wallet.getImplementationLimits(
            ctx,
            WalletContractV1.IMPLEMENTATION_LIMITS_ALL_COLUMNS
        )?.use { c ->
            c.count == 1 && // Current implementations only define a single PURPOSE_* value
                    c.moveToFirst() &&
                    c.getInt(0) == WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION &&
                    c.getShort(1) >= MIN_ALLOWED_MAX_SIGNING_REQUESTS &&
                    c.getShort(2) >= MIN_ALLOWED_MAX_REQUESTED_SIGNATURES &&
                    c.getShort(3) >= MIN_ALLOWED_MAX_REQUESTED_PUBLIC_KEYS
        } ?: false
    }

    private fun testWalletHelperExpressionFilterPurpose(): Boolean {
        return Wallet.getImplementationLimits(
            ctx,
            WalletContractV1.IMPLEMENTATION_LIMITS_ALL_COLUMNS,
            WalletContractV1.IMPLEMENTATION_LIMITS_AUTH_PURPOSE,
            WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION.toString()
        )?.use { c ->
            c.count == 1 && // Current implementations only define a single PURPOSE_* value
                    c.moveToFirst() &&
                    c.getInt(0) == WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION &&
                    c.getShort(1) >= MIN_ALLOWED_MAX_SIGNING_REQUESTS &&
                    c.getShort(2) >= MIN_ALLOWED_MAX_REQUESTED_SIGNATURES &&
                    c.getShort(3) >= MIN_ALLOWED_MAX_REQUESTED_PUBLIC_KEYS
        } ?: false
    }

    private fun testWalletHelperExpressionFilterUnknownPurpose(): Boolean {
        return Wallet.getImplementationLimits(
            ctx,
            WalletContractV1.IMPLEMENTATION_LIMITS_ALL_COLUMNS,
            WalletContractV1.IMPLEMENTATION_LIMITS_AUTH_PURPOSE,
            (WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION + 1).toString()
        )?.use { c ->
            c.count == 0
        } ?: false
    }

    companion object {
        private const val MIN_ALLOWED_MAX_SIGNING_REQUESTS: Short = 3
        private const val MIN_ALLOWED_MAX_REQUESTED_SIGNATURES: Short = 3
        private const val MIN_ALLOWED_MAX_REQUESTED_PUBLIC_KEYS: Short = 10
    }
}