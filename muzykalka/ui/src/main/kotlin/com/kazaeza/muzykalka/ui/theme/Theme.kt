package com.kazaeza.muzykalka.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kazaeza.muzykalka.ui.R

/** The juicy palette: deep ink background, saturated neon accents. */
object Juicy {
    val Ink = Color(0xFF0B0A16)
    val Ink2 = Color(0xFF15132B)
    val Ink3 = Color(0xFF1F1C3D)
    val Ink4 = Color(0xFF2A2650)
    val Line = Color(0x26FFFFFF)
    val Text = Color(0xFFF6F4FF)
    val TextDim = Color(0xFFADA7CF)
    val TextFaint = Color(0xFF6E6896)

    val Hot = Color(0xFFFF4D6D)
    val Orange = Color(0xFFFF8A1F)
    val Gold = Color(0xFFFFC53D)
    val Lime = Color(0xFF9BF25C)
    val Mint = Color(0xFF2EE6B6)
    val Cyan = Color(0xFF38D7FF)
    val Blue = Color(0xFF5B7CFF)
    val Violet = Color(0xFFA06BFF)
    val Magenta = Color(0xFFFF4FD8)

    val Good = Color(0xFF3DFF9A)
    val Bad = Color(0xFFFF4D6D)
    val Paper = Color(0xFFFFF3DA)
    val PaperInk = Color(0xFF3B2412)
}

/** Gradients per area. Modes map onto these in :app (ui does not know the game catalogue). */
object Accents {
    val Hero = listOf(Juicy.Orange, Juicy.Hot, Juicy.Magenta)
    val Hunt = listOf(Juicy.Lime, Juicy.Mint)
    val Tir = listOf(Juicy.Orange, Juicy.Hot)
    val Kamerton = listOf(Juicy.Cyan, Juicy.Blue)
    val Parrot = listOf(Juicy.Gold, Juicy.Mint)
    val Taper = listOf(Juicy.Gold, Juicy.Orange)
    val Recorder = listOf(Juicy.Violet, Juicy.Magenta)
    val Calm = listOf(Juicy.Blue, Juicy.Violet)
    val Good = listOf(Juicy.Good, Juicy.Mint)
    val Bad = listOf(Juicy.Hot, Juicy.Orange)
}

val Unbounded = FontFamily(
    Font(R.font.unbounded, FontWeight.Medium),
    Font(R.font.unbounded, FontWeight.SemiBold),
    Font(R.font.unbounded, FontWeight.Bold),
    Font(R.font.unbounded, FontWeight.ExtraBold),
    Font(R.font.unbounded, FontWeight.Black),
)

val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold),
    Font(R.font.manrope, FontWeight.Bold),
    Font(R.font.manrope, FontWeight.ExtraBold),
)

private val Colors = darkColorScheme(
    primary = Juicy.Orange,
    onPrimary = Color(0xFF1A0B00),
    primaryContainer = Juicy.Ink4,
    onPrimaryContainer = Juicy.Text,
    secondary = Juicy.Cyan,
    onSecondary = Color(0xFF00141A),
    tertiary = Juicy.Violet,
    background = Juicy.Ink,
    onBackground = Juicy.Text,
    surface = Juicy.Ink2,
    onSurface = Juicy.Text,
    surfaceVariant = Juicy.Ink3,
    onSurfaceVariant = Juicy.TextDim,
    surfaceContainer = Juicy.Ink2,
    surfaceContainerHigh = Juicy.Ink3,
    surfaceContainerHighest = Juicy.Ink4,
    outline = Juicy.Line,
    outlineVariant = Juicy.Line,
    error = Juicy.Bad,
)

private val Type = Typography(
    displayLarge = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Black, fontSize = 96.sp, lineHeight = 100.sp, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Black, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 50.sp),
    headlineLarge = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp),
)

@Composable
fun MuzykalkaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
