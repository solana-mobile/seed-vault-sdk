/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solanamobile.fakewallet.usecase.VerifyEd25519SignatureUseCase
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

    private var maxSigningRequests: Int = 0
    private var maxRequestedSignatures: Int = 0
    private var maxRequestedPublicKeys: Int = 0

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

        // Note: Add a synthetic entry to the implementation limits, to display and test the BIP32
        // path length limits (which are not a normal implementation limit)
        val implementationLimits = Wallet.getImplementationLimitsForPurpose(
            getApplication(),
            WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
        ).plus(IMPLEMENTATION_LIMITS_MAX_BIP32_PATH_DEPTH to WalletContractV1.BIP32_URI_MAX_DEPTH.toLong())
        maxSigningRequests =
            implementationLimits[WalletContractV1.IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS]!!.toInt()
        maxRequestedSignatures =
            implementationLimits[WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES]!!.toInt()
        maxRequestedPublicKeys =
            implementationLimits[WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS]!!.toInt()
        val firstRequestedPublicKey = Bip32DerivationPath.newBuilder()
            .appendLevel(BipLevel(FIRST_REQUESTED_PUBLIC_KEY_INDEX, true)).build().toUri()
            .toString()
        val lastRequestedPublicKey = Bip32DerivationPath.newBuilder()
            .appendLevel(
                BipLevel(
                    FIRST_REQUESTED_PUBLIC_KEY_INDEX + maxRequestedPublicKeys - 1,
                    true
                )
            ).build().toUri().toString()

        _uiState.update {
            it.copy(seeds = seeds, hasUnauthorizedSeeds = hasUnauthorizedSeeds,
                implementationLimits = implementationLimits,
                maxSigningRequests = maxSigningRequests,
                maxRequestedSignatures = maxRequestedSignatures,
                firstRequestedPublicKey = firstRequestedPublicKey,
                lastRequestedPublicKey = lastRequestedPublicKey)
        }
    }

    fun authorizeNewSeed() {
        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.AuthorizeNewSeed
            )
        }
    }

    fun createNewSeed() {
        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.CreateNewSeed
            )
        }
    }

    fun importExistingSeed() {
        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.ImportExistingSeed
            )
        }
    }

    fun onAddSeedSuccess(event: ViewModelEvent.AddSeedViewModelEvent, authToken: Long) {
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

    fun onAddSeedFailure(event: ViewModelEvent.AddSeedViewModelEvent, resultCode: Int) {
        showErrorMessage(resultCode)
    }

    fun deauthorizeSeed(authToken: Long) {
        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.DeauthorizeSeed(authToken)
            )
        }
    }

    fun onDeauthorizeSeedSuccess(event: ViewModelEvent.DeauthorizeSeed) {
    }

    fun onDeauthorizeSeedFailure(event: ViewModelEvent.DeauthorizeSeed, resultCode: Int) {
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

    fun onUpdateAccountNameSuccess(event: ViewModelEvent.UpdateAccountName) {
    }

    fun onUpdateAccountNameFailure(event: ViewModelEvent.UpdateAccountName, resultCode: Int) {
        showErrorMessage(resultCode)
    }

    fun signFakeTransaction(@WalletContractV1.AuthToken authToken: Long, account: Account) {
        val fakeTransaction = createFakeTransaction(0)
        viewModelScope.launch {
            val transaction = SigningRequest(fakeTransaction, listOf(account.derivationPath))
            _viewModelEvents.emit(
                ViewModelEvent.SignTransactions(authToken, arrayListOf(transaction))
            )
        }
    }

    fun signMaxTransactionsWithMaxSignatures(@WalletContractV1.AuthToken authToken: Long) {
        signMTransactionsWithNSignatures(authToken, maxSigningRequests, maxRequestedSignatures)
    }

    private fun signMTransactionsWithNSignatures(
        @WalletContractV1.AuthToken authToken: Long,
        m: Int,
        n: Int
    ) {
        val signingRequests = (0 until m).map { i ->
            val derivationPaths = (0 until n).map { j ->
                Bip44DerivationPath.newBuilder()
                    .setAccount(BipLevel(i * maxRequestedSignatures + j, true)).build().toUri()
            }
            SigningRequest(createFakeTransaction(i), derivationPaths)
        }

        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.SignTransactions(authToken, ArrayList(signingRequests))
            )
        }
    }

    private fun createFakeTransaction(i: Int): ByteArray {
        return ByteArray(TRANSACTION_SIZE) { i.toByte() }
    }

    fun onSignTransactionsSuccess(
        event: ViewModelEvent.SignTransactions,
        signatures: List<SigningResponse>
    ) {
        verifySignatures(
            event.authToken,
            event.transactions,
            signatures,
            "Transactions signed successfully"
        )
    }

    fun onSignTransactionsFailure(event: ViewModelEvent.SignTransactions, resultCode: Int) {
        showErrorMessage(resultCode)
    }

    fun signFakeMessage(@WalletContractV1.AuthToken authToken: Long, account: Account) {
        val fakeMessage = createFakeMessage(0)
        viewModelScope.launch {
            val message = SigningRequest(fakeMessage, listOf(account.derivationPath))
            _viewModelEvents.emit(
                ViewModelEvent.SignMessages(authToken, arrayListOf(message))
            )
        }
    }

    fun signMaxMessagesWithMaxSignatures(@WalletContractV1.AuthToken authToken: Long) {
        signMMessagesWithNSignatures(authToken, maxSigningRequests, maxRequestedSignatures)
    }

    private fun signMMessagesWithNSignatures(
        @WalletContractV1.AuthToken authToken: Long,
        m: Int,
        n: Int
    ) {
        val signingRequests = (0 until m).map { i ->
            val derivationPaths = (0 until n).map { j ->
                Bip44DerivationPath.newBuilder()
                    .setAccount(BipLevel(i * maxRequestedSignatures + j, true)).build().toUri()
            }
            SigningRequest(createFakeMessage(i), derivationPaths)
        }

        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.SignMessages(authToken, ArrayList(signingRequests))
            )
        }
    }

    private fun createFakeMessage(i: Int): ByteArray {
        return ByteArray(MESSAGE_SIZE) { i.toByte() }
    }

    fun onSignMessagesSuccess(
        event: ViewModelEvent.SignMessages,
        signatures: List<SigningResponse>
    ) {
        verifySignatures(
            event.authToken,
            event.messages,
            signatures,
            "Messages signed successfully"
        )
    }

    fun onSignMessagesFailure(event: ViewModelEvent.SignMessages, resultCode: Int) {
        showErrorMessage(resultCode)
    }

    fun requestPublicKeys(@WalletContractV1.AuthToken authToken: Long) {
        requestMPublicKeys(authToken, maxRequestedPublicKeys)
    }

    private fun requestMPublicKeys(@WalletContractV1.AuthToken authToken: Long, m: Int) {
        val derivationPaths = (0 until m).map { i ->
            Bip32DerivationPath.newBuilder()
                .appendLevel(BipLevel(FIRST_REQUESTED_PUBLIC_KEY_INDEX + i, true))
                .build().toUri()
        }

        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.RequestPublicKeys(authToken, ArrayList(derivationPaths))
            )
        }
    }

    fun onRequestPublicKeysSuccess(event: ViewModelEvent.RequestPublicKeys, publicKeys: List<PublicKeyResponse>) {
        showMessage("Public key(s) retrieved")
    }

    fun onRequestPublicKeysFailure(event: ViewModelEvent.RequestPublicKeys, resultCode: Int) {
        showErrorMessage(resultCode)
    }

    fun exceedImplementationLimit(implementationLimit: String) {
        val seed = _uiState.value.seeds.getOrNull(0)
        if (seed == null) {
            showMessage("Cannot test implementation limit without an authorized seed")
            return
        }

        when (implementationLimit) {
            WalletContractV1.IMPLEMENTATION_LIMITS_MAX_SIGNING_REQUESTS ->
                signMTransactionsWithNSignatures(seed.authToken, maxSigningRequests + 1, 1)
            WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_SIGNATURES ->
                signMTransactionsWithNSignatures(seed.authToken, 1, maxRequestedSignatures + 1)
            WalletContractV1.IMPLEMENTATION_LIMITS_MAX_REQUESTED_PUBLIC_KEYS ->
                requestMPublicKeys(seed.authToken, maxRequestedPublicKeys + 1)
            IMPLEMENTATION_LIMITS_MAX_BIP32_PATH_DEPTH ->
                exceedBip32PathMaxDepth(seed.authToken)
            else -> showMessage("Cannot test unknown implementation limit")
        }
    }

    private fun exceedBip32PathMaxDepth(@WalletContractV1.AuthToken authToken: Long) {
        val derivationPathBuilder = Uri.Builder().scheme(WalletContractV1.BIP32_URI_SCHEME)
            .appendPath(WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR)
        for (i in 0..WalletContractV1.BIP32_URI_MAX_DEPTH) {
            derivationPathBuilder.appendPath(i.toString() + WalletContractV1.BIP_URI_HARDENED_INDEX_IDENTIFIER)
        }

        viewModelScope.launch {
            _viewModelEvents.emit(
                ViewModelEvent.RequestPublicKeys(authToken, arrayListOf(derivationPathBuilder.build()))
            )
        }
    }

    private fun verifySignatures(
        @WalletContractV1.AuthToken authToken: Long,
        signingRequests: List<SigningRequest>,
        signingResponses: List<SigningResponse>,
        successMessage: String
    ) {
        check(signingRequests.size == signingResponses.size) { "Mismatch between number of requested and provided signatures" }
        viewModelScope.launch {
            val signaturesVerified = signingRequests.zip(signingResponses) { request, response ->
                val publicKeys = response.resolvedDerivationPaths.map { resolvedDerivationPath ->
                    val c = Wallet.getAccounts(
                        getApplication(),
                        authToken,
                        arrayOf(WalletContractV1.ACCOUNTS_PUBLIC_KEY_RAW),
                        WalletContractV1.ACCOUNTS_BIP32_DERIVATION_PATH,
                        resolvedDerivationPath.toString()
                    )
                    if (c?.moveToNext() != true) {
                        showMessage("Error: one or more public keys not found")
                        return@launch
                    }
                    c.getBlob(0)
                }

                response.signatures.zip(publicKeys) { payloadSignature, publicKey ->
                    VerifyEd25519SignatureUseCase(publicKey, request.payload, payloadSignature)
                }.all { it }
            }.all { it }

            if (!signaturesVerified) {
                showMessage("ERROR: One or more signatures not valid")
                return@launch
            }

            showMessage(successMessage)
        }
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
        private const val FIRST_REQUESTED_PUBLIC_KEY_INDEX = 1000
        private const val IMPLEMENTATION_LIMITS_MAX_BIP32_PATH_DEPTH = "MaxBip32PathDepth"
        private const val TRANSACTION_SIZE = 512
        private const val MESSAGE_SIZE = 512
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
    val maxSigningRequests: Int = -1,
    val maxRequestedSignatures: Int = -1,
    val firstRequestedPublicKey: String = "",
    val lastRequestedPublicKey: String = "",
    val messages: List<Message> = listOf()
)

