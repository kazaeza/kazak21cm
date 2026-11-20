package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.Voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-77 «Бас-линия» (ADR-075): the bass alone, played on the guitar; then the band; then the bass again; then another key. */
class BassLineDriverTest {
    private fun driver(seed: Int = 1) = BassLineDriver(KeyPicker(Random(seed)))

    private fun tasks(level: Int, n: Int, seed: Int = 1): List<BassLineTask> {
        val d = driver(seed)
        return List(n) { d.nextTask(DriverContext(Random(seed * 100 + it), level, emptyList())) }
    }

    private fun phrase(vararg notes: Int) = Answer.Phrase(notes.map { Answer.NotePlayed(MidiNote(it), 0.0) })

    /** The notes of the line as a guitar would play them, an octave up and down at random from [base]. */
    private fun played(t: BassLineTask, octave: Int = 0) = Answer.Phrase(t.pitchClasses.map { Answer.NotePlayed(MidiNote(40 + (it.value - 40 % 12 + 12) % 12 + 12 * octave), 0.0) })

    @Test
    fun `a cycle is three scenes - the first key twice, then another key - and the loop is the same`() {
        for (level in 1..3) for (seed in 1..6) {
            tasks(level, 9, seed).chunked(3).forEach { (a, b, c) ->
                assertEquals(listOf(1, 2, 3), listOf(a.scene, b.scene, c.scene))
                assertEquals(a.loop, b.loop); assertEquals(b.loop, c.loop)
                assertEquals(a.key, b.key)
                assertTrue(a.key.tonic.pitchClass != c.key.tonic.pitchClass, "the third scene is in another key")
                assertNotNull(a.intro); assertNull(b.intro, "the key is already set"); assertNotNull(c.intro)
            }
        }
    }

    @Test
    fun `only the bass sounds, low and in two octaves, a note per chord, and there is no drone for the microphone to hear`() {
        val t = tasks(1, 3).first()
        assertTrue(t.question.notes.all { it.voice == Voice.BASS })
        assertEquals(2 * t.loop.size, t.question.notes.size)
        assertNull(t.drone)
        assertEquals(t.question, t.prompt.audio)
        assertEquals(t.loop.size, t.expected)
        assertTrue(t.question.notes.minOf { it.note!!.value } < 60 && t.question.notes.all { it.note!!.value < 76 }, "low, and not out of a guitar's reach")
    }

    @Test
    fun `the levels add vi and then the flat seventh and minor`() {
        val l1 = tasks(1, 60).flatMap { it.degrees.map(Any::toString) }.toSet()
        assertEquals(setOf("1", "4", "5"), l1, "I IV V")
        val l2 = tasks(2, 90).flatMap { it.degrees.map(Any::toString) }.toSet()
        assertTrue("6" in l2 && "♭7" !in l2)
        val l3 = tasks(3, 150)
        assertTrue(l3.any { "♭7" in it.degrees.map(Any::toString) })
        assertTrue(l3.any { it.key.mode == Mode.MINOR })
    }

    @Test
    fun `the right line in any octave is right, a wrong note, a wrong order or a short line is not`() {
        val d = driver(2)
        repeat(10) { seed ->
            val t = d.nextTask(DriverContext(Random(seed), 2, emptyList()))
            assertTrue(d.evaluate(t, played(t, 0)).correct)
            assertTrue(d.evaluate(t, played(t, 1)).correct, "any octave")
            val notes = (played(t, 0)).notes
            val wrongNote = Answer.Phrase(notes.mapIndexed { i, n -> if (i == 1) Answer.NotePlayed(n.note + 1, 0.0) else n })
            assertFalse(d.evaluate(t, wrongNote).correct)
            assertFalse(d.evaluate(t, Answer.Phrase(notes.dropLast(1))).correct)
            assertFalse(d.evaluate(t, Answer.Phrase(emptyList())).correct)
        }
    }

    @Test
    fun `a miss hears the bass again and then the band (rule 5), a hit hears the band, the line is said in degrees`() {
        val d = driver(3)
        val t = tasks(1, 3, 3).first()
        val right = d.evaluate(t, played(t))
        assertEquals(t.heard, right.confirmation)
        assertNull(right.correction)
        val wrong = d.evaluate(t, Answer.Phrase(emptyList()))
        val fix = requireNotNull(wrong.correction)
        assertTrue(fix.notes.take(t.question.notes.size).all { it.voice == Voice.BASS }, "the bass first")
        assertTrue(fix.notes.any { it.voice == Voice.GUITAR }, "then the whole band")
        assertEquals(t.degrees.joinToString(" "), wrong.expectedCode)
        assertEquals(t.loop.size, wrong.expectedText.split(' ').size)
        assertNotNull(d.evaluate(t, Answer.Pass).correction)
        assertFalse(d.evaluate(t, Answer.Pass).correct)
    }

    @Test
    fun `the scene is in the item key and a bass line is not a spaced review item`() {
        val ts = tasks(1, 3)
        assertEquals(listOf("s1", "s2", "s3"), ts.map { it.itemKey.split(':')[2] })
        assertNull(TirDriver.reviewKey(ts[0].itemKey))
        assertEquals(3, Levels.TIR_BASSLINE.size)
    }
}
