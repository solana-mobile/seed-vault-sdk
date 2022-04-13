/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.seeddetail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.model.Authorization
import com.solanamobile.seedvaultimpl.model.SeedDetails
import com.solanamobile.seedvaultimpl.usecase.Bip39PhraseUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SeedDetailViewModel private constructor(
    private val seedRepository: SeedRepository,
    private val seedId: Long
) : ViewModel() {
    private val _seedDetailUiState: MutableStateFlow<SeedDetailUiState> = MutableStateFlow(SeedDetailUiState())
    val seedDetailUiState = _seedDetailUiState.asStateFlow()

    init {
        if (seedId == 0L) {
            // Initializing for create
            _seedDetailUiState.value = SeedDetailUiState()
        } else {
            // Initializing for update
            viewModelScope.launch {
                val seed = seedRepository.seeds.value[seedId]
                require(seed != null) { "Seed $seedId not found" }
                val seedPhraseLength = if (seed.details.seedPhraseWordIndices.size == SeedDetails.SEED_PHRASE_WORD_COUNT_SHORT) {
                    SeedDetailUiState.SeedPhraseLength.SEED_PHRASE_12_WORDS
                } else {
                    SeedDetailUiState.SeedPhraseLength.SEED_PHRASE_24_WORDS
                }
                _seedDetailUiState.value = SeedDetailUiState(
                    isCreateMode = false,
                    name = seed.details.name ?: "",
                    phraseLength = seedPhraseLength,
                    phrase = List(SeedDetailUiState.SeedPhraseLength.SEED_PHRASE_24_WORDS.length) {
                        seed.details.seedPhraseWordIndices.getOrNull(it)?.let { Bip39PhraseUseCase.toWord(it) } ?: ""
                    },
                    pin = seed.details.pin,
                    enableBiometrics = seed.details.unlockWithBiometrics,
                    authorizedApps = seed.authorizations
                )
            }
        }
    }

    fun setSeedPhraseLength(phraseLength: SeedDetailUiState.SeedPhraseLength) {
        check(_seedDetailUiState.value.isCreateMode) { "Cannot set seed phrase length when editing a seed" }
        Log.d(TAG, "setSeedPhraseLength($phraseLength)")
        _seedDetailUiState.update { it.copy(phraseLength = phraseLength) }
    }

    fun setName(name: String) {
        Log.d(TAG, "setName($name)")
        _seedDetailUiState.update { it.copy(name = name) }
    }

    fun setSeedPhraseWord(index: Int, word: String) {
        require(index in 0 until SeedDetailUiState.SeedPhraseLength.SEED_PHRASE_24_WORDS.length)
        Log.d(TAG, "setSeedPhraseWord($index, $word)")
        _seedDetailUiState.update { current ->
            val newPhrase = current.phrase.toMutableList().also {
                it[index] = word
            }
            current.copy(phrase = newPhrase)
        }
    }

    fun setPIN(pin: String) {
        Log.d(TAG, "setPIN($pin)")
        _seedDetailUiState.update { it.copy(pin = pin) }
    }

    fun enableBiometrics(en: Boolean) {
        Log.d(TAG, "enableBiometrics($en)")
        _seedDetailUiState.update { it.copy(enableBiometrics = en) }
    }

    fun saveSeed(): Boolean {
        Log.d(TAG, "Validating seed parameters")

        try {
            val seed = seedDetailUiState.value.let {
                val phrase: List<Int> = it.phrase.take(it.phraseLength.length).map { w ->
                    Bip39PhraseUseCase.toIndex(w)
                }
                val seedBytes = Bip39PhraseUseCase.toSeed(phrase)
                SeedDetails(seedBytes, phrase, it.name.ifBlank { null }, it.pin, it.enableBiometrics)
            }

            Log.i(TAG, "Successfully created Seed $seed; committing to SeedRepository")
            viewModelScope.launch {
                if (seedId == 0L) {
                    seedRepository.createSeed(seed)
                } else {
                    seedRepository.updateSeed(seedId, seed)
                }
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Seed creation/update failed", e)
            return false
        }

        return true
    }

    fun deauthorize(authToken: Long) {
        Log.d(TAG, "Deauthorizing AuthToken $authToken for seed $seedId")

        viewModelScope.launch {
            seedRepository.deauthorizeSeed(seedId, authToken)

            // This ViewModel doesn't observe the SeedRepository state - update our UiState manually
            _seedDetailUiState.update { current ->
                val updatedAuthorizedApps = current.authorizedApps.filterNot { auth ->
                    auth.authToken == authToken
                }
                current.copy(authorizedApps = updatedAuthorizedApps)
            }
        }
    }

    companion object {
        private val TAG = SeedDetailViewModel::class.simpleName

        fun provideFactory(
            seedRepository: SeedRepository,
            seedId: Long
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SeedDetailViewModel(seedRepository, seedId) as T
            }
        }
    }
}

data class SeedDetailUiState(
    val isCreateMode: Boolean = true,
    val name: String = "",
    val phraseLength: SeedPhraseLength = SeedPhraseLength.SEED_PHRASE_12_WORDS,
    val phrase: List<String> = List(SeedPhraseLength.SEED_PHRASE_24_WORDS.length) { "" },
    val pin: String = "",
    val enableBiometrics: Boolean = false,
    val authorizedApps: List<Authorization> = listOf()
) {
    enum class SeedPhraseLength(val length: Int) {
        SEED_PHRASE_12_WORDS(12), SEED_PHRASE_24_WORDS(24);

        init {
            assert(SeedDetails.SEED_PHRASE_WORD_COUNT_SHORT == 12) { "Unexpected length of short seed phrase; SeedDetailViewModel needs updating" }
            assert(SeedDetails.SEED_PHRASE_WORD_COUNT_LONG == 24) { "Unexpected length of long seed phrase; SeedDetailViewModel needs updating" }
        }
    }

    init {
        require(phrase.size == SeedPhraseLength.SEED_PHRASE_24_WORDS.length) {
            "Phrase array size is ${phrase.size}; expected ${SeedPhraseLength.SEED_PHRASE_24_WORDS.length}"
        }
    }
}

