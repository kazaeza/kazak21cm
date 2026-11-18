package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.SongHint
import com.kazaeza.muzykalka.music.Voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-65 «Созвучие» (ADR-064): two sounds struck together, the upper one is named from the lower. */
class DyadDriverTest {
    private fun driver(seed: Int = 1, tune: (Degree, com.kazaeza.muzykalka.music.Key) -> com.kazaeza.muzykalka.music.Score? = { _, _ -> null }) =
        TirDriver(KeyPicker(Random(seed)), variant = Variant.TIR_DYAD, tune = tune)

    @Test
    fun `two sounds start together, nothing hums, the upper one is the answer`() {
        val d = driver()
        for (level in 1..4) repeat(30) {
            val t = d.nextTask(DriverContext(Random(level * 100 + it), level, emptyList()))
            assertTrue(t.dyad)
            assertEquals(TirKind.NOTE, t.kind)
            assertNull(t.drone, "no hum: the pair is the whole question")
            assertNull(t.intro)
            val notes = t.question.notes
            assertEquals(2, notes.size)
            assertTrue(notes.all { n -> n.startBeat == 0.0 }, "struck together")
            val (low, high) = notes.map { n -> requireNotNull(n.note).value }.sorted()
            assertEquals(t.tonic.value, low)
            val semis = TirDriver.DYAD_STEPS[level - 1]
            val asked = t.notes.single().degree
            assertTrue(semis.contains(asked.semitones), "on the pad of the level")
            assertEquals(if (asked.semitones == 0) 12 else asked.semitones, high - low, "the upper sound is the degree above the lower")
            assertEquals(semis.size, t.noteChoices.size)
            assertTrue(t.itemKey.startsWith("tir:dyad:L$level:"))
            assertNull(TirDriver.reviewKey(t.itemKey), "kept out of the Tir's own review")
        }
    }

    @Test
    fun `the lower sound changes, and on the last level it wanders across the registers`() {
        val d = driver(3)
        val lows = List(40) { d.nextTask(DriverContext(Random(it), 4, emptyList())).tonic.value }
        assertTrue(lows.toSet().size > 6)
        assertTrue(lows.min() < 52 && lows.max() > 60)
    }

    @Test
    fun `a right answer takes the pair apart and strikes it again, a wrong one first hears it again (rule 5)`() {
        val d = driver(5)
        repeat(15) {
            val t = d.nextTask(DriverContext(Random(it), 3, emptyList()))
            val right = d.evaluate(t, Answer.DegreeChoice(t.notes.single().degree))
            assertTrue(right.correct)
            val c = requireNotNull(right.confirmation)
            assertEquals(4, c.notes.size, "apart, then together")
            assertTrue(c.notes.all { n -> n.voice == Voice.PIANO })
            val wrong = d.evaluate(t, Answer.Pass)
            val fix = requireNotNull(wrong.correction)
            assertEquals(6, fix.notes.size, "together, apart, together")
            assertNull(wrong.confirmation)
        }
    }

    @Test
    fun `where songs are chosen a miss ends on the tune of the upper sound`() {
        val d = driver(7) { deg, key -> SongHint.forDegree(deg)?.score(key) }
        var withSong = 0
        repeat(20) {
            val t = d.nextTask(DriverContext(Random(it), 3, emptyList()))
            val song = SongHint.forDegree(t.notes.single().degree) ?: return@repeat
            withSong++
            assertEquals(6 + song.melody.size + 1, requireNotNull(d.evaluate(t, Answer.Pass).correction).notes.size)
            assertEquals(4, requireNotNull(d.evaluate(t, Answer.DegreeChoice(t.notes.single().degree)).confirmation).notes.size, "a right answer stays short")
        }
        assertTrue(withSong > 5)
        assertNotNull(Levels.TIR_DYAD)
        assertTrue(Variant.TIR_DYAD in Training.EAR.plan.map { it.variant })
    }
}
