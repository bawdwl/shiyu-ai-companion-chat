package com.shiyu.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val ShiyuColors = lightColorScheme(
    primary = Color(0xFF2D684F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EEE5),
    onPrimaryContainer = Color(0xFF173D2D),
    secondary = Color(0xFF6E5E86),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE8F5),
    onSecondaryContainer = Color(0xFF3E3154),
    tertiary = Color(0xFF9B604A),
    tertiaryContainer = Color(0xFFF4E6DF),
    onTertiaryContainer = Color(0xFF572F23),
    background = Color(0xFFF6F7F5),
    onBackground = Color(0xFF202622),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202622),
    surfaceVariant = Color(0xFFEDF0EC),
    onSurfaceVariant = Color(0xFF68716B),
    outline = Color(0xFFD5DAD5),
    outlineVariant = Color(0xFFE9ECE8),
    error = Color(0xFFA04640),
)

private val ShiyuTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 39.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 33.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 15.sp,
    ),
)

private val ShiyuShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun ShiyuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShiyuColors,
        typography = ShiyuTypography,
        shapes = ShiyuShapes,
        content = content,
    )
}
