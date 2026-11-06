package com.kazaeza.muzykalka.ui.theme

import androidx.compose.ui.graphics.Color
import com.kazaeza.muzykalka.music.Degree

/**
 * One fixed colour per degree, used EVERYWHERE (keypad, fretboard dots, formulas, heatmaps).
 * A second, visual name for each sound: the tonic is always warm gold, the fifth always blue.
 * Never the only cue — the degree label is always printed too (colour-blind safe).
 */
object DegreeColors {
    private val bySemitone = listOf(
        Color(0xFFFFB454), // 1  tonic — gold
        Color(0xFFB65F7A), // ♭2
        Color(0xFFFF8A65), // 2
        Color(0xFF9C7BEA), // ♭3
        Color(0xFF7FD1C7), // 3
        Color(0xFF8BC34A), // 4
        Color(0xFFE57373), // ♯4/♭5
        Color(0xFF5AA9FF), // 5
        Color(0xFF7E57C2), // ♭6
        Color(0xFFF06292), // 6
        Color(0xFF4DB6AC), // ♭7
        Color(0xFFFFD54F), // 7 — leading tone, close to gold
    )

    fun of(degree: Degree): Color = bySemitone[degree.semitones]
}
