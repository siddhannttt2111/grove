package com.grove.autopilot.theme

import androidx.compose.foundation.shape.RoundedCornerShape
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

object GroveColor {
    val Paper = Color(0xFFFFFFFF)
    val Ink = Color(0xFF14241A)
    val Moss = Color(0xFF1B7A45)
    val MossDeep = Color(0xFF0F4D2C)
    val Leaf = Color(0xFF3E8F5A)
    val Mist = Color(0xFFE7F3EB)
    val Mute = Color(0xFF5E6B63)
    val Line = Color(0xFFD5E3DA)
    val Glass = Color.White.copy(alpha = 0.80f)
}

private val Scheme = lightColorScheme(
    primary = GroveColor.Moss,
    onPrimary = Color.White,
    secondary = GroveColor.Leaf,
    onSecondary = Color.White,
    background = GroveColor.Paper,
    onBackground = GroveColor.Ink,
    surface = GroveColor.Paper,
    onSurface = GroveColor.Ink,
    surfaceVariant = GroveColor.Mist,
    onSurfaceVariant = GroveColor.Mute,
    outline = GroveColor.Line,
)

private val Type = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        letterSpacing = (-0.6).sp,
        color = GroveColor.Ink,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        color = GroveColor.Ink,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        color = GroveColor.Ink,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = GroveColor.Mute,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = GroveColor.Ink,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
        color = GroveColor.Mute,
    ),
)

private val GroveShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun GroveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Type,
        shapes = GroveShapes,
        content = content,
    )
}
