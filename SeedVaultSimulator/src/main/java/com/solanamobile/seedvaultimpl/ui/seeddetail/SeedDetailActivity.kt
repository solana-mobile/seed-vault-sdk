/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.seeddetail

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.lifecycleScope
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.seedvaultimpl.model.Authorization
import com.solanamobile.seedvaultimpl.model.SeedDetails
import com.solanamobile.seedvaultimpl.usecase.Base58EncodeUseCase
import com.solanamobile.seedvaultimpl.usecase.Bip39PhraseUseCase
import com.solanamobile.ui.apptheme.Sizes
import com.solanamobile.ui.apptheme.SolanaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SeedDetailActivity : AppCompatActivity() {

    private val viewModel: SeedDetailViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED) // this will be set to RESULT_OK on success

        when (intent.action) {
            WalletContractV1.ACTION_CREATE_SEED,
            WalletContractV1.ACTION_IMPORT_SEED -> {
                val authorizePurposeInt =
                    if (intent.hasExtra(WalletContractV1.EXTRA_PURPOSE)) intent.getIntExtra(
                        WalletContractV1.EXTRA_PURPOSE, -1
                    ) else null

                try {
                    val authorize = authorizePurposeInt?.let {
                        callingPackage?.let { packageName ->
                            PreAuthorizeSeed(
                                packageManager.getPackageUid(packageName, 0),
                                Authorization.Purpose.fromWalletContractConstant(it)
                            )
                        }
                    }
                    if (intent.action == WalletContractV1.ACTION_CREATE_SEED) {
                        viewModel.createNewSeed(authorize)
                    } else {
                        viewModel.importExistingSeed(authorize)
                    }
                } catch (e: IllegalArgumentException) {
                    Log.e(
                        TAG,
                        "Invalid purpose $authorizePurposeInt specified for Intent $intent; terminating...",
                        e
                    )
                    setResult(WalletContractV1.RESULT_INVALID_PURPOSE)
                    finish()
                }
            }

            ACTION_EDIT_SEED -> {
                val seedId = intent.getLongExtra(EXTRA_SEED_ID, -1L)
                if (seedId != -1L) {
                    viewModel.editSeed(seedId)
                } else {
                    Log.e(
                        TAG, "Invalid seed ID $seedId specified for Intent $intent; terminating..."
                    )
                    finish()
                }
            }

            else -> throw IllegalArgumentException("Unsupported Intent $intent")
        }

        enableEdgeToEdge()
        setContent {
            val seedDetails = viewModel.seedDetailUiState.collectAsState().value
            val context = LocalContext.current
            val snackbarHostState = remember {
                SnackbarHostState()
            }
            val message = seedDetails.errorMessage
            if (message != null) {
                LaunchedEffect(key1 = message) {
                    when (snackbarHostState.showSnackbar(
                        message = message.toString(),
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )) {
                        SnackbarResult.Dismissed,
                        SnackbarResult.ActionPerformed -> {
                            viewModel.clearErrorMessage()
                        }
                    }
                }
            }
            SolanaTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        modifier = Modifier
                            .imePadding()
                            .navigationBarsPadding()
                            .semantics {
                                testTagsAsResourceId = true
                            },
                        snackbarHost = {
                            SnackbarHost(snackbarHostState)
                        },
                        topBar = {
                            TopAppBar(
                                title = { Text(text = "Create Seed") },
                                actions = {
                                    IconButton(
                                        modifier = Modifier
                                            .size(Sizes.dp48)
                                            .semantics {
                                                testTag = "Save"
                                            },
                                        onClick = {
                                            lifecycleScope.launch {
                                                viewModel.saveSeed()?.let { authToken ->
                                                    val intent = if (authToken != -1L)
                                                        Intent().putExtra(
                                                            WalletContractV1.EXTRA_AUTH_TOKEN,
                                                            authToken
                                                        )
                                                    else
                                                        null
                                                    setResult(Activity.RESULT_OK, intent)
                                                    finish()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(id = android.R.drawable.ic_menu_save),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            contentDescription = stringResource(id = R.string.label_save_seed)
                                        )
                                    }
                                }
                            )
                        },
                    ) { contentPadding ->
                        Column(
                            modifier = Modifier
                                .padding(contentPadding)
                                .padding(horizontal = Sizes.dp16)
                                .verticalScroll(rememberScrollState())
                        ) {
                            EditField(
                                modifier = Modifier.padding(vertical = Sizes.dp16),
                                fieldValue = seedDetails.name,
                                onValueSet = {
                                    viewModel.setName(it)
                                },
                                keyboardType = KeyboardType.Text,
                                emptyHint = stringResource(id = R.string.hint_seed_name),
                                testResourceId = "SeedName"
                            )
                            Row(
                                modifier = Modifier.padding(vertical = Sizes.dp16),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    modifier = Modifier.padding(end = Sizes.dp16),
                                    text = stringResource(id = R.string.label_pin)
                                )
                                EditField(
                                    fieldValue = seedDetails.pin,
                                    onValueSet = {
                                        viewModel.setPIN(it)
                                    },
                                    keyboardType = KeyboardType.NumberPassword,
                                    emptyHint = stringResource(id = R.string.hint_pin),
                                    testResourceId = "SeedPin"
                                )
                            }
                            if (seedDetails.pin.isNotEmpty() && seedDetails.pin.length !in SeedDetails.PIN_MIN_LENGTH..SeedDetails.PIN_MAX_LENGTH) {
                                Text(
                                    text = stringResource(id = R.string.error_invalid_pin),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            Row(
                                modifier = Modifier.padding(vertical = Sizes.dp16),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    modifier = Modifier
                                        .padding(end = Sizes.dp16)
                                        .weight(1f),
                                    text = stringResource(id = R.string.label_enable_biometrics_for_seed),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    modifier = Modifier.semantics {
                                        testTag = "EnableBiometrics"
                                    },
                                    checked = seedDetails.enableBiometrics,
                                    onCheckedChange = {
                                        viewModel.enableBiometrics(it)
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Sizes.dp16),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.title_seed_phrase),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                if (seedDetails.isCreateMode) {
                                    val longPhrase =
                                        seedDetails.phraseLength == SeedDetailUiState.SeedPhraseLength.SEED_PHRASE_24_WORDS
                                    Row {
                                        FilterChip(
                                            modifier = Modifier
                                                .padding(end = Sizes.dp16)
                                                .clearAndSetSemantics {
                                                    contentDescription = context.getString(
                                                        R.string.phrase_length_description,
                                                        12
                                                    )
                                                    testTag = "PhraseLength12"
                                                },
                                            selected = !longPhrase,
                                            onClick = {
                                                viewModel.setSeedPhraseLength(SeedDetailUiState.SeedPhraseLength.SEED_PHRASE_12_WORDS)
                                            },
                                            label = { Text(text = stringResource(id = R.string.text_phrase_length_12)) }
                                        )
                                        FilterChip(
                                            modifier = Modifier
                                                .clearAndSetSemantics {
                                                    contentDescription = context.getString(
                                                        R.string.phrase_length_description,
                                                        24
                                                    )
                                                    testTag = "PhraseLength24"
                                                },
                                            selected = longPhrase,
                                            onClick = {
                                                viewModel.setSeedPhraseLength(SeedDetailUiState.SeedPhraseLength.SEED_PHRASE_24_WORDS)
                                            },
                                            label = { Text(text = stringResource(id = R.string.text_phrase_length_24)) }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()
                            seedDetails.phrase.take(seedDetails.phraseLength.length)
                                .forEachIndexed { index, phrase ->
                                    var isWordRecognized = true
                                    if (seedDetails.isCreateMode && phrase.isNotEmpty()) {
                                        try {
                                            Bip39PhraseUseCase.toIndex(phrase)
                                        } catch (_: IllegalArgumentException) {
                                            isWordRecognized = false
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.padding(vertical = Sizes.dp8),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            modifier = Modifier.padding(end = Sizes.dp16),
                                            text = (index + 1).toString()
                                        )
                                        var expanded by remember { mutableStateOf(false) }

                                        ExposedDropdownMenuBox(
                                            expanded = expanded,
                                            onExpandedChange = { expanded = !expanded },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            EditField(
                                                modifier = Modifier
                                                    .menuAnchor()
                                                    .onFocusChanged {
                                                        expanded = it.isFocused
                                                    },
                                                fieldValue = phrase,
                                                onValueSet = { editedValue ->
                                                    viewModel.setSeedPhraseWord(index, editedValue)
                                                    expanded = true
                                                },
                                                keyboardType = KeyboardType.Text,
                                                isReadOnly = !seedDetails.isCreateMode,
                                                testResourceId = "SeedPhrase#$index"
                                            )

                                            if (seedDetails.isCreateMode && phrase.length >= 2) {
                                                val options =
                                                    Bip39PhraseUseCase.bip39EnglishWordlist.filter {
                                                        it.contains(
                                                            phrase
                                                        )
                                                    }
                                                if (options.isNotEmpty()) {
                                                    DropdownMenu(
                                                        properties = PopupProperties(focusable = false),
                                                        expanded = expanded,
                                                        onDismissRequest = { expanded = false }
                                                    ) {
                                                        options.forEach { option: String ->
                                                            DropdownMenuItem(
                                                                text = { Text(text = option) },
                                                                onClick = {
                                                                    expanded = false
                                                                    viewModel.setSeedPhraseWord(
                                                                        index,
                                                                        option
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (!isWordRecognized) {
                                        Text(
                                            text = stringResource(id = R.string.error_invalid_seed_word),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }

                            HorizontalDivider()
                            if (!seedDetails.isCreateMode) {
                                if (seedDetails.accounts.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = Sizes.dp8),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = Sizes.dp16),
                                            text = "Accounts",
                                            style = MaterialTheme.typography.titleLarge
                                        )

                                        IconButton(onClick = {
                                            viewModel.removeAllAccounts()
                                        }) {
                                            Icon(
                                                painter = painterResource(id = android.R.drawable.ic_delete),
                                                contentDescription = null
                                            )
                                        }
                                    }
                                    seedDetails.accounts.forEach { account ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = Sizes.dp8)
                                        ) {
                                            account.name?.let {
                                                Text(
                                                    modifier = Modifier.padding(bottom = Sizes.dp12),
                                                    text = it
                                                )
                                            }
                                            Text(
                                                modifier = Modifier.padding(bottom = Sizes.dp12),
                                                text = account.bip32DerivationPathUri.toString()
                                            )
                                            Text(
                                                text = Base58EncodeUseCase(account.publicKey)
                                            )
                                        }
                                        HorizontalDivider()
                                    }
                                }

                                if (seedDetails.authorizedApps.isNotEmpty()) {
                                    Text(
                                        modifier = Modifier.padding(vertical = Sizes.dp16),
                                        text = "Authorizations",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }

                                seedDetails.authorizedApps.forEach { authorization ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = Sizes.dp8),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val packages =
                                            context.packageManager.getPackagesForUid(authorization.uid)
                                        val text = packages?.joinToString("\n")
                                            ?: authorization.uid.toString()
                                        val isPrivilegedApp = packages?.firstOrNull()?.let {
                                            context.packageManager.checkPermission(
                                                WalletContractV1.PERMISSION_ACCESS_SEED_VAULT_PRIVILEGED,
                                                it
                                            ) == PackageManager.PERMISSION_GRANTED
                                        } ?: false
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                modifier = Modifier.padding(end = Sizes.dp16),
                                                text = text
                                            )
                                            Text(
                                                modifier = Modifier.padding(end = Sizes.dp16),
                                                text = authorization.purpose.toString()
                                            )
                                        }
                                        if (!isPrivilegedApp) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.deauthorize(authorization.authToken)
                                                }
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = android.R.drawable.ic_delete),
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = SeedDetailActivity::class.simpleName
        const val ACTION_EDIT_SEED =
            "com.solanamobile.seedvaultimpl.ui.seeddetail.ACTION_EDIT_SEED"
        const val EXTRA_SEED_ID = "seed_id"
    }
}

@Composable
fun EditField(
    modifier: Modifier = Modifier,
    fieldValue: String = "",
    onValueSet: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    emptyHint: String = "",
    isReadOnly: Boolean = false,
    testResourceId: String = "",
) {
    val keyboardController =
        LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BasicTextField(
        enabled = !isReadOnly,
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.dp48)
            .background(
                MaterialTheme.colorScheme.inverseOnSurface, CircleShape
            )
            .semantics {
                testTag = testResourceId
            },
        value = fieldValue,
        onValueChange = {
            onValueSet(it.trim())
        },
        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done,
            keyboardType = keyboardType,
        ),
        keyboardActions = KeyboardActions(onDone = {
            if (!focusManager.moveFocus(FocusDirection.Down)) {
                keyboardController?.hide()
            }
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
                if (!isReadOnly) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(start = Sizes.dp16)
                        .weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (emptyHint.isNotBlank() && fieldValue.isBlank()) {
                        Text(
                            text = emptyHint,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}