package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Degree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnchorHintsTest {
    private fun a(variant: Variant, expected: String, ok: Boolean) =
        AttemptRecord(variant, 1, "k", expected, "", ok, 1500, null, 0, null, 0)

    @Test
    fun `missed single degrees, most missed first, one per pitch`() {
        val attempts = listOf(
            a(Variant.TIR_CLASSIC, "3", false),
            a(Variant.TIR_CLASSIC, "5", false),
            a(Variant.TIR_CLASSIC, "5", false),
            a(Variant.TIR_CLASSIC, "1", true),
            a(Variant.TIR_OVER_CHORDS, "♭3", false),
            a(Variant.KAMERTON_SING, "#4", false),
        )
        val missed = AnchorHints.missed(attempts)
        assertEquals(Degree.parse("5"), missed.first())
        assertEquals(listOf(7, 4, 3, 6), missed.map { it.semitones })
    }

    @Test
    fun `chords, melodies and frets are not degree answers`() {
        val attempts = listOf(
            a(Variant.TIR_CLASSIC, "ii", false),
            a(Variant.TIR_CLASSIC, "1 3 5", false),
            a(Variant.HUNT_FIND, "3", false),
            a(Variant.HUNT_REVERSE, "E", false),
        )
        assertTrue(AnchorHints.missed(attempts).isEmpty())
    }
}
