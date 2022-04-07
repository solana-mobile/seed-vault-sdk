/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.util.Log
import androidx.core.database.getStringOrNull
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solanamobile.seedvault.Bip32DerivationPath
import com.solanamobile.seedvault.Bip32Level
import com.solanamobile.seedvault.Wallet
import com.solanamobile.seedvault.WalletContractV1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val _viewModelEvents = MutableSharedFlow<ViewModelEvent>()
    val viewModelEvents = _viewModelEvents.asSharedFlow()

    private var nextMessageIndex = 0

    init {
        viewModelScope.launch {
            observeSeedVaultContentChanges()
            refreshUiState()
        }
    }

    private fun observeSeedVaultContentChanges() {
        val application = getApplication<Application>()
        application.contentResolver.registerContentObserver(
            WalletContractV1.WALLET_PROVIDER_CONTENT_URI_BASE,
            true,
            object : ContentObserver(Handler(application.mainLooper)) {
                override fun onChange(selfChange: Boolean) =
                    throw NotImplementedError("Stub for legacy onChange")
                override fun onChange(selfChange: Boolean, uri: Uri?) =
                    throw NotImplementedError("Stub for legacy onChange")
                override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) =
                    throw NotImplementedError("Stub for legacy onChange")

                override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
                    Log.d(TAG, "Received change notification for $uris (flags=$flags); refreshing viewmodel")
                    viewModelScope.launch {
                        refreshUiState()
                    }
                }
            }
        )
    }

    private suspend fun refreshUiState() {
        val hasUnauthorizedSeeds = withContext(Dispatchers.Main) {
            Wallet.hasUnauthorizedSeeds(getApplication(),
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        }
        val seeds = mutableListOf<Seed>()

        val authorizedSeedsCursor = withContext(Dispatchers.Main) {
            Wallet.getAllAuthorizedSeeds(getApplication(),
                WalletContractV1.WALLET_AUTHORIZED_SEEDS_ALL_COLUMNS)!!
        }
        while (authorizedSeedsCursor.moveToNext()) {
            val authToken = authorizedSeedsCursor.getInt(0)
            val authPurpose = authorizedSeedsCursor.getInt(1)
            val seedName = authorizedSeedsCursor.getStringOrNull(2)
            val accounts = mutableListOf<Account>()

            val accountsCursor = withContext(Dispatchers.Main) {
                Wallet.getAllAccounts(getApplication(), authToken,
                    WalletContractV1.WALLET_ACCOUNTS_ALL_COLUMNS)!!
            }
            while (accountsCursor.moveToNext()) {
                val accountId = accountsCursor.getInt(0)
                val derivationPath = Uri.parse(accountsCursor.getString(1))
                val publicKeyBase58 = accountsCursor.getString(3)
                val accountName = accountsCursor.getStringOrNull(4)
                accounts.add(Account(accountId,
                    if (accountName.isNullOrBlank()) publicKeyBase58.substring(0, 10) else accountName,
                    derivationPath, publicKeyBase58))
            }

            seeds.add(Seed(authToken, seedName ?: authToken.toString(), authPurpose, accounts))
        }

        _uiState.update {
            it.copy(seeds = seeds, hasUnauthorizedSeeds = hasUnauthorizedSeeds)
        }
    }

    fun authorizeNewSeed() {
        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.AuthorizeNewSeed
            )
        }
    }

    fun onAuthorizeNewSeedSuccess(authToken: Int) {
        // TODO: mark the first two accounts as valid. This simulates a real wallet exploring each
        // account and marking any with funds as valid.
    }

    fun onAuthorizeNewSeedFailure(resultCode: Int) {
        showErrorMessage(resultCode)
    }

    fun deauthorizeSeed(authToken: Int) {
        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.DeauthorizeSeed(authToken)
            )
        }
    }

    fun onDeauthorizeSeedSuccess() {
    }

    fun onDeauthorizeSeedFailure(resultCode: Int) {
        showErrorMessage(resultCode)
    }

    fun updateAccountName(
        authToken: Int,
        accountId: Int,
        name: String?
    ) {
        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.UpdateAccountName(authToken, accountId, name)
            )
        }
    }

    fun onUpdateAccountNameSuccess() {
    }

    fun onUpdateAccountNameFailure(resultCode: Int) {
        showErrorMessage(resultCode)
    }

    fun signFakeTransaction(authToken: Int, account: Account) {
        val fakeTransaction = byteArrayOf(0.toByte())
        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.SignTransaction(authToken, account.derivationPath, fakeTransaction)
            )
        }
    }

    fun onSignTransactionSuccess(signature: ByteArray) {
        showMessage("Transaction signed successfully")
    }

    fun onSignTransactionFailure(resultCode: Int) {
        showErrorMessage(resultCode)
    }

    fun requestPublicKeyForM1000H(authToken: Int) {
        val derivationPath = Bip32DerivationPath.newBuilder()
            .appendLevel(Bip32Level(1000, true))
            .build()
        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.RequestPublicKey(authToken, derivationPath.toUri())
            )
        }
    }

    fun onRequestPublicKeySuccess(publicKey: ByteArray) {
        showMessage("Public key for m/1000' retrieved")
    }

    fun onRequestPublicKeyFailure(resultCode: Int) {
        showErrorMessage(resultCode)
    }

    private fun showErrorMessage(resultCode: Int) {
        showMessage("Action failed, error=$resultCode")
    }

    private fun showMessage(message: String) {
        _uiState.update {
            val newMessages = it.messages.toMutableList()
            newMessages.add(Message(nextMessageIndex++, message))
            it.copy(messages = newMessages)
        }
    }

    fun messageShown(index: Int) {
        _uiState.update {
            val newMessages = it.messages.toMutableList()
            newMessages.removeIf { m -> m.first == index }
            it.copy(messages = newMessages)
        }
    }

    companion object {
        private val TAG = MainViewModel::class.simpleName
    }
}

data class Account(
    val id: Int,
    val name: String,
    val derivationPath: Uri,
    val publicKeyBase58: String
)

data class Seed(
    val authToken: Int,
    val name: String,
    val purpose: Int,
    val accounts: List<Account> = listOf()
)

typealias Message = Pair<Int, String>

data class UiState(
    val seeds: List<Seed> = listOf(),
    val hasUnauthorizedSeeds: Boolean = false,
    val messages: List<Message> = listOf()
)

sealed interface ViewModelEvent {
    object AuthorizeNewSeed : ViewModelEvent

    data class DeauthorizeSeed(
        val authToken: Int
    ) : ViewModelEvent

    data class UpdateAccountName(
        val authToken: Int,
        val accountId: Int,
        val name: String?,
    ) : ViewModelEvent

    data class SignTransaction(
        val authToken: Int,
        val derivationPath: Uri,
        val transaction: ByteArray,
    ) : ViewModelEvent

    data class RequestPublicKey(
        val authToken: Int,
        val derivationPath: Uri,
    ) : ViewModelEvent
}
