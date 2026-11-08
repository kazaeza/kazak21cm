package com.kazaeza.muzykalka.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.kazaeza.muzykalka.music.Degree

/**
 * One fixed colour per degree, used EVERYWHERE (keypad, fretboard dots, formulas, heatmaps).
 * A second, visual name for each sound: the tonic is always gold, the fifth always blue.
 * Never the only cue — the degree label is always printed too (colour-blind safe).
 *
 * O-13 (ADR-027): all twelve share one lightness (OKLCH L = 0.79), so no colour is darker than the
 * rest — the old violets were lost on the dark background. Dark ink on any of them reads at ≥ 9:1.
 * O-25: each at the most chroma sRGB allows at that lightness (neon, not pastel).
 * Hues are spread so that the degrees of one key (1 2 3 4 5 6 7, or 1 2 ♭3 4 5 ♭6 ♭7) differ most.
 */
object DegreeColors {
    private val bySemitone = listOf(
        Color(0xFFE8B11A), // 1  tonic — gold
        Color(0xFFFD95BF), // ♭2 — pink
        Color(0xFFFDA166), // 2  — orange
        Color(0xFFD6A0FD), // ♭3 — violet
        Color(0xFF1FD1E9), // 3  — cyan
        Color(0xFF1FE044), // 4  — green
        Color(0xFF63C7FD), // ♯4/♭5 — sky
        Color(0xFF88BFFD), // 5  — blue
        Color(0xFFFD9B98), // ♭6 — coral
        Color(0xFFFD87F5), // 6  — magenta
        Color(0xFF1FD7BE), // ♭7 — teal
        Color(0xFFB1C91B), // 7  — lime, the leading tone next to gold
    )

    fun of(degree: Degree): Color = bySemitone[degree.semitones]

    fun ofSemitone(semitones: Int): Color = bySemitone[Math.floorMod(semitones, 12)]

    /** Top-to-bottom gradient for keys and chips: lighter top, deeper bottom. */
    fun gradient(degree: Degree): List<Color> = gradient(of(degree))

    fun gradient(c: Color): List<Color> = listOf(lerp(c, Color.White, 0.25f), c, lerp(c, Color.Black, 0.18f))
}
