package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.ChordQuality
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-50 «Цвет аккорда» (ADR-048): a chord on a pitch of chance, its quality is picked. */
class ColorDriverTest {
    private fun tasks(level: Int, n: Int, seed: Int = 1): List<ColorTask> {
        val d = ColorDriver(KeyPicker(Random(seed)))
        return List(n) { d.nextTask(DriverContext(Random(seed * 100 + it), level, emptyList())) }
    }

    @Test
    fun `the chord sounds on a root of chance, with no key and no drone`() {
        for (level in 1..4) tasks(level, 60).forEach { t ->
            assertTrue(t.quality in t.choices)
            assertEquals(ColorDriver.pad(level), t.choices)
            assertNull(t.prompt.key, "there is no key: nothing to show (rule 1)")
            assertNull(t.drone)
            assertEquals(t.question, t.prompt.audio)
            assertEquals("color:L$level:" + t.quality.name, t.itemKey)
            val root = t.key.tonic.pitchClass.value
            val heard = t.question.notes.filter { it.voice == Voice.PIANO }.map { Math.floorMod(it.note!!.pitchClass.value - root, 12) }.toSet()
            assertEquals(t.quality.pitchClassSet, heard, "the chord of the quality, on the root")
            assertEquals(t.quality.intervals.size + 1, t.apart.notes.size, "taken apart tone by tone, and back to the root")
            assertEquals(t.quality.intervals.size, t.tones.size)
        }
        assertTrue(tasks(2, 80).map { it.key.tonic }.toSet().size >= 8, "the root is random")
    }

    @Test
    fun `the ladder goes from two colours to all of them`() {
        assertEquals(listOf(ChordQuality.MAJOR, ChordQuality.MINOR), ColorDriver.pad(1))
        assertEquals(6, ColorDriver.pad(2).size)
        assertTrue(ColorDriver.pad(3).all { it.intervals.size == 4 }, "the seventh chords")
        assertEquals(ColorDriver.pad(2) + ColorDriver.pad(3), ColorDriver.pad(4))
        for (level in 1..4) assertEquals(ColorDriver.pad(level).toSet(), tasks(level, 300).map { it.quality }.toSet(), "every colour of level $level is asked")
        assertTrue(tasks(2, 200).zipWithNext().none { (a, b) -> a.quality == b.quality }, "not the same colour twice in a row")
        assertTrue(tasks(1, 200).zipWithNext().any { (a, b) -> a.quality == b.quality }, "two colours may repeat: «not the last one» must not answer")
    }

    @Test
    fun `a right pick hears the chord taken apart, a wrong one the chord again first`() {
        val d = ColorDriver(KeyPicker(Random(3)))
        repeat(30) {
            val t = d.nextTask(DriverContext(Random(it), 4, emptyList()))
            val right = d.evaluate(t, Answer.QualityChoice(t.quality))
            assertTrue(right.correct)
            assertEquals(t.apart, right.confirmation)
            val other = t.choices.first { q -> q != t.quality }
            val wrong = d.evaluate(t, Answer.QualityChoice(other))
            assertFalse(wrong.correct)
            assertTrue(wrong.correction!!.notes.size > t.question.notes.size, "rule 5: the chord, then its tones")
            assertEquals(other.name, wrong.givenCode)
            assertNotNull(d.evaluate(t, Answer.Pass).correction)
            assertFalse(Regex("[A-H]").containsMatchIn(right.expectedText.replace("VII", "").replace("VI", "").replace("IV", "").replace("V", "").replace("I", "")), "no note letters: ${right.expectedText}")
        }
    }

    @Test
    fun `a colour that is missed comes back more often`() {
        val d = ColorDriver(KeyPicker(Random(5)))
        val miss = AttemptRecord(Variant.TIR_COLOR, 2, "color:L2:AUGMENTED", "AUGMENTED", "MAJOR", false, 4_000, null, 0, null, 0)
        val hits = ColorDriver.pad(2).filter { it != ChordQuality.AUGMENTED }
            .flatMap { q -> List(5) { AttemptRecord(Variant.TIR_COLOR, 2, "color:L2:${q.name}", q.name, q.name, true, 900, null, 0, null, 0) } }
        val history = hits + List(5) { miss }
        val asked = List(600) { d.nextTask(DriverContext(Random(it), 2, history)).quality }.count { it == ChordQuality.AUGMENTED }
        assertTrue(asked > 600 / 6 * 1.5, "asked $asked of 600")
    }
}
