/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import com.solanamobile.fakewallet.R
import com.solanamobile.seedvault.Wallet
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.ui.apptheme.Sizes
import com.solanamobile.ui.apptheme.SolanaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private var pendingEvent: ViewModelEvent? = null
    private var requestCode: Int? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        pendingEvent = savedInstanceState?.getParcelable(KEY_PENDING_EVENT)

        enableEdgeToEdge()
        setContent {
            SolanaTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState = viewModel.uiState.collectAsState().value
                    val snackbarHostState = remember {
                        SnackbarHostState()
                    }
                    if (uiState.messages.isNotEmpty()) {
                        val message = uiState.messages.first()
                        LaunchedEffect(key1 = message) {
                            when (
                                snackbarHostState.showSnackbar(
                                    message = message.second,
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Short
                                )
                            ) {
                                SnackbarResult.Dismissed -> {
                                    viewModel.messageShown(message.first)
                                }

                                SnackbarResult.ActionPerformed -> {
                                    viewModel.messageShown(message.first)
                                }
                            }
                        }
                    }
                    Scaffold(
                        snackbarHost = {
                            SnackbarHost(
                                hostState = snackbarHostState,
                            )
                        },
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(stringResource(id = R.string.app_name))
                                }
                            )
                        }
                    ) { contentPadding ->
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding)
                        ) {
                            uiState.seeds.forEach {
                                item {
                                    SeedDetails(
                                        seed = it,
                                        implementationLimits = ImplementationLimits(
                                            uiState.maxSigningRequests,
                                            uiState.maxRequestedSignatures,
                                            uiState.firstRequestedPublicKey,
                                            uiState.lastRequestedPublicKey
                                        ),
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
                                        onRequestOpenPublicKeys = { seed ->
                                            viewModel.requestPermissionedPublicKeys(seed.authToken)
                                        },
                                        onSignMaxTransactionsWithMaxSignatures = { seed ->
                                            viewModel.signMaxTransactionsWithMaxSignatures(seed.authToken)
                                        },
                                        onSignPermissionedAccountTransactions = { seed ->
                                            viewModel.signPermissionedAccountTransactions(
                                                authToken = seed.authToken,
                                            )
                                        },
                                        onSignMaxMessagesWithMaxSignatures = { seed ->
                                            viewModel.signMaxMessagesWithMaxSignatures(seed.authToken)
                                        },
                                        onSignPermissionedAccountMessages = { seed->
                                            viewModel.signPermissionedAccountMessages(authToken = seed.authToken)
                                        }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = Sizes.dp16)
                                    )
                                }
                            }
                            item {
                                UnauthorizedSeeds(
                                    hasUnauthorizedSeeds = uiState.hasUnauthorizedSeeds,
                                    onAuthorizeNewSeed = { viewModel.authorizeNewSeed() }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = Sizes.dp16)
                                )
                            }
                            item {
                                CreateSeed {
                                    viewModel.createNewSeed()
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = Sizes.dp16)
                                )
                            }
                            item {
                                ImportSeed {
                                    viewModel.importExistingSeed()
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = Sizes.dp16)
                                )
                            }
                            uiState.implementationLimits.map {
                                item {
                                    ImplementationLimits(implementationLimit = it.key to it.value) {
                                        viewModel.exceedImplementationLimit(it)
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = Sizes.dp16)
                                    )
                                }
                            }
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
                            val i =
                                Wallet.authorizeSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
                            requestCode = REQUEST_AUTHORIZE_SEED_ACCESS
                            seedVaultActivityResultLauncher.launch(i)
                            pendingEvent = event
                        }

                        is ViewModelEvent.CreateNewSeed -> {
                            val i =
                                Wallet.createSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
                            requestCode = REQUEST_CREATE_NEW_SEED
                            seedVaultActivityResultLauncher.launch(i)
                            pendingEvent = event
                        }

                        is ViewModelEvent.ImportExistingSeed -> {
                            val i =
                                Wallet.importSeed(WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
                            requestCode = REQUEST_IMPORT_EXISTING_SEED
                            seedVaultActivityResultLauncher.launch(i)
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
                                Wallet.updateAccountName(
                                    this@MainActivity, event.authToken,
                                    event.accountId, event.name
                                )
                                Log.d(TAG, "Account name updated (to '${event.name})'")
                                viewModel.onUpdateAccountNameSuccess(event)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update account name", e)
                                viewModel.onUpdateAccountNameFailure(event, -1)
                            }
                        }

                        is ViewModelEvent.SignTransactions -> {
                            val i = Wallet.signTransactions(
                                event.authToken, event.transactions
                            )
                            requestCode = REQUEST_SIGN_TRANSACTIONS
                            seedVaultActivityResultLauncher.launch(i)
                            pendingEvent = event
                        }

                        is ViewModelEvent.SignMessages -> {
                            val i = Wallet.signMessages(
                                event.authToken, event.messages
                            )
                            requestCode = REQUEST_SIGN_MESSAGES
                            seedVaultActivityResultLauncher.launch(i)
                            pendingEvent = event
                        }

                        is ViewModelEvent.RequestPublicKeys -> {
                            val i = Wallet.requestPublicKeys(
                                event.authToken, event.derivationPaths
                            )
                            requestCode = REQUEST_GET_PUBLIC_KEYS
                            seedVaultActivityResultLauncher.launch(i)
                            pendingEvent = event
                        }
                    }
                }
            }
        }
    }

    private var seedVaultActivityResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { res ->
            val resultCode = res.resultCode
            val data = res.data

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
