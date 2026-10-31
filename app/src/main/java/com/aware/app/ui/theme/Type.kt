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
    headlineLarge = textStyle(28, 36, FontWeight.SemiBold),
    headlineMedium = textStyle(22, 28, FontWeight.SemiBold),
    headlineSmall = textStyle(20, 26, FontWeight.SemiBold),
    titleLarge = textStyle(18, 24, FontWeight.SemiBold),
    titleMedium = textStyle(16, 22, FontWeight.SemiBold),
    titleSmall = textStyle(15, 21, FontWeight.SemiBold),
    bodyLarge = textStyle(16, 23),
    bodyMedium = textStyle(14, 20),
    bodySmall = textStyle(13, 18),
    labelLarge = textStyle(14, 20, FontWeight.SemiBold),
    labelMedium = textStyle(13, 18, FontWeight.SemiBold),
    labelSmall = textStyle(12, 18, FontWeight.Medium),
)

// Emergency content keeps its original accessible scale independently of normal UI.
val EmergencyTitle = textStyle(52, 60, FontWeight.Bold)
val EmergencyMessage = textStyle(32, 40, FontWeight.Bold)
val EmergencyStatus = textStyle(32, 40, FontWeight.Bold)
