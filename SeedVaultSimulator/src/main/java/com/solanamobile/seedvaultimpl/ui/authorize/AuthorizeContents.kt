/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.authorize

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.core.graphics.drawable.toBitmap
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.ui.apptheme.Sizes

@Composable
fun AuthorizeContents(
    authorizationViewState: AuthorizeUiState,
    onMessageShown: () -> Unit,
    onCheckEnteredPIN: (String) -> Unit,
    onBiometricAuthorizationSuccess: () -> Unit,
    onBiometricsAuthorizationFailed: () -> Unit,
    onCancel: () -> Unit,
    onNavigateAuthorizeInfo: () -> Unit,
    onNavigateSelectSeedDialog: () -> Unit,
) {
    val snackbarHostState = remember {
        SnackbarHostState()
    }
    val context = LocalContext.current
    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    val message = authorizationViewState.message
    if (message != null) {
        LaunchedEffect(key1 = message) {
            when (
                snackbarHostState.showSnackbar(
                    message = message.toString(),
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )
            ) {
                SnackbarResult.Dismissed -> {
                    onMessageShown()
                }

                SnackbarResult.ActionPerformed -> {
                    onMessageShown()
                }
            }
        }
    }
    if (authorizationViewState.isPrivilegedApp) {
        PrivilegedAuthorizeContents(
            authorizationViewState = authorizationViewState,
            onCheckEnteredPIN = onCheckEnteredPIN,
            onBiometricAuthorizationSuccess = onBiometricAuthorizationSuccess,
            onBiometricsAuthorizationFailed = {
                onBiometricsAuthorizationFailed()
                vibratorManager.defaultVibrator.cancel()
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            },
            onCancel = onCancel,
        )
    } else {
        NonPrivilegedAuthorizeContents(
            authorizationViewState = authorizationViewState,
            onCheckEnteredPIN = onCheckEnteredPIN,
            onBiometricAuthorizationSuccess = onBiometricAuthorizationSuccess,
            onBiometricsAuthorizationFailed = {
                onBiometricsAuthorizationFailed()
                vibratorManager.defaultVibrator.cancel()
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            },
            onCancel = onCancel,
            onNavigateAuthorizeInfo = onNavigateAuthorizeInfo,
            onNavigateSelectSeedDialog = onNavigateSelectSeedDialog
        )
    }
    // This is a hack to show snackbar above bottomsheet.
    // https://stackoverflow.com/questions/78006372/jetpack-compose-snackbar-hidden-behind-bottomsheet
    SnackbarHost(
        modifier = Modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        hostState = snackbarHostState,
    ) {
        Popup(
            alignment = Alignment.TopCenter,
        ) {
            Snackbar(it)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PrivilegedAuthorizeContents(
    authorizationViewState: AuthorizeUiState,
    onCheckEnteredPIN: (String) -> Unit,
    onBiometricAuthorizationSuccess: () -> Unit,
    onBiometricsAuthorizationFailed: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    if (authorizationViewState.enablePIN) {
        val pin = rememberSaveable { mutableStateOf("") }
        AlertDialog(
            modifier = Modifier
                .padding(horizontal = Sizes.dp16)
                .semantics {
                    testTagsAsResourceId = true
                },
            title = {
                Text(text = stringResource(id = R.string.label_enter_pin))
            },
            text = {
                val keyboardController =
                    LocalSoftwareKeyboardController.current

                BasicTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizes.dp48)
                        .background(
                            MaterialTheme.colorScheme.inverseOnSurface,
                            CircleShape
                        )
                        .semantics {
                            testTag = "PinEntryField"
                        },
                    value = pin.value,
                    onValueChange = {
                        pin.value = it
                    },
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        onCheckEnteredPIN(pin.value)
                        keyboardController?.hide()
                    }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    maxLines = 1,
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .height(Sizes.dp48)
                                .padding(
                                    start = Sizes.dp16
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .padding(start = Sizes.dp16)
                                    .weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                innerTextField()
                            }
                        }
                    }
                )
            },
            onDismissRequest = onCancel,
            confirmButton = {
                TextButton(
                    enabled = pin.value.length >= 4,
                    onClick = {
                        onCheckEnteredPIN(pin.value)
                    }
                ) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancel
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            }
        )
    }

    if (authorizationViewState.enableBiometrics && !authorizationViewState.enablePIN) {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(android.R.attr.fingerprintAuthDrawable, typedValue, true)
        val drawable = AppCompatResources.getDrawable(context, typedValue.resourceId)

        AlertDialog(
            modifier = Modifier
                .height(197.dp)
                .width(178.dp)
                .semantics {
                    testTagsAsResourceId = true
                },
            shape = RoundedCornerShape(size = Sizes.dp32),
            text = {
                val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
                dialogWindowProvider?.window?.setGravity(Gravity.CENTER)

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(Sizes.dp56)
                            .border(
                                width = Sizes.dp4,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            modifier = Modifier
                                .height(Sizes.dp48)
                                .semantics {
                                    testTag = "AuthorizeFingerprint"
                                },
                            onClick = onBiometricAuthorizationSuccess
                        ) {
                            drawable?.let {
                                Icon(
                                    modifier = Modifier.size(Sizes.dp48),
                                    bitmap = it.toBitmap().asImageBitmap(),
                                    contentDescription = stringResource(id = R.string.authorize_fingerprint)
                                )
                            }
                        }
                    }
                    Text(
                        modifier = Modifier.padding(top = Sizes.dp16),
                        text = stringResource(id = R.string.label_authorize_fingerprint),
                        style = MaterialTheme.typography.labelLarge
                    )

                    BackHandler {
                        onCancel()
                    }
                }
            },
            onDismissRequest = onBiometricsAuthorizationFailed,
            confirmButton = {},
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NonPrivilegedAuthorizeContents(
    authorizationViewState: AuthorizeUiState,
    onCheckEnteredPIN: (String) -> Unit,
    onBiometricAuthorizationSuccess: () -> Unit,
    onBiometricsAuthorizationFailed: () -> Unit,
    onCancel: () -> Unit,
    onNavigateAuthorizeInfo: () -> Unit,
    onNavigateSelectSeedDialog: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    var showPinDialog by remember { mutableStateOf(false) }
    if (showPinDialog) {
        val pin = rememberSaveable { mutableStateOf("") }
        AlertDialog(
            modifier = Modifier
                .padding(horizontal = Sizes.dp16)
                .semantics {
                    testTagsAsResourceId = true
                },
            title = {
                Text(text = stringResource(id = R.string.label_enter_pin))
            },
            text = {
                val keyboardController =
                    LocalSoftwareKeyboardController.current

                BasicTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizes.dp48)
                        .background(
                            MaterialTheme.colorScheme.inverseOnSurface,
                            CircleShape
                        )
                        .semantics {
                            testTag = "PinEntryField"
                        },
                    value = pin.value,
                    onValueChange = {
                        pin.value = it
                    },
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        onCheckEnteredPIN(pin.value)
                        keyboardController?.hide()
                    }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    maxLines = 1,
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .height(Sizes.dp48)
                                .padding(
                                    start = Sizes.dp16
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .padding(start = Sizes.dp16)
                                    .weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                innerTextField()
                            }
                        }
                    }
                )
            },
            onDismissRequest = {
                showPinDialog = false
            },
            confirmButton = {
                TextButton(
                    enabled = pin.value.length >= 4,
                    onClick = {
                        onCheckEnteredPIN(pin.value)
                    }
                ) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                    }
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            }
        )
    }
    ModalBottomSheet(
        modifier = Modifier
            .semantics {
                testTagsAsResourceId = true
            },
        sheetState = sheetState,
        scrimColor = Color.Transparent, // scrim provided at window level by backgroundDimEnabled in theme
        contentWindowInsets = { BottomSheetDefaults.windowInsets.only(WindowInsetsSides.Top) },
        onDismissRequest = onCancel
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            Image(
                modifier = Modifier
                    .padding(top = Sizes.dp24)
                    .padding(horizontal = Sizes.dp16)
                    .height(Sizes.dp48),
                painter = painterResource(id = R.drawable.ic_seed_vault_hero),
                contentDescription = null
            )

            val authTypeText = when (authorizationViewState.authorizationType) {
                AuthorizeUiState.AuthorizationType.SEED -> R.string.label_authorize_seed
                AuthorizeUiState.AuthorizationType.TRANSACTION -> R.string.label_authorize_transaction
                AuthorizeUiState.AuthorizationType.MESSAGE -> R.string.label_authorize_message
                AuthorizeUiState.AuthorizationType.PUBLIC_KEY -> R.string.label_authorize_public_key
                null -> android.R.string.unknownName
            }
            Text(
                modifier = Modifier
                    .padding(top = Sizes.dp24)
                    .padding(horizontal = Sizes.dp16),
                text = stringResource(id = authTypeText),
                style = MaterialTheme.typography.headlineSmall,
            )

            Text(
                modifier = Modifier
                    .padding(top = Sizes.dp40)
                    .padding(horizontal = Sizes.dp16),
                text = stringResource(id = R.string.label_authorize_app),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )

            val isClickEnabled =
                authorizationViewState.authorizationType == AuthorizeUiState.AuthorizationType.SEED
            Row(
                modifier = Modifier
                    .clickable(
                        enabled = isClickEnabled
                    ) {
                        onNavigateAuthorizeInfo()
                    }
                    .padding(all = Sizes.dp16),
                verticalAlignment = Alignment.CenterVertically
            ) {
                authorizationViewState.requestorAppIcon?.let {
                    Image(
                        modifier = Modifier
                            .height(Sizes.dp48),
                        bitmap = it.toBitmap().asImageBitmap(),
                        contentDescription = null
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(horizontal = Sizes.dp16)
                        .weight(1f)
                ) {
                    Text(
                        text = authorizationViewState.requestorAppName.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (isClickEnabled) {
                        Text(
                            text = stringResource(id = R.string.label_authorize_info),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (isClickEnabled) {
                    Icon(
                        modifier = Modifier
                            .size(Sizes.dp48),
                        tint = MaterialTheme.colorScheme.onSurface,
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = Sizes.dp16))

            if (authorizationViewState.authorizationType == AuthorizeUiState.AuthorizationType.SEED) {
                Text(
                    modifier = Modifier
                        .padding(top = Sizes.dp24)
                        .padding(horizontal = Sizes.dp16),
                    text = stringResource(id = R.string.label_authorize_for),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(
                    modifier = Modifier
                        .clickable {
                            onNavigateSelectSeedDialog()
                        }
                        .padding(all = Sizes.dp16),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Sizes.dp4))
                            .background(Color.White)
                            .padding(Sizes.dp8)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_seed_vault),
                            contentDescription = null
                        )
                    }
                    Text(
                        modifier = Modifier
                            .padding(horizontal = Sizes.dp8)
                            .weight(1f),
                        text = authorizationViewState.seedName.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Icon(
                        modifier = Modifier
                            .size(Sizes.dp48),
                        tint = MaterialTheme.colorScheme.onSurface,
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = Sizes.dp16))
            }

            val typedValue = TypedValue()
            val theme = context.theme
            theme.resolveAttribute(android.R.attr.fingerprintAuthDrawable, typedValue, true)
            val drawable = AppCompatResources.getDrawable(context, typedValue.resourceId)

            if (authorizationViewState.enableBiometrics) {
                Row(
                    modifier = Modifier
                        .padding(end = Sizes.dp16)
                        .padding(vertical = Sizes.dp16)
                        .semantics {
                            testTagsAsResourceId = true
                        }
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            modifier = Modifier
                                .height(Sizes.dp48)
                                .semantics {
                                    testTag = "AuthorizeFingerprint"
                                },
                            onClick = {
                                onBiometricAuthorizationSuccess()
                            }
                        ) {
                            drawable?.let {
                                Icon(
                                    modifier = Modifier.size(Sizes.dp48),
                                    bitmap = it.toBitmap().asImageBitmap(),
                                    contentDescription = stringResource(id = R.string.authorize_fingerprint)
                                )
                            }
                        }
                        Text(
                            modifier = Modifier.padding(top = Sizes.dp8),
                            text = stringResource(id = R.string.label_authorize_fingerprint)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            modifier = Modifier
                                .height(Sizes.dp48)
                                .semantics {
                                    testTag = "DenyFingerprint"
                                },
                            onClick = onBiometricsAuthorizationFailed,
                        ) {
                            drawable?.let {
                                Icon(
                                    modifier = Modifier.size(Sizes.dp48),
                                    bitmap = it.toBitmap().asImageBitmap(),
                                    contentDescription = stringResource(id = R.string.deny_fingerprint),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Text(
                            modifier = Modifier.padding(top = Sizes.dp8),
                            text = stringResource(id = R.string.deny_fingerprint)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = Sizes.dp16))
            }

            Row(
                modifier = Modifier
                    .padding(all = Sizes.dp16)
                    .semantics {
                        testTagsAsResourceId = true
                    }
            ) {
                TextButton(
                    onClick = {
                        onCancel()
                    },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text(text = stringResource(id = R.string.action_cancel))
                }

                Spacer(modifier = Modifier.weight(1f))

                if (authorizationViewState.enablePIN) {
                    TextButton(
                        modifier = Modifier
                            .semantics {
                                testTag = "UsePin"
                            },
                        onClick = {
                            showPinDialog = true
                        },
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text(text = stringResource(id = R.string.action_pin))
                    }
                }
            }
        }
    }
}
