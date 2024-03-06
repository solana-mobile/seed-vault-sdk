package com.solanamobile.seedvault.reactnative

import android.net.Uri
import com.facebook.react.bridge.*
import com.solanamobile.seedvault.*
import kotlinx.serialization.Serializable

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