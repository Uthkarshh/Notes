package com.notesapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val baseType = Typography()
val AppTypography = baseType.copy(
    headlineMedium = baseType.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
    titleMedium = baseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = baseType.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = baseType.labelMedium.copy(fontFeatureSettings = "tnum", letterSpacing = 0.sp),
)
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(28.dp),
)