sealed interface ViewModelEvent : Parcelable {
    sealed interface AddSeedViewModelEvent : ViewModelEvent

    object AuthorizeNewSeed : AddSeedViewModelEvent {
        override fun writeToParcel(parcel: Parcel, flags: Int) = Unit
        override fun describeContents(): Int = 0

        @JvmField
        val CREATOR = object : Parcelable.Creator<AuthorizeNewSeed> {
            override fun createFromParcel(parcel: Parcel) = AuthorizeNewSeed
            override fun newArray(size: Int): Array<AuthorizeNewSeed?> = arrayOfNulls(size)
        }
    }

    object CreateNewSeed : AddSeedViewModelEvent {
        override fun writeToParcel(parcel: Parcel, flags: Int) = Unit
        override fun describeContents(): Int = 0

        @JvmField
        val CREATOR = object : Parcelable.Creator<CreateNewSeed> {
            override fun createFromParcel(parcel: Parcel) = CreateNewSeed
            override fun newArray(size: Int): Array<CreateNewSeed?> = arrayOfNulls(size)
        }
    }

    object ImportExistingSeed : AddSeedViewModelEvent {
        override fun writeToParcel(parcel: Parcel, flags: Int) = Unit
        override fun describeContents(): Int = 0

        @JvmField
        val CREATOR = object : Parcelable.Creator<ImportExistingSeed> {
            override fun createFromParcel(parcel: Parcel) = ImportExistingSeed
            override fun newArray(size: Int): Array<ImportExistingSeed?> = arrayOfNulls(size)
        }
    }

