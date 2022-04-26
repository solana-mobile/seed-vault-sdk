/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.authorize

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.solanamobile.seedvault.*
import com.solanamobile.seedvaultimpl.data.SeedRepository
import com.solanamobile.seedvaultimpl.model.Account
import com.solanamobile.seedvaultimpl.model.Authorization
import com.solanamobile.seedvaultimpl.model.Seed
import com.solanamobile.seedvaultimpl.ui.AuthorizeRequest
import com.solanamobile.seedvaultimpl.ui.AuthorizeRequestType
import com.solanamobile.seedvaultimpl.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthorizeViewModel private constructor(
    private val seedRepository: SeedRepository,
    private val activityViewModel: com.solanamobile.seedvaultimpl.ui.AuthorizeViewModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthorizeUiState())
    val uiState = _uiState.asStateFlow()

    private var seed: Seed? = null
    private var purpose: Authorization.Purpose? = null
    private var request: AuthorizeRequest? = null
    private var normalizedDerivationPaths: List<List<Bip32DerivationPath>>? = null

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
                    normalizedDerivationPaths = null
                    return@collect
                }

                this@AuthorizeViewModel.request = request

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
                        normalizedDerivationPaths = null
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
                        if (request.type.transactions.any { t -> t.payload.isEmpty() }) {
                            Log.e(TAG, "Only non-empty transaction payloads can be signed")
                            activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_TRANSACTION)
                            return@collect
                        }
                        val numTransactions = request.type.transactions.size
                        if (numTransactions > RequestLimitsUseCase.MAX_SIGNING_REQUESTS) {
                            Log.e(TAG, "Too many transactions provided: actual=$numTransactions, max=${RequestLimitsUseCase.MAX_SIGNING_REQUESTS}")
                            activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_IMPLEMENTATION_LIMIT_EXCEEDED)
                            return@collect
                        }
                        val maxRequestedSignatures = request.type.transactions.maxOf { t -> t.requestedSignatures.size }
                        if (maxRequestedSignatures > RequestLimitsUseCase.MAX_REQUESTED_SIGNATURES) {
                            Log.e(TAG, "Too many signatures requested: actual=$maxRequestedSignatures, max=${RequestLimitsUseCase.MAX_REQUESTED_SIGNATURES}")
                            activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_IMPLEMENTATION_LIMIT_EXCEEDED)
                            return@collect
                        }
                        try {
                            normalizedDerivationPaths = request.type.transactions.map { t ->
                                normalizeDerivationPaths(t.requestedSignatures)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed normalizing BIP derivation paths", e)
                            activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_DERIVATION_PATH)
                            return@collect
                        }
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
                        val numDerivationPaths = request.type.derivationPaths.size
                        if (numDerivationPaths > RequestLimitsUseCase.MAX_REQUESTED_PUBLIC_KEYS) {
                            Log.e(TAG, "Too many public keys requested: actual=$numDerivationPaths, max=${RequestLimitsUseCase.MAX_REQUESTED_PUBLIC_KEYS}")
                            activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_IMPLEMENTATION_LIMIT_EXCEEDED)
                            return@collect
                        }
                        val normalizedDerivationPaths = try {
                            listOf(normalizeDerivationPaths(request.type.derivationPaths))
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed normalizing BIP derivation paths", e)
                            activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_DERIVATION_PATH)
                            return@collect
                        }
                        this@AuthorizeViewModel.normalizedDerivationPaths = normalizedDerivationPaths

                        // Check if we already have cached public keys for these addresses
                        if (normalizedDerivationPaths[0].all { path -> publicKeyForPath(path.toUri()) != null }) {
                            doAuthorizationAction()
                            return@collect
                        }
                    }
                }

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

    private fun normalizeDerivationPaths(derivationPaths: List<Uri>): List<Bip32DerivationPath> {
        require(derivationPaths.isNotEmpty()) { "At least one derivation path must be provided" }
        val purpose = purpose!!
        return derivationPaths.map { uri ->
            BipDerivationPath.fromUri(uri).toBip32DerivationPath(purpose).normalize(purpose)
        }
    }

    private fun publicKeyForPath(derivationPath: Uri): ByteArray? {
        return seed!!.accounts.firstOrNull { account ->
            account.purpose == purpose && account.bip32DerivationPathUri == derivationPath
        }?.publicKey
    }

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
                val normalizedDerivationPaths = normalizedDerivationPaths!!

                viewModelScope.launch {
                    val signatures = ArrayList<SigningResponse>(request.type.transactions.size)
                    val bipDerivationUseCase = BipDerivationUseCase(seedRepository)
                    request.type.transactions.mapIndexedTo(signatures) { i, sr ->
                        val requestNormalizedDerivationPaths = normalizedDerivationPaths[i]
                        val sigs = requestNormalizedDerivationPaths.map { path ->
                            try {
                                withContext(Dispatchers.Default) {
                                    val privateKey = bipDerivationUseCase.derivePrivateKey(purpose, seed, path)
                                    SignTransactionUseCase(purpose, privateKey, sr.payload)
                                }
                            } catch (_: BipDerivationUseCase.KeyDoesNotExistException) {
                                Log.e(TAG, "Key does not exist for $purpose:$path")
                                // Technically, it's the key that's invalid, not the derivation path.
                                // However, the caller should have verified the account was valid before
                                // using it, and discovered it was invalid then. Thus, the use of this
                                // derivation path can be considered invalid.
                                activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_DERIVATION_PATH)
                                return@launch
                            }
                        }
                        SigningResponse(sigs, requestNormalizedDerivationPaths.map { path -> path.toUri() })
                    }
                    activityViewModel.completeAuthorizationWithSignatures(signatures)
                }
            }

            is AuthorizeRequestType.PublicKey -> {
                val normalizedDerivationPaths = normalizedDerivationPaths!![0]

                viewModelScope.launch {
                    val publicKeys = ArrayList<PublicKeyResponse>(normalizedDerivationPaths.size)
                    val bipDerivationUseCase = BipDerivationUseCase(seedRepository)
                    normalizedDerivationPaths.mapTo(publicKeys) { path ->
                        val pathUri = path.toUri()
                        val publicKey = publicKeyForPath(pathUri) ?: run {
                            try {
                                withContext(Dispatchers.Default) {
                                    bipDerivationUseCase.derivePublicKey(purpose, seed, path).also {
                                        seedRepository.addKnownAccountForSeed(seed.id, Account(
                                            Account.INVALID_ACCOUNT_ID, purpose, pathUri, it))
                                    }
                                }
                            } catch (_: BipDerivationUseCase.KeyDoesNotExistException) {
                                Log.e(TAG, "Key does not exist for $purpose:$path")
                                null
                            }
                        }
                        PublicKeyResponse(publicKey, publicKey?.let { Base58EncodeUseCase(it) }, pathUri)
                    }
                    activityViewModel.completeAuthorizationWithPublicKeys(publicKeys)
                }
            }
        }
    }

    companion object {
        private val TAG = AuthorizeViewModel::class.simpleName

        fun provideFactory(
            seedRepository: SeedRepository,
            activityViewModel: com.solanamobile.seedvaultimpl.ui.AuthorizeViewModel
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