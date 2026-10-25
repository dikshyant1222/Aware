package com.aware.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun textStyle(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
)

val AwareTypography = Typography(
    displayLarge = textStyle(64, 72, FontWeight.Bold),
    displayMedium = textStyle(52, 60, FontWeight.Bold),
    displaySmall = textStyle(44, 52, FontWeight.Bold),
    headlineLarge = textStyle(36, 44, FontWeight.Bold),
    headlineMedium = textStyle(32, 40, FontWeight.Bold),
    headlineSmall = textStyle(28, 36, FontWeight.Bold),
    titleLarge = textStyle(32, 40, FontWeight.Bold),
    titleMedium = textStyle(28, 36, FontWeight.SemiBold),
    titleSmall = textStyle(28, 36, FontWeight.SemiBold),
    bodyLarge = textStyle(22, 32),
    bodyMedium = textStyle(20, 30),
    bodySmall = textStyle(20, 30),
    labelLarge = textStyle(20, 28, FontWeight.SemiBold),
    labelMedium = textStyle(20, 28, FontWeight.SemiBold),
    labelSmall = textStyle(20, 28, FontWeight.SemiBold),
)
