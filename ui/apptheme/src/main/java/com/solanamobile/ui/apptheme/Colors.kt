/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.ui.apptheme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Neutral0 = Color(0xFF000000)
val Neutral20 = Color(0xFF332F37)
val Neutral90 = Color(0xFFE8E0EB)
val Neutral95 = Color(0xFFF7EEF9)

val Primary40 = Color(0xFF7F21E5)
val Primary80 = Color(0xFFD8B9FF)

val LightPrimary = Color(0xFF7F21E5)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEDDCFF)
val LightOnPrimaryContainer = Color(0xFF290055)

val LightSecondary = Color(0xFF645A6F)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFEBDDF7)
val LightOnSecondaryContainer = Color(0xFF20182A)

val LightTertiary = Color(0xFF7F21E5)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFD9DF)
val LightOnTertiaryContainer = Color(0xFF321019)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF1D1B1E)

val LightSurface = Color(0xFFFFFBFF)
val LightOnSurface = Color(0xFF000000)
val LightSurfaceVariant = Color(0xFFF5F0F7)
val LightOnSurfaceVariant = Color(0xFF4A454E)

val LightOutline = Color(0xFF7B757F)
val LightOutlineVariant = Color(0xFFCCC4CF)

val DarkPrimary = Color(0xFFD8B9FF)
val DarkOnPrimary = Color(0xFF450086)
val DarkPrimaryContainer = Color(0xFF6300BB)
val DarkOnPrimaryContainer = Color(0xFFEDDCFF)

val DarkSecondary = Color(0xFFCFC2DA)
val DarkOnSecondary = Color(0xFF352D40)
val DarkSecondaryContainer = Color(0xFF4C4357)
val DarkOnSecondaryContainer = Color(0xFFEBDDF7)

val DarkTertiary = Color(0xFF7F21E5)
val DarkOnTertiary = Color(0xFFFFFFFF)
val DarkTertiaryContainer = Color(0xFF653B43)
val DarkOnTertiaryContainer = Color(0xFFFFD9DF)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF000000)
val DarkOnBackground = Color(0xFFE7E1E5)

val DarkSurface = Color(0xFF1D1B1E)
val DarkOnSurface = Color(0xFFFFFBFF)
val DarkSurfaceVariant = Color(0xFF1E1A22)
val DarkOnSurfaceVariant = Color(0xFFCCC4CF)

val DarkOutline = Color(0xFF958E99)
val DarkOutlineVariant = Color(0xFF4A454E)

@Composable
fun shimmerBase(): Color {
    return if (isSystemInDarkTheme()) Color(0xFF2F2A35) else Color(0xFFE8E0EB)
}

@Composable
fun shimmerOverlay(): Color {
    return if (isSystemInDarkTheme()) Color(0xFF3D3745) else Color(0xFFF1EAF3)
}
