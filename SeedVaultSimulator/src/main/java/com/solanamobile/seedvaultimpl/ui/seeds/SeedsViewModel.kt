/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.seeds

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.model.Seed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SeedsViewModel @Inject constructor(
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

    fun deleteSeed(seedId: Long) {
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
    }
}

data class SeedsUiState(
    val seeds: List<Seed> = listOf(),
    val canCreateSeeds: Boolean = false
)