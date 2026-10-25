package com.aware.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AwareColorScheme = darkColorScheme(
    primary = AwareAccent,
    onPrimary = AwareBackground,
    primaryContainer = Color(0xFF303E00),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF80CBC4),
    onSecondary = AwareBackground,
    tertiary = CategoryColors.HOME,
    onTertiary = AwareBackground,
    background = AwareBackground,
    onBackground = Color.White,
    surface = AwareSurface,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF252A30),
    onSurfaceVariant = Color.White,
    error = CategoryColors.EMERGENCY,
    onError = AwareBackground,
    outline = Color(0xFFBFC7D1),
)

@Composable
fun AwareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AwareColorScheme,
        typography = AwareTypography,
        content = content,
    )
}
