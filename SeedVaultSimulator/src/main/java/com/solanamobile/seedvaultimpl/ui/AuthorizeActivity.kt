/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvaultimpl.ui.authorize.AuthorizeContents
import com.solanamobile.seedvaultimpl.ui.authorize.AuthorizeViewModel
import com.solanamobile.seedvaultimpl.ui.authorizeinfo.AuthorizeInfoContents
import com.solanamobile.seedvaultimpl.ui.selectseed.SelectSeedContents
import com.solanamobile.seedvaultimpl.ui.selectseed.SelectSeedViewModel
import com.solanamobile.ui.apptheme.SolanaTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthorizeActivity : ComponentActivity() {
    private val authorizeCommonViewModel: AuthorizeCommonViewModel by viewModels()
    private val authorizeViewModel: AuthorizeViewModel by viewModels(extrasProducer = {
        defaultViewModelCreationExtras.withCreationCallback<AuthorizeViewModel.Factory> { factory ->
            factory.create(authorizeCommonViewModel)
        }
    })
    private val selectSeedViewModel: SelectSeedViewModel by viewModels(extrasProducer = {
        defaultViewModelCreationExtras.withCreationCallback<SelectSeedViewModel.Factory> { factory ->
            factory.create(authorizeCommonViewModel)
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
        setContent {
            val navController = rememberNavController()

            SolanaTheme {
                NavHost(
                    modifier = Modifier.fillMaxSize(),
                    navController = navController,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    startDestination = "auth"
                ) {
                    composable("auth") {
                        val authorizationViewState = authorizeViewModel.uiState.collectAsState().value
                        if (authorizationViewState.requireAuthentication) {
                            AuthorizeContents(
                                authorizationViewState = authorizationViewState,
                                onMessageShown = { authorizeViewModel.onMessageShown() },
                                onCheckEnteredPIN = { authorizeViewModel.checkEnteredPIN(it) },
                                onBiometricAuthorizationSuccess = { authorizeViewModel.biometricAuthorizationSuccess() },
                                onBiometricsAuthorizationFailed = { authorizeViewModel.biometricsAuthorizationFailed() },
                                onCancel = { authorizeViewModel.cancel() },
                                onNavigateAuthorizeInfo = {
                                    navController.navigate("authInfo")
                                },
                                onNavigateSelectSeedDialog = {
                                    navController.navigate("seedSelector")
                                }
                            )
                        } else {
                            LaunchedEffect(key1 = Unit) {
                                authorizeViewModel.biometricAuthorizationSuccess()
                            }
                        }
                    }

                    composable("authInfo") {
                        AuthorizeInfoContents(
                            onNavigateBack = {
                                navController.navigateUp()
                            }
                        )
                    }

                    composable("seedSelector") {
                        val uiState = selectSeedViewModel.selectSeedUiState.collectAsState().value
                        SelectSeedContents(
                            uiState = uiState,
                            selectSeedForAuthorization = { selectSeedViewModel.selectSeedForAuthorization() },
                            onNavigateBack = {
                                navController.navigateUp()
                            },
                            completeAuthorizationWithError = {
                                authorizeCommonViewModel.completeAuthorizationWithError(
                                    WalletContractV1.RESULT_UNSPECIFIED_ERROR
                                )
                            },
                            onSetSelectedSeed = {
                                selectSeedViewModel.setSelectedSeed(it)
                            }
                        )
                    }
                }
            }
        }

        // Default result code to RESULT_CANCELED; all valid and error return paths will replace
        // this with a more appropriate result code.
        setResult(RESULT_CANCELED)

        // Mapping callingActivity (if one exists) to UID
        val packageName = callingActivity?.packageName ?: ""
        val uid = try {
            val pmUid = packageManager.getPackageUid(packageName, 0)
            Log.d(TAG, "Mapped package $packageName to UID $pmUid")
            pmUid
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Requester package UID not found", e)
            null
        }

        authorizeCommonViewModel.setRequest(callingActivity, uid, intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authorizeCommonViewModel.events.collect { event ->
                    when (event.event) {
                        AuthorizeEventType.COMPLETE -> {
                            Log.i(TAG, "Returning result=${event.resultCode}/intent=${event.data} from AuthorizeActivity")
                            setResult(event.resultCode!!, event.data)
                            finish()
                        }
                        AuthorizeEventType.START_AUTHORIZATION -> {
                            // Do nothing.
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = AuthorizeActivity::class.simpleName
    }
}