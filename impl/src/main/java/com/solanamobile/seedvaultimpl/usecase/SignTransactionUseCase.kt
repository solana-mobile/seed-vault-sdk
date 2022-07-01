/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.usecase

import androidx.annotation.Size
import com.goterl.lazysodium.LazySodiumAndroid
import com.solanamobile.seedvaultimpl.model.Authorization
import com.goterl.lazysodium.interfaces.Sign
import com.solanamobile.seedvaultimpl.usecase.SignTransactionUseCase.Companion.ED25519_SECRET_KEY_SIZE
import com.solanamobile.seedvaultimpl.usecase.SignTransactionUseCase.Companion.ED25519_SIGNATURE_SIZE

interface SignTransactionUseCase {

    fun sign(
        purpose: Authorization.Purpose,
        key: ByteArray,
        @Size(min = 1) transaction: ByteArray
    ): ByteArray

    companion object {
        const val ED25519_SECRET_KEY_SIZE = Sign.ED25519_SECRETKEYBYTES.toLong()
        const val ED25519_PUBLIC_KEY_SIZE = Sign.ED25519_PUBLICKEYBYTES.toLong()
        const val ED25519_SIGNATURE_SIZE = Sign.ED25519_BYTES.toLong()
    }
}

class SignTransactionUseCaseImpl(
    private val lazySodiumAndroid: LazySodiumAndroid
) : SignTransactionUseCase {

    override fun sign(
        purpose: Authorization.Purpose,
        key: ByteArray,
        @Size(min = 1) transaction: ByteArray
    ): ByteArray {
        require(transaction.isNotEmpty()) { "Transaction cannot be empty" }

        return when (purpose) {
            Authorization.Purpose.SIGN_SOLANA_TRANSACTIONS -> {
                // TODO: validate transaction is a Solana transaction before signing
                require(key.size == ED25519_SECRET_KEY_SIZE.toInt()) { "Invalid private key for signing Solana transactions" }
                signEd25519(key, transaction)
            }
        }
    }

    @Size(ED25519_SIGNATURE_SIZE)
    private fun signEd25519(
        @Size(ED25519_SECRET_KEY_SIZE) key: ByteArray,
        @Size(min = 1) transaction: ByteArray
    ): ByteArray {
        val signature = ByteArray(Sign.ED25519_BYTES)
        lazySodiumAndroid.cryptoSignDetached(
            signature, transaction,
            transaction.size.toLong(), key
        )
        return signature
    }
}