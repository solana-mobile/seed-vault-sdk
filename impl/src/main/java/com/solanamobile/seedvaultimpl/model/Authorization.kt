/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.model

import android.os.Process
import com.solanamobile.seedvault.WalletContractV1

data class Authorization(
    val uid: Int,
    val authToken: Int,
    val purpose: Purpose
) {
    enum class Purpose {
        SIGN_SOLANA_TRANSACTIONS;

        fun toWalletContractConstant(): Int {
            return when (this) {
                SIGN_SOLANA_TRANSACTIONS -> WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
            }
        }

        companion object {
            fun fromWalletContractConstant(@WalletContractV1.Purpose c: Int): Purpose {
                return when (c) {
                    WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION -> SIGN_SOLANA_TRANSACTIONS
                    else -> throw IllegalArgumentException("Unknown purpose $c")
                }
            }
        }
    }

    companion object {
        const val INVALID_UID = Process.INVALID_UID
    }

    init {
        require(uid > INVALID_UID) { "UID is $uid; must be > $INVALID_UID" }
    }
}