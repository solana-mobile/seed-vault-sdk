/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.usecase

import android.util.Log
import com.solanamobile.seedvault.Bip32DerivationPath
import com.solanamobile.seedvault.BipLevel
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.model.Account
import com.solanamobile.seedvaultimpl.model.Authorization
import com.solanamobile.seedvaultimpl.model.Seed
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrepopulateKnownAccountsUseCase @Inject constructor(
    private val seedRepository: SeedRepository,
    private val ed25519Slip10UseCase: Ed25519Slip10UseCase
) {
    suspend fun populateKnownAccounts(
        seed: Seed,
        purpose: Authorization.Purpose
    ) {
        when (purpose) {
            Authorization.Purpose.SIGN_SOLANA_TRANSACTIONS -> {
                val derivationRootPath = Bip32DerivationPath.newBuilder()
                    .appendLevel(BipLevel(BIP44_PURPOSE, true))
                    .appendLevel(BipLevel(BIP44_COIN_TYPE_SOLANA, true))
                    .build().normalize(purpose)
                val derivationRoot = ed25519Slip10UseCase.derivePublicKeyPartialDerivation(
                    seed.details, derivationRootPath
                )
                val knownAccounts = mutableListOf<Account>()

                for (i in 0 until SOLANA_NUM_KNOWN_ACCOUNTS) {
                    // Type 1 paths: m/44'/501'/X'
                    val type1Levels = listOf(BipLevel(i, true))
                    val type1Uri = Bip32DerivationPath.newBuilder()
                        .appendLevels(derivationRootPath.levels)
                        .appendLevels(type1Levels)
                        .build()
                        .normalize(purpose)
                        .toUri()
                    if (seed.accounts.firstOrNull { account ->
                            account.purpose == purpose && account.bip32DerivationPathUri == type1Uri
                    } == null
                    ) {
                        val partialPath = Bip32DerivationPath.newBuilder()
                            .appendLevels(type1Levels)
                            .build()
                            .normalize(purpose)

                        try {
                            val publicKey = ed25519Slip10UseCase.derivePublicKey(
                                seed.details,
                                partialPath,
                                derivationRoot
                            )
                            val account = Account(Account.INVALID_ACCOUNT_ID, purpose, type1Uri, publicKey)
                            knownAccounts.add(account)
                            Log.d(TAG, "Added account ${GetNameUseCase.getName(account)} for $type1Uri with purpose $purpose")
                        } catch (e: BipDerivationUseCase.KeyDoesNotExistException) {
                            Log.w(TAG, "Key for derivation path $type1Uri with purpose $purpose does not exist; skipping...")
                        }
                    } else {
                        Log.d(TAG, "Account for $type1Uri with purpose $purpose already exists; skipping...")
                    }

                    // Type 2 paths: m/44'/501'/X'/0'
                    val type2Levels = listOf(BipLevel(i, true), BipLevel(0, true))
                    val type2Uri = Bip32DerivationPath.newBuilder()
                        .appendLevels(derivationRootPath.levels)
                        .appendLevels(type2Levels)
                        .build()
                        .normalize(purpose)
                        .toUri()
                    if (seed.accounts.firstOrNull { account ->
                            account.bip32DerivationPathUri == type2Uri && account.purpose == purpose
                        } == null
                    ) {
                        val partialPath = Bip32DerivationPath.newBuilder()
                            .appendLevels(type2Levels)
                            .build()
                        try {
                            val publicKey = ed25519Slip10UseCase.derivePublicKey(
                                seed.details,
                                partialPath,
                                derivationRoot
                            )
                            val account = Account(Account.INVALID_ACCOUNT_ID, purpose, type2Uri, publicKey)
                            knownAccounts.add(account)
                            Log.d(TAG, "Added account ${GetNameUseCase.getName(account)} for $type2Uri with purpose $purpose")
                        } catch (e: BipDerivationUseCase.KeyDoesNotExistException) {
                            Log.w(TAG, "Key for derivation path $type2Uri  with purpose $purpose does not exist; skipping...")
                        }
                    } else {
                        Log.d(TAG, "Account for $type2Uri with purpose $purpose already exists; skipping...")
                    }
                }

                for (account in knownAccounts) {
                    seedRepository.addKnownAccountForSeed(seed.id, account)
                }
            }
        }
    }

    companion object {
        private val TAG = PrepopulateKnownAccountsUseCase::class.simpleName

        private const val BIP44_PURPOSE: Int = 44
        private const val BIP44_COIN_TYPE_SOLANA: Int = 501

        private const val SOLANA_NUM_KNOWN_ACCOUNTS = 50
    }
}