    data class DeauthorizeSeed(
        @WalletContractV1.AuthToken val authToken: Long
    ) : ViewModelEvent {
        constructor(p: Parcel) : this(p.readLong())

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeLong(authToken)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<DeauthorizeSeed> {
            override fun createFromParcel(parcel: Parcel) = DeauthorizeSeed(parcel)
            override fun newArray(size: Int): Array<DeauthorizeSeed?> = arrayOfNulls(size)
        }
    }

    data class UpdateAccountName(
        @WalletContractV1.AuthToken val authToken: Long,
        @WalletContractV1.AccountId val accountId: Long,
        val name: String?,
    ) : ViewModelEvent {
        constructor(p: Parcel) : this(p.readLong(), p.readLong(), p.readString())

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeLong(authToken)
            parcel.writeLong(accountId)
            parcel.writeString(name)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<UpdateAccountName> {
            override fun createFromParcel(parcel: Parcel) = UpdateAccountName(parcel)
            override fun newArray(size: Int): Array<UpdateAccountName?> = arrayOfNulls(size)
        }
    }

    data class SignTransactions(
        @WalletContractV1.AuthToken val authToken: Long,
        val transactions: ArrayList<SigningRequest>,
    ) : ViewModelEvent {
        constructor(p: Parcel) : this(p.readLong(), p.createTypedArrayList(SigningRequest.CREATOR)!!)

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeLong(authToken)
            parcel.writeParcelableList(transactions, 0)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<SignTransactions> {
            override fun createFromParcel(parcel: Parcel) = SignTransactions(parcel)
            override fun newArray(size: Int): Array<SignTransactions?> = arrayOfNulls(size)
        }
    }

    data class SignMessages(
        @WalletContractV1.AuthToken val authToken: Long,
        val messages: ArrayList<SigningRequest>,
    ) : ViewModelEvent {
        constructor(p: Parcel) : this(p.readLong(), p.createTypedArrayList(SigningRequest.CREATOR)!!)

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeLong(authToken)
            parcel.writeParcelableList(messages, 0)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<SignMessages> {
            override fun createFromParcel(parcel: Parcel) = SignMessages(parcel)
            override fun newArray(size: Int): Array<SignMessages?> = arrayOfNulls(size)
        }
    }

    data class RequestPublicKeys(
        @WalletContractV1.AuthToken val authToken: Long,
        val derivationPaths: ArrayList<Uri>,
    ) : ViewModelEvent {
        constructor(p: Parcel) : this(p.readLong(), p.createTypedArrayList(Uri.CREATOR)!!)

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeLong(authToken)
            parcel.writeParcelableList(derivationPaths, 0)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<RequestPublicKeys> {
            override fun createFromParcel(parcel: Parcel) = RequestPublicKeys(parcel)
            override fun newArray(size: Int): Array<RequestPublicKeys?> = arrayOfNulls(size)
        }
    }
}
