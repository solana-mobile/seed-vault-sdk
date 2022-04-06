/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.usecase

import android.util.Log
import com.example.seedvault.Bip32DerivationPath
import com.example.seedvault.Bip32Level
import com.example.walletapidemo.data.SeedRepository
import com.example.walletapidemo.model.Account
import com.example.walletapidemo.model.Authorization
import com.example.walletapidemo.model.Seed

class PrepopulateKnownAccountsUseCase(private val seedRepository: SeedRepository) {
    suspend fun populateKnownAccounts(
        seed: Seed,
        purpose: Authorization.Purpose
    ) {
        when (purpose) {
            Authorization.Purpose.SIGN_SOLANA_TRANSACTIONS -> {
                val derivationRootPath = Bip32DerivationPath.newBuilder()
                    .appendLevel(Bip32Level(BIP44_PURPOSE, true))
                    .appendLevel(Bip32Level(BIP44_COIN_TYPE_SOLANA, true))
                    .build().normalize(purpose)
                val derivationRoot = Ed25519Slip10UseCase.derivePublicKeyPartialDerivation(
                    seed.details, derivationRootPath
                )
                val knownAccounts = mutableListOf<Account>()

                for (i in 0 until SOLANA_NUM_KNOWN_ACCOUNTS) {
                    // Type 1 paths: m/44'/501'/X'
                    val type1Levels = listOf(
                        Bip32Level(i, true)
                    )
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
                            val publicKey = Ed25519Slip10UseCase.derivePublicKey(
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
                    val type2Levels = listOf(
                        Bip32Level(i, true),
                        Bip32Level(0, true)
                    )
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
                            val publicKey = Ed25519Slip10UseCase.derivePublicKey(
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