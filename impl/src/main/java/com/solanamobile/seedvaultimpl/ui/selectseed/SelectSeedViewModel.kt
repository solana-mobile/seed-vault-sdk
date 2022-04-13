/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.selectseed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.model.Authorization
import com.solanamobile.seedvaultimpl.model.Seed
import com.solanamobile.seedvaultimpl.ui.AuthorizeRequestType
import com.solanamobile.seedvaultimpl.ui.AuthorizeViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SelectSeedViewModel private constructor(
    private val seedRepository: SeedRepository,
    private val activityViewModel: AuthorizeViewModel
) : ViewModel() {
    private val _selectSeedUiState: MutableStateFlow<SelectSeedUiState> = MutableStateFlow(SelectSeedUiState())
    val selectSeedUiState = _selectSeedUiState.asStateFlow()

    private var uid: Int? = null
    private var uidJob: Job? = null

    init {
        viewModelScope.launch {
            activityViewModel.requests.collect { request ->
                if (request.type !is AuthorizeRequestType.Seed) {
                    // Any other request types should only be observed transiently, whilst the
                    // activity state is being updated.
                    _selectSeedUiState.value = SelectSeedUiState()
                    return@collect
                }

                setUid(request.requestorUid, request.type.purpose)
            }
        }
    }

    private fun setUid(uid: Int, purpose: Authorization.Purpose) {
        Log.d(TAG, "setUid($uid)")

        if (uid == Authorization.INVALID_UID) {
            Log.e(TAG, "No UID provided; canceling authorization")
            activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_UNSPECIFIED_ERROR)
            return
        } else if (this.uid == uid) {
            return // no change in UID, return without (re-)creating the relevant coroutine job
        }

        uidJob?.cancel()

        this.uid = uid
        uidJob = viewModelScope.launch {
            // We return an error if there are no remaining seeds to be authorized. For this to be
            // correct, we must wait until the repository indicates the data it shares is valid.
            seedRepository.delayUntilDataValid()

            // Generate a list of all seeds for which this UID is not already authorized for this
            // purpose
            seedRepository.seeds.collect { sim ->
                val seeds = sim.values.filterNot { seed ->
                    seed.authorizations.any { auth ->
                        auth.uid == uid && auth.purpose == purpose
                    }
                }
                if (seeds.isEmpty()) {
                    Log.w(TAG, "No non-authorized seeds remaining for UID $uid; aborting...")
                    activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_NO_AVAILABLE_SEEDS)
                    return@collect
                }
                _selectSeedUiState.update { it.copy(seeds = seeds) }
            }
        }
    }

    fun setSelectedSeed(seedId: Long?) {
        Log.d(TAG, "setSelectedSeed($seedId)")
        _selectSeedUiState.update { it.copy(selectedSeedId = seedId) }
    }

    fun selectSeedForAuthorization() {
        val uid = this.uid
        check(uid != null) { "authorizeSelectedSeedForUid called when no UID is set" }

        val selectedSeedId = _selectSeedUiState.value.selectedSeedId
        check(selectedSeedId != null) { "authorizeSelectedSeedForUid called when no seed is selected" }

        Log.d(TAG, "setSelectedSeed for seedId=$selectedSeedId/uid=$uid")

        activityViewModel.updateSeedRequestWithSeedId(selectedSeedId)
    }

    companion object {
        private val TAG = SelectSeedViewModel::class.simpleName

        fun provideFactory(
            seedRepository: SeedRepository,
            activityViewModel: AuthorizeViewModel
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SelectSeedViewModel(seedRepository, activityViewModel) as T
            }
        }
    }
}

data class SelectSeedUiState(
    val seeds: List<Seed> = listOf(),
    val selectedSeedId: Long? = null
)