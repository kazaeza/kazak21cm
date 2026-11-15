package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.ModeVibes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-51 «Какой лад?» (ADR-049): the scale of a mode over the tonic drone, the mode is picked. */
class ModeQuizDriverTest {
    private fun tasks(level: Int, n: Int, seed: Int = 1): List<ModeQuizTask> {
        val d = ModeQuizDriver(KeyPicker(Random(seed)))
        return List(n) { d.nextTask(DriverContext(Random(seed * 100 + it), level, emptyList())) }
    }

    @Test
    fun `the whole scale of the mode runs over the tonic drone`() {
        for (level in 1..4) tasks(level, 60).forEach { t ->
            assertTrue(t.mode in t.choices)
            assertEquals(ModeQuizDriver.pad(level), t.choices)
            assertEquals(t.mode, t.key.mode)
            assertEquals(Arranger().drone(t.key), t.drone, "the tonic hums: a mode is a colour against home")
            val tonic = t.key.tonic.pitchClass.value
            val heard = t.question.notes.map { Math.floorMod(it.note!!.pitchClass.value - tonic, 12) }.toSet()
            assertEquals(t.mode.degrees.map { it.semitones }.toSet(), heard, "all seven degrees are heard")
            assertEquals(15, t.question.notes.size, "to the octave and back")
            assertEquals(t.question.notes.first().note!!.pitchClass, t.question.notes.last().note!!.pitchClass, "it ends where it began")
            assertEquals("mode:L$level:" + t.mode.name, t.itemKey)
        }
        val firsts = tasks(1, 60).map { it.question.notes.first().note!!.value < it.question.notes[7].note!!.value }.toSet()
        assertEquals(setOf(true, false), firsts, "up first, or down first")
    }

    @Test
    fun `the ladder adds the modes that differ by one degree`() {
        assertEquals(listOf(Mode.MAJOR, Mode.MINOR), ModeQuizDriver.pad(1))
        assertEquals(listOf(Mode.MAJOR, Mode.MINOR, Mode.DORIAN, Mode.MIXOLYDIAN), ModeQuizDriver.pad(2))
        assertEquals(6, ModeQuizDriver.pad(3).size)
        assertEquals(7, ModeQuizDriver.pad(4).size)
        for (level in 1..4) assertEquals(ModeQuizDriver.pad(level).toSet(), tasks(level, 400).map { it.mode }.toSet())
        // every added mode is one degree away from major or minor, and that degree is its mark
        for (mode in ModeQuizDriver.pad(4).drop(2)) {
            val parent = if (mode.hasMinorThird) Mode.MINOR else Mode.MAJOR
            val differ = mode.degrees.zip(parent.degrees).filter { (a, b) -> a.semitones != b.semitones }.map { it.first }
            assertEquals(listOf(ModeVibes.of(mode).mark), differ, "$mode against $parent")
        }
        assertNull(ModeVibes.of(Mode.MAJOR).mark)
        assertEquals(listOf("♭3", "♭7"), ModeVibes.altered(Mode.DORIAN).map { it.toString() })
        assertTrue(ModeVibes.altered(Mode.MAJOR).isEmpty())
    }

    @Test
    fun `a right pick hears the telling degree walk home, a wrong one the scale again first`() {
        val d = ModeQuizDriver(KeyPicker(Random(2)))
        repeat(30) {
            val t = d.nextTask(DriverContext(Random(it), 4, emptyList()))
            val right = d.evaluate(t, Answer.ModeChoice(t.mode))
            assertTrue(right.correct)
            assertEquals(t.mark, right.confirmation)
            assertEquals(t.mode.ruName, right.expectedText)
            val other = t.choices.first { m -> m != t.mode }
            val wrong = d.evaluate(t, Answer.ModeChoice(other))
            assertFalse(wrong.correct)
            assertEquals(t.question.notes.size + t.mark.notes.size, wrong.correction!!.notes.size, "rule 5")
            assertNotNull(d.evaluate(t, Answer.Pass).correction)
            val tonic = t.key.tonic.pitchClass.value
            val told = Math.floorMod(t.mark.notes.first().note!!.pitchClass.value - tonic, 12)
            assertEquals((ModeVibes.of(t.mode).mark ?: t.mode.degrees[2]).semitones, told, "the mark of ${t.mode}")
            assertEquals(0, Math.floorMod(t.mark.notes.last().note!!.pitchClass.value - tonic, 12), "home")
        }
    }
}
