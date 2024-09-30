/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.seedvaultimpl.ui.authorizeinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.solanamobile.seedvaultimpl.R
import com.solanamobile.ui.apptheme.Sizes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizeInfoContents(
    onNavigateBack: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    ModalBottomSheet(
        sheetState = sheetState,
        contentWindowInsets = { BottomSheetDefaults.windowInsets.only(WindowInsetsSides.Top) },
        onDismissRequest = { onNavigateBack() }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            IconButton(
                modifier = Modifier
                    .size(Sizes.dp48),
                onClick = { onNavigateBack() },
            ) {
                Icon(
                    modifier = Modifier
                        .size(Sizes.dp28),
                    tint = MaterialTheme.colorScheme.onSurface,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null
                )
            }

            Box(
                modifier = Modifier
                    .padding(all = Sizes.dp16)
                    .clip(RoundedCornerShape(Sizes.dp16))
                    .background(color = MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = Sizes.dp4, horizontal = Sizes.dp16),
            ) {
                Text(
                    text = stringResource(R.string.label_authorize_info_title),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                modifier = Modifier
                    .padding(all = Sizes.dp16),
                text = stringResource(R.string.label_authorize_info_body1),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                modifier = Modifier
                    .padding(all = Sizes.dp16)
                    .navigationBarsPadding(),
                text = stringResource(R.string.label_authorize_info_body2),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}