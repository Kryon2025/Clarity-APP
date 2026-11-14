package com.shenqing.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.shenqing.mobile.data.ThemePalette

val LocalPalette = staticCompositionLocalOf<ThemePalette> {
    error("ThemePalette not provided")
}

@Composable
fun ShenQingTheme(
    palette: ThemePalette,
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scheme = darkColorScheme(
        primary = Color(palette.accent),
        onPrimary = Color(palette.onAccent),
        secondary = Color(palette.accentSoft),
        onSecondary = Color(palette.onAccent),
        tertiary = Color(palette.gradC),
        background = Color(palette.bgTop),
        onBackground = Color(palette.textPrimary),
        surface = Color(palette.cardBg),
        onSurface = Color(palette.textPrimary),
        surfaceVariant = Color(palette.cardBgAlt),
        onSurfaceVariant = Color(palette.textSecondary),
        error = Color(palette.danger),
        onError = Color.White,
        outline = Color(palette.cardBorder),
    )
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalDensity provides Density(density.density, fontScale = fontScale.coerceIn(0.8f, 1.4f)),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content,
        )
    }
}

fun Int.asColor(): Color = Color(this)
