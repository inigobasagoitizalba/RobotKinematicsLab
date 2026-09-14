package com.robotkinematicslab.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LabSans = FontFamily.SansSerif
private val LabMono = FontFamily.Monospace

val Typography = Typography(
    displaySmall = labText(FontWeight.Bold, 36, 42, -0.5f),
    headlineLarge = labText(FontWeight.Bold, 30, 36, -0.3f),
    headlineMedium = labText(FontWeight.Bold, 26, 32, -0.2f),
    headlineSmall = labText(FontWeight.SemiBold, 23, 29, 0f),
    titleLarge = labText(FontWeight.SemiBold, 21, 27, 0f),
    titleMedium = labText(FontWeight.SemiBold, 16, 22, 0.1f),
    titleSmall = labText(FontWeight.SemiBold, 14, 20, 0.1f),
    bodyLarge = labText(FontWeight.Normal, 16, 24, 0.15f),
    bodyMedium = labText(FontWeight.Normal, 14, 21, 0.15f),
    bodySmall = labText(FontWeight.Normal, 12, 18, 0.2f),
    labelLarge = labText(FontWeight.SemiBold, 14, 19, 0.1f),
    labelMedium = labText(FontWeight.SemiBold, 12, 17, 0.2f),
    labelSmall = labText(FontWeight.Medium, 10, 15, 0.4f, LabMono)
)

internal fun appTypography(useBoldText: Boolean): Typography =
    if (!useBoldText) {
        Typography
    } else {
        Typography.copy(
            bodyLarge = Typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            bodyMedium = Typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            bodySmall = Typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            labelLarge = Typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            labelMedium = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            labelSmall = Typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
    }

private fun labText(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Float,
    family: FontFamily = LabSans
): TextStyle =
    TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp
    )
