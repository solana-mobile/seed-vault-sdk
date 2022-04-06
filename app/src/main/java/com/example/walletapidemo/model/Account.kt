/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.model

import android.net.Uri

data class Account(
    val id: Int = INVALID_ACCOUNT_ID,
    val purpose: Authorization.Purpose,
    val bip32DerivationPathUri: Uri,
    val publicKey: ByteArray,
    val name: String? = null,
    val isUserWallet: Boolean = false,
    val isValid: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Account

        if (id != other.id) return false
        if (purpose != other.purpose) return false
        if (bip32DerivationPathUri != other.bip32DerivationPathUri) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (name != other.name) return false
        if (isUserWallet != other.isUserWallet) return false
        if (isValid != other.isValid) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + purpose.hashCode()
        result = 31 * result + bip32DerivationPathUri.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + isUserWallet.hashCode()
        result = 31 * result + isValid.hashCode()
        return result
    }

    companion object {
        const val INVALID_ACCOUNT_ID = -1
    }
}