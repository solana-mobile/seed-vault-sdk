/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.usecase

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

object VerifyEd25519SignatureUseCase {
    operator fun invoke(publicKey: ByteArray, payload: ByteArray, signature: ByteArray): Boolean {
        require(publicKey.size == ACCOUNT_PUBLIC_KEY_LEN) { "Invalid public key length for a Solana transaction" }
        require(payload.isNotEmpty()) { "Payload cannot be empty" }
        require(signature.size == SIGNATURE_LEN) { "Invalid signature length for a Solana transaction" }
        val publicKeyParams = Ed25519PublicKeyParameters(publicKey, 0)
        val signer = Ed25519Signer()
        signer.init(false, publicKeyParams)
        signer.update(payload, 0, payload.size)
        return signer.verifySignature(signature)
    }

    private const val ACCOUNT_PUBLIC_KEY_LEN = 32
    private const val SIGNATURE_LEN = 64
}