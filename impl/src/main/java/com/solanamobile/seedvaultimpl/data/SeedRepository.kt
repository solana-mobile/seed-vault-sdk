/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.solanamobile.seedvaultimpl.data.proto.*
import com.solanamobile.seedvaultimpl.model.Account
import com.solanamobile.seedvaultimpl.model.Authorization
import com.solanamobile.seedvaultimpl.model.SeedDetails
import com.solanamobile.seedvaultimpl.model.Seed
import com.google.protobuf.ByteString
import com.solanamobile.seedvaultimpl.data.proto.seedCollectionDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

typealias SeedIdMap = Map<Int, Seed> // maps from seed ID to Seed
typealias SeedAuthorizationMap = Map<SeedRepository.AuthorizationKey, Seed> // maps from Authorization to Seed

class SeedRepository(
    private val context: Context,
    private val repositoryOwnerScope: CoroutineScope) {

    companion object {
        private val TAG = SeedRepository::class.simpleName
        private const val MAX_SEEDS = 4
        private const val FIRST_SEED_ID = 1000
        private const val FIRST_AUTH_TOKEN = 4000
        private const val FIRST_ACCOUNT_ID = 7000
    }

    data class AuthorizationKey(
        val uid: Int,
        val authToken: Int
    )

    data class ChangeNotification(
        val category: Category,
        val type: Type,
        val id: Int?
    ) {
        enum class Category { SEED, AUTHORIZATION, ACCOUNT }
        enum class Type { CREATE, UPDATE, DELETE }
    }

    // Protects all shared state that can be modified by arbitrary threads
    private val mutex = Mutex()
    private var nextSeedId = FIRST_SEED_ID
    private var nextAuthToken = FIRST_AUTH_TOKEN
    private var nextAccountId = FIRST_ACCOUNT_ID

    private val seedCollection: SharedFlow<SeedCollection> = context.seedCollectionDataStore.data.catch { e ->
        if (e is IOException) {
            Log.e(TAG, "Error reading seed collection; using defaults", e)
            emit(SeedCollection.getDefaultInstance())
        } else {
            throw e
        }
    }.onStart {
        Log.d(TAG, "seedCollection flow opened")
    }.onCompletion {
        Log.d(TAG, "seedCollection flow closed")
    }.onEach { sc ->
        mutex.withLock {
            if (sc.nextId > nextSeedId) {
                nextSeedId = sc.nextId
            }
            if (sc.nextAuthToken > nextAuthToken) {
                nextAuthToken = sc.nextAuthToken
            }
            if (sc.nextAccountId > nextAccountId) {
                nextAccountId = sc.nextAccountId
            }
        }
    }.shareIn(repositoryOwnerScope, SharingStarted.Eagerly, 1)

    val seeds: StateFlow<SeedIdMap> = seedCollection.map { sc ->
        sc.seedsList.associate { sr ->
            val details = SeedDetails(
                sr.seed.seed.toByteArray(),
                sr.seed.seedPhraseWordIndicesList,
                sr.seed.name.ifEmpty { null },
                sr.seed.pin,
                sr.seed.unlockWithBiometrics
            )
            val authorizations = sr.authorizationsList.map { ae ->
                Authorization(ae.uid, ae.authToken, Authorization.Purpose.values()[ae.purpose])
            }
            val accounts = sr.knownAccountsList.map { kae ->
                Account(kae.accountId, Authorization.Purpose.values()[kae.purpose],
                    Uri.parse(kae.bip32Uri), kae.publicKey.toByteArray(), kae.name.ifEmpty { null },
                    kae.isUserWallet, kae.isValid)
            }
            sr.seedId to Seed(sr.seedId, details, authorizations, accounts)
        }
    }.stateIn(repositoryOwnerScope, SharingStarted.Eagerly, mapOf())

    val isFull: StateFlow<Boolean> = seedCollection.map { sc ->
        sc.seedsList.count() >= MAX_SEEDS
    }.stateIn(repositoryOwnerScope, SharingStarted.Eagerly, true)

    val authorizations: StateFlow<SeedAuthorizationMap> = seeds.map { sim ->
        val map = mutableMapOf<AuthorizationKey, Seed>()
        sim.values.forEach { seed ->
            seed.authorizations.map {
                    auth -> AuthorizationKey(auth.uid, auth.authToken)
            }.associateWithTo(map) { seed }
        }
        map
    }.stateIn(repositoryOwnerScope, SharingStarted.Eagerly, mapOf())

    private val _changes: MutableSharedFlow<ChangeNotification> = MutableSharedFlow(extraBufferCapacity = 1)
    val changes = _changes.asSharedFlow()

    suspend fun delayUntilDataValid() {
        // seedCollection is a SharedFlow with replay 1, so first() won't complete until it's
        // emitted at least once.
        seedCollection.first()
    }

    suspend fun createSeed(details: SeedDetails): Int {
        Log.d(TAG, "ENTER createSeed: $details")

        // NOTE: we can't rely on the incoming coroutine context to remain active for the entire
        // duration of validating this action and operating on the repository. As such, switch to
        // the repository owner context immediately, to ensure that this action will complete, even
        // in the event of cancellation of the originating context.
        val id: Int
        withContext(repositoryOwnerScope.coroutineContext) {
            val newSeedEntryBuilder = createSeedEntryBuilderFromSeed(details)
            val newSeedRecordBuilder = SeedRecord.newBuilder().setSeed(newSeedEntryBuilder)

            mutex.withLock {
                check(!isFull.value) { "Seed repository is full; cannot add a new seed" }
                check(!seeds.value.containsKey(nextSeedId)) { "Seed repository already contains an entry for seed $nextSeedId" }
                newSeedRecordBuilder.seedId = nextSeedId
                id = nextSeedId

                context.seedCollectionDataStore.updateData {
                    it.toBuilder().addSeeds(newSeedRecordBuilder).apply {
                        nextId = ++nextSeedId
                    }.build()
                }
            }

            _changes.emit(
                ChangeNotification(
                    ChangeNotification.Category.SEED,
                    ChangeNotification.Type.CREATE,
                    id
                )
            )
        }

        Log.d(TAG, "EXIT createSeed: $details -> $id")
        return id
    }

    suspend fun updateSeed(id: Int, details: SeedDetails) {
        Log.d(TAG, "ENTER updateSeed: $details")

        // NOTE: we can't rely on the incoming coroutine context to remain active for the entire
        // duration of validating this action and operating on the repository. As such, switch to
        // the repository owner context immediately, to ensure that this action will complete, even
        // in the event of cancellation of the originating context.
        withContext(repositoryOwnerScope.coroutineContext) {
            val newSeedEntryBuilder = createSeedEntryBuilderFromSeed(details)

            mutex.withLock {
                context.seedCollectionDataStore.updateData {
                    val i = it.seedsList.indexOfFirst { seed -> seed.seedId == id }
                    check(i != -1) { "Seed repository does not contain an entry for seed $id" }
                    val newSeedRecordBuilder = it.seedsList[i].toBuilder().setSeed(newSeedEntryBuilder)
                    it.toBuilder().setSeeds(i, newSeedRecordBuilder).build()
                }
            }

            _changes.emit(
                ChangeNotification(
                    ChangeNotification.Category.SEED,
                    ChangeNotification.Type.UPDATE,
                    id
                )
            )
        }

        Log.d(TAG, "EXIT updateSeed: $details")
    }

    suspend fun deleteSeed(id: Int) {
        Log.d(TAG, "ENTER deleteSeed: $id")

        // NOTE: we can't rely on the incoming coroutine context to remain active for the entire
        // duration of validating this action and operating on the repository. As such, switch to
        // the repository owner context immediately, to ensure that this action will complete, even
        // in the event of cancellation of the originating context.
        withContext(repositoryOwnerScope.coroutineContext) {
            mutex.withLock {
                context.seedCollectionDataStore.updateData {
                    val i = it.seedsList.indexOfFirst { seed -> seed.seedId == id }
                    require(i != -1) { "Seed repository does not contain an entry for seed $id" }
                    it.toBuilder().removeSeeds(i).build()
                }
            }

            _changes.emit(
                ChangeNotification(
                    ChangeNotification.Category.SEED,
                    ChangeNotification.Type.DELETE,
                    id
                )
            )
        }

        Log.d(TAG, "EXIT deleteSeed: $id")
    }

    suspend fun deleteAllSeeds() {
        Log.d(TAG, "ENTER deleteAllSeeds")

        // NOTE: we can't rely on the incoming coroutine context to remain active for the entire
        // duration of validating this action and operating on the repository. As such, switch to
        // the repository owner context immediately, to ensure that this action will complete, even
        // in the event of cancellation of the originating context.
        withContext(repositoryOwnerScope.coroutineContext) {
            mutex.withLock {
                context.seedCollectionDataStore.updateData {
                    it.toBuilder().clearSeeds().build()
                }
            }

            _changes.emit(
                ChangeNotification(
                    ChangeNotification.Category.SEED,
                    ChangeNotification.Type.DELETE,
                    null
                )
            )
        }

        Log.d(TAG, "EXIT deleteAllSeeds")
    }

    suspend fun authorizeSeedForUid(id: Int, uid: Int, purpose: Authorization.Purpose): Int {
        require(uid > Authorization.INVALID_UID) { "UID $uid is invalid" }
        Log.d(TAG, "ENTER authorizeSeedForUid")

        val authToken: Int

        // NOTE: we can't rely on the incoming coroutine context to remain active for the entire
        // duration of validating this action and operating on the repository. As such, switch to
        // the repository owner context immediately, to ensure that this action will complete, even
        // in the event of cancellation of the originating context.
        withContext(repositoryOwnerScope.coroutineContext) {
            val newAuthorizationEntryBuilder = AuthorizationEntry.newBuilder().apply {
                this.uid = uid
                this.purpose = purpose.ordinal
            }

            mutex.withLock {
                var assignedAuthToken = nextAuthToken
                newAuthorizationEntryBuilder.authToken = nextAuthToken

                context.seedCollectionDataStore.updateData {
                    val i = it.seedsList.indexOfFirst { sr -> sr.seedId == id }
                    require(i != -1) { "Seed repository does not contain an entry for seed $id" }
                    val existingAuthRecord = it.seedsList[i].authorizationsList.firstOrNull { ae -> ae.uid == uid }
                    if (existingAuthRecord != null) {
                        // UID is already authorized for this seed; don't change anything
                        assignedAuthToken = existingAuthRecord.authToken
                        return@updateData it
                    }
                    val newSeedRecordBuilder = it.seedsList[i].toBuilder().addAuthorizations(newAuthorizationEntryBuilder)
                    it.toBuilder().setSeeds(i, newSeedRecordBuilder).apply {
                        nextAuthToken = ++this@SeedRepository.nextAuthToken
                    }.build()
                }

                authToken = assignedAuthToken
            }

            _changes.emit(
                ChangeNotification(
                    ChangeNotification.Category.AUTHORIZATION,
                    ChangeNotification.Type.CREATE,
                    authToken
                )
            )
        }

        Log.d(TAG, "EXIT authorizeSeedForUid: $id/$uid -> $authToken")

        return authToken
    }

    suspend fun deauthorizeSeed(id: Int, authToken: Int) {
        Log.d(TAG, "ENTER deauthorizeSeed: $id/$authToken")

        // NOTE: we can't rely on the incoming coroutine context to remain active for the entire
        // duration of validating this action and operating on the repository. As such, switch to
        // the repository owner context immediately, to ensure that this action will complete, even
        // in the event of cancellation of the originating context.
        withContext(repositoryOwnerScope.coroutineContext) {
            mutex.withLock {
                context.seedCollectionDataStore.updateData {
                    val i = it.seedsList.indexOfFirst { sr -> sr.seedId == id }
                    require(i != -1) { "Seed repository does not contain an entry for seed $id" }
                    val j = it.seedsList[i].authorizationsList.indexOfFirst { ae -> ae.authToken == authToken }
                    require(j != -1) { "AuthToken $authToken not found for seed $id" }
                    val newSeedRecordBuilder = it.seedsList[i].toBuilder().removeAuthorizations(j)
                    it.toBuilder().setSeeds(i, newSeedRecordBuilder).build()
                }
            }

            _changes.emit(
                ChangeNotification(
                    ChangeNotification.Category.AUTHORIZATION,
                    ChangeNotification.Type.DELETE,
                    authToken
                )
            )
        }

        Log.d(TAG, "EXIT deauthorizeSeed: $id/$authToken")
    }

    suspend fun addKnownAccountForSeed(id: Int, account: Account): Int {
        require(account.id == Account.INVALID_ACCOUNT_ID) { "Accound ID must be invalid" }
        Log.d(TAG, "ENTER addKnownAccountForSeed")

        val accountId: Int

        // NOTE: we can't rely on the incoming coroutine context to remain active for the entire
        // duration of validating this action and operating on the repository. As such, switch to
        // the repository owner context immediately, to ensure that this action will complete, even
        // in the event of cancellation of the originating context.
        withContext(repositoryOwnerScope.coroutineContext) {
            val newKnownAccountEntryBuilder = KnownAccountEntry.newBuilder().apply {
                purpose = account.purpose.ordinal
                bip32Uri = account.bip32DerivationPathUri.toString()
                publicKey = ByteString.copyFrom(account.publicKey)
                if (account.name != null) {
                    name = account.name
                }
                isUserWallet = account.isUserWallet
                isValid = account.isValid
            }

            mutex.withLock {
                var assignedAccountId = nextAccountId
                newKnownAccountEntryBuilder.accountId = nextAccountId

                context.seedCollectionDataStore.updateData {
                    val i = it.seedsList.indexOfFirst { sr -> sr.seedId == id }
                    require(i != -1) { "Seed repository does not contain an entry for seed $id" }
                    val bip32Uri = account.bip32DerivationPathUri.toString()
                    val existingKnownAccountEntry = it.seedsList[i].knownAccountsList.firstOrNull { kae ->
                        kae.bip32Uri == bip32Uri && kae.purpose == account.purpose.ordinal
                    }
                    if (existingKnownAccountEntry != null) {
                        // Bip32 path is already known for this seed; don't change anything
                        assignedAccountId = existingKnownAccountEntry.accountId
                        return@updateData it
                    }
                    val newSeedRecordBuilder = it.seedsList[i].toBuilder().addKnownAccounts(newKnownAccountEntryBuilder)
                    it.toBuilder().setSeeds(i, newSeedRecordBuilder).apply {
                        nextAccountId = ++this@SeedRepository.nextAccountId
                    }.build()
                }

                accountId = assignedAccountId
            }

            _changes.emit(
                ChangeNotification(
                    ChangeNotification.Category.ACCOUNT,
                    ChangeNotification.Type.CREATE,
                    accountId
                )
            )
        }

        Log.d(TAG, "EXIT addKnownAccountForSeed")

        return accountId
    }

    suspend fun updateKnownAccountForSeed(id: Int, account: Account) {
        require(account.id != Account.INVALID_ACCOUNT_ID) { "Account ID must be valid" }
        Log.d(TAG, "ENTER updateKnownAccountForSeed")

        // NOTE: we can't rely on the incoming coroutine context to remain active for the entire
        // duration of validating this action and operating on the repository. As such, switch to
        // the repository owner context immediately, to ensure that this action will complete, even
        // in the event of cancellation of the originating context.
        withContext(repositoryOwnerScope.coroutineContext) {
            val newKnownAccountEntryBuilder = KnownAccountEntry.newBuilder().apply {
                accountId = account.id
                purpose = account.purpose.ordinal
                bip32Uri = account.bip32DerivationPathUri.toString()
                publicKey = ByteString.copyFrom(account.publicKey)
                if (account.name != null) {
                    name = account.name
                }
                isUserWallet = account.isUserWallet
                isValid = account.isValid
            }

            mutex.withLock {
                context.seedCollectionDataStore.updateData {
                    val i = it.seedsList.indexOfFirst { sr -> sr.seedId == id }
                    require(i != -1) { "Seed repository does not contain an entry for seed $id" }
                    val j = it.seedsList[i].knownAccountsList.indexOfFirst { kae -> kae.accountId == account.id }
                    require(j != -1) { "Seed repository does not contain an entry for account ${account.id} in seed $id" }
                    val newSeedRecordBuilder = it.seedsList[i].toBuilder().setKnownAccounts(j, newKnownAccountEntryBuilder)
                    it.toBuilder().setSeeds(i, newSeedRecordBuilder).build()
                }
            }

            _changes.emit(
                ChangeNotification(
                    ChangeNotification.Category.ACCOUNT,
                    ChangeNotification.Type.UPDATE,
                    account.id
                )
            )
        }

        Log.d(TAG, "EXIT updateKnownAccountForSeed")
    }

    private fun createSeedEntryBuilderFromSeed(details: SeedDetails): SeedEntry.Builder =
        SeedEntry.newBuilder().apply {
            seed = ByteString.copyFrom(details.seed)
            addAllSeedPhraseWordIndices(details.seedPhraseWordIndices)
            if (details.name != null) {
                name = details.name
            }
            pin = details.pin
            unlockWithBiometrics = details.unlockWithBiometrics
        }
}