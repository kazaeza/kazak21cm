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
 * Hues are spread so that the degrees of one key (1 2 3 4 5 6 7, or 1 2 ♭3 4 5 ♭6 ♭7) differ most.
 */
object DegreeColors {
    private val bySemitone = listOf(
        Color(0xFFE9B10D), // 1  tonic — gold
        Color(0xFFFF93BF), // ♭2 — pink
        Color(0xFFFFA063), // 2  — orange
        Color(0xFFD79FFF), // ♭3 — violet
        Color(0xFF00D2EB), // 3  — cyan
        Color(0xFF72D577), // 4  — green
        Color(0xFF5FC7FF), // ♯4/♭5 — sky
        Color(0xFF86BFFF), // 5  — blue
        Color(0xFFFF9A97), // ♭6 — coral
        Color(0xFFF392EB), // 6  — magenta
        Color(0xFF00D8BE), // ♭7 — teal
        Color(0xFFB2C83E), // 7  — lime, the leading tone next to gold
    )

    fun of(degree: Degree): Color = bySemitone[degree.semitones]

    fun ofSemitone(semitones: Int): Color = bySemitone[Math.floorMod(semitones, 12)]

    /** Top-to-bottom gradient for keys and chips: lighter top, deeper bottom. */
    fun gradient(degree: Degree): List<Color> = gradient(of(degree))

    fun gradient(c: Color): List<Color> = listOf(lerp(c, Color.White, 0.25f), c, lerp(c, Color.Black, 0.18f))
}
