package com.friendsvibe.autopilot.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFFFFFFFF)
val Paper = Color(0xFF14241A)
val Amber = Color(0xFF1B7A45)
val Clay = Color(0xFF0F4D2C)
val Mist = Color(0xFF5E6B63)
val Panel = Color(0xCCFFFFFF)
val Line = Color(0xFFD5E3DA)
val Calm = Color(0xFF3E8F5A)
val Wash = Color(0xFFE7F3EB)

private val scheme = lightColorScheme(
    background = Ink,
    surface = Ink,
    primary = Amber,
    onPrimary = Color.White,
    secondary = Calm,
    onSecondary = Color.White,
    onBackground = Paper,
    onSurface = Paper,
    error = Clay,
    outline = Line,
)

private val typography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
    ),
)

@Composable
fun FriendsVibeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
