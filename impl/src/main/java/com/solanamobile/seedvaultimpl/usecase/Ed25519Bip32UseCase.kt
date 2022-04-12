/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.usecase

import android.util.Log
import androidx.annotation.Size
import com.solanamobile.seedvault.Bip32DerivationPath
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvaultimpl.model.SeedDetails
import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and
import kotlin.experimental.or

object Ed25519Bip32UseCase {
    private data class KeyDerivationMaterial(
        @Size(32) val kL: ByteArray,
        @Size(32) val kR: ByteArray,
        @Size(32) val c: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as KeyDerivationMaterial

            if (!kL.contentEquals(other.kL)) return false
            if (!kR.contentEquals(other.kR)) return false
            if (!c.contentEquals(other.c)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = kL.contentHashCode()
            result = 31 * result + kR.contentHashCode()
            result = 31 * result + c.contentHashCode()
            return result
        }
    }

    private val TAG = Ed25519Bip32UseCase::class.simpleName
    
    private const val MASTER_SECRET_MAC_KEY = "ed25519 seed"
    private const val MASTER_SECRET_KEY_DERIVATION_MAC = "HmacSHA512"
    private const val MASTER_SECRET_CHAIN_CODE_DERIVATION_MAC = "HmacSHA256"
    private const val MASTER_SECRET_CHAIN_CODE_DATA_PREFIX: Byte = 1
    
    private const val KEY_DERIVATION_MAC = "HmacSHA512"

    private val ED25519_BASE_ORDER by lazy {
        BigInteger(1, byteArrayOf(
            0x10.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x14.toByte(), 0xDE.toByte(), 0xF9.toByte(), 0xDE.toByte(),
            0xA2.toByte(), 0xF7.toByte(), 0x9C.toByte(), 0xD6.toByte(),
            0x58.toByte(), 0x12.toByte(), 0x63.toByte(), 0x1A.toByte(),
            0x5C.toByte(), 0xF5.toByte(), 0xD3.toByte(), 0xED.toByte()))
    }

    @Size(SignTransactionUseCase.ED25519_SECRET_KEY_SIZE)
    fun derivePrivateKey(
        seed: SeedDetails,
        bip32DerivationPath: Bip32DerivationPath
    ): ByteArray {
        Log.d(TAG, "Deriving master secret private key")
        var node = deriveMasterSecret(seed.seed)
        for (level in bip32DerivationPath.levels) {
            Log.d(TAG, "Deriving child private key ${level.index} (hardened=${level.hardened})")
            node = deriveChildPrivateKey(node, level.index, level.hardened)
        }

        TODO("wrong. This is the expanded private key, not the Sodium secret key.")
        val privateKey = ByteArray(64)
        node.kL.copyInto(privateKey, 0)

        return privateKey
    }

    @Size(SignTransactionUseCase.ED25519_PUBLIC_KEY_SIZE)
    fun derivePublicKey(
        seed: SeedDetails,
        bip32DerivationPath: Bip32DerivationPath
    ): ByteArray {
        val privKey = derivePrivateKey(seed, bip32DerivationPath)
        Log.d(TAG, "Deriving public key")
        return derivePublicKey(privKey)
    }

    private fun deriveMasterSecret(@Size(SeedDetails.SEED_LENGTH.toLong()) seed: ByteArray): KeyDerivationMaterial {
        @Size(64) val privKey = deriveMasterPrivateKey(seed)
        @Size(32) val chainCode = deriveMasterChainCode(seed)
        return KeyDerivationMaterial(privKey.copyOf(32), privKey.copyOfRange(32, 64), chainCode)
    }

    @Size(64)
    private fun deriveMasterPrivateKey(@Size(64) seed: ByteArray): ByteArray {
        val hmac = Mac.getInstance(MASTER_SECRET_KEY_DERIVATION_MAC)
        val keySpec = SecretKeySpec(MASTER_SECRET_MAC_KEY.encodeToByteArray(), MASTER_SECRET_KEY_DERIVATION_MAC)
        @Size(64) var data: ByteArray = seed
        do {
            hmac.init(keySpec)
            data = hmac.doFinal(data)
        } while ((data[31].and(0b0010_0000.toByte())) != 0.toByte())

        data[0] = data[0].and(0b1111_1000.toByte())
        data[31] = data[31].and(0b0111_1111.toByte()).or(0b0100_0000.toByte())

        return data
    }

    @Size(32)
    private fun deriveMasterChainCode(@Size(64) seed: ByteArray): ByteArray {
        val hmac = Mac.getInstance(MASTER_SECRET_CHAIN_CODE_DERIVATION_MAC)
        val keySpec = SecretKeySpec(MASTER_SECRET_MAC_KEY.encodeToByteArray(), MASTER_SECRET_CHAIN_CODE_DERIVATION_MAC)
        hmac.init(keySpec)
        hmac.update(MASTER_SECRET_CHAIN_CODE_DATA_PREFIX)
        return hmac.doFinal(seed)
    }

