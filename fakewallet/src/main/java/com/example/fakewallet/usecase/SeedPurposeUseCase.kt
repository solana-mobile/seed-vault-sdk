/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.fakewallet.usecase

import com.example.seedvault.WalletContractV1

object SeedPurposeUseCase {
    operator fun invoke(purpose: Int): String {
        return when (purpose) {
            WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION -> "Sign Solana Transactions"
            else -> "Unknown"
        }
    }
}