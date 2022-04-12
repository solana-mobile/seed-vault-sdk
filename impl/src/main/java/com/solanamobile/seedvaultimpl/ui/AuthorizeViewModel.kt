/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui

import android.app.Activity.RESULT_OK
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solanamobile.seedvault.BipDerivationPath
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvaultimpl.model.Authorization
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AuthorizeViewModel : ViewModel() {
    private val _requests = MutableSharedFlow<AuthorizeRequest>(replay = 1)
    val requests = _requests.asSharedFlow()

    private val _events = MutableSharedFlow<AuthorizeEvent>(replay = 1)
    val events = _events.asSharedFlow()

    private var cachedRequest: AuthorizeRequest? = null

    fun setRequest(
        callerActivity: ComponentName?,
        callerUid: Int?,
        callerIntent: Intent
    ) {
        Log.d(TAG, "setRequest($callerActivity, $callerUid, $callerIntent)")

        if (callerActivity == null || callerUid == null) {
            Log.e(TAG, "No caller or invalid caller; aborting...")
            completeAuthorizationWithError(WalletContractV1.RESULT_UNSPECIFIED_ERROR)
            return
        }

        when (callerIntent.action) {
            WalletContractV1.ACTION_AUTHORIZE_SEED_ACCESS -> {
                val rawPurpose = callerIntent.getIntExtra(WalletContractV1.EXTRA_PURPOSE, -1)
                val purpose: Authorization.Purpose
                try {
                    purpose = Authorization.Purpose.fromWalletContractConstant(rawPurpose)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "No or invalid purpose provided; aborting...", e)
                    completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_PURPOSE)
                    return
                }
                startSeedSelection()
                val request = AuthorizeRequest(AuthorizeRequestType.Seed(purpose), callerActivity, callerUid)
                cachedRequest = request
                viewModelScope.launch {
                    _requests.emit(request)
                }
            }
            WalletContractV1.ACTION_SIGN_TRANSACTION -> {
                val authToken = getAuthTokenFromIntent(callerIntent)
                if (authToken == -1) {
                    Log.e(TAG, "No or invalid auth token provided; aborting...")
                    completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_AUTH_TOKEN)
                    return
                }
                val transaction = callerIntent.getByteArrayExtra(WalletContractV1.EXTRA_TRANSACTION)
                if (transaction == null || transaction.isEmpty()) {
                    Log.e(TAG, "No or empty transaction payload provided; aborting...")
                    completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_TRANSACTION)
                    return
                }
                // TODO: validate transaction is a Solana txn
                val derivationPath = getBipDerivationPathFromIntent(callerIntent)
                if (derivationPath == null) {
                    Log.e(TAG, "No or invalid derivation path provided; aborting...")
                    completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_DERIVATION_PATH)
                    return
                }
                startAuthorization()
                val request = AuthorizeRequest(AuthorizeRequestType.Transaction(authToken, transaction, derivationPath), callerActivity, callerUid)
                cachedRequest = request
                viewModelScope.launch {
                    _requests.emit(request)
                }
            }
            WalletContractV1.ACTION_GET_PUBLIC_KEY -> {
                val authToken = getAuthTokenFromIntent(callerIntent)
                if (authToken == -1) {
                    Log.e(TAG, "No or invalid auth token provided; aborting...")
                    completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_AUTH_TOKEN)
                    return
                }
                val derivationPath = getBipDerivationPathFromIntent(callerIntent)
                if (derivationPath == null) {
                    Log.e(TAG, "No or invalid derivation path provided; aborting...")
                    completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_DERIVATION_PATH)
                    return
                }
                startAuthorization()
                val request = AuthorizeRequest(AuthorizeRequestType.PublicKey(authToken, derivationPath), callerActivity, callerUid)
                cachedRequest = request
                viewModelScope.launch {
                    _requests.emit(request)
                }
            }
            else -> {
                Log.e(TAG, "Unknown action '${callerIntent.action}'; aborting...")
                completeAuthorizationWithError(WalletContractV1.RESULT_UNSPECIFIED_ERROR)
            }
        }
    }

    private fun getAuthTokenFromIntent(intent: Intent): Int {
        return intent.getIntExtra(WalletContractV1.EXTRA_AUTH_TOKEN, -1)
    }

    private fun getBipDerivationPathFromIntent(intent: Intent): BipDerivationPath? {
        return intent.data?.let { derivationPathUri ->
            try {
                BipDerivationPath.fromUri(derivationPathUri)
            } catch (e: UnsupportedOperationException) {
                Log.e(TAG, "Invalid BIP derivation path URI '$derivationPathUri'; aborting...", e)
                null
            }
        }
    }

    fun updateSeedRequestWithSeedId(seedId: Int) {
        Log.d(TAG, "updateSeedRequestWithSeedId($seedId)")
        val origRequest = cachedRequest
        check(origRequest != null && origRequest.type is AuthorizeRequestType.Seed) { "Expected AuthorizeRequestType.Seed; is ${origRequest?.type}" }
        val request = origRequest.copy(type = origRequest.type.copy(seedId = seedId))
        cachedRequest = request
        viewModelScope.launch {
            _requests.emit(request)
        }
    }

    private fun startSeedSelection() {
        Log.d(TAG, "startSeedAuthorization")
        viewModelScope.launch {
            _events.emit(AuthorizeEvent(AuthorizeEventType.START_SEED_SELECTION))
        }
    }

    private fun startAuthorization() {
        Log.d(TAG, "startTransactionAuthorization")
        viewModelScope.launch {
            _events.emit(AuthorizeEvent(AuthorizeEventType.START_AUTHORIZATION))
        }
    }

    fun completeAuthorizationWithAuthToken(authToken: Int) {
        Log.d(TAG, "completeAuthorizationWithAuthToken($authToken)")
        val intent = Intent().putExtra(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
        val event = AuthorizeEvent(AuthorizeEventType.COMPLETE, RESULT_OK, intent)
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    fun completeAuthorizationWithSignature(
        signature: ByteArray,
        derivationPath: BipDerivationPath
    ) {
        @Suppress("EXPERIMENTAL_API_USAGE")
        Log.d(TAG, "completeAuthorizationWithSignature(${signature.toUByteArray().toList()}, $derivationPath)")
        val intent = Intent()
            .putExtra(WalletContractV1.EXTRA_SIGNATURE, signature)
            .setData(derivationPath.toUri())
        val event = AuthorizeEvent(AuthorizeEventType.COMPLETE, RESULT_OK, intent)
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    fun completeAuthorizationWithPublicKey(
        key: ByteArray,
        derivationPath: BipDerivationPath
    ) {
        @Suppress("EXPERIMENTAL_API_USAGE")
        Log.d(TAG, "completeAuthorizationWithPublicKey(${key.toUByteArray().toList()}, $derivationPath)")
        val intent = Intent()
            .putExtra(WalletContractV1.EXTRA_PUBLIC_KEY, key)
            .setData(derivationPath.toUri())
        val event = AuthorizeEvent(AuthorizeEventType.COMPLETE, RESULT_OK, intent)
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    fun completeAuthorizationWithError(errorCode: Int) {
        Log.d(TAG, "completeAuthorizationWithError($errorCode)")
        val event = AuthorizeEvent(AuthorizeEventType.COMPLETE, errorCode)
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    companion object {
        private val TAG = AuthorizeViewModel::class.simpleName
    }
}

sealed interface AuthorizeRequestType {
    data class Seed(
        val purpose: Authorization.Purpose,
        val seedId: Int? = null
    ) : AuthorizeRequestType

    data class Transaction(
        val authToken: Int,
        val transaction: ByteArray,
        val derivationPath: BipDerivationPath
    ) : AuthorizeRequestType {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Transaction

            if (authToken != other.authToken) return false
            if (!transaction.contentEquals(other.transaction)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = authToken
            result = 31 * result + transaction.contentHashCode()
            return result
        }
    }

    data class PublicKey(
        val authToken: Int,
        val derivationPath: BipDerivationPath
    ) : AuthorizeRequestType
}

data class AuthorizeRequest(
    val type: AuthorizeRequestType,
    val requestor: ComponentName? = null,
    val requestorUid: Int = Authorization.INVALID_UID
)

enum class AuthorizeEventType {
    START_SEED_SELECTION, START_AUTHORIZATION, COMPLETE
}

data class AuthorizeEvent(
    val event: AuthorizeEventType,
    val resultCode: Int? = null,
    val data: Intent? = null
)