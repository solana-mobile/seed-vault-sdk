/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.solanamobile.fakewallet.BuildConfig
import com.solanamobile.fakewallet.R
import com.solanamobile.fakewallet.usecase.SeedPurposeUseCase
import com.solanamobile.ui.apptheme.Sizes

data class ImplementationLimits(
    val maxSigningRequests: Int,
    val maxRequestedSignatures: Int,
    val firstRequestedPublicKey: String,
    val lastRequestedPublicKey: String
)

@Composable
fun SeedDetails(
    seed: Seed,
    implementationLimits: ImplementationLimits,
    onSignTransaction: (Seed, Account) -> Unit,
    onSignMessage: (Seed, Account) -> Unit,
    onAccountNameUpdated: (Seed, Account, String) -> Unit,
    onDeauthorizeSeed: (Seed) -> Unit,
    onShowSeedSettings: (Seed) -> Unit,
    onRequestPublicKeys: (Seed) -> Unit,
    onRequestOpenPublicKeys: (Seed) -> Unit,
    onSignMaxTransactionsWithMaxSignatures: (Seed) -> Unit,
    onSignPermissionedAccountTransactions: (Seed) -> Unit,
    onSignMaxMessagesWithMaxSignatures: (Seed) -> Unit,
    onSignPermissionedAccountMessages: (Seed) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Sizes.dp16, vertical = Sizes.dp8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(end = Sizes.dp8),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                text = seed.name,
            )
            if (BuildConfig.FLAVOR != "Privileged") {
                IconButton(onClick = { onDeauthorizeSeed(seed) }) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_delete),
                        contentDescription = null
                    )
                }
            } else {
                IconButton(onClick = { onShowSeedSettings(seed) }) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_edit),
                        contentDescription = null
                    )
                }
            }
        }
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Sizes.dp8),
            text = stringResource(id = R.string.label_seed_purpose) + " " + SeedPurposeUseCase(
                seed.purpose
            )
        )
        TextButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Sizes.dp8),
            onClick = { onRequestPublicKeys(seed) },
            colors = ButtonDefaults.buttonColors()
        ) {
            Text(
                text = stringResource(
                    R.string.action_request_public_keys,
                    implementationLimits.firstRequestedPublicKey,
                    implementationLimits.lastRequestedPublicKey
                )
            )
        }
        if (BuildConfig.FLAVOR == "Privileged") {
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Sizes.dp8),
                onClick = { onRequestOpenPublicKeys(seed) },
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(
                    text = stringResource(
                        R.string.action_get_open_public_keys,
                    )
                )
            }
        }
        TextButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Sizes.dp8),
            onClick = { onSignMaxTransactionsWithMaxSignatures(seed) },
            colors = ButtonDefaults.buttonColors()
        ) {
            Text(
                text = stringResource(
                    R.string.action_sign_max_transactions_with_max_signatures,
                    implementationLimits.maxSigningRequests,
                    implementationLimits.maxRequestedSignatures
                )
            )
        }
        if (BuildConfig.FLAVOR == "Privileged") {
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Sizes.dp8),
                onClick = { onSignPermissionedAccountTransactions(seed) },
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(
                    text = stringResource(
                        R.string.action_sign_no_auth_transactions,
                        implementationLimits.maxSigningRequests,
                        implementationLimits.maxRequestedSignatures
                    )
                )
            }
        }
        TextButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Sizes.dp8),
            onClick = { onSignMaxMessagesWithMaxSignatures(seed) },
            colors = ButtonDefaults.buttonColors()
        ) {
            Text(
                text = stringResource(
                    R.string.action_sign_max_messages_with_max_signatures,
                    implementationLimits.maxSigningRequests,
                    implementationLimits.maxRequestedSignatures
                )
            )
        }
        if (BuildConfig.FLAVOR == "Privileged") {
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Sizes.dp8),
                onClick = { onSignPermissionedAccountMessages(seed) },
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(
                    text = stringResource(
                        R.string.action_sign_no_auth_messages,
                        implementationLimits.maxSigningRequests,
                        implementationLimits.maxRequestedSignatures
                    )
                )
            }
        }
        seed.accounts.forEach { account ->
            AccountComposable(
                account = account,
                onSignMessage = { onSignMessage(seed, account) },
                onSignTransaction = { onSignTransaction(seed, account) },
                onAccountNameUpdated = { editedName ->
                    if (account.name != editedName) {
                        onAccountNameUpdated(seed, account, editedName)
                    }
                }
            )
        }
    }
}

@Composable
fun AccountComposable(
    account: Account,
    onSignMessage: () -> Unit,
    onSignTransaction: () -> Unit,
    onAccountNameUpdated: (String) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    val accountName = rememberSaveable { mutableStateOf(account.name) }
    if (showRenameDialog) {
        AlertDialog(
            title = {
                Text(text = stringResource(id = R.string.title_set_account_name))
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
                        ),
                    value = TextFieldValue(
                        accountName.value,
                        selection = TextRange(accountName.value.length)
                    ),
                    onValueChange = {
                        accountName.value = it.text
                    },
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (accountName.value != account.name) {
                            onAccountNameUpdated(accountName.value)
                        }
                        showRenameDialog = false
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
                showRenameDialog = false
            },
            confirmButton = {
                TextButton(onClick = {
                    if (accountName.value != account.name) {
                        onAccountNameUpdated(accountName.value)
                    }
                    showRenameDialog = false
                }) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                }) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            }
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Sizes.dp8)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Sizes.dp16, vertical = Sizes.dp8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                text = stringResource(id = R.string.label_account_name) + " " + account.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(
                onClick = {
                    accountName.value = account.name
                    showRenameDialog = true
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Sizes.dp16, vertical = Sizes.dp8),
            text = stringResource(id = R.string.label_public_key) + " " + account.publicKeyEncoded
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Sizes.dp16),
            text = stringResource(id = R.string.label_derivation_path) + " " + account.derivationPath.toString()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Sizes.dp16, vertical = Sizes.dp8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.label_sign)
            )
            TextButton(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Sizes.dp16),
                onClick = onSignTransaction,
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(text = stringResource(R.string.action_sign_transaction))
            }
            TextButton(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Sizes.dp16),
                onClick = onSignMessage,
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(text = stringResource(R.string.action_sign_message))
            }
        }
    }
}