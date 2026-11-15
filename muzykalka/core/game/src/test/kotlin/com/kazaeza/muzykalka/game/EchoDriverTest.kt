package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** PR-01 «Попугай», step 1 (ADR-051): a degree sounds after the tonic and is played on the guitar. */
class EchoDriverTest {
    private fun tasks(level: Int, n: Int, seed: Int = 1): List<EchoTask> {
        val d = EchoDriver(KeyPicker(Random(seed)))
        return List(n) { d.nextTask(DriverContext(Random(seed * 100 + it), level, emptyList())) }
    }

    @Test
    fun `the tonic sounds for reference, then the note, and there is no drone for the microphone to hear`() {
        for (level in 1..2) tasks(level, 60).forEach { t ->
            assertNull(t.drone, "ADR-021: a drone would leak into the microphone")
            assertEquals(2, t.question.notes.size, "the tonic and the note")
            assertEquals(t.key.tonic.pitchClass, t.question.notes[0].note!!.pitchClass)
            assertEquals(t.pitchClass, t.question.notes[1].note!!.pitchClass)
            assertEquals(t.key.pitchClassOf(t.note.degree), t.pitchClass)
            assertTrue(t.question.notes[1].note!!.value in 45..76, "in the guitar's register: ${t.question.notes[1].note}")
            assertEquals(t.question, t.replay)
            assertEquals(t.key, t.prompt.key, "the key is named: the neck needs an address")
            assertEquals("echo:L$level:" + t.note.degree, t.itemKey)
        }
    }

    @Test
    fun `a key holds for a block and opens with its scale`() {
        val ts = tasks(2, EchoDriver.KEY_BLOCK * 2 + 1)
        assertEquals(1, ts.take(EchoDriver.KEY_BLOCK).map { it.key }.toSet().size)
        assertTrue(ts[EchoDriver.KEY_BLOCK].key != ts[0].key, "then another key")
        assertTrue(ts[0].prompt.audio!!.notes.size > ts[0].question.notes.size, "the scale sets the key")
        ts.drop(1).take(EchoDriver.KEY_BLOCK - 1).forEach { assertEquals(it.question, it.prompt.audio) }
        assertTrue(ts[EchoDriver.KEY_BLOCK].prompt.audio!!.notes.size > 2)
    }

    @Test
    fun `level 1 asks the steady degrees in major, level 2 the whole scale in either mode`() {
        val l1 = tasks(1, 200)
        assertEquals(setOf("1", "3", "5"), l1.map { it.note.degree.toString() }.toSet())
        assertTrue(l1.all { it.key.mode == Mode.MAJOR })
        val l2 = (0 until 30).flatMap { tasks(2, 16, seed = it) }
        assertEquals(setOf(Mode.MAJOR, Mode.MINOR), l2.map { it.key.mode }.toSet())
        assertTrue(l2.map { it.note.degree.semitones }.toSet().size >= 9, "both scales, all their degrees")
    }

    @Test
    fun `the first note played is judged by its pitch class, in any octave`() {
        val d = EchoDriver(KeyPicker(Random(4)))
        repeat(30) {
            val t = d.nextTask(DriverContext(Random(it), 2, emptyList()))
            val target = t.question.notes[1].note!!
            for (octave in listOf(-12, 0, 12)) {
                val right = d.evaluate(t, Answer.NotePlayed(MidiNote(target.value + octave), 7.0))
                assertTrue(right.correct, "any octave of the sound")
                assertEquals(7.0, right.centsError)
                assertNotNull(right.confirmation, "the note walks home")
            }
            val wrong = d.evaluate(t, Answer.NotePlayed(MidiNote(target.value + 2), -3.0))
            assertFalse(wrong.correct)
            assertNotNull(wrong.correction, "rule 5")
            assertTrue(wrong.givenText.isNotBlank() && wrong.givenText != wrong.expectedText, "what was played, as a degree of the key: ${wrong.givenText}")
            assertFalse(Regex("[A-H]").containsMatchIn(wrong.givenText + wrong.expectedText), "degrees, never letters")
            assertNotNull(d.evaluate(t, Answer.Pass).correction)
        }
    }
}
