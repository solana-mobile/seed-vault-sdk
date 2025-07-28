/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.ui.apptheme

import android.util.DisplayMetrics
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

val LocalTypography = compositionLocalOf { Typography() }

@Composable
fun ProvideTypography(
    content: @Composable () -> Unit
) {
    val currScale = LocalDensity.current.fontScale.times(displayScaleFactor())

    val typography = if (currScale > 1.0) {
        val invertScale = 1 / currScale

        SolanaTypography.copy(
            displayLarge = SolanaTypography.displayLarge.scaleFont(invertScale),
            displayMedium = SolanaTypography.displayMedium.scaleFont(invertScale),
            displaySmall = SolanaTypography.displaySmall.scaleFont(invertScale),
            headlineLarge = SolanaTypography.headlineLarge.scaleFont(invertScale),
            headlineMedium = SolanaTypography.headlineMedium.scaleFont(invertScale),
            headlineSmall = SolanaTypography.headlineSmall.scaleFont(invertScale),
            titleLarge = SolanaTypography.titleLarge.scaleFont(invertScale)
        )
    } else {
        SolanaTypography
    }

    CompositionLocalProvider(LocalTypography provides typography) {
        content()
    }
}

@Composable
fun displayScaleFactor(): Float {
    if (LocalInspectionMode.current || LocalContext.current.resources.displayMetrics.densityDpi == DisplayMetrics.DENSITY_DEVICE_STABLE) {
        return 1f // Optimize floating operation
    }
    return LocalContext.current.resources.displayMetrics.densityDpi.toFloat().div(DisplayMetrics.DENSITY_DEVICE_STABLE)
}

// Disables display scale factor.
@Composable
fun Dp.renderWithoutDisplayScaling(): Dp {
    return this.div(displayScaleFactor())
}

fun TextStyle.scaleFont(scale: Float): TextStyle {
    return this.copy(
        fontSize = this.fontSize.times(scale),
        lineHeight = this.lineHeight.times(scale)
    )
}

val KraftigFontFamily = FontFamily(
    Font(R.font.sohne_kraftig)
)

val BuchFontFamily = FontFamily(
    Font(R.font.sohne_buch)
)

val SolanaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = KraftigFontFamily,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = KraftigFontFamily,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = KraftigFontFamily,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = KraftigFontFamily,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = KraftigFontFamily,
        fontSize = 28.sp,
        lineHeight = 31.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = KraftigFontFamily,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = KraftigFontFamily,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = KraftigFontFamily,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = KraftigFontFamily,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = KraftigFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = KraftigFontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = KraftigFontFamily,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BuchFontFamily,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BuchFontFamily,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BuchFontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
)
