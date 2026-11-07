package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

class SingDriverTest {
    private fun ctx(seed: Int) = DriverContext(Random(seed), 1, emptyList())

    /** The asked degree, [octaves] away from the reference, [cents] off. */
    private fun sung(task: SingTask, octaves: Int = 0, semitonesOff: Int = 0, cents: Double = 0.0) =
        Answer.Sung(MidiNote(task.tonic.value + task.degree.semitones + 12 * octaves + semitonesOff), cents)

    @Test
    fun `the degree is shown, the drone holds the key, a new key opens with its scale`() {
        val driver = SingDriver(KeyPicker(Random(1)), withDrone = true)
        val tasks = List(TirDriver.KEY_BLOCK + 1) { driver.nextTask(ctx(it)) }
        val first = tasks.first()
        assertEquals(first.degree.toString(), first.prompt.title)
        assertNotNull(first.drone)
        assertTrue(requireNotNull(first.prompt.audio).notes.size >= 8, "new key: the scale")
        assertNull(tasks[1].prompt.audio, "same key over the drone: nothing to wait for")
        assertTrue(tasks.take(TirDriver.KEY_BLOCK).all { it.key == first.key })
        assertTrue(tasks.last().prompt.audio != null, "the next block opens with a scale again")
        assertTrue(first.key.mode.degrees.contains(first.degree))
    }

    @Test
    fun `without headphones there is no drone and every question opens with the tonic`() {
        val driver = SingDriver(KeyPicker(Random(1)), withDrone = false)
        val first = driver.nextTask(ctx(1))
        val second = driver.nextTask(ctx(2))
        assertNull(first.drone)
        assertEquals(first.reference.notes.single().note, requireNotNull(second.prompt.audio).notes.single().note)
        assertEquals(second.tonic, second.reference.notes.single().note)
    }

    @Test
    fun `any octave counts and the cents are kept`() {
        val driver = SingDriver(KeyPicker(Random(4)))
        val task = driver.nextTask(ctx(4))
        for (octave in listOf(-1, 0, 1)) {
            val ev = driver.evaluate(task, sung(task, octave, cents = 18.0))
            assertTrue(ev.correct, "octave $octave")
            assertEquals(18.0, requireNotNull(ev.centsError), 1e-9)
            assertNotNull(ev.confirmation)
            // The reward is heard where the voice is: it starts on the sung note.
            assertEquals(sung(task, octave).note, ev.confirmation!!.notes.first().note)
        }
    }

    @Test
    fun `a semitone off is wrong, says what was sung and plays the right note there (rule 5)`() {
        val driver = SingDriver(KeyPicker(Random(4)))
        val task = driver.nextTask(ctx(4))
        val ev = driver.evaluate(task, sung(task, octaves = 1, semitonesOff = -1, cents = 10.0))
        assertFalse(ev.correct)
        val correction = requireNotNull(ev.correction)
        assertEquals(sung(task, octaves = 1).note, correction.notes.first().note)
        assertEquals(-90.0, requireNotNull(ev.centsError), 1e-9)
        assertEquals(Math.floorMod(task.degree.semitones - 1, 12), Degree.parse(ev.givenCode).semitones)
    }

    @Test
    fun `don't know and silence are wrong with the right note`() {
        val driver = SingDriver(KeyPicker(Random(2)))
        val task = driver.nextTask(ctx(2))
        for (a in listOf(Answer.Pass, Answer.TimedOut)) {
            val ev = driver.evaluate(task, a)
            assertFalse(ev.correct)
            assertNotNull(ev.correction)
            assertNull(ev.centsError)
        }
    }

    @Test
    fun `the steady part decides, not the scoop into the note`() {
        val scoop = listOf(400.0, 410.0, 420.0)
        val steady = List(9) { 440.0 + (it % 3 - 1) * 0.5 }
        val reading = requireNotNull(SingDriver.steadyPitch(scoop + steady))
        assertEquals(69, reading.note.value)
        assertTrue(abs(reading.cents) < 3)
        assertNull(SingDriver.steadyPitch(emptyList()))
    }
}
