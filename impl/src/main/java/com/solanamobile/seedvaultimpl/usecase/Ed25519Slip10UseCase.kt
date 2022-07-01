/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.usecase

import android.util.Log
import androidx.annotation.Size
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.exceptions.SodiumException
import com.solanamobile.seedvault.Bip32DerivationPath
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvaultimpl.model.SeedDetails
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

interface Ed25519Slip10UseCase {

    @Size(SignTransactionUseCase.ED25519_SECRET_KEY_SIZE)
    fun derivePrivateKey(
        seed: SeedDetails,
        bip32DerivationPath: Bip32DerivationPath
    ): ByteArray

    @Size(SignTransactionUseCase.ED25519_PUBLIC_KEY_SIZE)
    fun derivePublicKey(
        seed: SeedDetails,
        bip32DerivationPath: Bip32DerivationPath,
        derivationRoot: BipDerivationUseCase.PartialPublicDerivation? = null
    ): ByteArray

    @Size(SignTransactionUseCase.ED25519_PUBLIC_KEY_SIZE)
    fun derivePublicKeyPartialDerivation(
        seed: SeedDetails,
        bip32DerivationPath: Bip32DerivationPath
    ): BipDerivationUseCase.PartialPublicDerivation
}


internal class Ed25519Slip10UseCaseImpl(
    private val lazySodiumAndroid: LazySodiumAndroid
) : Ed25519Slip10UseCase {
    private data class KeyDerivationMaterial(
        @Size(SignTransactionUseCase.ED25519_SECRET_KEY_SIZE) val k: ByteArray,
        @Size(SignTransactionUseCase.ED25519_SECRET_KEY_SIZE) val c: ByteArray,
    ) : BipDerivationUseCase.PartialPublicDerivation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as KeyDerivationMaterial

            if (!k.contentEquals(other.k)) return false
            if (!c.contentEquals(other.c)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = k.contentHashCode()
            result = 31 * result + c.contentHashCode()
            return result
        }
    }

    private companion object {
        val TAG = Ed25519Slip10UseCase::class.simpleName

        const val MASTER_SECRET_MAC_KEY = "ed25519 seed"
        const val MAC = "HmacSHA512"
    }

    @Size(SignTransactionUseCase.ED25519_SECRET_KEY_SIZE)
    override fun derivePrivateKey(
        seed: SeedDetails,
        bip32DerivationPath: Bip32DerivationPath
    ): ByteArray {
        Log.d(TAG, "Deriving private key from root")
        val kdm = deriveSecretKey(seed, bip32DerivationPath)
        val keyPair = try {
            lazySodiumAndroid.cryptoSignSeedKeypair(kdm.k)
        } catch (_: SodiumException) {
            throw BipDerivationUseCase.KeyDoesNotExistException("Key does not exist for $bip32DerivationPath")
        }
        return keyPair.secretKey.asBytes
    }

    @Size(SignTransactionUseCase.ED25519_PUBLIC_KEY_SIZE)
    override fun derivePublicKey(
        seed: SeedDetails,
        bip32DerivationPath: Bip32DerivationPath,
        derivationRoot: BipDerivationUseCase.PartialPublicDerivation?
    ): ByteArray {
        Log.d(
            TAG,
            "Deriving public key from derivationRoot=${if (derivationRoot != null) "partial" else "root"}"
        )
        val kdm =
            deriveSecretKey(seed, bip32DerivationPath, derivationRoot as KeyDerivationMaterial?)
        val keyPair = try {
            lazySodiumAndroid.cryptoSignSeedKeypair(kdm.k)
        } catch (_: SodiumException) {
            throw BipDerivationUseCase.KeyDoesNotExistException("Key does not exist for $bip32DerivationPath")
        }
        return keyPair.publicKey.asBytes
    }

    @Size(SignTransactionUseCase.ED25519_PUBLIC_KEY_SIZE)
    override fun derivePublicKeyPartialDerivation(
        seed: SeedDetails,
        bip32DerivationPath: Bip32DerivationPath
    ): BipDerivationUseCase.PartialPublicDerivation {
        Log.d(TAG, "Deriving master secret partial public derivation")
        return deriveSecretKey(seed, bip32DerivationPath)
    }

    private fun deriveMasterSecret(@Size(SeedDetails.SEED_LENGTH.toLong()) seed: ByteArray): KeyDerivationMaterial {
        val hmac = Mac.getInstance(MAC)
        val keySpec = SecretKeySpec(MASTER_SECRET_MAC_KEY.encodeToByteArray(), MAC)
        hmac.init(keySpec)
        val data = hmac.doFinal(seed)
        return KeyDerivationMaterial(data.copyOf(32), data.copyOfRange(32, 64))
    }

    private fun deriveSecretKey(
        seed: SeedDetails,
        bip32DerivationPath: Bip32DerivationPath,
        derivationRoot: KeyDerivationMaterial? = null
    ): KeyDerivationMaterial {
        var node = derivationRoot ?: deriveMasterSecret(seed.seed)
        for (level in bip32DerivationPath.levels) {
            Log.d(TAG, "Deriving child private key ${level.index} (hardened=${level.hardened})")
            node = deriveChildSecretKey(node, level.index, level.hardened)
        }

        return node
    }

    private fun deriveChildSecretKey(
        kdm: KeyDerivationMaterial,
        @WalletContractV1.BipIndex index: Int,
        hardened: Boolean
    ): KeyDerivationMaterial {
        require(hardened) { "Ed25519-SLIP10 does not support non-hardened keys" }

        val hmac = Mac.getInstance(MAC)
        val keySpec = SecretKeySpec(kdm.c, MAC)

        hmac.init(keySpec)
        hmac.update(0.toByte())
        hmac.update(kdm.k)
        hmac.update(index.or(0x8000_0000.toInt()).toBigEndianByteArray())
        val h = hmac.doFinal()

        return KeyDerivationMaterial(h.copyOf(32), h.copyOfRange(32, 64))
    }
}

@Size(4)
private fun Int.toBigEndianByteArray(): ByteArray {
    val b = ByteArray(4)
    b[0] = (shr(24)).toByte()
    b[1] = (shr(16)).toByte()
    b[2] = (shr(8)).toByte()
    b[3] = toByte()
    return b
}