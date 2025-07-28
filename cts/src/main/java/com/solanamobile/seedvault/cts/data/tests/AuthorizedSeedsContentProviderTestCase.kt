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
import com.solanamobile.seedvault.cts.PrivilegedSeedVaultChecker
import com.solanamobile.seedvault.cts.data.ConditionChecker
import com.solanamobile.seedvault.cts.data.TestCaseImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.conditioncheckers.HasSeedVaultPermissionChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed12AuthorizedChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.KnownSeed24AuthorizedChecker
import com.solanamobile.seedvault.cts.data.conditioncheckers.NoAuthorizedSeedsChecker
import com.solanamobile.seedvault.cts.data.testdata.ImplementationDetails
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed12
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed24
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal abstract class AuthorizedSeedsContentProviderTestCase(
    private val authorizedSeeds: List<String>,
    preConditions: List<ConditionChecker>,
    private val ctx: Context,
    private val logger: TestSessionLogger,
    private val implementationDetails: ImplementationDetails,
    private val privilegedSeedVaultChecker: PrivilegedSeedVaultChecker,
) : TestCaseImpl(
    preConditions = preConditions
) {
    private val authTokens = LongArray(authorizedSeeds.size)

    override suspend fun doExecute(): TestResult {
        // Reset the array of auth tokens; will be filled by testTable
        authTokens.fill(-1)

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

        if (!testTableIdFilterAuthToken()) {
            logger.warn("$id: ID-filtered table returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperGetByAuthToken()) {
            logger.warn("$id: Wallet helper query filtered by auth token returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterAuthToken()) {
            logger.warn("$id: Query-filtered table by auth token returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperFilterAuthToken()) {
            logger.warn("$id: Wallet helper query filtered by auth token returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableIdFilterUnknownAuthToken()) {
            logger.warn("$id: ID-filtered table with unknown auth token returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperGetByAuthTokenFilterUnknownAuthToken()) {
            logger.warn("$id: Wallet helper query filtered with unknown auth token returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterUnknownAuthToken()) {
            logger.warn("$id: Query-filtered table with unknown auth token returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperFilterUnknownAuthToken()) {
            logger.warn("$id: Wallet helper query filtered with unknown auth token returned unexpected value")
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

        if (!testTableExpressionFilterUnknownPurpose()) {
            logger.warn("$id: Query-filtered table with unknown purpose returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperFilterUnknownPurpose()) {
            logger.warn("$id: Wallet helper query filtered with unknown purpose returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterName()) {
            logger.warn("$id: Query-filtered table by name returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperFilterName()) {
            logger.warn("$id: Wallet helper query filtered by name returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testTableExpressionFilterUnknownName()) {
            logger.warn("$id: Query-filtered table with unknown name returned unexpected value")
            result = TestResult.FAIL
        }

        if (!testWalletHelperFilterUnknownName()) {
            logger.warn("$id: Wallet helper query filtered with unknown name returned unexpected value")
            result = TestResult.FAIL
        }

        return result
    }

    private fun testTableMimeType(): Boolean {
        val expectedTableMimeType = if (implementationDetails.IS_LEGACY_IMPLEMENTATION) {
            "${ContentResolver.CURSOR_DIR_BASE_TYPE}${WalletContractV1.AUTHORIZED_SEEDS_MIME_SUBTYPE}"
        } else {
            "${ContentResolver.CURSOR_DIR_BASE_TYPE}/${WalletContractV1.AUTHORIZED_SEEDS_MIME_SUBTYPE}"
        }
        val expectedItemMimeType = if (implementationDetails.IS_LEGACY_IMPLEMENTATION) {
            "${ContentResolver.CURSOR_ITEM_BASE_TYPE}${WalletContractV1.AUTHORIZED_SEEDS_MIME_SUBTYPE}"
        } else {
            "${ContentResolver.CURSOR_ITEM_BASE_TYPE}/${WalletContractV1.AUTHORIZED_SEEDS_MIME_SUBTYPE}"
        }

        return ctx.contentResolver.getType(WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI) == expectedTableMimeType && ctx.contentResolver.getType(
            ContentUris.withAppendedId(
                WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION.toLong()
            )
        ) == expectedItemMimeType
    }

    private fun validateAllRowsCursor(
        c: Cursor,
        action: ((index: Int, authToken: Long) -> Unit)? = null
    ): Boolean {
        if (c.count != authorizedSeeds.size) return false
        if (!validateAuthorizedSeedsCursor(c)) return false

        val found = BooleanArray(authorizedSeeds.size)
        while (c.moveToNext()) {
            val authToken = c.getLong(0)
            if (authToken < 0) return false

            val purpose = c.getInt(1)
            val name = c.getString(2)
            val idx = authorizedSeeds.indexOf(name)
            if (idx != -1 && purpose == WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION) {
                found[idx] = true
                action?.invoke(idx, authToken)
            }
        }

        return found.all { it }
    }

    private fun testTable(): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
            null,
            null,
            null
        )?.use { c ->
            validateAllRowsCursor(c) { idx, authToken ->
                authTokens[idx] = authToken
            }
        } ?: false
    }

    private fun testWalletHelper(): Boolean {
        return Wallet.getAuthorizedSeeds(
            ctx,
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS
        )?.use { c -> validateAllRowsCursor(c) } ?: false
    }

    private fun validateAuthTokenFilteredCursor(
        c: Cursor,
        expectedAuthToken: Long,
        expectedName: String
    ): Boolean {
        return validateAuthorizedSeedsCursor(c) &&
                c.count == 1 && // Current implementations only define a single PURPOSE_* value
                c.moveToNext() &&
                c.getLong(0) == expectedAuthToken &&
                c.getInt(1) == WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION &&
                c.getString(2) == expectedName
    }

    private fun testTableIdFilterAuthToken(): Boolean {
        if (authTokens.any { it == -1L }) return false

        return authTokens.mapIndexed { i, authToken ->
            ctx.contentResolver.query(
                ContentUris.withAppendedId(
                    WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
                    authToken
                ),
                WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
                null,
                null,
                null
            )?.use { c ->
                validateAuthTokenFilteredCursor(c, authToken, authorizedSeeds[i])
            } ?: false
        }.all { it }
    }

    private fun testWalletHelperGetByAuthToken(): Boolean {
        return authTokens.mapIndexed { i, authToken ->
            Wallet.getAuthorizedSeed(
                ctx,
                authToken,
                WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS
            )?.use { c ->
                validateAuthTokenFilteredCursor(c, authToken, authorizedSeeds[i])
            } ?: false
        }.all { it }
    }

    private fun testTableExpressionFilterAuthToken(): Boolean {
        return authTokens.mapIndexed { i, authToken ->
            ctx.contentResolver.query(
                WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
                WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
                "${WalletContractV1.AUTHORIZED_SEEDS_AUTH_TOKEN}=?",
                arrayOf(authToken.toString()),
                null
            )?.use { c ->
                validateAuthTokenFilteredCursor(c, authToken, authorizedSeeds[i])
            } ?: false
        }.all { it }
    }

    private fun testWalletHelperFilterAuthToken(): Boolean {
        return authTokens.mapIndexed { i, authToken ->
            Wallet.getAuthorizedSeeds(
                ctx,
                WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
                WalletContractV1.AUTHORIZED_SEEDS_AUTH_TOKEN,
                authToken.toString()
            )?.use { c ->
                validateAuthTokenFilteredCursor(c, authToken, authorizedSeeds[i])
            } ?: false
        }.all { it }
    }

    private fun validateUnknownAuthTokenFilteredCursor(c: Cursor): Boolean {
        return validateAuthorizedSeedsCursor(c) &&
                c.count == 0
    }

    private fun testTableIdFilterUnknownAuthToken(): Boolean {
        return ctx.contentResolver.query(
            ContentUris.withAppendedId(
                WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
                UNKNOWN_AUTH_TOKEN
            ),
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
            null,
            null,
            null
        )?.use { c -> validateUnknownAuthTokenFilteredCursor(c) } ?: false
    }

    private fun testTableExpressionFilterUnknownAuthToken(): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
            "${WalletContractV1.AUTHORIZED_SEEDS_AUTH_TOKEN}=?",
            arrayOf(UNKNOWN_AUTH_TOKEN.toString()),
            null
        )?.use { c -> validateUnknownAuthTokenFilteredCursor(c) } ?: false
    }

    private fun testWalletHelperGetByAuthTokenFilterUnknownAuthToken(): Boolean {
        return Wallet.getAuthorizedSeed(
            ctx,
            UNKNOWN_AUTH_TOKEN,
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS
        )?.use { c -> validateUnknownAuthTokenFilteredCursor(c) } ?: false
    }

    private fun testWalletHelperFilterUnknownAuthToken(): Boolean {
        return Wallet.getAuthorizedSeeds(
            ctx,
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
            WalletContractV1.AUTHORIZED_SEEDS_AUTH_TOKEN,
            UNKNOWN_AUTH_TOKEN.toString()
        )?.use { c -> validateUnknownAuthTokenFilteredCursor(c) } ?: false
    }

    private fun validatePurposeFilteredCursor(c: Cursor): Boolean {
        if (c.count != authTokens.size) return false
        if (!validateAuthorizedSeedsCursor(c)) return false

        val found = BooleanArray(authTokens.size)
        while (c.moveToNext()) {
            val authToken = c.getLong(0)
            if (authToken < 0) return false

            val purpose = c.getInt(1)
            if (purpose != WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION) return false // should only contain PURPOSE_SIGN_SOLANA_TRANSACTION

            val name = c.getString(2)
            authTokens.indexOf(authToken).takeIf { it != -1 }?.let { idx ->
                if (name != authorizedSeeds[idx]) return false
                found[idx] = true
            }
        }

        return found.all { it }
    }

    private fun testTableExpressionFilterPurpose(): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
            "${WalletContractV1.AUTHORIZED_SEEDS_AUTH_PURPOSE}=?",
            arrayOf(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION.toString()),
            null
        )?.use { c -> validatePurposeFilteredCursor(c) } ?: false
    }

    private fun testWalletHelperFilterPurpose(): Boolean {
        return Wallet.getAuthorizedSeeds(
            ctx,
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
            WalletContractV1.AUTHORIZED_SEEDS_AUTH_PURPOSE,
            WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION.toString()
        )?.use { c -> validatePurposeFilteredCursor(c) } ?: false
    }

    private fun validateUnknownPurposeFilteredCursor(c: Cursor): Boolean {
        return validateAuthorizedSeedsCursor(c) &&
                c.count == 0
    }

    private fun testTableExpressionFilterUnknownPurpose(): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
            "${WalletContractV1.AUTHORIZED_SEEDS_AUTH_PURPOSE}=?",
            arrayOf((WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION + 1).toString()),
            null
        )?.use { c -> validateUnknownPurposeFilteredCursor(c) } ?: false
    }

    private fun testWalletHelperFilterUnknownPurpose(): Boolean {
        return Wallet.getAuthorizedSeeds(
            ctx,
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
            WalletContractV1.AUTHORIZED_SEEDS_AUTH_PURPOSE,
            (WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION + 1).toString()
        )?.use { c -> validateUnknownPurposeFilteredCursor(c) } ?: false
    }

    private fun validateNameFilteredCursor(
        c: Cursor,
        expectedAuthToken: Long,
        expectedName: String
    ): Boolean {
        return validateAuthorizedSeedsCursor(c) &&
                c.count == 1 &&
                c.moveToFirst() &&
                c.getLong(0) == expectedAuthToken &&
                c.getInt(1) == WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION &&
                c.getString(2) == expectedName
    }

    private fun testTableExpressionFilterName(): Boolean {
        return authorizedSeeds.mapIndexed { i, name ->
            ctx.contentResolver.query(
                WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
                WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
                "${WalletContractV1.AUTHORIZED_SEEDS_SEED_NAME}=?",
                arrayOf(name),
                null
            )?.use { c -> validateNameFilteredCursor(c, authTokens[i], name) } ?: false
        }.all { it }
    }

    private fun testWalletHelperFilterName(): Boolean {
        return authorizedSeeds.mapIndexed { i, name ->
            Wallet.getAuthorizedSeeds(
                ctx,
                WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
                WalletContractV1.AUTHORIZED_SEEDS_SEED_NAME,
                name
            )?.use { c -> validateNameFilteredCursor(c, authTokens[i], name) } ?: false
        }.all { it }
    }

    private fun validateUnknownNameFilteredCursor(c: Cursor): Boolean {
        return validateAuthorizedSeedsCursor(c) && c.count == 0
    }

    private fun testTableExpressionFilterUnknownName(): Boolean {
        return ctx.contentResolver.query(
            WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
            "${WalletContractV1.AUTHORIZED_SEEDS_SEED_NAME}=?",
            arrayOf("NoSeedShouldExistWithThisName"),
            null
        )?.use { c -> validateUnknownNameFilteredCursor(c) } ?: false
    }

    private fun testWalletHelperFilterUnknownName(): Boolean {
        return Wallet.getAuthorizedSeeds(
            ctx,
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS,
            WalletContractV1.AUTHORIZED_SEEDS_SEED_NAME,
            "NoSeedShouldExistWithThisName"
        )?.use { c -> validateUnknownNameFilteredCursor(c) } ?: false
    }

    private fun validateAuthorizedSeedsCursor(c: Cursor): Boolean {
        val authTokenIdx = c.getColumnIndex(WalletContractV1.AUTHORIZED_SEEDS_AUTH_TOKEN)
        val authPurposeIdx = c.getColumnIndex(WalletContractV1.AUTHORIZED_SEEDS_AUTH_PURPOSE)
        val seedNameIdx = c.getColumnIndex(WalletContractV1.AUTHORIZED_SEEDS_SEED_NAME)
        val isBackedUpIdx = c.getColumnIndex(WalletContractV1.AUTHORIZED_SEEDS_IS_BACKED_UP)

        // Ensure all required columns are present
        if (authTokenIdx == -1 ||
            authPurposeIdx == -1 ||
            seedNameIdx == -1 ||
            if (privilegedSeedVaultChecker.isPrivileged()) {
                isBackedUpIdx == -1
            } else {
                isBackedUpIdx != -1
            }) return false

        // Check the data type of every record
        val originalPosition = c.position
        c.moveToPosition(-1)
        var result = true
        while (c.moveToNext()) {
            if (c.getType(authTokenIdx) != Cursor.FIELD_TYPE_INTEGER ||
                c.getType(authPurposeIdx) != Cursor.FIELD_TYPE_INTEGER ||
                c.getType(seedNameIdx) != Cursor.FIELD_TYPE_STRING ||
                (isBackedUpIdx != -1 && c.getType(isBackedUpIdx) != Cursor.FIELD_TYPE_INTEGER)
            ) {
                result = false
                break
            }
        }
        c.moveToPosition(originalPosition)

        return result
    }
    
    companion object {
        private const val UNKNOWN_AUTH_TOKEN = 7394872231938472276L // if this random value matches, ¯\_(ツ)_/¯
    }
}

internal class NoAuthorizedSeedsContentProviderTestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    noAuthorizedSeedsChecker: NoAuthorizedSeedsChecker,
    @ApplicationContext private val ctx: Context,
    logger: TestSessionLogger,
    implementationDetails: ImplementationDetails,
    privilegedSeedVaultChecker: PrivilegedSeedVaultChecker
) : AuthorizedSeedsContentProviderTestCase(
    emptyList(),
    preConditions = listOf(hasSeedVaultPermissionChecker, noAuthorizedSeedsChecker),
    ctx,
    logger,
    implementationDetails,
    privilegedSeedVaultChecker
) {
    override val id: String = "nascp"
    override val description: String = "Verify content provider behavior for ${WalletContractV1.AUTHORIZED_SEEDS_TABLE} when no seeds are authorized"
    override val instructions: String = ""
}

internal class HasAuthorizedSeedsContentProviderTestCase @Inject constructor(
    hasSeedVaultPermissionChecker: HasSeedVaultPermissionChecker,
    knownSeed12AuthorizedChecker: KnownSeed12AuthorizedChecker,
    knownSeed24AuthorizedChecker: KnownSeed24AuthorizedChecker,
    @ApplicationContext private val ctx: Context,
    logger: TestSessionLogger,
    implementationDetails: ImplementationDetails,
    @KnownSeed12 knownSeed12: KnownSeed,
    @KnownSeed24 knownSeed24: KnownSeed,
    privilegedSeedVaultChecker: PrivilegedSeedVaultChecker
) : AuthorizedSeedsContentProviderTestCase(
    listOf(knownSeed12.SEED_NAME, knownSeed24.SEED_NAME),
    preConditions = listOf(
        hasSeedVaultPermissionChecker,
        knownSeed12AuthorizedChecker,
        knownSeed24AuthorizedChecker
    ),
    ctx,
    logger,
    implementationDetails,
    privilegedSeedVaultChecker
) {
    override val id: String = "hascp"
    override val description: String = "Verify content provider behavior for ${WalletContractV1.AUTHORIZED_SEEDS_TABLE} when two seeds are authorized"
    override val instructions: String = ""
}