/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.solanamobile.fakewallet.databinding.ActivityMainBinding
import com.solanamobile.seedvault.Wallet
import com.solanamobile.seedvault.WalletContractV1
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private var shownMessageIndex: Int? = null
    private var pendingEvent: ViewModelEvent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pendingEvent = savedInstanceState?.getParcelable(KEY_PENDING_EVENT)

        val seedListadapter = SeedListAdapter(
            lifecycleScope = lifecycleScope,
            implementationLimits = viewModel.uiState.map { uiState ->
                SeedListAdapter.ImplementationLimits(
                    uiState.maxSigningRequests,
                    uiState.maxRequestedSignatures,
                    uiState.firstRequestedPublicKey,
                    uiState.lastRequestedPublicKey
                )
            },
            onSignTransaction = { seed, account ->
                viewModel.signFakeTransaction(seed.authToken, account)
            },
            onSignMessage = { seed, account ->
                viewModel.signFakeMessage(seed.authToken, account)
            },
            onAccountNameUpdated = { seed, account, name ->
                viewModel.updateAccountName(
                    seed.authToken,
                    account.id,
                    name.ifBlank { null }
                )
            },
            onDeauthorizeSeed = { seed ->
                viewModel.deauthorizeSeed(seed.authToken)
            },
            onRequestPublicKeys = { seed ->
                viewModel.requestPublicKeys(seed.authToken)
            },
            onSignMaxTransactionsWithMaxSignatures = { seed ->
                viewModel.signMaxTransactionsWithMaxSignatures(seed.authToken)
            },
            onSignMaxMessagesWithMaxSignatures = { seed ->
                viewModel.signMaxMessagesWithMaxSignatures(seed.authToken)
            }
        )
        val remainingSeedsAdapter = HasUnauthorizedSeedsAdapter(
            onAuthorizeNewSeed = {
                viewModel.authorizeNewSeed()
            }
        )
        val createNewSeedAdapter = CreateSeedAdapter(
            onCreateNewSeed = {
                viewModel.createNewSeed()
            }
        )
        val importExistingSeedAdapter = ImportSeedAdapter(
            onImportExistingSeed = {
                viewModel.importExistingSeed()
            }
        )
        val implementationLimitsAdapter = ImplementationLimitsAdapter(
            onTestExceedLimit = {
                viewModel.exceedImplementationLimit(it)
            }
        )
        val concatAdapter = ConcatAdapter(
            ConcatAdapter.Config.Builder().setStableIdMode(
                ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS
            ).build(),
            seedListadapter,
            remainingSeedsAdapter,
            createNewSeedAdapter,
            importExistingSeedAdapter,
            implementationLimitsAdapter
        )
        binding.recyclerviewSeeds.adapter = concatAdapter
        binding.recyclerviewSeeds.addItemDecoration(DividerItemDecoration(
            binding.recyclerviewSeeds.context, DividerItemDecoration.VERTICAL))

        lifecycle.coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    seedListadapter.submitList(uiState.seeds)
                    remainingSeedsAdapter.submitList(listOf(uiState.hasUnauthorizedSeeds))
                    implementationLimitsAdapter.submitList(uiState.implementationLimits.map { entry ->
                        entry.key to entry.value
                    })

                    if (uiState.messages.isNotEmpty()) {
                        val i = shownMessageIndex
                        if (i == null) {
                            val m = uiState.messages.first()
                            shownMessageIndex = m.first
                            Snackbar.make(binding.root, m.second, Snackbar.LENGTH_SHORT)
                                .addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                                    override fun onDismissed(
                                        transientBottomBar: Snackbar?,
                                        event: Int
                                    ) {
                                        shownMessageIndex = null
                                        viewModel.messageShown(m.first)
                                    }
                                }).show()
                        }
                    }
                }
            }
        }

        lifecycle.coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewModelEvents.collect { event ->
                    check(pendingEvent == null) { "Received a request while another is pending" }
                    when (event) {
                        is ViewModelEvent.AuthorizeNewSeed -> {
                            val i = Wallet.authorizeSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
                            @Suppress("deprecation")
                            startActivityForResult(i, REQUEST_AUTHORIZE_SEED_ACCESS)
                            pendingEvent = event
                        }
                        is ViewModelEvent.CreateNewSeed -> {
                            val i = Wallet.createSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
                            @Suppress("deprecation")
                            startActivityForResult(i, REQUEST_CREATE_NEW_SEED)
                            pendingEvent = event
                        }
                        is ViewModelEvent.ImportExistingSeed -> {
                            val i = Wallet.importSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
                            @Suppress("deprecation")
                            startActivityForResult(i, REQUEST_IMPORT_EXISTING_SEED)
                            pendingEvent = event
                        }
                        is ViewModelEvent.DeauthorizeSeed -> {
                            try {
                                Wallet.deauthorizeSeed(this@MainActivity, event.authToken)
                                Log.d(TAG, "Seed ${event.authToken} deauthorized")
                                viewModel.onDeauthorizeSeedSuccess(event)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to deauthorize seed", e)
                                viewModel.onDeauthorizeSeedFailure(event, -1)
                            }
                        }
                        is ViewModelEvent.UpdateAccountName -> {
                            try {
                                Wallet.updateAccountName(this@MainActivity, event.authToken,
                                    event.accountId, event.name)
                                Log.d(TAG, "Account name updated (to '${event.name})'")
                                viewModel.onUpdateAccountNameSuccess(event)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update account name", e)
                                viewModel.onUpdateAccountNameFailure(event, -1)
                            }
                        }
                        is ViewModelEvent.SignTransactions -> {
                            val i = Wallet.signTransactions(
                                event.authToken, event.transactions)
                            @Suppress("deprecation")
                            startActivityForResult(i, REQUEST_SIGN_TRANSACTIONS)
                            pendingEvent = event
                        }
                        is ViewModelEvent.SignMessages -> {
                            val i = Wallet.signMessages(
                                event.authToken, event.messages)
                            @Suppress("deprecation")
                            startActivityForResult(i, REQUEST_SIGN_MESSAGES)
                            pendingEvent = event
                        }
                        is ViewModelEvent.RequestPublicKeys ->  {
                            val i = Wallet.requestPublicKeys(
                                event.authToken, event.derivationPaths)
                            @Suppress("deprecation")
                            startActivityForResult(i, REQUEST_GET_PUBLIC_KEYS)
                            pendingEvent = event
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("deprecation")
        super.onActivityResult(requestCode, resultCode, data)

        val event = pendingEvent!!
        pendingEvent = null

        when (requestCode) {
            REQUEST_AUTHORIZE_SEED_ACCESS -> {
                check(event is ViewModelEvent.AuthorizeNewSeed)
                try {
                    val authToken = Wallet.onAuthorizeSeedResult(resultCode, data)
                    Log.d(TAG, "Seed authorized, AuthToken=$authToken")
                    viewModel.onAddSeedSuccess(event, authToken)
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Seed authorization failed", e)
                    viewModel.onAddSeedFailure(event, resultCode)
                }
            }
            REQUEST_CREATE_NEW_SEED -> {
                check(event is ViewModelEvent.CreateNewSeed)
                try {
                    val authToken = Wallet.onCreateSeedResult(resultCode, data)
                    Log.d(TAG, "Seed created, AuthToken=$authToken")
                    viewModel.onAddSeedSuccess(event, authToken)
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Seed creation failed", e)
                    viewModel.onAddSeedFailure(event, resultCode)
                }
            }
            REQUEST_IMPORT_EXISTING_SEED -> {
                check(event is ViewModelEvent.ImportExistingSeed)
                try {
                    val authToken = Wallet.onImportSeedResult(resultCode, data)
                    Log.d(TAG, "Seed imported, AuthToken=$authToken")
                    viewModel.onAddSeedSuccess(event, authToken)
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Seed import failed", e)
                    viewModel.onAddSeedFailure(event, resultCode)
                }
            }
            REQUEST_SIGN_TRANSACTIONS -> {
                check(event is ViewModelEvent.SignTransactions)
                try {
                    val result = Wallet.onSignTransactionsResult(resultCode, data)
                    Log.d(TAG, "Transaction signed: signatures=$result")
                    viewModel.onSignTransactionsSuccess(event, result)
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Transaction signing failed", e)
                    viewModel.onSignTransactionsFailure(event, resultCode)
                }
            }
            REQUEST_SIGN_MESSAGES -> {
                check(event is ViewModelEvent.SignMessages)
                try {
                    val result = Wallet.onSignMessagesResult(resultCode, data)
                    Log.d(TAG, "Message signed: signatures=$result")
                    viewModel.onSignMessagesSuccess(event, result)
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Message signing failed", e)
                    viewModel.onSignMessagesFailure(event, resultCode)
                }
            }
            REQUEST_GET_PUBLIC_KEYS -> {
                check(event is ViewModelEvent.RequestPublicKeys)
                try {
                    val result = Wallet.onRequestPublicKeysResult(resultCode, data)
                    Log.d(TAG, "Public key retrieved: publicKey=$result")
                    viewModel.onRequestPublicKeysSuccess(event, result)
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Public Key retrieval failed", e)
                    viewModel.onRequestPublicKeysFailure(event, resultCode)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_PENDING_EVENT, pendingEvent)
    }

    companion object {
        private val TAG = MainActivity::class.simpleName
        private const val REQUEST_AUTHORIZE_SEED_ACCESS = 0
        private const val REQUEST_CREATE_NEW_SEED = 1
        private const val REQUEST_IMPORT_EXISTING_SEED = 2
        private const val REQUEST_SIGN_TRANSACTIONS = 3
        private const val REQUEST_SIGN_MESSAGES = 4
        private const val REQUEST_GET_PUBLIC_KEYS = 5
        private const val KEY_PENDING_EVENT = "pendingEvent"
    }
}
