/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import com.solanamobile.fakewallet.R
import com.solanamobile.ui.apptheme.Sizes

@Composable
fun UnauthorizedSeeds(
    hasUnauthorizedSeeds: Boolean,
    onAuthorizeNewSeed: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Sizes.dp56)
            .then(
                if (hasUnauthorizedSeeds) {
                    Modifier.clickable {
                        onAuthorizeNewSeed()
                    }
                } else {
                    Modifier
                }
            )
            .padding(all = Sizes.dp16),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            text = pluralStringResource(
                id = R.plurals.label_has_unauthorized_seeds,
                count = if (hasUnauthorizedSeeds) 1 else 0,
                if (hasUnauthorizedSeeds) 1 else 0
            ),
            maxLines = 4,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (hasUnauthorizedSeeds) {
            Icon(
                modifier = Modifier.padding(start = Sizes.dp8),
                painter = painterResource(android.R.drawable.ic_input_add),
                contentDescription = null
            )
        }
    }
}