    private fun deriveChildPrivateKey(
        kdm: KeyDerivationMaterial,
        @WalletContractV1.BipIndex index: Int,
        hardened: Boolean
    ): KeyDerivationMaterial {
        val i: ByteArray
        val zPrefix: Byte
        val cPrefix: Byte
        val data1: ByteArray
        val data2: ByteArray?
        if (hardened) {
            i = index.or(0x8000_0000.toInt()).toLittleEndianByteArray()
            zPrefix = 0
            cPrefix = 1
            data1 = kdm.kL
            data2 = kdm.kR
        } else {
            i = index.toLittleEndianByteArray()
            zPrefix = 2
            cPrefix = 3
            data1 = derivePublicKey(kdm.kL)
            data2 = null
        }

        val hmac = Mac.getInstance(KEY_DERIVATION_MAC)
        val keySpec = SecretKeySpec(kdm.c, KEY_DERIVATION_MAC)

        hmac.init(keySpec)
        hmac.update(zPrefix)
        hmac.update(data1)
        hmac.update(data2) // May be null
        hmac.update(i)
        val z = hmac.doFinal()

        val zLBI = bigIntegerFromUnsignedLittleEndianByteArray(z.copyOfRange(0, 28))
        val kPLBI = bigIntegerFromUnsignedLittleEndianByteArray(kdm.kL)
        val kLBI = zLBI.multiply(BigInteger.valueOf(8L)).plus(kPLBI)
        if (isEd25519ScalarMultipleOfBaseOrder(kLBI)) {
            throw BipDerivationUseCase.KeyDoesNotExistException("No child private key for index $index (hardened=$hardened)")
        }
        @Size(32) val kL = littleEndianByteArray32FromBigInteger(kLBI)
        // NOTE: above math guarantees that kL will meet the requirements of a Ed25519 private key
        // re: setting/clearing bits in kL[0] and kL[31]

        val zRBI = bigIntegerFromUnsignedLittleEndianByteArray(z.copyOfRange(32, 64))
        val kPRBI = bigIntegerFromUnsignedLittleEndianByteArray(kdm.kR)
        val kRBI = zRBI.plus(kPRBI) // still needs to be mod 2**256
        @Size(32) val kR = littleEndianByteArray32FromBigInteger(kRBI) // includes implicit mod 2**256

        hmac.reset()
        hmac.update(cPrefix)
        hmac.update(data1)
        hmac.update(data2) // May be null
        hmac.update(i)

        @Size(32) val c = hmac.doFinal().copyOfRange(32, 64)

        return KeyDerivationMaterial(kL, kR, c)
    }

    @Size(32)
    private fun derivePublicKey(
        @Size(32) privKey: ByteArray
    ): ByteArray {
        val pubKey = scalarMultiplyByEd25519BasePoint(privKey)
        if (isEd25519IdentityPointEncoded(pubKey)) {
            throw BipDerivationUseCase.KeyDoesNotExistException()
        }
        return pubKey
    }

    @Size(32)
    private fun scalarMultiplyByEd25519BasePoint(
        @Size(32) scalar: ByteArray
    ): ByteArray {
        val result = ByteArray(32)
        TODO("Scalar multiplication by Ed25519 base point")
        return result
    }

    private fun isEd25519ScalarMultipleOfBaseOrder(scalar: BigInteger): Boolean {
        return (scalar.mod(ED25519_BASE_ORDER) == BigInteger.ZERO)
    }

    // This function checks if the encoded point represents the identity point for Ed25519. Note
    // that it assumes the point is fully normalized, as the output of
    // scalarMultiplyByEd25519BasePoint is guaranteed to be.
    private fun isEd25519IdentityPointEncoded(
        @Size(SignTransactionUseCase.ED25519_SECRET_KEY_SIZE) encodedPoint: ByteArray
    ): Boolean {
        if (encodedPoint[0] != 1.toByte()) return false
        for (i in 1..31) {
            if (encodedPoint[i] != 0.toByte()) return false
        }
        return true
    }

    private fun bigIntegerFromUnsignedLittleEndianByteArray(b: ByteArray): BigInteger {
        return BigInteger(1, b.reversedArray())
    }

    @Size(32)
    private fun littleEndianByteArray32FromBigInteger(bi: BigInteger): ByteArray {
        return bi.toByteArray().reversedArray().copyOf(32)
    }
}

@Size(4)
private fun Int.toLittleEndianByteArray(): ByteArray {
    val b = ByteArray(4)
    b[0] = toByte()
    b[1] = (shr(8)).toByte()
    b[2] = (shr(16)).toByte()
    b[3] = (shr(24)).toByte()
    return b
}