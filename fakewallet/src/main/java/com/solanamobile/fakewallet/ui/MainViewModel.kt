/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solanamobile.seedvault.*
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
        if (!SeedVault.isAvailable(application, true)) {
            throw UnsupportedOperationException("Seed Vault is not available; please install the Seed Vault simulator")
        }

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
        val hasUnauthorizedSeeds = withContext(Dispatchers.Default) {
            Wallet.hasUnauthorizedSeedsForPurpose(getApplication(),
                WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        }

        val seeds = mutableListOf<Seed>()

        val authorizedSeedsCursor = withContext(Dispatchers.Default) {
            Wallet.getAuthorizedSeeds(getApplication(),
                WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS)!!
        }
        while (authorizedSeedsCursor.moveToNext()) {
            val authToken = authorizedSeedsCursor.getLong(0)
            val authPurpose = authorizedSeedsCursor.getInt(1)
            val seedName = authorizedSeedsCursor.getString(2)
            val accounts = mutableListOf<Account>()

            val accountsCursor = withContext(Dispatchers.Default) {
                Wallet.getAccounts(getApplication(), authToken,
                    WalletContractV1.ACCOUNTS_ALL_COLUMNS,
                    WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET, "1")!!
            }
            while (accountsCursor.moveToNext()) {
                val accountId = accountsCursor.getLong(0)
                val derivationPath = Uri.parse(accountsCursor.getString(1))
                val publicKeyEncoded = accountsCursor.getString(3)
                val accountName = accountsCursor.getString(4)
                accounts.add(Account(accountId,
                    accountName.ifBlank { publicKeyEncoded.substring(0, 10) },
                    derivationPath, publicKeyEncoded))
            }
            accountsCursor.close()

            seeds.add(
                Seed(authToken, seedName.ifBlank { authToken.toString() }, authPurpose, accounts)
            )
        }
        authorizedSeedsCursor.close()

        val implementationLimits = Wallet.getImplementationLimitsForPurpose(getApplication(),
            WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)

        _uiState.update {
            it.copy(seeds = seeds, hasUnauthorizedSeeds = hasUnauthorizedSeeds,
                implementationLimits = implementationLimits)
        }
    }

    fun authorizeNewSeed() {
        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.AuthorizeNewSeed
            )
        }
    }

    fun onAuthorizeNewSeedSuccess(authToken: Long) {
        // Mark two accounts as user wallets. This simulates a real wallet app exploring each
        // account and marking them as containing user funds.
        viewModelScope.launch {
            for (i in 0..1) {
                val derivationPath = Bip44DerivationPath.newBuilder()
                    .setAccount(BipLevel(i, true))
                    .build()
                val resolvedDerivationPath = Wallet.resolveDerivationPath(
                    getApplication(),
                    derivationPath.toUri(),
                    WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
                )
                Log.d(TAG, "Resolved BIP derivation path '$derivationPath' to BIP32 derivation path '$resolvedDerivationPath' for purpose ${WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION}")
                val cursor = Wallet.getAccounts(
                    getApplication(),
                    authToken,
                    arrayOf(
                        WalletContractV1.ACCOUNTS_ACCOUNT_ID,
                        WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET
                    ),
                    WalletContractV1.ACCOUNTS_BIP32_DERIVATION_PATH,
                    resolvedDerivationPath.toString()
                )!!
                check(cursor.moveToNext()) { "Failed to find expected account '$resolvedDerivationPath'" }
                val accountId = cursor.getLong(0)
                val isUserWallet = (cursor.getShort(1) == 1.toShort())
                cursor.close()
                if (!isUserWallet) {
                    Wallet.updateAccountIsUserWallet(getApplication(), authToken, accountId, true)
                    Log.d(TAG, "Marking account '$resolvedDerivationPath' as a user wallet")
                } else {
                    Log.d(TAG, "Account '$resolvedDerivationPath' is already marked as a user wallet")
                }
            }
        }
    }

    fun onAuthorizeNewSeedFailure(resultCode: Int) {
        showErrorMessage(resultCode)
    }

    fun deauthorizeSeed(authToken: Long) {
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
        @WalletContractV1.AuthToken authToken: Long,
        @WalletContractV1.AccountId accountId: Long,
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

    fun signFakeTransaction(@WalletContractV1.AuthToken authToken: Long, account: Account) {
        val fakeTransaction = byteArrayOf(0.toByte())
        viewModelScope.launch {
            val transaction = SigningRequest(fakeTransaction, listOf(account.derivationPath))
            _viewModelEvents.emit(
                ViewModelEvent.SignTransactions(authToken, arrayListOf(transaction))
            )
        }
    }

    fun signTwoTransactionsWithTwoSignatures(@WalletContractV1.AuthToken authToken: Long) {
        val fakeTransaction1 = byteArrayOf(0.toByte())
        val fakeTransaction2 = byteArrayOf(1.toByte())
        val derivationPath11 = Bip44DerivationPath.newBuilder().setAccount(
            BipLevel(0, true)).build().toUri()
        val derivationPath12 = Bip44DerivationPath.newBuilder().setAccount(
            BipLevel(1, true)).build().toUri()
        val derivationPath21 = Bip44DerivationPath.newBuilder().setAccount(
            BipLevel(2, true)).build().toUri()
        val derivationPath22 = Bip44DerivationPath.newBuilder().setAccount(
            BipLevel(3, true)).build().toUri()

        viewModelScope.launch {
            val signingRequest1 = SigningRequest(fakeTransaction1, listOf(derivationPath11, derivationPath12))
            val signingRequest2 = SigningRequest(fakeTransaction2, listOf(derivationPath21, derivationPath22))
            _viewModelEvents.emit(
                ViewModelEvent.SignTransactions(authToken, arrayListOf(signingRequest1, signingRequest2))
            )
        }
    }

    fun onSignTransactionsSuccess(signatures: List<SigningResponse>) {
        showMessage("Transaction signed successfully")
    }

    fun onSignTransactionsFailure(resultCode: Int) {
        showErrorMessage(resultCode)
    }

    fun requestPublicKeyForM1000HAndM1001H(@WalletContractV1.AuthToken authToken: Long) {
        val derivationPaths = arrayListOf(
            Bip32DerivationPath.newBuilder()
                .appendLevel(BipLevel(1000, true))
                .build().toUri(),
            Bip32DerivationPath.newBuilder()
                .appendLevel(BipLevel(1001, true))
                .build().toUri(),
        )
        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.RequestPublicKeys(authToken, derivationPaths)
            )
        }
    }

    fun onRequestPublicKeysSuccess(publicKeys: List<PublicKeyResponse>) {
        showMessage("Public key for m/1000' and m/1001' retrieved")
    }

    fun onRequestPublicKeysFailure(resultCode: Int) {
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
    @WalletContractV1.AccountId val id: Long,
    val name: String,
    val derivationPath: Uri,
    val publicKeyEncoded: String
)

data class Seed(
    @WalletContractV1.AuthToken val authToken: Long,
    val name: String,
    @WalletContractV1.Purpose val purpose: Int,
    val accounts: List<Account> = listOf()
)

typealias Message = Pair<Int, String>

data class UiState(
    val seeds: List<Seed> = listOf(),
    val hasUnauthorizedSeeds: Boolean = false,
    val implementationLimits: Map<String, Long> = mapOf(),
    val messages: List<Message> = listOf()
)

sealed interface ViewModelEvent {
    object AuthorizeNewSeed : ViewModelEvent

    data class DeauthorizeSeed(
        @WalletContractV1.AuthToken val authToken: Long
    ) : ViewModelEvent

    data class UpdateAccountName(
        @WalletContractV1.AuthToken val authToken: Long,
        @WalletContractV1.AccountId val accountId: Long,
        val name: String?,
    ) : ViewModelEvent

    data class SignTransactions(
        @WalletContractV1.AuthToken val authToken: Long,
        val transactions: ArrayList<SigningRequest>,
    ) : ViewModelEvent

    data class RequestPublicKeys(
        @WalletContractV1.AuthToken val authToken: Long,
        val derivationPaths: ArrayList<Uri>,
    ) : ViewModelEvent
}
