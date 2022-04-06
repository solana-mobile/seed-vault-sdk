/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DividerItemDecoration
import com.solanamobile.fakewallet.databinding.ActivityMainBinding
import com.solanamobile.seedvault.Wallet
import com.solanamobile.seedvault.WalletContractV1
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
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
            onSignTransaction = { seed, account ->
                viewModel.signFakeTransaction(seed.authToken, account)
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
            onRequestPublicKeyForM1000H = { seed ->
                viewModel.requestPublicKeyForM1000H(seed.authToken)
            }
        )
        val remainingSeedsAdapter = HasUnauthorizedSeedsAdapter(
            onAuthorizeNewSeed = {
                viewModel.authorizeNewSeed()
            }
        )
        val concatAdapter = ConcatAdapter(
            ConcatAdapter.Config.Builder().setStableIdMode(
                ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS
            ).build(),
            seedListadapter,
            remainingSeedsAdapter
        )
        binding.recyclerviewSeeds.adapter = concatAdapter
        binding.recyclerviewSeeds.addItemDecoration(DividerItemDecoration(
            binding.recyclerviewSeeds.context, DividerItemDecoration.VERTICAL))

        lifecycle.coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    seedListadapter.submitList(uiState.seeds)
                    remainingSeedsAdapter.submitList(listOf(uiState.hasUnauthorizedSeeds))

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
                        is ViewModelEvent.SignTransaction -> {
                            val i = Wallet.signTransaction(
                                event.authToken, event.derivationPath, event.transaction)
                            startActivityForResult(i, REQUEST_SIGN_TRANSACTION)
                        }
                        is ViewModelEvent.RequestPublicKey ->  {
                            val i = Wallet.requestPublicKey(
                                event.authToken, event.derivationPath)
                            startActivityForResult(i, REQUEST_GET_PUBLIC_KEY)
                            val i2 = Wallet.signTransaction(
                                event.authToken, event.derivationPath, ByteArray(1))
                            startActivityForResult(i2, REQUEST_SIGN_TRANSACTION)
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
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
            REQUEST_SIGN_TRANSACTION -> {
                try {
                    val result = Wallet.onSignTransactionResult(resultCode, data)
                    Log.d(TAG, "Transaction signed: " +
                            "signature=${result.signature.asUByteArray().toList()}, " +
                            "derivationPath=${result.resolvedDerivationPath}")
                    viewModel.onSignTransactionSuccess(result.signature)
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Transaction signing failed", e)
                    viewModel.onSignTransactionFailure(resultCode)
                }
            }
            REQUEST_GET_PUBLIC_KEY -> {
                try {
                    val result = Wallet.onRequestPublicKeyResult(resultCode, data)
                    Log.d(TAG, "Public key retrieved: " +
                            "publicKey=${result.publicKey.asUByteArray().toList()}, " +
                            "derivationPath=${result.resolvedDerivationPath}")
                    viewModel.onRequestPublicKeySuccess(result.publicKey)
                } catch (e: Wallet.ActionFailedException) {
                    Log.e(TAG, "Transaction signing failed", e)
                    viewModel.onRequestPublicKeyFailure(resultCode)
                }
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.simpleName
        private const val REQUEST_AUTHORIZE_SEED_ACCESS = 0
        private const val REQUEST_SIGN_TRANSACTION = 1
        private const val REQUEST_GET_PUBLIC_KEY = 2
    }
}