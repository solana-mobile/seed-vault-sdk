/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.model

data class SeedDetails(
    val seed: ByteArray,
    val seedPhraseWordIndices: List<Int>,
    val name: String? = null,
    val pin: String,
    val unlockWithBiometrics: Boolean = false,
) {
    companion object {
        const val ENTROPY_SHORT = 128
        const val ENTROPY_LONG = 256
        const val SEED_LENGTH = (512 / 8)
        const val SEED_PHRASE_WORD_COUNT_SHORT = ((ENTROPY_SHORT + ENTROPY_SHORT / 32) / 11)
        const val SEED_PHRASE_WORD_COUNT_LONG = ((ENTROPY_LONG + ENTROPY_LONG / 32) / 11)
        const val PIN_MIN_LENGTH = 4
        const val PIN_MAX_LENGTH = 20
    }

    init {
        require(seed.size == SEED_LENGTH) {
            "Seed size is ${seed.size}; must be $SEED_LENGTH"
        }
        require(seedPhraseWordIndices.size == SEED_PHRASE_WORD_COUNT_SHORT ||
                seedPhraseWordIndices.size == SEED_PHRASE_WORD_COUNT_LONG) {
            "Seed phrase word count is ${seedPhraseWordIndices.size}; must be either $SEED_PHRASE_WORD_COUNT_SHORT or $SEED_PHRASE_WORD_COUNT_LONG"
        }
        require(pin.length in PIN_MIN_LENGTH..PIN_MAX_LENGTH) {
            "PIN length is ${pin.length}; must be between $PIN_MIN_LENGTH and $PIN_MAX_LENGTH"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SeedDetails

        if (!seed.contentEquals(other.seed)) return false
        if (seedPhraseWordIndices != other.seedPhraseWordIndices) return false
        if (name != other.name) return false
        if (pin != other.pin) return false
        if (unlockWithBiometrics != other.unlockWithBiometrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = seed.contentHashCode()
        result = 31 * result + seedPhraseWordIndices.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + pin.hashCode()
        result = 31 * result + unlockWithBiometrics.hashCode()
        return result
    }
}
