/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solanamobile.fakewallet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.solanamobile.fakewallet.R
import com.solanamobile.ui.apptheme.Sizes

typealias ImplementationLimit = Pair<String, Long>

@Composable
fun ImplementationLimits(
    implementationLimit: ImplementationLimit,
    onTestExceedLimit: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = Sizes.dp16),
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Sizes.dp8),
            text = implementationLimit.first + "=" + implementationLimit.second.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onTestExceedLimit(
                    implementationLimit.first
                )
            },
            colors = ButtonDefaults.buttonColors()
        ) {
            Text(text = stringResource(id = R.string.action_exceed_implementation_limit))
        }
    }
}