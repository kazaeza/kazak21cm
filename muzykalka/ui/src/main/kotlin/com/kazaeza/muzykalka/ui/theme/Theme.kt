package com.kazaeza.muzykalka.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Always dark: practice happens on a music stand, often in the evening. High contrast, big type. */
private val Colors = darkColorScheme(
    primary = Color(0xFFFFB454),
    onPrimary = Color(0xFF231400),
    secondary = Color(0xFF7FD1C7),
    onSecondary = Color(0xFF00201C),
    tertiary = Color(0xFFB4A7FF),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFECEDEF),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF232731),
    onSurfaceVariant = Color(0xFFB9BEC9),
    error = Color(0xFFFF6B6B),
)

private val Type = Typography(
    displayLarge = TextStyle(fontSize = 88.sp, fontWeight = FontWeight.Black, lineHeight = 92.sp),
    displayMedium = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold, lineHeight = 60.sp),
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun MuzykalkaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
