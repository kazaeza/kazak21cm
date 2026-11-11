package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.RuNames
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

    private fun steadyAnswers(n: Int, ok: Boolean) = List(n) {
        AttemptRecord(Variant.KAMERTON_SING, 1, SingDriver.PREFIX + listOf("1", "3", "5", "b3")[it % 4], "", "", ok, 2_000, 0.0, 0, null, it.toLong())
    }

    /** O-28: it is hard to sing a degree cold — «Спой ступень» starts from the steady ones. */
    @Test
    fun `only 1 3 5 are asked until they come out, then the whole mode`() {
        val driver = SingDriver(KeyPicker(Random(4)))
        val random = Random(5)
        val early = List(120) { driver.nextTask(DriverContext(random, 1, steadyAnswers(19, ok = true))).degree.number }.toSet()
        assertEquals(setOf(1, 3, 5), early)
        assertTrue(!SingDriver.steadyLearnt(steadyAnswers(20, ok = false)))
        assertTrue(SingDriver.steadyLearnt(steadyAnswers(20, ok = true)))
        val later = List(200) { driver.nextTask(DriverContext(random, 1, steadyAnswers(20, ok = true))).degree.number }.toSet()
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), later)
    }

    /** O-28: a miss is not the end of it — the same degree is asked again one question later. */
    @Test
    fun `a missed degree comes back after one other question`() {
        val driver = SingDriver(KeyPicker(Random(6)))
        val random = Random(7)
        val history = steadyAnswers(20, ok = true)
        val missed = driver.nextTask(DriverContext(random, 1, history))
        driver.evaluate(missed, sung(missed, semitonesOff = 1))
        driver.nextTask(DriverContext(random, 1, history))
        assertEquals(missed.degree.number, driver.nextTask(DriverContext(random, 1, history)).degree.number)
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
    fun `«Пропевка» searches, names the interval and starts with 1 3 5 (O-14)`() {
        val driver = SingDriver(KeyPicker(Random(3)), variant = Variant.KAMERTON_SEARCH)
        assertTrue(driver.searches)
        assertFalse(SingDriver(KeyPicker(Random(3))).searches)
        val tasks = List(40) { driver.nextTask(ctx(it)) }
        assertTrue(tasks.all { it.degree.number in setOf(1, 3, 5) })
        assertTrue(tasks.all { it.itemKey.startsWith("pevka:L1:") })
        val fifth = tasks.first { it.degree.number == 5 }
        assertEquals("спой квинту", fifth.prompt.subtitle)
        val level2 = List(60) { driver.nextTask(DriverContext(Random(it), 2, emptyList())) }
        assertTrue(level2.any { it.degree.number !in setOf(1, 3, 5) })
        assertTrue(level2.all { it.itemKey.startsWith("pevka:L2:") })
    }

    @Test
    fun `interval names by ear size`() {
        assertEquals("квинта", RuNames.interval(Degree.parse("5")))
        assertEquals("малую терцию", RuNames.interval(Degree.parse("b3"), accusative = true))
        assertEquals("большая терция", RuNames.interval(Degree.parse("3")))
        assertEquals("тонику", RuNames.interval(Degree.ONE, accusative = true))
        assertEquals("тритон", RuNames.interval(Degree.parse("#4")))
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
