package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.ChordFunction
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.ChordQuality
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.Voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-75 «Слои аккорда» (ADR-072): the job of a chord is asked apart from what it is made of. */
class LayersDriverTest {
    private fun driver(seed: Int = 1) = LayersDriver(KeyPicker(Random(seed)))

    private fun tasks(d: LayersDriver, level: Int, n: Int, seed: Int = 7): List<LayersTask> =
        List(n) { d.nextTask(DriverContext(Random(seed + it), level, emptyList())) }

    @Test
    fun `level 1 asks the job on five triads, in a major key, evenly`() {
        val ts = tasks(driver(), 1, 90)
        assertTrue(ts.all { it.layer == Layer.FUNCTION && it.key.mode == Mode.MAJOR })
        assertEquals(setOf("I", "ii", "IV", "V", "vi"), ts.map { ChordParser.formatRoman(it.chord) }.toSet())
        val perJob = ts.groupingBy { it.function }.eachCount()
        assertEquals(ChordFunction.entries.toSet(), perJob.keys)
        assertTrue(perJob.values.all { it >= 20 }, "the job is picked first, so none is starved: $perJob")
        ts.zipWithNext().forEach { (a, b) -> assertTrue(a.function != b.function, "the same job is not asked twice running") }
    }

    @Test
    fun `the job is the same for chords of different colours - that is the point`() {
        val ts = tasks(driver(), 2, 200)
        val home = ts.filter { it.function == ChordFunction.HOME }.map { it.chord.quality }.toSet()
        val tension = ts.filter { it.function == ChordFunction.TENSION }.map { it.chord.quality }.toSet()
        assertTrue(home.size >= 3, "home is major, minor and sevenths: $home")
        assertTrue(ChordQuality.MAJOR in tension && ChordQuality.DOMINANT7 in tension, "tension is a plain triad and a seventh: $tension")
        assertTrue(ts.any { it.key.mode == Mode.MINOR })
    }

    @Test
    fun `level 3 asks the colour of the very chord whose job was just asked, in the same key and without a new scale`() {
        val d = driver()
        val ts = tasks(d, 3, 12)
        ts.chunked(2).forEach { (job, colour) ->
            assertEquals(Layer.FUNCTION, job.layer)
            assertEquals(Layer.COLOR, colour.layer)
            assertEquals(job.chord, colour.chord)
            assertEquals(job.key, colour.key)
            assertEquals(job.question, colour.question, "the same sound")
            assertNull(colour.intro, "no scale between the two layers")
            assertTrue(colour.sameChordAgain)
            assertEquals("layers:color:L3:${job.chord.quality.name}", colour.itemKey)
            assertTrue(job.chord.quality in colour.colorChoices && colour.colorChoices.size >= 6)
        }
    }

    @Test
    fun `the tonic hums, the chord stays a chord of the key, and a new key is set by its scale`() {
        val ts = tasks(driver(), 2, LayersDriver.KEY_BLOCK + 1)
        assertNotNull(ts.first().intro)
        assertNotNull(ts.first().introDrone)
        ts.drop(1).take(LayersDriver.KEY_BLOCK - 1).forEach { assertNull(it.intro) }
        assertNotNull(ts.last().intro, "the next block opens with its scale")
        ts.forEach { t ->
            assertTrue(t.drone.isNotEmpty())
            assertTrue(t.question.notes.any { it.voice == Voice.BASS }, "the tonic is held under the chord (O-71)")
            assertEquals(t.question, t.prompt.audio)
        }
    }

    @Test
    fun `a right job is rewarded by what it does, a wrong one hears the chord again and then that`() {
        val d = driver(3)
        repeat(20) { seed ->
            val t = d.nextTask(DriverContext(Random(seed), 1, emptyList()))
            val right = d.evaluate(t, Answer.FunctionChoice(t.function))
            assertTrue(right.correct)
            assertNull(right.correction)
            assertEquals(t.answer, right.confirmation)
            val other = ChordFunction.entries.first { it != t.function }
            val wrong = d.evaluate(t, Answer.FunctionChoice(other))
            assertFalse(wrong.correct)
            val fix = requireNotNull(wrong.correction) { "rule 5" }
            assertTrue(t.question.notes.all { q -> fix.notes.any { it.startBeat == q.startBeat && it.note == q.note } }, "the question first, again")
            assertTrue(fix.lengthBeats > t.question.lengthBeats)
            assertEquals(t.function.ru, wrong.expectedText)
            assertEquals(other.ru, wrong.givenText)
            assertNotNull(d.evaluate(t, Answer.Pass).correction)
            assertFalse(d.evaluate(t, Answer.QualityChoice(t.chord.quality)).correct, "naming the colour is not naming the job")
        }
    }

    @Test
    fun `tension resolves, a step away comes back, home stays`() {
        val ts = tasks(driver(2), 1, 60)
        for (t in ts) {
            val chords = t.answer.notes.filter { it.voice == Voice.PIANO }.map { it.startBeat }.distinct().size
            when (t.function) {
                ChordFunction.HOME -> assertEquals(1, chords, "home goes nowhere")
                else -> assertEquals(2, chords, "${t.function}: the chord and where it goes")
            }
        }
    }

    @Test
    fun `the colour layer is judged by the quality and taken apart after`() {
        val d = driver(4)
        val ts = tasks(d, 3, 6)
        val t = ts[1]
        assertEquals(Layer.COLOR, t.layer)
        assertTrue(d.evaluate(t, Answer.QualityChoice(t.chord.quality)).correct)
        val other = t.colorChoices.first { it != t.chord.quality }
        val wrong = d.evaluate(t, Answer.QualityChoice(other))
        assertFalse(wrong.correct)
        assertNotNull(wrong.correction)
        assertEquals(t.chord.quality.intervals.size + 1, t.answer.notes.size, "the tones, one by one, and the root again")
        assertFalse(d.evaluate(t, Answer.FunctionChoice(t.function)).correct)
    }

    @Test
    fun `the layers are told apart in the item key and are not spaced review items`() {
        val ts = tasks(driver(), 3, 4)
        assertTrue(ts[0].itemKey.startsWith("layers:fn:L3:") && ts[1].itemKey.startsWith("layers:color:L3:"))
        assertNull(TirDriver.reviewKey(ts[0].itemKey))
        assertEquals(3, Levels.TIR_LAYERS.size)
    }
}
