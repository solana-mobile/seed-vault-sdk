package com.solanamobile.seedvault.reactnative

import android.app.Activity;
import android.content.Intent;
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.solanamobile.seedvault.*
import kotlinx.serialization.Serializable
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
                activity: Activity?,
                requestCode: Int,
                resultCode: Int,
                data: Intent?
            ) {
                handleActivityResult(requestCode, resultCode, data)
            }
        }

    init {
        reactContext.addActivityEventListener(mActivityEventListener)

        observeSeedVaultContentChanges()
    }

    @ReactMethod
    fun isSeedVaultAvailable(allowSimulated: Boolean = false, promise: Promise) {
        val seedVaultAvailable = SeedVault.isAvailable(reactContext, allowSimulated)
        promise.resolve(seedVaultAvailable)
    }

    @ReactMethod
    fun hasUnauthorizedSeeds(promise: Promise) {
        val application = reactContext.currentActivity?.application!!
        val hasUnauthorizedSeeds = Wallet.hasUnauthorizedSeedsForPurpose(application, 
            WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
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
    fun getAccounts(authToken: String, promise: Promise) {
        val application = reactContext.currentActivity?.application!!
        val accountsCursor = Wallet.getAccounts(application, authToken.toLong(),
                    WalletContractV1.ACCOUNTS_ALL_COLUMNS,
                    WalletContractV1.ACCOUNTS_ACCOUNT_IS_USER_WALLET, "1")!!

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
    fun authorizeNewSeed() {
        Log.d(TAG, "Requesting authorization for a new seed...")
        val intent = Wallet.authorizeSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_AUTHORIZE_SEED_ACCESS)
    }

    @ReactMethod
    fun authorizeNewSeedAsync(promise: Promise) {
        Log.d(TAG, "Requesting authorization for a new seed...")
        val intent = Wallet.authorizeSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
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
    fun createNewSeed() {
        Log.d(TAG, "Requesting creation of a new seed...")
        val intent = Wallet.createSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_CREATE_NEW_SEED)
    }

    @ReactMethod
    fun createNewSeedAsync(promise: Promise) {
        Log.d(TAG, "Requesting creation of a new seed...")
        val intent = Wallet.createSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
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
    fun importExistingSeed() {
        Log.d(TAG, "Requesting import of an existing seed...")
        val intent = Wallet.importSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_IMPORT_EXISTING_SEED)
    }

    @ReactMethod
    fun importExistingSeedAsync(promise: Promise) {
        Log.d(TAG, "Requesting import of an existing seed...")
        val intent = Wallet.importSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
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
    fun updateAccountName(authToken: String, accountId: Long, name: String?) {
        Wallet.updateAccountName(reactContext, authToken.toLong(), accountId, name)
        Log.d(TAG, "Account name updated (to '$name')")
    }

    @ReactMethod
    fun signMessage(authToken: String, derivationPath: String, message: ReadableArray) {
        signMessages(authToken, listOf(SigningRequest(message.toByteArray(), arrayListOf(Uri.parse(derivationPath)))))
    }

    @ReactMethod
    fun signMessages(authToken: String, signingRequestsJson: String) {
        val signingRequests: List<SigningRequest> = json.decodeFromString(ListSerializer(SigningRequestSerializer), signingRequestsJson)
        signMessages(authToken, signingRequests)
    }

    private fun signMessages(authToken: String, signingRequests: List<SigningRequest>) {
        Log.d(TAG, "Requesting provided messages to be signed...")
        val intent = Wallet.signMessages(authToken.toLong(), ArrayList(signingRequests))
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_SIGN_MESSAGES);
    }

    @ReactMethod
    fun signMessageAsync(authToken: String, derivationPath: String, message: ReadableArray, promise: Promise) {
        signMessagesAsync(authToken, listOf(SigningRequest(message.toByteArray(), arrayListOf(Uri.parse(derivationPath)))), promise)
    }

    @ReactMethod
    fun signMessagesAsync(authToken: String, signingRequestsJson: String, promise: Promise) {
        val signingRequests: List<SigningRequest> = json.decodeFromString(ListSerializer(SigningRequestSerializer), signingRequestsJson)
        signMessagesAsync(authToken, signingRequests, promise)
    }

    private fun signMessagesAsync(authToken: String, signingRequests: List<SigningRequest>, promise: Promise) {
        Log.d(TAG, "Requesting provided messages to be signed...")
        val intent = Wallet.signMessages(authToken.toLong(), ArrayList(signingRequests))
        registerForActivityResult(intent, REQUEST_SIGN_MESSAGES) { resultCode, data ->
            try {
                val result = Wallet.onSignMessagesResult(resultCode, data)
                Log.d(TAG, "Message signed: signatures=$result")

                promise.resolve(
                    Arguments.makeNativeArray(result.map { response ->
                        Arguments.createMap().apply {
                            putArray("signatures", Arguments.makeNativeArray(response.signatures.map { it.toWritableArray() }))
                            putArray("resolvedDerivationPaths", Arguments.makeNativeArray(response.resolvedDerivationPaths.map { it.toString() }))
                        }
                    })
                )
            } catch (e: Wallet.ActionFailedException) {
                Log.e(TAG, "Message signing failed", e)
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun signTransaction(authToken: String, derivationPath: String, transaction: ReadableArray) {
        signTransactions(authToken, listOf(SigningRequest(transaction.toByteArray(), arrayListOf(Uri.parse(derivationPath)))))
    }

    @ReactMethod
    fun signTransactions(authToken: String, signingRequestsJson: String) {
        val signingRequests: List<SigningRequest> = json.decodeFromString(ListSerializer(SigningRequestSerializer), signingRequestsJson)
        signTransactions(authToken, signingRequests)
    }

    private fun signTransactions(authToken: String, signingRequests: List<SigningRequest>) {
        Log.d(TAG, "Requesting provided transactions to be signed...")
        val intent = Wallet.signTransactions(authToken.toLong(), ArrayList(signingRequests))
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_SIGN_TRANSACTIONS)
    }

    @ReactMethod
    fun signTransactionAsync(authToken: String, derivationPath: String, transaction: ReadableArray, promise: Promise) {
        signTransactionsAsync(authToken, listOf(SigningRequest(transaction.toByteArray(), arrayListOf(Uri.parse(derivationPath)))), promise)
    }

    @ReactMethod
    fun signTransactionsAsync(authToken: String, signingRequestsJson: String, promise: Promise) {
        val signingRequests: List<SigningRequest> = json.decodeFromString(ListSerializer(SigningRequestSerializer), signingRequestsJson)
        signTransactionsAsync(authToken, signingRequests, promise)
    }

    private fun signTransactionsAsync(authToken: String, signingRequests: List<SigningRequest>, promise: Promise) {
        Log.d(TAG, "Requesting provided transactions to be signed...")
        val intent = Wallet.signTransactions(authToken.toLong(), ArrayList(signingRequests))
        registerForActivityResult(intent, REQUEST_SIGN_TRANSACTIONS) { resultCode, data ->
            try {
                val result = Wallet.onSignTransactionsResult(resultCode, data)
                Log.d(TAG, "Transactions signed: signatures=$result")

                promise.resolve(
                    Arguments.makeNativeArray(result.map { response ->
                        Arguments.createMap().apply {
                            putArray("signatures", Arguments.makeNativeArray(response.signatures.map { it.toWritableArray() }))
                            putArray("resolvedDerivationPaths", Arguments.makeNativeArray(response.resolvedDerivationPaths.map { it.toString() }))
                        }
                    })
                )
            } catch (e: Wallet.ActionFailedException) {
                Log.e(TAG, "Transaction signing failed", e)
                promise.reject(e)
            }
        }
    }

    private fun registerForActivityResult(intent: Intent, requestCode: Int, callback: (resultCode: Int, data: Intent?) -> Unit) {
        val timeout = object : CountDownTimer(30000, 30000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                reactContext.currentActivity?.finishActivity(requestCode)
            }
        }

        reactContext.addActivityEventListener(object : BaseActivityEventListener() {
            override fun onActivityResult(
                activity: Activity?,
                receivedRequestCode: Int,
                resultCode: Int,
                data: Intent?
            ) {
                if (receivedRequestCode == requestCode) {
                    reactContext.removeActivityEventListener(this)
                    callback(resultCode, data)
                    timeout.cancel()
                }
            }
        })
        
        reactContext.currentActivity?.startActivityForResult(intent, requestCode)
        timeout.start()
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
        val intent = Wallet.requestPublicKeys(authToken.toLong(), Arguments.toList(derivationPaths)?.mapNotNull {
            (it as? String)?.let { uriString -> Uri.parse(uriString) }
        } as ArrayList ?: arrayListOf())
        reactContext.currentActivity?.startActivityForResult(intent, REQUEST_GET_PUBLIC_KEYS);
    }

    @ReactMethod
    fun requestPublicKeyAsync(authToken: String, derivationPath: String, promise: Promise) {
        requestPublicKeysAsync(authToken, Arguments.createArray().apply {
            pushString(derivationPath)
        }, promise)
    }

    @ReactMethod
    fun requestPublicKeysAsync(authToken: String, derivationPaths: ReadableArray, promise: Promise) {
        Log.d(TAG, "Requesting public keys for provided derviation paths...")
        val intent = Wallet.requestPublicKeys(authToken.toLong(), Arguments.toList(derivationPaths)?.mapNotNull {
            (it as? String)?.let { uriString -> Uri.parse(uriString) }
        } as ArrayList ?: arrayListOf())
        registerForActivityResult(intent, REQUEST_GET_PUBLIC_KEYS) { resultCode, data ->
            try {
                val result = Wallet.onRequestPublicKeysResult(resultCode, data)
                Log.d(TAG, "Public key retrieved: publicKey=$result")

                promise.resolve(
                    Arguments.makeNativeArray(result.map { response ->
                        Arguments.createMap().apply {
                            putArray("publicKey", response.publicKey.toWritableArray())
                            putString("publicKeyEncoded", response.publicKeyEncoded)
                            putString("resolvedDerviationPath", response.resolvedDerivationPath.toString())
                        }
                    })
                )
            } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Public Key retrieval failed", e)
                promise.reject(e)
            }
        }
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
                    Log.d(TAG, "Received change notification for $uris (flags=$flags); refreshing viewmodel")
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
        private const val KEY_PENDING_EVENT = "pendingEvent"
    }
}

interface RNWritable {
    fun toWritableMap(): WritableMap
}

data class Account(
    @WalletContractV1.AccountId val id: Long,
    val name: String,
    val derivationPath: Uri,
    val publicKeyEncoded: String
) : RNWritable {
    override fun toWritableMap() = Arguments.createMap().apply {
        putString("id", "$id")
        putString("name", name)
        putString("derivationPath", "$derivationPath")
        putString("publicKeyEncoded", publicKeyEncoded)
    }
}

data class Seed(
    @WalletContractV1.AuthToken val authToken: Long,
    val name: String,
    @WalletContractV1.Purpose val purpose: Int,
    // val accounts: List<Account> = listOf()
) : RNWritable {
    override fun toWritableMap() = Arguments.createMap().apply {
        putString("authToken", "$authToken")
        putString("name", name)
        putInt("purpose", purpose)
    }
}

fun List<RNWritable>.toWritableArray() = Arguments.createArray().apply {
    forEach { writable ->
        pushMap(writable.toWritableMap())
    }
}

@Serializable
sealed interface SeedVaultEvent {
    sealed class SeedEvent(val authToken: Long) : SeedVaultEvent
    class SeedAuthorized(authToken: Long) : SeedEvent(authToken)
    class NewSeedCreated(authToken: Long) : SeedEvent(authToken)
    class ExistingSeedImported(authToken: Long) : SeedEvent(authToken)

    data class PayloadsSigned(val result: List<SigningResponse>) : SeedVaultEvent

    data class PublicKeysEvent(val result: List<PublicKeyResponse>) : SeedVaultEvent
}

internal fun SeedVaultEvent.toWritableMap() : WritableMap = Arguments.createMap().apply {
    putString("__type", this@toWritableMap::class.simpleName)
    when (this@toWritableMap) {
        is SeedVaultEvent.SeedEvent -> {
            putString("authToken", authToken.toString())
        }
        is SeedVaultEvent.PayloadsSigned -> {
            putArray("result", Arguments.makeNativeArray(result.map { response ->
                Arguments.createMap().apply {
                    putArray("signatures", Arguments.makeNativeArray(response.signatures.map { it.toWritableArray() }))
                    putArray("resolvedDerivationPaths", Arguments.makeNativeArray(response.resolvedDerivationPaths.map { it.toString() }))
                }
            }))
        }
        is SeedVaultEvent.PublicKeysEvent -> {
            putArray("result", Arguments.makeNativeArray(result.map { response ->
                Arguments.createMap().apply {
                    putArray("publicKey", response.publicKey.toWritableArray())
                    putString("publicKeyEncoded", response.publicKeyEncoded)
                    putString("resolvedDerviationPath", response.resolvedDerivationPath.toString())
                }
            }))
        }
    }
}
