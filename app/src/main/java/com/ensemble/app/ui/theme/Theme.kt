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
    secondary = RoseSecondary,
    background = CreamBackground,
    onBackground = InkText,
    surface = CreamBackground,
    onSurface = InkText
)

private val DarkColors = darkColorScheme(
    primary = RosePrimary,
    onPrimary = Color.White,
    primaryContainer = RosePrimaryDark,
    secondary = RoseSecondary
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
        content = content
    )
}
