package com.ensemble.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = RosePrimary,
    onPrimary = Color.White,
    primaryContainer = RoseContainer,
    onPrimaryContainer = RosePrimaryDark,
    secondary = RoseSecondary,
    secondaryContainer = RoseContainer,
    tertiary = GoldAccent,
    background = CreamBackground,
    onBackground = InkText,
    surface = CreamSurface,
    onSurface = InkText,
    surfaceVariant = RoseContainer,
    onSurfaceVariant = InkTextSoft,
    outline = InkTextSoft
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8FA3),
    onPrimary = Color(0xFF3A0A1A),
    primaryContainer = RosePrimaryDark,
    onPrimaryContainer = Color(0xFFFFE1E9),
    secondary = Color(0xFFD9A3AC),
    background = Color(0xFF241417),
    onBackground = Color(0xFFF3E3E6),
    surface = Color(0xFF2C1A1E),
    onSurface = Color(0xFFF3E3E6),
    surfaceVariant = Color(0xFF3A2429),
    onSurfaceVariant = Color(0xFFD9BAC0)
)

@Composable
fun EnsembleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = EnsembleTypography,
        shapes = EnsembleShapes,
        content = content
    )
}
