/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.selectseed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.model.Authorization
import com.solanamobile.seedvaultimpl.model.Seed
import com.solanamobile.seedvaultimpl.ui.AuthorizeRequestType
import com.solanamobile.seedvaultimpl.ui.AuthorizeCommonViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = SelectSeedViewModel.Factory::class)
class SelectSeedViewModel @AssistedInject constructor(
    private val seedRepository: SeedRepository,
    @Assisted private val authorizeCommonViewModel: AuthorizeCommonViewModel
) : ViewModel() {
    private val _selectSeedUiState: MutableStateFlow<SelectSeedUiState> = MutableStateFlow(SelectSeedUiState())
    val selectSeedUiState = _selectSeedUiState.asStateFlow()

    private var uid: Int? = null
    private var uidJob: Job? = null

    init {
        viewModelScope.launch {
            authorizeCommonViewModel.requests.collect { request ->
                if (request.type !is AuthorizeRequestType.Seed) {
                    // Any other request types should only be observed transiently, whilst the
                    // activity state is being updated.
                    _selectSeedUiState.value = SelectSeedUiState()
                    return@collect
                }

                setUid(request.requestorUid, request.type.purpose, request.type.seedId)
            }
        }
    }

    private fun setUid(uid: Int, purpose: Authorization.Purpose, currentSeedId: Long?) {
        Log.d(TAG, "setUid($uid)")

        if (uid == Authorization.INVALID_UID) {
            Log.e(TAG, "No UID provided; canceling authorization")
            authorizeCommonViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_UNSPECIFIED_ERROR)
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
                    authorizeCommonViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_NO_AVAILABLE_SEEDS)
                    return@collect
                }
                val selectedSeedId = seeds.find { seed -> seed.id == currentSeedId }?.id ?: seeds[0].id
                _selectSeedUiState.update { it.copy(seeds = seeds, selectedSeedId = selectedSeedId) }
            }
        }
    }

    fun setSelectedSeed(seedId: Long?) {
        Log.d(TAG, "setSelectedSeed($seedId)")
        _selectSeedUiState.update {
            val selectedSeedId = it.seeds.find { seed -> seed.id == seedId }?.id ?: it.seeds[0].id
            it.copy(selectedSeedId = selectedSeedId)
        }
    }

    fun selectSeedForAuthorization() {
        val uid = this.uid
        check(uid != null) { "authorizeSelectedSeedForUid called when no UID is set" }

        val selectedSeedId = _selectSeedUiState.value.selectedSeedId
        check(selectedSeedId != null) { "authorizeSelectedSeedForUid called when no seed is selected" }

        Log.d(TAG, "setSelectedSeed for seedId=$selectedSeedId/uid=$uid")

        authorizeCommonViewModel.updateAuthorizeSeedRequestWithSeedId(selectedSeedId)
    }

    companion object {
        private val TAG = SelectSeedViewModel::class.simpleName
    }

    @AssistedFactory
    interface Factory {
        fun create(activityViewModel: AuthorizeCommonViewModel): SelectSeedViewModel
    }
}

data class SelectSeedUiState(
    val seeds: List<Seed> = listOf(),
    val selectedSeedId: Long? = null
)
