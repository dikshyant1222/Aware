package com.aware.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AwareColorScheme = lightColorScheme(
    primary = AwareAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF8D7),
    onPrimaryContainer = AwareInk,
    secondary = CategoryColors.HOME,
    onSecondary = Color.White,
    tertiary = CategoryColors.HOME,
    onTertiary = AwareBackground,
    background = AwareBackground,
    onBackground = AwareInk,
    surface = AwareSurface,
    onSurface = AwareInk,
    surfaceVariant = AwareGroup,
    onSurfaceVariant = AwareSecondary,
    error = CategoryColors.EMERGENCY,
    onError = AwareBackground,
    outline = Color(0xFF77847C),
)

@Composable
fun AwareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AwareColorScheme,
        typography = AwareTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content,
    )
}
