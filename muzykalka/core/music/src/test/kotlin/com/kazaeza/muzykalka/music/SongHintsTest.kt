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
        assertEquals("как в «Twinkle, twinkle»", SongHint.forDegree(Degree.SIX)?.line)
    }

    @Test
    fun `no two sounds are hung on the same song (O-64)`() {
        val mains = (1..11).map { requireNotNull(SongHint.forSemitone(it).first()).title }
        assertEquals(mains.size, mains.toSet().size, mains.toString())
    }

    @Test
    fun `the tune plays in its rhythm, the notes to remember accented, then the first of them alone`() {
        val key = Key(NoteName(Letter.D), Mode.MAJOR)
        for (h in SongHint.ALL) {
            val score = h.score(key)
            assertEquals(h.melody.size + 1, score.notes.size, h.title)
            val tune = score.notes.dropLast(1)
            // the starts follow the lengths
            var at = 0.0
            tune.forEachIndexed { i, n ->
                assertEquals(at, n.startBeat, 1e-9, "${h.title} note $i")
                at += h.beats[i]
            }
            tune.forEachIndexed { i, n ->
                if (i in h.targets) assertEquals(SongHint.ACCENT, n.velocity, h.title) else assertEquals(SongHint.SOFT, n.velocity, h.title)
            }
            val echo = score.notes.last()
            assertTrue(echo.startBeat > at, "the echo after a breath")
            assertEquals(tune[h.targets.min()].note, echo.note, "${h.title}: the echo is the note to remember")
            assertTrue(echo.durationBeats >= 2.0)
            assertTrue(h.cue.isNotBlank())
        }
    }

    @Test
    fun `a tune is written with stars and lengths`() {
        val h = SongHint.parse("7", "С днём рождения", "5/0.75 5/0.25 6 5 1' *7/2", "x")
        assertEquals(listOf(0.75, 0.25, 1.0, 1.0, 1.0, 2.0), h.beats)
        assertEquals(setOf(5), h.targets)
        assertEquals(DegreeNote(Degree.ONE, 1), h.melody[4])
        assertEquals(DegreeNote(Degree.SEVEN), h.target)
        assertEquals("1 1 5 5 6 6 5", SongHint.forDegree(Degree.SIX)?.melodyText)
        assertEquals(HintStyle.FEEL, HintStyle.entries.first())
    }
}
