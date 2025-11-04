package com.solanamobile.seedvault.reactnative

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Base64
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.solanamobile.seedvault.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

import com.solanamobile.seedvault.model.SigningRequestSerializer

class SolanaMobileSeedVaultLibModule(val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    // Sets the name of the module in React, accessible at ReactNative.NativeModules.SeedVaultLib
    override fun getName() = "SolanaMobileSeedVaultLib"

    private val json = Json { ignoreUnknownKeys = true }

    private val mActivityEventListener: ActivityEventListener =
        object : BaseActivityEventListener() {
            override fun onActivityResult(
                activity: Activity,
                requestCode: Int,
                resultCode: Int,
                data: Intent?
            ) {
                handleActivityResult(requestCode, resultCode, data)
            }
        }

    private var activityResultTimeout: Long? = DEFAULT_ACTIVITY_RESULT_TIMEOUT_MS

    init {
        reactContext.addActivityEventListener(mActivityEventListener)

        if ((reactContext.checkSelfPermission(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT) == PackageManager.PERMISSION_GRANTED
                || reactContext.checkSelfPermission(WalletContractV1.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED) == PackageManager.PERMISSION_GRANTED) 
                && SeedVault.isAvailable(reactContext, true)) {
            observeSeedVaultContentChanges()
        }
    }

    @ReactMethod
    fun setActivtyResultTimeout(timeoutStr: String) {
        try {
            val timeout = timeoutStr.toLong()
            activityResultTimeout =
                if (timeout > MINIMUM_ACTIVITY_RESULT_TIMEOUT_MS) timeout
                else if (timeout == 0L) null
                else MINIMUM_ACTIVITY_RESULT_TIMEOUT_MS
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Invalid timeout value: $timeoutStr", e)
        }
    }

    @ReactMethod
    fun isSeedVaultAvailable(allowSimulated: Boolean = false, promise: Promise) {
        val seedVaultAvailable = SeedVault.isAvailable(reactContext, allowSimulated)
        promise.resolve(seedVaultAvailable)
    }

    @ReactMethod
    fun hasUnauthorizedSeeds(promise: Promise) {
        hasUnauthorizedSeedsForPurpose(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION, promise)
    }

    @ReactMethod
    fun hasUnauthorizedSeedsForPurpose(purpose: Double, promise: Promise) {
        hasUnauthorizedSeedsForPurpose(purpose.toInt(), promise)
    }

    private fun hasUnauthorizedSeedsForPurpose(purpose: Int, promise: Promise) {
        val application = reactContext.currentActivity?.application!!
        val hasUnauthorizedSeeds = Wallet.hasUnauthorizedSeedsForPurpose(application, purpose)
        promise.resolve(hasUnauthorizedSeeds)
    }

    @ReactMethod
    fun getAuthorizedSeeds(promise: Promise) {
        val application = reactContext.currentActivity?.application!!
        val authorizedSeedsCursor = Wallet.getAuthorizedSeeds(application,
            WalletContractV1.AUTHORIZED_SEEDS_ALL_COLUMNS)!!

        val seeds = mutableListOf<Seed>()
        
        while (authorizedSeedsCursor.moveToNext()) {
            val authToken = authorizedSeedsCursor.getLong(0)
            val authPurpose = authorizedSeedsCursor.getInt(1)
            val seedName = authorizedSeedsCursor.getString(2)

            seeds.add(
                Seed(authToken, seedName.ifBlank { authToken.toString() }, authPurpose)
            )
        }

        promise.resolve(seeds.toWritableArray())
    }

    @ReactMethod
    fun getUnauthorizedSeeds(promise: Promise) {
        val application = reactContext.currentActivity?.application!!
        val unauthorizedSeedsCursor = Wallet.getUnauthorizedSeeds(application,
            WalletContractV1.UNAUTHORIZED_SEEDS_ALL_COLUMNS)!!

        val unauthorizedSeedsByPurpose = Arguments.createMap()
        
        while (unauthorizedSeedsCursor.moveToNext()) {
            val authPurpose = unauthorizedSeedsCursor.getInt(0)
            val hasUnauthorizedSeeds = unauthorizedSeedsCursor.getInt(1) == 1

            unauthorizedSeedsByPurpose.putBoolean("$authPurpose", hasUnauthorizedSeeds)
        }

        promise.resolve(unauthorizedSeedsByPurpose)
    }

    @ReactMethod
    fun getAccounts(authToken: String, filterOnColumn: String?, value: String?, promise: Promise) {
        val application = reactContext.currentActivity?.application!!
        val accountsCursor = Wallet.getAccounts(application, authToken.toLong(),
                    WalletContractV1.ACCOUNTS_ALL_COLUMNS, filterOnColumn, value)!!

        val accounts = mutableListOf<Account>()
        
        while (accountsCursor.moveToNext()) {
            val accountId = accountsCursor.getLong(0)
            val derivationPath = Uri.parse(accountsCursor.getString(1))
            val publicKeyEncoded = accountsCursor.getString(3)
            val accountName = accountsCursor.getString(4)
            
            accounts.add(
                Account(accountId, 
                accountName.ifBlank { publicKeyEncoded.substring(0, 10) },
                derivationPath, publicKeyEncoded)
            )
        }

        promise.resolve(accounts.toWritableArray())
    }

    @ReactMethod
    fun getUserWallets(authToken: String, promise: Promise) {
        getAccounts(authToken, WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET, "1", promise)
    }

    @ReactMethod
    fun requestAuthorizeNewSeed() {
        Log.d(TAG, "Requesting authorization for a new seed...")
        val intent = Wallet.authorizeSeed(reactContext, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_AUTHORIZE_SEED_ACCESS)
    }

    @ReactMethod
    fun authorizeNewSeed(promise: Promise) {
        Log.d(TAG, "Requesting authorization for a new seed...")
        val intent = Wallet.authorizeSeed(reactContext, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        registerForActivityResult(intent, REQUEST_AUTHORIZE_SEED_ACCESS) { resultCode, data -> 
            try {
                val authToken = Wallet.onAuthorizeSeedResult(resultCode, data)
                Log.d(TAG, "Seed authorized, AuthToken=$authToken")
                
                promise.resolve(
                    Arguments.createMap().apply {
                        putString("authToken", authToken.toString())
                    }
                )
            } catch (e: Wallet.ActionFailedException) {
                Log.e(TAG, "Seed authorization failed", e)
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun requestCreateNewSeed() {
        Log.d(TAG, "Requesting creation of a new seed...")
        val intent = Wallet.createSeed(reactContext, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_CREATE_NEW_SEED)
    }

    @ReactMethod
    fun createNewSeed(promise: Promise) {
        Log.d(TAG, "Requesting creation of a new seed...")
        val intent = Wallet.createSeed(reactContext, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        registerForActivityResult(intent, REQUEST_CREATE_NEW_SEED) { resultCode, data -> 
            try {
                val authToken = Wallet.onCreateSeedResult(resultCode, data)
                Log.d(TAG, "Seed created, AuthToken=$authToken")
                
                promise.resolve(
                    Arguments.createMap().apply {
                        putString("authToken", authToken.toString())
                    }
                )
            } catch (e: Wallet.ActionFailedException) {
                Log.e(TAG, "Seed creation failed", e)
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun requestImportExistingSeed() {
        Log.d(TAG, "Requesting import of an existing seed...")
        val intent = Wallet.importSeed(reactContext, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_IMPORT_EXISTING_SEED)
    }

    @ReactMethod
    fun importExistingSeed(promise: Promise) {
        Log.d(TAG, "Requesting import of an existing seed...")
        val intent = Wallet.importSeed(reactContext, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        registerForActivityResult(intent, REQUEST_IMPORT_EXISTING_SEED) { resultCode, data -> 
            try {
                val authToken = Wallet.onImportSeedResult(resultCode, data)
                Log.d(TAG, "Seed imported, AuthToken=$authToken")
                
                promise.resolve(
                    Arguments.createMap().apply {
                        putString("authToken", authToken.toString())
                    }
                )
            } catch (e: Wallet.ActionFailedException) {
                Log.e(TAG, "Seed import failed", e)
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun deauthorizeSeed(authToken: String) {
        Wallet.deauthorizeSeed(reactContext, authToken.toLong())
        Log.d(TAG, "Seed $authToken deauthorized")
    }
    
    @ReactMethod
    fun updateAccountName(authToken: String, accountId: String, name: String?) {
        try {
            Wallet.updateAccountName(reactContext, authToken.toLong(), accountId.toLong(), name)
            Log.d(TAG, "Account name updated (to '$name')")
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Invalid accountId: $accountId", e)
        }
    }

    @ReactMethod
    fun updateAccountIsUserWallet(authToken: String, accountId: String, isUserWallet: Boolean) {
        try {
            Wallet.updateAccountIsUserWallet(reactContext, authToken.toLong(), accountId.toLong(), isUserWallet)
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Invalid accountId: $accountId", e)
        }
    }

    @ReactMethod
    fun updateAccountIsValid(authToken: String, accountId: String, isValid: Boolean) {
        try {
            Wallet.updateAccountIsValid(reactContext, authToken.toLong(), accountId.toLong(), isValid)
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Invalid accountId: $accountId", e)
        }
    }

    @ReactMethod
    fun requestShowSeedSettings(authToken: String) {
        Log.d(TAG, "Requesting Seed Settings to be shown...")
        val intent = Wallet.showSeedSettings(reactContext, authToken.toLong())
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_SHOW_SEED_SETTINGS);
    }

    @ReactMethod
    fun showSeedSettings(authToken: String, promise: Promise) {
        showSeedSettingsAsync(authToken) { error ->
            error?.let { promise.reject(it) } ?: promise.resolve(null)
        }
    }

    private fun showSeedSettingsAsync(authToken: String, callback: (error: Throwable?) -> Unit) {
        Log.d(TAG, "Requesting Seed Settings to be shown...")
        val intent = Wallet.showSeedSettings(reactContext, authToken.toLong())
        try {
            registerForActivityResult(intent, REQUEST_SHOW_SEED_SETTINGS) { resultCode, data ->
                if (resultCode != Activity.RESULT_CANCELED) {
                    try {
                        Wallet.onShowSeedSettingsResult(resultCode, data)
                        Log.d(TAG, "Seed settings shown")
                        callback(null)
                    } catch (e: Wallet.ActionFailedException) {
                        Log.e(TAG, "Show seed settings failed", e)
                        callback(e)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Show seed settings failed, permision denied")
            callback(e)
        }
    }

    @ReactMethod
    fun requestSignMessage(authToken: String, derivationPath: String, message: ReadableArray) {
        requestSignMessages(authToken, listOf(SigningRequest(message.toByteArray(), arrayListOf(Uri.parse(derivationPath)))))
    }

    @ReactMethod
    fun requestSignMessages(authToken: String, signingRequestsJson: String) {
        val signingRequests: List<SigningRequest> = json.decodeFromString(ListSerializer(SigningRequestSerializer), signingRequestsJson)
        requestSignMessages(authToken, signingRequests)
    }

    private fun requestSignMessages(authToken: String, signingRequests: List<SigningRequest>) {
        Log.d(TAG, "Requesting provided messages to be signed...")
        val intent = Wallet.signMessages(reactContext, authToken.toLong(), ArrayList(signingRequests))
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_SIGN_MESSAGES);
    }

    @ReactMethod
    fun signMessage(authToken: String, derivationPath: String, message: String, promise: Promise) {
        val messageBytes = Base64.decode(message, Base64.DEFAULT);
        signMessagesAsync(authToken, listOf(SigningRequest(messageBytes, arrayListOf(Uri.parse(derivationPath))))) { result, error ->
            result?.let { promise.resolve(Arguments.makeNativeMap(Arguments.toBundle(result.getMap(0)))) } 
            ?: promise.reject(error ?: Error("An unkown error occurred"))
        }
    }

    @ReactMethod
    fun signMessages(authToken: String, signingRequests: ReadableArray, promise: Promise) {
        val signingRequests: List<SigningRequest> = json.decodeFromJsonElement(ListSerializer(SigningRequestSerializer), signingRequests.toJson())
        signMessagesAsync(authToken, signingRequests) { result, error ->
            result?.let { promise.resolve(result) } ?: promise.reject(error ?: Error("An unkown error occurred"))
        }
    }

    private fun signMessagesAsync(authToken: String, signingRequests: List<SigningRequest>, callback: (result: ReadableArray?, error: Throwable?) -> Unit) {
        Log.d(TAG, "Requesting provided messages to be signed...")
        val intent = Wallet.signMessages(reactContext, authToken.toLong(), ArrayList(signingRequests))
        registerForActivityResult(intent, REQUEST_SIGN_MESSAGES) { resultCode, data ->
            try {
                val result = Wallet.onSignMessagesResult(resultCode, data)
                Log.d(TAG, "Message signed: signatures=$result")

                callback(
                    Arguments.makeNativeArray(result.map { response ->
                        Arguments.createMap().apply {
                            putArray("signatures", Arguments.makeNativeArray(response.signatures.map { Base64.encodeToString(it, Base64.NO_WRAP) }))
                            putArray("resolvedDerivationPaths", Arguments.makeNativeArray(response.resolvedDerivationPaths.map { it.toString() }))
                        }
                    }), null
                )
            } catch (e: Wallet.ActionFailedException) {
                Log.e(TAG, "Message signing failed", e)
                callback(null, e)
            }
        }
    }

    @ReactMethod
    fun requestSignTransaction(authToken: String, derivationPath: String, transaction: ReadableArray) {
        requestSignTransactions(authToken, listOf(SigningRequest(transaction.toByteArray(), arrayListOf(Uri.parse(derivationPath)))))
    }

    @ReactMethod
    fun requestSignTransactions(authToken: String, signingRequestsJson: String) {
        val signingRequests: List<SigningRequest> = json.decodeFromString(ListSerializer(SigningRequestSerializer), signingRequestsJson)
        requestSignTransactions(authToken, signingRequests)
    }

    private fun requestSignTransactions(authToken: String, signingRequests: List<SigningRequest>) {
        Log.d(TAG, "Requesting provided transactions to be signed...")
        val intent = Wallet.signTransactions(reactContext, authToken.toLong(), ArrayList(signingRequests))
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_SIGN_TRANSACTIONS)
    }

    @ReactMethod
    fun signTransaction(authToken: String, derivationPath: String, transaction: String, promise: Promise) {
        val txBytes = Base64.decode(transaction, Base64.DEFAULT);
        signTransactionsAsync(authToken, listOf(SigningRequest(txBytes, arrayListOf(Uri.parse(derivationPath))))) { result, error ->
            result?.let { promise.resolve(Arguments.makeNativeMap(Arguments.toBundle(result.getMap(0)))) } 
            ?: promise.reject(error ?: Error("An unkown error occurred"))
        }
    }

    @ReactMethod
    fun signTransactions(authToken: String, signingRequests: ReadableArray, promise: Promise) {
        val signingRequests: List<SigningRequest> = json.decodeFromJsonElement(ListSerializer(SigningRequestSerializer), signingRequests.toJson())
        signTransactionsAsync(authToken, signingRequests) { result, error ->
            result?.let { promise.resolve(result) } ?: promise.reject(error ?: Error("An unkown error occurred"))
        }
    }

    private fun signTransactionsAsync(authToken: String, signingRequests: List<SigningRequest>, callback: (result: ReadableArray?, error: Throwable?) -> Unit) {
        Log.d(TAG, "Requesting provided transactions to be signed...")
        val intent = Wallet.signTransactions(reactContext, authToken.toLong(), ArrayList(signingRequests))
        registerForActivityResult(intent, REQUEST_SIGN_TRANSACTIONS) { resultCode, data ->
            try {
                val result = Wallet.onSignTransactionsResult(resultCode, data)
                Log.d(TAG, "Transactions signed: signatures=$result")

                callback(
                    Arguments.makeNativeArray(result.map { response ->
                        Arguments.createMap().apply {
                            putArray("signatures", Arguments.makeNativeArray(response.signatures.map { Base64.encodeToString(it, Base64.NO_WRAP) }))
                            putArray("resolvedDerivationPaths", Arguments.makeNativeArray(response.resolvedDerivationPaths.map { it.toString() }))
                        }
                    }), null
                )
            } catch (e: Wallet.ActionFailedException) {
                Log.e(TAG, "Transaction signing failed", e)
                callback(null, e)
            }
        }
    }

    private fun registerForActivityResult(intent: Intent, requestCode: Int, callback: (resultCode: Int, data: Intent?) -> Unit) {
        val timeout = activityResultTimeout?.let { timeout ->
            object : CountDownTimer(timeout, timeout) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() {
                    reactContext.currentActivity?.finishActivity(requestCode)
                }
            }
        }

        reactContext.addActivityEventListener(object : BaseActivityEventListener() {
            override fun onActivityResult(
                activity: Activity,
                receivedRequestCode: Int,
                resultCode: Int,
                data: Intent?
            ) {
                if (receivedRequestCode == requestCode) {
                    reactContext.removeActivityEventListener(this)
                    callback(resultCode, data)
                    timeout?.cancel()
                }
            }
        })
        
        reactContext.currentActivity?.startActivityForResult(intent, requestCode)
        timeout?.start()
    }

    @ReactMethod
    fun requestPublicKey(authToken: String, derivationPath: String) {
        requestPublicKeys(authToken, Arguments.createArray().apply {
            pushString(derivationPath)
        })
    }

    @ReactMethod
    fun requestPublicKeys(authToken: String, derivationPaths: ReadableArray) {
        Log.d(TAG, "Requesting public keys for provided derviation paths...")
        val intent = Wallet.requestPublicKeys(reactContext, authToken.toLong(), Arguments.toList(derivationPaths)?.mapNotNull {
            (it as? String)?.let { uriString -> Uri.parse(uriString) }
        } as ArrayList ?: arrayListOf())
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_GET_PUBLIC_KEYS);
    }

    @ReactMethod
    fun getPublicKey(authToken: String, derivationPath: String, promise: Promise) {
        getPublicKeysAsync(authToken, Arguments.createArray().apply {
            pushString(derivationPath)
        }) { result, error ->
            result?.let { promise.resolve(Arguments.makeNativeMap(Arguments.toBundle(result.getMap(0)))) } 
            ?: promise.reject(error ?: Error("An unkown error occurred"))
        }
    }

    @ReactMethod
    fun getPublicKeys(authToken: String, derivationPaths: ReadableArray, promise: Promise) {
        getPublicKeysAsync(authToken, derivationPaths) { result, error ->
            result?.let { promise.resolve(result) } ?: promise.reject(error ?: Error("An unkown error occurred"))
        }
    }

    private fun getPublicKeysAsync(authToken: String, derivationPaths: ReadableArray, callback: (result: ReadableArray?, error: Throwable?) -> Unit) {
        Log.d(TAG, "Requesting public keys for provided derviation paths...")
        val intent = Wallet.requestPublicKeys(reactContext, authToken.toLong(), Arguments.toList(derivationPaths)?.mapNotNull {
            (it as? String)?.let { uriString -> Uri.parse(uriString) }
        } as ArrayList ?: arrayListOf())
        registerForActivityResult(intent, REQUEST_GET_PUBLIC_KEYS) { resultCode, data ->
            try {
                val result = Wallet.onRequestPublicKeysResult(resultCode, data)
                Log.d(TAG, "Public key retrieved: publicKey=$result")

                callback(
                    Arguments.makeNativeArray(result.map { response ->
                        Arguments.createMap().apply {
                            putArray("publicKey", response.publicKey.toWritableArray())
                            putString("publicKeyEncoded", response.publicKeyEncoded)
                            putString("resolvedDerivationPath", response.resolvedDerivationPath.toString())
                        }
                    }), null
                )
            } catch (e: Wallet.ActionFailedException) {
                Log.e(TAG, "Public Key retrieval failed", e)
                callback(null, e)
            }
        }
    }

    @ReactMethod
    fun resolveDerivationPath(derivationPath: String, promise: Promise) {
        resolveDerivationPath(Uri.parse(derivationPath), WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION, promise)
    }

    @ReactMethod
    fun resolveDerivationPathForPurpose(derivationPath: String, purpose: Double, promise: Promise) {
        resolveDerivationPath(Uri.parse(derivationPath), purpose.toInt(), promise)
    }

    private fun resolveDerivationPath(derivationPath: Uri, purpose: Int, promise: Promise) {
        val resolvedDerivationPath = Wallet.resolveDerivationPath(reactContext, derivationPath, purpose)
        promise.resolve(resolvedDerivationPath.toString())
    }

    private fun sendEvent(reactContext: ReactContext, eventName: String, params: WritableMap? = null) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    private fun sendSeedVaultEvent(event: SeedVaultEvent) {
        sendEvent(reactContext, Companion.SEED_VAULT_EVENT_BRIDGE_NAME, event.toWritableMap())
    }

    private fun observeSeedVaultContentChanges() {
        reactContext.contentResolver.registerContentObserver(
            WalletContractV1.WALLET_PROVIDER_CONTENT_URI_BASE,
            true,
            object : ContentObserver(Handler(reactContext.mainLooper)) {
                override fun onChange(selfChange: Boolean) =
                    throw NotImplementedError("Stub for legacy onChange")
                override fun onChange(selfChange: Boolean, uri: Uri?) =
                    throw NotImplementedError("Stub for legacy onChange")
                override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) =
                    throw NotImplementedError("Stub for legacy onChange")

                override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
                    sendEvent(reactContext, SEED_VAULT_CONTENT_CHANGE_EVENT_BRIDGE_NAME, 
                        params = Arguments.createMap().apply {
                            putString("__type", "SeedVaultContentChange")
                            putArray("uris", Arguments.createArray().apply {
                                uris.forEach { uri ->
                                    pushString(uri.toString())
                                }
                            })
                        }
                    )
                }
            }
        )
    }

    private fun handleActivityResult(requestCode: Int,  resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_AUTHORIZE_SEED_ACCESS -> {
                try {
                    val authToken = Wallet.onAuthorizeSeedResult(resultCode, data)
                    Log.d(TAG, "Seed authorized, AuthToken=$authToken")
                    sendSeedVaultEvent(SeedVaultEvent.SeedAuthorized(authToken))
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Seed authorization failed", e)
                }
            }
            REQUEST_CREATE_NEW_SEED -> {
                try {
                    val authToken = Wallet.onCreateSeedResult(resultCode, data)
                    Log.d(TAG, "Seed created, AuthToken=$authToken")
                    sendSeedVaultEvent(SeedVaultEvent.NewSeedCreated(authToken))
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Seed creation failed", e)
                }
            }
            REQUEST_IMPORT_EXISTING_SEED -> {
                try {
                    val authToken = Wallet.onImportSeedResult(resultCode, data)
                    Log.d(TAG, "Seed imported, AuthToken=$authToken")
                    sendSeedVaultEvent(SeedVaultEvent.ExistingSeedImported(authToken))
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Seed import failed", e)
                }
            }
            REQUEST_SIGN_TRANSACTIONS -> {
                try {
                    val result = Wallet.onSignTransactionsResult(resultCode, data)
                    Log.d(TAG, "Transaction signed: signatures=$result")
                    sendSeedVaultEvent(SeedVaultEvent.PayloadsSigned(result))
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Transaction signing failed", e)
                }
            }
            REQUEST_SIGN_MESSAGES -> {
                try {
                    val result = Wallet.onSignMessagesResult(resultCode, data)
                    Log.d(TAG, "Message signed: signatures=$result")
                    sendSeedVaultEvent(SeedVaultEvent.PayloadsSigned(result))
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Message signing failed", e)
                }
            }
            REQUEST_GET_PUBLIC_KEYS -> {
                try {
                    val result = Wallet.onRequestPublicKeysResult(resultCode, data)
                    Log.d(TAG, "Public key retrieved: publicKey=$result")
                    sendSeedVaultEvent(SeedVaultEvent.PublicKeysEvent(result))
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Public Key retrieval failed", e)
                }
            }
        }
    }

    companion object {
        private val TAG = SolanaMobileSeedVaultLibModule::class.simpleName
        private const val SEED_VAULT_EVENT_BRIDGE_NAME = "SeedVaultEventBridge"
        private const val SEED_VAULT_CONTENT_CHANGE_EVENT_BRIDGE_NAME = "SeedVaultContentChangeEventBridge"
        private const val REQUEST_AUTHORIZE_SEED_ACCESS = 0
        private const val REQUEST_CREATE_NEW_SEED = 1
        private const val REQUEST_IMPORT_EXISTING_SEED = 2
        private const val REQUEST_SIGN_TRANSACTIONS = 3
        private const val REQUEST_SIGN_MESSAGES = 4
        private const val REQUEST_GET_PUBLIC_KEYS = 5
        private const val REQUEST_SHOW_SEED_SETTINGS = 6
        private const val KEY_PENDING_EVENT = "pendingEvent"
        private const val DEFAULT_ACTIVITY_RESULT_TIMEOUT_MS = 300000L
        private const val MINIMUM_ACTIVITY_RESULT_TIMEOUT_MS = 30000L
    }
}
