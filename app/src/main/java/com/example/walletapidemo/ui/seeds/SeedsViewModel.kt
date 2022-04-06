/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.ui.seeds

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.walletapidemo.data.SeedRepository
import com.example.walletapidemo.model.Seed
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SeedsViewModel private constructor(
    private val seedRepository: SeedRepository
) : ViewModel() {
    private val _seedsUiState: MutableStateFlow<SeedsUiState> = MutableStateFlow(SeedsUiState())
    val seedsUiState = _seedsUiState.asStateFlow()

    init {
        viewModelScope.launch {
            seedRepository.seeds.collect { sim ->
                _seedsUiState.update { it.copy(seeds = sim.values.sortedBy { seed -> seed.id }) }
            }
        }

        viewModelScope.launch {
            seedRepository.isFull.collect { full ->
                _seedsUiState.update { it.copy(canCreateSeeds = !full) }
            }
        }
    }

    fun deleteSeed(seedId: Int) {
        viewModelScope.launch {
            Log.d(TAG, "Deleting seed $seedId")
            seedRepository.deleteSeed(seedId)
        }
    }

    fun deleteAllSeeds() {
        viewModelScope.launch {
            Log.d(TAG, "Deleting all seeds")
            seedRepository.deleteAllSeeds()
        }
    }

    companion object {
        private val TAG = SeedsViewModel::class.simpleName

        fun provideFactory(
            seedRepository: SeedRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SeedsViewModel(seedRepository) as T
            }
        }
    }
}

data class SeedsUiState(
    val seeds: List<Seed> = listOf(),
    val canCreateSeeds: Boolean = false
)