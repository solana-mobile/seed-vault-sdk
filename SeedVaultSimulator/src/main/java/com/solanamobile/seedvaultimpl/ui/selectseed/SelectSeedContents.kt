/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.selectseed

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.solanamobile.seedvault.WalletContractV1
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.seedvaultimpl.usecase.GetNameUseCase
import com.solanamobile.ui.apptheme.Sizes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectSeedContents(
    uiState: SelectSeedUiState,
    selectSeedForAuthorization: () -> Unit,
    onNavigateBack: () -> Unit,
    completeAuthorizationWithError: (Int) -> Unit,
    onSetSelectedSeed: (Long) -> Unit,
) {
    val backNavigation = {
        try {
            selectSeedForAuthorization()
            onNavigateBack()
        } catch (e: IllegalStateException) {
            Log.e(
                "SelectSeedContents",
                "Failed authorizing selected seed for UID",
                e
            )
            completeAuthorizationWithError(WalletContractV1.RESULT_UNSPECIFIED_ERROR)
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        sheetState = sheetState,
        contentWindowInsets = { BottomSheetDefaults.windowInsets.only(WindowInsetsSides.Top) },
        onDismissRequest = backNavigation
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            IconButton(
                modifier = Modifier
                    .size(Sizes.dp48),
                onClick = backNavigation,
            ) {
                Icon(
                    modifier = Modifier
                        .size(Sizes.dp28),
                    tint = MaterialTheme.colorScheme.onSurface,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null
                )
            }

            Text(
                modifier = Modifier
                    .padding(all = Sizes.dp16),
                text = stringResource(R.string.label_select_seed_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                modifier = Modifier
                    .padding(all = Sizes.dp16),
                text = stringResource(R.string.label_select_seed),
                style = MaterialTheme.typography.titleMedium
            )
            uiState.seeds.forEach {
                Row(
                    modifier = Modifier
                        .clickable {
                            onSetSelectedSeed(it.id)
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
                            .weight(1f)
                            .padding(horizontal = Sizes.dp16)
                            .fillMaxWidth(),
                        text = GetNameUseCase.getName(it),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (uiState.selectedSeedId == it.id) {
                        Icon(
                            modifier = Modifier
                                .size(Sizes.dp28),
                            tint = MaterialTheme.colorScheme.onSurface,
                            imageVector = Icons.Filled.Check,
                            contentDescription = null
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier
                        .padding(horizontal = Sizes.dp16)
                )
            }
            Box(
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}