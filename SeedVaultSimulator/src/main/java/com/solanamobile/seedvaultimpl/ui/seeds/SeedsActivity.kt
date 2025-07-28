/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.seeds

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.seedvaultimpl.model.Seed
import com.solanamobile.seedvaultimpl.ui.seeddetail.SeedDetailActivity
import com.solanamobile.seedvaultimpl.usecase.GetNameUseCase
import com.solanamobile.ui.apptheme.Sizes
import com.solanamobile.ui.apptheme.SolanaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SeedsActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val viewModel: SeedsViewModel = hiltViewModel()
            SolanaTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background
                ) {
                    val seedDetails = viewModel.seedsUiState.collectAsState().value
                    val context = LocalContext.current
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(text = "My Seeds") },
                                actions = {
                                    if (seedDetails.canCreateSeeds) {
                                        IconButton(
                                            modifier = Modifier
                                                .size(Sizes.dp56)
                                                .padding(Sizes.dp4)
                                                .clip(CircleShape),
                                            onClick = {
                                                val intent =
                                                    Intent(WalletContractV1.ACTION_IMPORT_SEED).setClass(
                                                        context,
                                                        SeedDetailActivity::class.java
                                                    ).putExtra(
                                                        WalletContractV1.EXTRA_PURPOSE,
                                                        WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
                                                    )
                                                startActivity(intent)
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource(id = android.R.drawable.ic_menu_edit),
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                contentDescription = stringResource(id = R.string.menu_action_import)
                                            )
                                        }

                                        IconButton(
                                            modifier = Modifier
                                                .size(Sizes.dp56)
                                                .padding(Sizes.dp4)
                                                .clip(CircleShape),
                                            onClick = {
                                                val intent =
                                                    Intent(WalletContractV1.ACTION_CREATE_SEED).setClass(
                                                        context,
                                                        SeedDetailActivity::class.java
                                                    ).putExtra(
                                                        WalletContractV1.EXTRA_PURPOSE,
                                                        WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION
                                                    )
                                                startActivity(intent)
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource(id = android.R.drawable.ic_menu_add),
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                contentDescription = stringResource(id = R.string.menu_action_add)
                                            )
                                        }
                                    }
                                    if (seedDetails.seeds.isNotEmpty()) {
                                        IconButton(
                                            modifier = Modifier
                                                .size(Sizes.dp56)
                                                .padding(Sizes.dp4)
                                                .clip(CircleShape),
                                            onClick = {
                                                viewModel.deleteAllSeeds()
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(id = android.R.drawable.ic_menu_delete),
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                contentDescription = stringResource(id = R.string.menu_action_clear)
                                            )
                                        }
                                    }
                                }
                            )
                        },
                    ) { contentPadding ->
                        LazyColumn(
                            modifier = Modifier
                                .padding(contentPadding)
                        ) {
                            items(
                                count = seedDetails.seeds.size,
                            ) { index ->
                                val seed = seedDetails.seeds[index]
                                SeedView(
                                    seed = seed,
                                    onClick = {
                                        val intent =
                                            Intent(SeedDetailActivity.ACTION_EDIT_SEED).setClass(
                                                context,
                                                SeedDetailActivity::class.java
                                            ).putExtra(SeedDetailActivity.EXTRA_SEED_ID, seed.id)
                                        startActivity(intent)
                                    },
                                    onDelete = {
                                        viewModel.deleteSeed(seed.id)
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = Sizes.dp16))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeedView(
    seed: Seed,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Sizes.dp16),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Sizes.dp16),
            text = GetNameUseCase.getName(seed)
        )
        IconButton(
            modifier = Modifier
                .padding(end = Sizes.dp8)
                .size(Sizes.dp48)
                .clip(CircleShape),
            onClick = { onDelete() }
        ) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_delete),
                contentDescription = null
            )
        }
    }
}