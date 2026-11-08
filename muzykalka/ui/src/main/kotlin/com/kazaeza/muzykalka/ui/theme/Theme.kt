package com.kazaeza.muzykalka.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kazaeza.muzykalka.ui.R

/**
 * O-24 «Обертон»: warm near-black, light instead of paint. The names stay (every screen uses them), the values
 * moved from violet ink and neon to a quiet warm dark with soft glow accents.
 */
object Juicy {
    val Ink = Color(0xFF0A0909)
    val Ink2 = Color(0xFF121110)
    val Ink3 = Color(0xFF1A1817)
    val Ink4 = Color(0xFF24211F)
    val Line = Color(0x17FFFFFF)
    val Text = Color(0xFFF2EEE9)
    val TextDim = Color(0xFF9C958E)
    val TextFaint = Color(0xFF5C5752)

    val Hot = Color(0xFFFF5A5F)
    val Orange = Color(0xFFFF8A3D)
    val Gold = Color(0xFFFFC46B)
    val Lime = Color(0xFFC8E86B)
    val Mint = Color(0xFF6FE3C1)
    val Cyan = Color(0xFF6CD3F0)
    val Blue = Color(0xFF7A8CFF)
    val Violet = Color(0xFFB38CFF)
    val Magenta = Color(0xFFF07AD8)

    val Good = Color(0xFF8FF0B4)
    val Bad = Color(0xFFFF6B6B)
    val Paper = Color(0xFFF2EEE9)
    val PaperInk = Color(0xFF15120F)
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

/** Dot-matrix numerals (O-24): scores, timers, counters. Digits and signs only — no letters, no ♭. */
val Doto = FontFamily(Font(R.font.doto, FontWeight.Bold))

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
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 92.sp, lineHeight = 96.sp, letterSpacing = (-4).sp),
    displayMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 60.sp, lineHeight = 64.sp, letterSpacing = (-2.5).sp),
    displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 48.sp, letterSpacing = (-1.5).sp),
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp),
)

/** Dot-matrix numbers: the score, the timer, big counters (O-24). */
object Numbers {
    val Large = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 76.sp)
    val Medium = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp)
    val Small = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp)
}

/** Motion (O-24): one calm curve for everything that moves. */
object Motion {
    const val SHORT = 220
    const val MEDIUM = 420
    const val LONG = 900
}

@Composable
fun MuzykalkaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type) {
        // Text outside a Surface would default to black: everything here sits on dark ink.
        CompositionLocalProvider(LocalContentColor provides Juicy.Text, content = content)
    }
}
