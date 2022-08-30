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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                    when (event) {
                        is ViewModelEvent.AuthorizeNewSeed -> {
                            val i = Wallet.authorizeSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
                            @Suppress("deprecation")
                            startActivityForResult(i, REQUEST_AUTHORIZE_SEED_ACCESS)
                        }
                        is ViewModelEvent.DeauthorizeSeed -> {
                            try {
                                Wallet.deauthorizeSeed(this@MainActivity, event.authToken)
                                Log.d(TAG, "Seed ${event.authToken} deauthorized")
                                viewModel.onDeauthorizeSeedSuccess()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to deauthorize seed", e)
                                viewModel.onDeauthorizeSeedFailure(-1)
                            }
                        }
                        is ViewModelEvent.UpdateAccountName -> {
                            try {
                                Wallet.updateAccountName(this@MainActivity, event.authToken,
                                    event.accountId, event.name)
                                Log.d(TAG, "Account name updated (to '${event.name})'")
                                viewModel.onUpdateAccountNameSuccess()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update account name", e)
                                viewModel.onUpdateAccountNameFailure(-1)
                            }
                        }
                        is ViewModelEvent.SignTransactions -> {
                            val i = Wallet.signTransactions(
                                event.authToken, event.transactions)
                            @Suppress("deprecation")
                            startActivityForResult(i, REQUEST_SIGN_TRANSACTIONS)
                        }
                        is ViewModelEvent.SignMessages -> {
                            val i = Wallet.signMessages(
                                event.authToken, event.messages)
                            @Suppress("deprecation")
                            startActivityForResult(i, REQUEST_SIGN_MESSAGES)
                        }
                        is ViewModelEvent.RequestPublicKeys ->  {
                            val i = Wallet.requestPublicKeys(
                                event.authToken, event.derivationPaths)
                            @Suppress("deprecation")
                            startActivityForResult(i, REQUEST_GET_PUBLIC_KEYS)
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("deprecation")
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_AUTHORIZE_SEED_ACCESS -> {
                try {
                    val authToken = Wallet.onAuthorizeSeedResult(resultCode, data)
                    Log.d(TAG, "Seed authorized, AuthToken=$authToken")
                    viewModel.onAuthorizeNewSeedSuccess(authToken)
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Seed authorization failed", e)
                    viewModel.onAuthorizeNewSeedFailure(resultCode)
                }
            }
            REQUEST_SIGN_TRANSACTIONS -> {
                try {
                    val result = Wallet.onSignTransactionsResult(resultCode, data)
                    Log.d(TAG, "Transaction signed: signatures=$result")
                    viewModel.onSignTransactionsSuccess(result)
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Transaction signing failed", e)
                    viewModel.onSignTransactionsFailure(resultCode)
                }
            }
            REQUEST_SIGN_MESSAGES -> {
                try {
                    val result = Wallet.onSignMessagesResult(resultCode, data)
                    Log.d(TAG, "Message signed: signatures=$result")
                    viewModel.onSignMessagesSuccess(result)
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Message signing failed", e)
                    viewModel.onSignMessagesFailure(resultCode)
                }
            }
            REQUEST_GET_PUBLIC_KEYS -> {
                try {
                    val result = Wallet.onRequestPublicKeysResult(resultCode, data)
                    Log.d(TAG, "Public key retrieved: publicKey=$result")
                    viewModel.onRequestPublicKeysSuccess(result)
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Transaction signing failed", e)
                    viewModel.onRequestPublicKeysFailure(resultCode)
                }
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.simpleName
        private const val REQUEST_AUTHORIZE_SEED_ACCESS = 0
        private const val REQUEST_SIGN_TRANSACTIONS = 1
        private const val REQUEST_SIGN_MESSAGES = 2
        private const val REQUEST_GET_PUBLIC_KEYS = 3
    }
}