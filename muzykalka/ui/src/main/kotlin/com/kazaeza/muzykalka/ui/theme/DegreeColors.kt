package com.kazaeza.muzykalka.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.kazaeza.muzykalka.music.Degree

/**
 * One fixed colour per degree, used EVERYWHERE (keypad, fretboard dots, formulas, heatmaps).
 * A second, visual name for each sound: the tonic is always gold, the fifth always blue.
 * Never the only cue — the degree label is always printed too (colour-blind safe).
 */
object DegreeColors {
    private val bySemitone = listOf(
        Color(0xFFFFC53D), // 1  tonic — gold
        Color(0xFFFF6B9A), // ♭2
        Color(0xFFFF8A3D), // 2
        Color(0xFFB57BFF), // ♭3
        Color(0xFF3DDCFF), // 3
        Color(0xFF8CF55A), // 4
        Color(0xFFFF5A5A), // ♯4/♭5
        Color(0xFF5B8CFF), // 5
        Color(0xFF9E6BFF), // ♭6
        Color(0xFFFF5BD8), // 6
        Color(0xFF2EE6B6), // ♭7
        Color(0xFFFFE45C), // 7 — leading tone, next to gold
    )

    fun of(degree: Degree): Color = bySemitone[degree.semitones]

    fun ofSemitone(semitones: Int): Color = bySemitone[Math.floorMod(semitones, 12)]

    /** Top-to-bottom gradient for keys and chips: lighter top, deeper bottom. */
    fun gradient(degree: Degree): List<Color> = gradient(of(degree))

    fun gradient(c: Color): List<Color> = listOf(lerp(c, Color.White, 0.25f), c, lerp(c, Color.Black, 0.18f))
}
