package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.Voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-76 «Одна петля в трёх тональностях» (ADR-073): the loop and the asked place stay, the key changes. */
class Loop3DriverTest {
    private fun tasks(level: Int, n: Int, seed: Int = 1): List<LoopTask> {
        val d = Loop3Driver()
        return List(n) { d.nextTask(DriverContext(Random(seed * 1000 + it), level, emptyList())) }
    }

    @Test
    fun `three scenes in a row ask the same chord of the same loop in three different keys`() {
        for (level in 1..3) for (seed in 1..8) {
            tasks(level, 9, seed).chunked(3).forEach { (a, b, c) ->
                assertEquals(listOf(1, 2, 3), listOf(a.scene, b.scene, c.scene))
                assertEquals(a.loop, b.loop); assertEquals(b.loop, c.loop)
                assertEquals(a.asked, b.asked); assertEquals(b.asked, c.asked)
                assertEquals(3, setOf(a.key, b.key, c.key).size, "three different keys")
                assertEquals(3, setOf(a.key.tonic.pitchClass, b.key.tonic.pitchClass, c.key.tonic.pitchClass).size)
                assertEquals(a.key.mode, c.key.mode)
                assertTrue(a.answer in a.choices, "the asked chord can be chosen: ${ChordParser.formatRoman(a.answer)} in ${a.choices}")
            }
        }
    }

    @Test
    fun `level 1 keeps to the four open keys and major, level 2 uses any key, minor comes with level 3`() {
        val open = setOf(0, 2, 7, 9)
        assertTrue(tasks(1, 60).all { it.key.tonic.pitchClass.value in open && it.key.mode == Mode.MAJOR })
        val l2 = tasks(2, 90)
        assertTrue(l2.any { it.key.tonic.pitchClass.value !in open }, "any key")
        assertTrue(l2.all { it.key.mode == Mode.MAJOR })
        assertEquals(setOf(Mode.MAJOR, Mode.MINOR), tasks(3, 120).map { it.key.mode }.toSet())
    }

    @Test
    fun `the asked chord is never the one the loop opens on and the loop has four chords`() {
        for (level in 1..3) tasks(level, 60).forEach { t ->
            assertEquals(4, t.loop.size)
            assertTrue(t.answer != t.loop[0], "home is not asked for in the first chord")
            assertTrue(t.asked in 1..3)
        }
    }

    @Test
    fun `every scene sets its key by a scale over the hum, then the loop is played twice with the tonic held`() {
        val t = tasks(1, 3).first()
        assertNotNull(t.intro)
        assertNotNull(t.introDrone)
        assertTrue(t.drone.isNotEmpty())
        assertEquals(t.question, t.prompt.audio)
        assertTrue(t.question.notes.any { it.voice == Voice.BASS }, "the tonic is held under the chords")
        assertEquals(2 * t.loop.size * Loop3Driver.LOOP_BEATS, t.question.lengthBeats, 1.0, "the loop twice")
        assertNotNull(t.key)
    }

    @Test
    fun `a right answer is rewarded by the loop, a wrong one hears the loop with the chosen chord, then the right one, and says what it does`() {
        val d = Loop3Driver()
        repeat(12) { seed ->
            val t = d.nextTask(DriverContext(Random(seed), 2, emptyList()))
            val right = d.evaluate(t, Answer.ChordChoice(t.answer))
            assertTrue(right.correct)
            assertNull(right.correction)
            assertNull(right.contrast)
            assertEquals(t.heard, right.confirmation)
            val other = t.choices.first { it.root.semitones != t.answer.root.semitones }
            val wrong = d.evaluate(t, Answer.ChordChoice(other))
            assertFalse(wrong.correct)
            val fix = requireNotNull(wrong.correction) { "rule 5" }
            assertNotNull(wrong.contrast)
            assertEquals(2 * t.heard.lengthBeats + 0.8, fix.lengthBeats, 1.0, "what was chosen, then the right loop")
            assertEquals(ChordParser.formatRoman(t.answer), wrong.expectedCode)
            assertNotNull(d.evaluate(t, Answer.Pass).correction)
            assertNull(d.evaluate(t, Answer.Pass).contrast, "«не знаю» has no wrong sound to set against")
        }
    }

    @Test
    fun `the scene is in the item key so the keys can be compared in the statistics, and the loop is not a review item`() {
        val ts = tasks(1, 3)
        assertEquals(listOf("s1", "s2", "s3"), ts.map { it.itemKey.split(':')[2] })
        assertNull(TirDriver.reviewKey(ts[0].itemKey))
        assertEquals(3, Levels.TIR_LOOP3.size)
    }

    @Test
    fun `a new loop does not repeat the last one`() {
        val d = Loop3Driver()
        val ts = List(30) { d.nextTask(DriverContext(Random(it), 2, emptyList())) }
        ts.chunked(3).map { it.first().loop }.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
    }
}
