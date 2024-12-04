/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui

import android.app.Activity.RESULT_OK
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solanamobile.seedvault.*
import com.solanamobile.seedvaultimpl.model.Authorization
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class AuthorizeCommonViewModel @Inject constructor() : ViewModel() {
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
                startAuthorization()
                val request =
                    AuthorizeRequest(AuthorizeRequestType.Seed(purpose), callerActivity, callerUid)
                cachedRequest = request
                viewModelScope.launch {
                    _requests.emit(request)
                }
            }

            WalletContractV1.ACTION_SIGN_TRANSACTION,
            WalletContractV1.ACTION_SIGN_MESSAGE -> {
                val authToken = getAuthTokenFromIntent(callerIntent)
                if (authToken == -1L) {
                    Log.e(TAG, "No or invalid auth token provided; aborting...")
                    completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_AUTH_TOKEN)
                    return
                }
                val signingRequests =
                    callerIntent.getParcelableArrayListExtra<SigningRequest>(WalletContractV1.EXTRA_SIGNING_REQUEST)
                if (signingRequests == null || signingRequests.isEmpty()) {
                    Log.e(TAG, "No or empty signing requests provided; aborting...")
                    completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_PAYLOAD)
                    return
                }
                val type = if (callerIntent.action == WalletContractV1.ACTION_SIGN_TRANSACTION)
                    AuthorizeRequestType.Signature.Type.Transaction
                else
                    AuthorizeRequestType.Signature.Type.Message
                startAuthorization()
                val request = AuthorizeRequest(
                    AuthorizeRequestType.Signature(
                        type,
                        authToken,
                        signingRequests
                    ), callerActivity, callerUid
                )
                cachedRequest = request
                viewModelScope.launch {
                    _requests.emit(request)
                }
            }

            WalletContractV1.ACTION_GET_PUBLIC_KEY -> {
                val authToken = getAuthTokenFromIntent(callerIntent)
                if (authToken == -1L) {
                    Log.e(TAG, "No or invalid auth token provided; aborting...")
                    completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_AUTH_TOKEN)
                    return
                }
                val derivationPaths =
                    callerIntent.getParcelableArrayListExtra<Uri>(WalletContractV1.EXTRA_DERIVATION_PATH)
                if (derivationPaths == null) {
                    Log.e(TAG, "No or empty derivation paths provided; aborting...")
                    completeAuthorizationWithError(WalletContractV1.RESULT_INVALID_DERIVATION_PATH)
                    return
                }
                startAuthorization()
                val request = AuthorizeRequest(
                    AuthorizeRequestType.PublicKey(authToken, derivationPaths),
                    callerActivity,
                    callerUid
                )
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

    private fun getAuthTokenFromIntent(intent: Intent): Long {
        return intent.getLongExtra(WalletContractV1.EXTRA_AUTH_TOKEN, -1)
    }

    fun updateAuthorizeSeedRequestWithSeedId(seedId: Long) {
        Log.d(TAG, "updateSeedRequestWithSeedId($seedId)")
        val origRequest = cachedRequest
        check(origRequest != null && origRequest.type is AuthorizeRequestType.Seed) { "Expected AuthorizeRequestType.Seed; is ${origRequest?.type}" }
        val request = origRequest.copy(type = origRequest.type.copy(seedId = seedId))
        cachedRequest = request
        viewModelScope.launch {
            _requests.emit(request)
        }
    }

    private fun startAuthorization() {
        Log.d(TAG, "startAuthorization")
        viewModelScope.launch {
            _events.emit(AuthorizeEvent(AuthorizeEventType.START_AUTHORIZATION))
        }
    }

    fun completeAuthorizationWithAuthToken(@WalletContractV1.AuthToken authToken: Long) {
        Log.d(TAG, "completeAuthorizationWithAuthToken($authToken)")
        val intent = Intent().putExtra(WalletContractV1.EXTRA_AUTH_TOKEN, authToken)
        val event = AuthorizeEvent(AuthorizeEventType.COMPLETE, RESULT_OK, intent)
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    fun completeAuthorizationWithSignatures(signatures: ArrayList<SigningResponse>) {
        Log.d(TAG, "completeAuthorizationWithSignatures($signatures)")
        val intent = Intent()
            .putExtra(WalletContractV1.EXTRA_SIGNING_RESPONSE, signatures)
        val event = AuthorizeEvent(AuthorizeEventType.COMPLETE, RESULT_OK, intent)
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    fun completeAuthorizationWithPublicKeys(publicKeys: ArrayList<PublicKeyResponse>) {
        Log.d(TAG, "completeAuthorizationWithPublicKeys($publicKeys")
        val intent = Intent()
            .putExtra(WalletContractV1.EXTRA_PUBLIC_KEY, publicKeys)
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
        private val TAG = AuthorizeCommonViewModel::class.simpleName
    }
}

sealed interface AuthorizeRequestType {
    data class Seed(
        val purpose: Authorization.Purpose,
        val seedId: Long? = null
    ) : AuthorizeRequestType

    data class Signature(
        val type: Type,
        @WalletContractV1.AuthToken val authToken: Long,
        val transactions: List<SigningRequest>,
    ) : AuthorizeRequestType {
        enum class Type { Transaction, Message }
    }

    data class PublicKey(
        @WalletContractV1.AuthToken val authToken: Long,
        val derivationPaths: List<Uri>,
    ) : AuthorizeRequestType
}

data class AuthorizeRequest(
    val type: AuthorizeRequestType,
    val requestor: ComponentName? = null,
    val requestorUid: Int = Authorization.INVALID_UID
)

enum class AuthorizeEventType {
    START_AUTHORIZATION, COMPLETE
}

data class AuthorizeEvent(
    val event: AuthorizeEventType,
    val resultCode: Int? = null,
    val data: Intent? = null
)