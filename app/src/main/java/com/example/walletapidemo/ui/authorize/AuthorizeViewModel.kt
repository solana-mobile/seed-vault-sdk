/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.example.walletapidemo.ui.authorize

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.seedvault.Bip32DerivationPath
import com.example.seedvault.WalletContractV1
import com.example.walletapidemo.data.SeedRepository
import com.example.walletapidemo.model.Account
import com.example.walletapidemo.model.Authorization
import com.example.walletapidemo.model.Seed
import com.example.walletapidemo.ui.AuthorizeRequest
import com.example.walletapidemo.ui.AuthorizeRequestType
import com.example.walletapidemo.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthorizeViewModel private constructor(
    private val seedRepository: SeedRepository,
    private val activityViewModel: com.example.walletapidemo.ui.AuthorizeViewModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthorizeUiState())
    val uiState = _uiState.asStateFlow()

    private var seed: Seed? = null
    private var purpose: Authorization.Purpose? = null
    private var request: AuthorizeRequest? = null
    private var normalizedDerivationPath: Bip32DerivationPath? = null

    init {
        viewModelScope.launch {
            activityViewModel.requests.collect { request ->
                if (request.type !is AuthorizeRequestType.Seed &&
                        request.type !is AuthorizeRequestType.Transaction &&
                        request.type !is AuthorizeRequestType.PublicKey) {
                    // Any other request types should only be observed transiently, whilst the
                    // activity state is being updated.
                    _uiState.value = AuthorizeUiState()
                    seed = null
                    purpose = null
                    this@AuthorizeViewModel.request = null
                    return@collect
                }

                val seed: Seed?
                val authorizationType: AuthorizeUiState.AuthorizationType

                when (request.type) {
                    is AuthorizeRequestType.Seed -> {
                        authorizationType = AuthorizeUiState.AuthorizationType.SEED
                        val seedId = request.type.seedId
                        check(seedId != null) { "Seed ID should not be null when authorizing" }
                        seed = seedRepository.seeds.value[seedId]
                        check(seed != null) { "Seed should be non-null for authorization" }
                        this@AuthorizeViewModel.seed = seed
                        this@AuthorizeViewModel.purpose = request.type.purpose
                    }

                    is AuthorizeRequestType.Transaction -> {
                        authorizationType = AuthorizeUiState.AuthorizationType.TRANSACTION
                        val authKey = SeedRepository.AuthorizationKey(
                            request.requestorUid,
                            request.type.authToken
                        )
                        seed = seedRepository.authorizations.value[authKey]
                        if (seed == null) {
                            Log.e(TAG, "No seed found for ${authKey.authToken}/${authKey.uid}; aborting...")
                            activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_AUTH_TOKEN)
                            return@collect
                        }
                        this@AuthorizeViewModel.seed = seed
                        val purpose = seed.authorizations.first { auth ->
                            auth.authToken == authKey.authToken
                        }.purpose
                        this@AuthorizeViewModel.purpose = purpose
                        normalizedDerivationPath = request.type.derivationPath
                            .toBip32DerivationPath(purpose).normalize(purpose)
                    }

                    is AuthorizeRequestType.PublicKey -> {
                        authorizationType = AuthorizeUiState.AuthorizationType.PUBLIC_KEY
                        val authKey = SeedRepository.AuthorizationKey(
                            request.requestorUid,
                            request.type.authToken
                        )
                        seed = seedRepository.authorizations.value[authKey]
                        if (seed == null) {
                            Log.e(TAG, "No seed found for ${authKey.authToken}/${authKey.uid}; aborting...")
                            activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_AUTH_TOKEN)
                            return@collect
                        }
                        this@AuthorizeViewModel.seed = seed
                        val purpose = seed.authorizations.first { auth ->
                            auth.authToken == authKey.authToken
                        }.purpose
                        this@AuthorizeViewModel.purpose = purpose
                        val normalizedDerivationPath = request.type.derivationPath
                            .toBip32DerivationPath(purpose).normalize(purpose)
                        this@AuthorizeViewModel.normalizedDerivationPath = normalizedDerivationPath

                        // Check if we already have a cached public key for this address
                        val account = seed.accounts.firstOrNull { account ->
                            account.purpose == purpose && account.bip32DerivationPathUri == normalizedDerivationPath.toUri()
                        }
                        if (account != null) {
                            Log.i(TAG, "Returning cached public key for $purpose:$normalizedDerivationPath")
                            activityViewModel.completeAuthorizationWithPublicKey(account.publicKey, normalizedDerivationPath)
                            return@collect
                        }
                    }
                }

                this@AuthorizeViewModel.request = request
                _uiState.value = AuthorizeUiState(
                    authorizationType = authorizationType,
                    enableBiometrics = seed.details.unlockWithBiometrics
                )
            }
        }
    }

    fun setPIN(pin: String) {
        Log.d(TAG, "setPIN($pin)")
        _uiState.update { it.copy(pin = pin, showAttemptFailedHint = false) }
    }

    fun checkEnteredPIN() {
        Log.d(TAG, "checkEnteredPIN")

        val curUiState = _uiState.value
        if (curUiState.pin != seed!!.details.pin) {
            if (curUiState.attemptsRemaining <= 1) {
                Log.e(TAG, "Max PIN attempts reached; aborting...")
                activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_AUTHENTICATION_FAILED)
            } else {
                Log.w(TAG, "PIN attempt failed, ${curUiState.attemptsRemaining - 1} attempt(s) remaining")
                _uiState.update { it.copy(attemptsRemaining = curUiState.attemptsRemaining - 1, showAttemptFailedHint = true) }
            }
            return
        }

        doAuthorizationAction()
    }

    fun biometricAuthorizationSuccess() = doAuthorizationAction()

    private fun doAuthorizationAction() {
        val purpose = purpose!!
        val request = request!!
        val seed = seed!!

        when (request.type) {
            is AuthorizeRequestType.Seed -> {
                viewModelScope.launch {
                    val authToken = seedRepository.authorizeSeedForUid(seed.id, request.requestorUid, purpose)

                    // Ensure that the seed vault contains appropriate known accounts for this authorization purpose
                    PrepopulateKnownAccountsUseCase(seedRepository).populateKnownAccounts(seed, purpose)

                    activityViewModel.completeAuthorizationWithAuthToken(authToken)
                }
            }

            is AuthorizeRequestType.Transaction -> {
                val derivationPath = normalizedDerivationPath!!

                viewModelScope.launch {
                    val privateKey: ByteArray
                    val sig: ByteArray

                    try {
                        withContext(Dispatchers.Default) {
                            val bipDerivationUseCase = BipDerivationUseCase(seedRepository)
                            privateKey = bipDerivationUseCase.derivePrivateKey(purpose, seed, derivationPath)
                            sig = SignTransactionUseCase(purpose, privateKey, request.type.transaction)
                        }
                    } catch (_: BipDerivationUseCase.KeyDoesNotExistException) {
                        Log.e(TAG, "Key does not exist for $purpose:$derivationPath")
                        activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_KEY_UNAVAILABLE)
                        return@launch
                    }

                    activityViewModel.completeAuthorizationWithSignature(sig, derivationPath)
                }
            }

            is AuthorizeRequestType.PublicKey -> {
                val derivationPath = normalizedDerivationPath!!

                viewModelScope.launch {
                    val publicKey: ByteArray

                    try {
                        withContext(Dispatchers.Default) {
                            val bipDerivationUseCase = BipDerivationUseCase(seedRepository)
                            publicKey = bipDerivationUseCase.derivePublicKey(purpose, seed, derivationPath)
                        }
                    } catch (_: BipDerivationUseCase.KeyDoesNotExistException) {
                        Log.e(TAG, "Key does not exist for $purpose:$derivationPath")
                        activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_KEY_UNAVAILABLE)
                        return@launch
                    }

                    // Save new public key to known account set
                    val account = Account(Account.INVALID_ACCOUNT_ID, purpose, derivationPath.toUri(), publicKey)
                    seedRepository.addKnownAccountForSeed(seed.id, account)

                    activityViewModel.completeAuthorizationWithPublicKey(publicKey, derivationPath)
                }

            }
        }
    }

    companion object {
        private val TAG = AuthorizeViewModel::class.simpleName

        fun provideFactory(
            seedRepository: SeedRepository,
            activityViewModel: com.example.walletapidemo.ui.AuthorizeViewModel
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthorizeViewModel(seedRepository, activityViewModel) as T
            }
        }
    }
}

data class AuthorizeUiState(
    val authorizationType: AuthorizationType? = null,
    val pin: String = "",
    val attemptsRemaining: Int = MAX_ATTEMPTS,
    val showAttemptFailedHint: Boolean = false,
    val enableBiometrics: Boolean = false
) {
    enum class AuthorizationType {
        SEED, TRANSACTION, PUBLIC_KEY
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
    }
}