/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.authorize

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.solanamobile.seedvault.*
import com.solanamobile.seedvaultimpl.R
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
    private val activityViewModel: com.solanamobile.seedvaultimpl.ui.AuthorizeViewModel,
    private val application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AuthorizeUiState())
    val uiState = _uiState.asStateFlow()

    private var seed: Seed? = null
    private var purpose: Authorization.Purpose? = null
    private var request: AuthorizeRequest? = null
    private var normalizedDerivationPaths: List<List<Bip32DerivationPath>>? = null
    private var biometricsFailureCount: Int = 0
    private var pinFailureCount: Int = 0

    init {
        viewModelScope.launch {
            activityViewModel.requests.collect { request ->
                if (request.type !is AuthorizeRequestType.Seed &&
                        request.type !is AuthorizeRequestType.Signature &&
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
                val doesRequireAuthentication: Boolean

                when (request.type) {
                    is AuthorizeRequestType.Seed -> {
                        if (isPrivilegedPermissionGranted()) {
                            activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_NO_AVAILABLE_SEEDS)
                            return@collect
                        }

                        authorizationType = AuthorizeUiState.AuthorizationType.SEED
                        doesRequireAuthentication = true
                        seed = seedRepository.seeds.value.values.firstOrNull { s ->
                            (request.type.seedId == null || s.id == request.type.seedId) &&
                            s.authorizations.none { auth ->
                                auth.uid == request.requestorUid && auth.purpose == request.type.purpose
                            }
                        }
                        if (seed == null) {
                            Log.e(TAG, "No non-authorized seeds remaining for ${request.requestorUid}; aborting...")
                            activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_NO_AVAILABLE_SEEDS)
                            return@collect
                        }
                        this@AuthorizeViewModel.seed = seed
                        this@AuthorizeViewModel.purpose = request.type.purpose
                        normalizedDerivationPaths = null
                    }

                    is AuthorizeRequestType.Signature -> {
                        authorizationType =
                            if (request.type.type == AuthorizeRequestType.Signature.Type.Transaction)
                                AuthorizeUiState.AuthorizationType.TRANSACTION
                            else
                                AuthorizeUiState.AuthorizationType.MESSAGE
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
                            activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_PAYLOAD)
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
                            val normalizedDerivationPaths = request.type.transactions.map { t ->
                                normalizeDerivationPaths(t.requestedSignatures)
                            }
                            doesRequireAuthentication = isAuthRequired(normalizedPaths = normalizedDerivationPaths)
                            this@AuthorizeViewModel.normalizedDerivationPaths = normalizedDerivationPaths
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
                        doesRequireAuthentication = isAuthRequired(normalizedPaths = normalizedDerivationPaths)
                        this@AuthorizeViewModel.normalizedDerivationPaths = normalizedDerivationPaths

                        // Check if we already have cached public keys for these addresses
                        if (normalizedDerivationPaths[0].all { path -> publicKeyForPath(path.toUri()) != null }) {
                            doAuthorizationAction()
                            return@collect
                        }
                    }
                }

                var requestorAppIcon: Drawable? = null
                var requestorAppName: CharSequence? = null
                try {
                    val callingApplicationInfo = application.packageManager.getApplicationInfo(request.requestor?.packageName ?: "", 0)
                    requestorAppIcon = callingApplicationInfo.loadIcon(application.packageManager)
                    requestorAppName = callingApplicationInfo.loadLabel(application.packageManager)
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w(TAG, "Requestor details not found", e)
                }
                val isPrivilegedApp = isPrivilegedPermissionGranted()

                _uiState.value = AuthorizeUiState(
                    authorizationType = authorizationType,
                    requestorAppIcon = requestorAppIcon,
                    requestorAppName = requestorAppName,
                    seedName = GetNameUseCase.getName(seed),
                    isPrivilegedApp = isPrivilegedApp,
                    enableBiometrics = seed.details.unlockWithBiometrics,
                    enablePIN = !seed.details.unlockWithBiometrics,
                    requireAuthentication = doesRequireAuthentication
                )

                biometricsFailureCount = 0
                pinFailureCount = 0
            }
        }
    }

    fun cancel() {
        activityViewModel.completeAuthorizationWithError(Activity.RESULT_CANCELED)
    }

    fun checkEnteredPIN(pin: String) {
        Log.d(TAG, "checkEnteredPIN")

        if (pin != seed!!.details.pin) {
            pinFailureCount++
            if (pinFailureCount >= MAX_PIN_ATTEMPTS) {
                Log.e(TAG, "Max PIN attempts reached; aborting...")
                activityViewModel.completeAuthorizationWithError(WalletContractV1.RESULT_AUTHENTICATION_FAILED)
            } else {
                val remaining = MAX_PIN_ATTEMPTS - pinFailureCount
                Log.w(TAG, "PIN attempt $pinFailureCount failed; $remaining attempts remaining")
                showMessage(getApplication<Application>().getString(R.string.error_incorrect_pin, remaining))
            }
            return
        }

        doAuthorizationAction()
    }

    fun biometricAuthorizationSuccess() = doAuthorizationAction()

    fun biometricsAuthorizationFailed() {
        biometricsFailureCount++
        if (biometricsFailureCount >= SHOW_PIN_ENTRY_AFTER_NUM_BIOMETRIC_FAILURES) {
            _uiState.update {
                it.copy(enablePIN = true)
            }
        }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

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

            is AuthorizeRequestType.Signature -> {
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
                                    if (request.type.type == AuthorizeRequestType.Signature.Type.Transaction)
                                        SignPayloadUseCase.signTransaction(
                                            purpose,
                                            privateKey,
                                            sr.payload
                                        )
                                    else
                                        SignPayloadUseCase.signMessage(
                                            purpose,
                                            privateKey,
                                            sr.payload
                                        )
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

    private fun isAuthRequired(normalizedPaths: List<List<Bip32DerivationPath>>): Boolean {
        if (!isPrivilegedPermissionGranted()) {
            return true
        }

        val permissionedAccountAncestor =
            PermissionedAccountUseCase.getPermissionedAccountAncestorForPurpose(purpose!!)

        val areAllPathsPermissionedAccounts = normalizedPaths.all { paths ->
            paths.all { permissionedAccountAncestor.isAncestorOf(it) }
        }

        return !areAllPathsPermissionedAccounts
    }

    private fun isPrivilegedPermissionGranted(): Boolean {
        val callingPackageName = request?.requestor?.packageName
        if (callingPackageName.isNullOrEmpty()) {
            return false
        }

        return application.packageManager.checkPermission(
            WalletContractV1.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED,
            callingPackageName
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val TAG = AuthorizeViewModel::class.simpleName
        private const val SHOW_PIN_ENTRY_AFTER_NUM_BIOMETRIC_FAILURES = 3
        private const val MAX_PIN_ATTEMPTS = 5

        fun provideFactory(
            seedRepository: SeedRepository,
            activityViewModel: com.solanamobile.seedvaultimpl.ui.AuthorizeViewModel,
            application: Application
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthorizeViewModel(seedRepository, activityViewModel, application) as T
            }
        }
    }
}

data class AuthorizeUiState(
    val authorizationType: AuthorizationType? = null,
    val requestorAppIcon: Drawable? = null,
    val requestorAppName: CharSequence? = null,
    val seedName: CharSequence? = null,
    val isPrivilegedApp: Boolean = false,
    val enablePIN: Boolean = true,
    val enableBiometrics: Boolean = false,
    val message: CharSequence? = null,
    val requireAuthentication: Boolean = true
) {
    enum class AuthorizationType {
        SEED, TRANSACTION, MESSAGE, PUBLIC_KEY
    }
}