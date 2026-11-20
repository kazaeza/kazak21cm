package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.Voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-58 «Аккорд над тоникой» (ADR-057): the tonic hums, a chord of the key is played over it — which step is it. */
class OnTonicDriverTest {
    private fun driver(seed: Int = 1) = TirDriver(KeyPicker(Random(seed)), variant = Variant.TIR_ON_TONIC)

    @Test
    fun `the levels widen the pad from the three main chords to the whole key`() {
        assertEquals(listOf(0, 3, 4), TirDriver.ON_TONIC_STEPS[0])
        assertEquals(listOf(0, 3, 4, 5), TirDriver.ON_TONIC_STEPS[1])
        assertEquals(Levels.TIR_ON_TONIC.size, TirDriver.ON_TONIC_STEPS.size)
        for (mode in listOf(Mode.MAJOR, Mode.MINOR)) {
            val sizes = (1..4).map { TirDriver.onTonicPad(mode, it).size }
            assertEquals(listOf(3, 4, 6, 7), sizes)
            assertEquals(listOf("I", "IV", "V").map { it.lowercase() }, TirDriver.onTonicPad(Mode.MAJOR, 1).map { ChordParser.formatRoman(it).lowercase() })
        }
    }

    @Test
    fun `a chord of the pad is played over a humming tonic`() {
        val d = driver()
        for (level in 1..4) repeat(2) { block ->
            val tasks = List(TirDriver.KEY_BLOCK) { d.nextTask(DriverContext(Random(level * 100 + block * 10 + it), level, emptyList())) }
            val first = tasks.first()
            assertTrue(tasks.all { it.key == first.key }, "a key holds for a block")
            val pad = TirDriver.onTonicPad(first.key.mode, level)
            tasks.forEach { t ->
                val chord = t.chords.single()
                assertEquals(TirKind.CHORD, t.kind)
                assertTrue(!t.underNote, "the shown thing is the key, not a note")
                assertEquals(pad, t.chordChoices)
                assertTrue(chord in pad)
                assertEquals(Arranger().drone(t.key), t.drone, "the tonic hums")
                assertTrue(t.question.notes.none { it.voice == Voice.TONE }, "the soft voice is the drone's")
                assertEquals("tir:ontonic:L$level:" + ChordParser.formatRoman(chord), t.itemKey)
            }
            assertNotNull(first.intro, "a new key is set by its scale")
            assertNull(tasks[1].intro)
        }
    }

    @Test
    fun `the right step goes home, a wrong one hears the chord again over the tonic (rule 5)`() {
        val d = driver(3)
        repeat(12) {
            val t = d.nextTask(DriverContext(Random(it), 2, emptyList()))
            val right = d.evaluate(t, Answer.ChordChoice(t.chords.single()))
            assertTrue(right.correct)
            assertNotNull(right.confirmation)
            assertNull(right.correction)
            val other = t.chordChoices.first { c -> c != t.chords.single() }
            val wrong = d.evaluate(t, Answer.ChordChoice(other))
            assertTrue(!wrong.correct)
            assertNotNull(wrong.correction, "a wrong answer to a sounding task has its correction")
            // O-74: the chosen chord over the tonic, then the asked one, then home
            val home = Arranger().chords(listOf(Harmony.diatonicChord(t.key.mode, 0)), t.key, beatsEach = 1.0, tempoBpm = TirDriver.TEMPO).notes.size
            assertTrue(wrong.correction!!.notes.size > t.question.notes.size + home, "chosen + asked + home")
            assertNotNull(wrong.contrast)
            assertEquals(ChordParser.formatRoman(t.chords.single()), wrong.expectedCode)
            assertTrue(d.evaluate(t, Answer.Pass).correction != null)
        }
    }
}

/** O-63 (ADR-062): where the user chose songs, a single note is answered by a song instead of a walk along the scale. */
class TuneDriverTest {
    private val tune = { d: com.kazaeza.muzykalka.music.Degree, key: com.kazaeza.muzykalka.music.Key ->
        com.kazaeza.muzykalka.music.SongHint.forDegree(d)?.score(key)
    }

    private fun task(driver: TirDriver, seed: Int = 1) = driver.nextTask(DriverContext(Random(seed), 1, emptyList()))

    @Test
    fun `with a tune a right answer is rewarded by the song, a wrong one hears the note and then the song`() {
        val d = TirDriver(KeyPicker(Random(2)), tune = tune)
        var checked = 0
        repeat(30) {
            val t = task(d, it)
            val degree = t.notes.single().degree
            val song = com.kazaeza.muzykalka.music.SongHint.forDegree(degree)?.score(t.key) ?: return@repeat
            checked++
            assertEquals(song, d.evaluate(t, Answer.DegreeChoice(degree)).confirmation)
            val wrong = d.evaluate(t, Answer.Pass)
            val correction = requireNotNull(wrong.correction)
            assertEquals(t.question.notes.size + song.notes.size, correction.notes.size, "the note again, then the song")
            assertTrue(correction.lengthBeats > t.question.lengthBeats + song.lengthBeats * 0.9)
        }
        assertTrue(checked > 10)
    }

    @Test
    fun `without a tune the walk home stays, and a degree with no song keeps it too`() {
        val plain = TirDriver(KeyPicker(Random(2)))
        val t = task(plain)
        assertTrue(requireNotNull(plain.evaluate(t, Answer.DegreeChoice(t.notes.single().degree)).confirmation).notes.size >= 2)
        val silent = TirDriver(KeyPicker(Random(2)), tune = { _, _ -> null })
        val u = task(silent)
        assertEquals(plain.evaluate(t, Answer.Pass).correction?.notes?.size, silent.evaluate(u, Answer.Pass).correction?.notes?.size)
    }
}
