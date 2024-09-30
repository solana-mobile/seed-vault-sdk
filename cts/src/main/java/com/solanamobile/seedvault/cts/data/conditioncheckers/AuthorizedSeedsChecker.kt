/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.conditioncheckers

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvault.cts.data.ConditionCheckerImpl
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed12
import com.solanamobile.seedvault.cts.data.testdata.KnownSeed24
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

typealias AccountWithAuthToken = Pair<Long, Long>
@WalletContractV1.AuthToken
val AccountWithAuthToken.authToken get() = first
@WalletContractV1.AccountId
val  AccountWithAuthToken.accountId get() = second
infix fun Long.with(@WalletContractV1.AccountId accountId: Long): AccountWithAuthToken = this to accountId

@Singleton
internal class NoAuthorizedSeedsChecker @Inject constructor(
    @ApplicationContext private val ctx: Context
) : ConditionCheckerImpl() {
    override val id: String = "nasc"
    override val description: String = "No seeds are authorized"

    override suspend fun doCheck(): TestResult {
        return ctx.contentResolver.query(
            WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
            arrayOf(WalletContractV1.AUTHORIZED_SEEDS_AUTH_TOKEN),
            null,
            null,
            null
        )?.use {
            if (it.count == 0) TestResult.PASS else TestResult.FAIL
        } ?: TestResult.FAIL
    }
}

internal abstract class AuthorizedSeedsChecker(
    val seedName: String,
    val verificationDerivationPath: String,
    val verificationPublicKeyBase58: String,
    private val ctx: Context
) : ConditionCheckerImpl() {
    fun findMatchingSeedAndAccount(): AccountWithAuthToken? {
        return ctx.contentResolver.query(
            WalletContractV1.AUTHORIZED_SEEDS_CONTENT_URI,
            arrayOf(
                WalletContractV1.AUTHORIZED_SEEDS_AUTH_TOKEN,
                WalletContractV1.AUTHORIZED_SEEDS_AUTH_PURPOSE
            ),
            "${WalletContractV1.AUTHORIZED_SEEDS_SEED_NAME}=?",
            arrayOf(seedName),
            null
        )?.use u1@ { c ->
            while (c.moveToNext()) {
                val purpose = c.getInt(1)
                if (purpose != WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION) continue

                val authToken = c.getLong(0)
                val queryArgs = Bundle().apply {
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SELECTION,
                        "${WalletContractV1.ACCOUNTS_BIP32_DERIVATION_PATH}=?"
                    )
                    putStringArray(
                        ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                        arrayOf(verificationDerivationPath)
                    )
                    putLong(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
                }
                ctx.contentResolver.query(
                    WalletContractV1.ACCOUNTS_CONTENT_URI,
                    arrayOf(
                        WalletContractV1.ACCOUNTS_ACCOUNT_ID,
                        WalletContractV1.ACCOUNTS_PUBLIC_KEY_ENCODED
                    ),
                    queryArgs,
                    null
                )?.use u2@ { c2 ->
                    if (c2.moveToNext()) {
                        val publicKeyBase58 = c2.getString(1)
                        if (publicKeyBase58 == verificationPublicKeyBase58) {
                            return@u1 authToken with c2.getLong(0)
                        }
                    }
                }
            }

            null
        }
    }
    fun findMatchingSeed(): Long? {
        return findMatchingSeedAndAccount()?.authToken
    }
    fun findMatchingAccount(): Long? {
        return findMatchingSeedAndAccount()?.accountId
    }
}

@Singleton
internal class KnownSeed12AuthorizedChecker @Inject constructor(
    @ApplicationContext ctx: Context
) : AuthorizedSeedsChecker(
    KnownSeed12.SEED_NAME,
    KnownSeed12.DERIVATION_PATH_0.toString(),
    KnownSeed12.DERIVATION_PATH_0_PUBLIC_KEY_BASE58,
    ctx
) {
    override val id: String = "ks12a"
    override val description: String = "Seed '${KnownSeed12.SEED_NAME}' is authorized"

    override suspend fun doCheck(): TestResult {
        return if (findMatchingSeed() != null) TestResult.PASS else TestResult.FAIL
    }
}

@Singleton
internal class KnownSeed12NotAuthorizedChecker @Inject constructor(
    @ApplicationContext ctx: Context
) : AuthorizedSeedsChecker(
    KnownSeed12.SEED_NAME,
    KnownSeed12.DERIVATION_PATH_0.toString(),
    KnownSeed12.DERIVATION_PATH_0_PUBLIC_KEY_BASE58,
    ctx
) {
    override val id: String = "ks12na"
    override val description: String = "Seed '${KnownSeed12.SEED_NAME}' is not authorized"

    override suspend fun doCheck(): TestResult {
        return if (findMatchingSeed() == null) TestResult.PASS else TestResult.FAIL
    }
}

@Singleton
internal class KnownSeed24AuthorizedChecker @Inject constructor(
    @ApplicationContext ctx: Context
) : AuthorizedSeedsChecker(
    KnownSeed24.SEED_NAME,
    KnownSeed24.DERIVATION_PATH_0.toString(),
    KnownSeed24.DERIVATION_PATH_0_PUBLIC_KEY_BASE58,
    ctx
) {
    override val id: String = "ks24a"
    override val description: String = "Seed '${KnownSeed24.SEED_NAME}' is authorized"

    override suspend fun doCheck(): TestResult {
        return if (findMatchingSeed() != null) TestResult.PASS else TestResult.FAIL
    }
}

@Singleton
internal class KnownSeed24NotAuthorizedChecker @Inject constructor(
    @ApplicationContext ctx: Context
) : AuthorizedSeedsChecker(
    KnownSeed24.SEED_NAME,
    KnownSeed24.DERIVATION_PATH_0.toString(),
    KnownSeed24.DERIVATION_PATH_0_PUBLIC_KEY_BASE58,
    ctx
) {
    override val id: String = "ks24na"
    override val description: String = "Seed '${KnownSeed24.SEED_NAME}' is not authorized"

    override suspend fun doCheck(): TestResult {
        return if (findMatchingSeed() == null) TestResult.PASS else TestResult.FAIL
    }
}
