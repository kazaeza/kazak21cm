package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-57 (ADR-056): the songs a hint can name. */
class SongHintsTest {
    @Test
    fun `a hint is a few notes that lean on its sound`() {
        for (h in SongHint.ALL) {
            assertTrue(h.melody.size in SongHint.MIN_NOTES..SongHint.MAX_NOTES, "${h.title}: ${h.melodyText}")
            assertTrue(h.melody.any { it.degree sameSoundAs h.degree }, "${h.title} leans on ${h.degree}")
            assertTrue(h.title.isNotBlank())
        }
    }

    @Test
    fun `every sound but the tonic has a song, the tonic has none`() {
        for (s in 1..11) assertTrue(SongHint.forSemitone(s).isNotEmpty(), "a song for $s")
        assertTrue(SongHint.forSemitone(0).isEmpty())
        assertEquals(SongHint.forSemitone(6).first(), SongHint.forDegree(Degree.FLAT_FIVE))
    }

    @Test
    fun `the line reads like an own anchor`() {
        assertEquals("как в «С днём рождения»", SongHint.forDegree(Degree.SIX)?.line)
    }

    @Test
    fun `the notes are in the key and play`() {
        val key = Key(NoteName(Letter.D), Mode.MAJOR)
        for (h in SongHint.ALL) {
            val score = h.score(key)
            assertNotNull(score)
            assertEquals(h.melody.size, score.notes.size, h.title)
            assertTrue(score.lengthSeconds > 0, h.title)
        }
    }

    @Test
    fun `the degrees of a hint are written as the song has them`() {
        assertEquals("5 5 6 5", SongHint.forDegree(Degree.SIX)?.melodyText)
        assertEquals("1 1 ♭3 1", SongHint.forDegree(Degree.FLAT_THREE)?.melodyText)
        // «С днём рождения» ends on the leading tone one octave below the tonic's octave above
        assertEquals(DegreeNote(Degree.ONE, 1), SongHint.forDegree(Degree.SEVEN)?.melody?.get(2))
        assertEquals(HintStyle.FEEL, HintStyle.entries.first())
    }
}
