/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.usecase

import androidx.annotation.Size
import com.solanamobile.seedvaultimpl.ApplicationDependencyContainer
import com.solanamobile.seedvaultimpl.model.Authorization
import com.goterl.lazysodium.interfaces.Sign

object SignTransactionUseCase {
    const val ED25519_SECRET_KEY_SIZE = Sign.ED25519_SECRETKEYBYTES.toLong()
    const val ED25519_PUBLIC_KEY_SIZE = Sign.ED25519_PUBLICKEYBYTES.toLong()
    const val ED25519_SIGNATURE_SIZE = Sign.ED25519_BYTES.toLong()

    operator fun invoke(
        purpose: Authorization.Purpose,
        key: ByteArray,
        @Size(min=1) transaction: ByteArray
    ): ByteArray {
        require(transaction.isNotEmpty()) { "Transaction cannot be empty" }

        return when (purpose) {
            Authorization.Purpose.SIGN_SOLANA_TRANSACTIONS -> {
                require(key.size == ED25519_SECRET_KEY_SIZE.toInt()) { "Invalid private key for signing Solana transactions" }
                signEd25519(key, transaction)
            }
        }
    }

    @Size(ED25519_SIGNATURE_SIZE)
    private fun signEd25519(
        @Size(ED25519_SECRET_KEY_SIZE) key: ByteArray,
        @Size(min=1) transaction: ByteArray
    ): ByteArray {
        val signature = ByteArray(Sign.ED25519_BYTES)
        ApplicationDependencyContainer.sodium.cryptoSignDetached(signature, transaction,
            transaction.size.toLong(), key)
        return signature
    }
}