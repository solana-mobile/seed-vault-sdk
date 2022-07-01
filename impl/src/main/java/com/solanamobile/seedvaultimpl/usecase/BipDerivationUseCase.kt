/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.usecase

import com.solanamobile.seedvault.Bip32DerivationPath
import com.solanamobile.seedvault.BipLevel
import com.solanamobile.seedvault.Bip44DerivationPath
import com.solanamobile.seedvault.BipDerivationPath
import com.solanamobile.seedvaultimpl.model.Authorization
import com.solanamobile.seedvaultimpl.model.Seed

interface BipDerivationUseCase {
    // TODO: also support Ed25519Bip32 derivations

    // Opaque object representing a partial derivation from a BIP32 path. Further derivations can
    // be performed on this. This is intended to be used when deriving groups of public keys, to
    // save on processing the same root derivation path multiple times.
    interface PartialPublicDerivation

    // In some key derivation schemes (such as BIP32-Ed25519), not every key exists.
    class KeyDoesNotExistException(message: String? = null, cause: Throwable? = null) :
        Exception(message, cause)

    fun derivePrivateKey(
        purpose: Authorization.Purpose,
        seed: Seed,
        derivationPath: Bip32DerivationPath
    ): ByteArray

    fun derivePublicKey(
        purpose: Authorization.Purpose,
        seed: Seed,
        derivationPath: Bip32DerivationPath,
        partialPublicDerivation: PartialPublicDerivation? = null
    ): ByteArray

    fun derivePublicKeyPartial(
        purpose: Authorization.Purpose,
        seed: Seed,
        derivationPath: Bip32DerivationPath
    ): PartialPublicDerivation
}

internal class BipDerivationUseCaseImpl(
    private val ed25519Slip10UseCase: Ed25519Slip10UseCase
) : BipDerivationUseCase {

    override fun derivePrivateKey(
        purpose: Authorization.Purpose,
        seed: Seed,
        derivationPath: Bip32DerivationPath
    ): ByteArray {
        return when (purpose) {
            Authorization.Purpose.SIGN_SOLANA_TRANSACTIONS ->
                ed25519Slip10UseCase.derivePrivateKey(seed.details, derivationPath)
        }
    }

    override fun derivePublicKey(
        purpose: Authorization.Purpose,
        seed: Seed,
        derivationPath: Bip32DerivationPath,
        partialPublicDerivation: BipDerivationUseCase.PartialPublicDerivation?
    ): ByteArray {
        return when (purpose) {
            Authorization.Purpose.SIGN_SOLANA_TRANSACTIONS ->
                ed25519Slip10UseCase.derivePublicKey(
                    seed.details,
                    derivationPath,
                    partialPublicDerivation
                )
        }
    }

    override fun derivePublicKeyPartial(
        purpose: Authorization.Purpose,
        seed: Seed,
        derivationPath: Bip32DerivationPath
    ): BipDerivationUseCase.PartialPublicDerivation {
        return when (purpose) {
            Authorization.Purpose.SIGN_SOLANA_TRANSACTIONS ->
                ed25519Slip10UseCase.derivePublicKeyPartialDerivation(seed.details, derivationPath)
        }
    }
}

fun BipDerivationPath.normalize(
    purpose: Authorization.Purpose
): BipDerivationPath {
    return when (this) {
        is Bip32DerivationPath -> normalize(purpose)
        is Bip44DerivationPath -> normalize(purpose)
        else -> throw UnsupportedOperationException("Unknown BIP derivation path type")
    }
}

fun Bip32DerivationPath.normalize(
    purpose: Authorization.Purpose
): Bip32DerivationPath {
    return when (purpose) {
        Authorization.Purpose.SIGN_SOLANA_TRANSACTIONS -> {
            hardenAllLevels()
        }
    }
}

fun Bip44DerivationPath.normalize(
    purpose: Authorization.Purpose
): Bip44DerivationPath {
    return when (purpose) {
        Authorization.Purpose.SIGN_SOLANA_TRANSACTIONS -> {
            hardenAllLevels()
        }
    }
}

fun BipDerivationPath.toBip32DerivationPath(
    purpose: Authorization.Purpose
): Bip32DerivationPath {
    return when (this) {
        is Bip32DerivationPath -> this
        is Bip44DerivationPath -> toBip32DerivationPath(purpose)
        else -> throw UnsupportedOperationException("Unknown BIP derivation path type")
    }
}

private const val BIP44_PURPOSE: Int = 44
private const val BIP44_COIN_TYPE_SOLANA: Int = 501

fun Bip44DerivationPath.toBip32DerivationPath(
    purpose: Authorization.Purpose
): Bip32DerivationPath {
    return when (purpose) {
        Authorization.Purpose.SIGN_SOLANA_TRANSACTIONS -> {
            Bip32DerivationPath.newBuilder()
                .appendLevel(BipLevel(BIP44_PURPOSE, true))
                .appendLevel(BipLevel(BIP44_COIN_TYPE_SOLANA, true))
                .appendLevels(hardenAllLevels().levels)
                .build()
        }
    }
}

private fun Bip32DerivationPath.hardenAllLevels(): Bip32DerivationPath {
    val levels = levels
    if (levels.all { level -> level.hardened }) {
        return this
    }
    return Bip32DerivationPath(levels.map { level ->
        BipLevel(level.index, true)
    })
}

private fun Bip44DerivationPath.hardenAllLevels(): Bip44DerivationPath {
    val levels = levels
    if (levels.all { level -> level.hardened }) {
        return this
    }
    val newLevels = levels.map { level ->
        if (level.hardened) level else BipLevel(level.index, true)
    }
    return Bip44DerivationPath(newLevels[0], newLevels.getOrNull(1), newLevels.getOrNull(2))
}