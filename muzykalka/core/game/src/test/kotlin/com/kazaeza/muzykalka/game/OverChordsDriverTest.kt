package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.music.KeyPicker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class OverChordsDriverTest {
    private fun driver(seed: Int = 1) = TirDriver(KeyPicker(Random(seed)), variant = Variant.TIR_OVER_CHORDS)

    @Test
    fun `a block opens over I with the scale, then other chords of the key hold under the note`() {
        val d = driver()
        val tasks = List(TirDriver.KEY_BLOCK) { d.nextTask(DriverContext(Random(it), 1, emptyList())) }
        val first = tasks.first()
        assertEquals(Harmony.diatonicChord(first.key.mode, 0), first.over)
        assertTrue(requireNotNull(first.prompt.audio).notes.size > first.question.notes.size, "the scale opens the block")
        assertTrue(tasks.all { it.key == first.key })
        val allowed = TirDriver.CHORD_STEPS_L1.map { Harmony.diatonicChord(first.key.mode, it) }
        tasks.forEach { t ->
            val chord = requireNotNull(t.over)
            assertTrue(chord in allowed, "$chord is one of I IV V vi")
            // O-68: the tonic hums all the time; the chord is played over it, and the note comes on top, above the chord
            assertEquals(Arranger().drone(t.key), t.drone)
            val played = t.question.notes.filter { it.voice == Voice.PIANO }
            assertEquals(chord.quality.intervals.map { (t.key.pitchClassOf(chord.root) + it).value }.toSet(), played.map { it.note!!.pitchClass.value }.toSet())
            val tone = t.question.notes.single { it.voice == Voice.TONE }
            assertTrue(tone.startBeat > 0.0 && tone.note!!.value >= played.maxOf { it.note!!.value } - 5, "the note enters after the chord, on top")
            assertEquals(t.key.mode.degrees, t.noteChoices)
            assertTrue(t.itemKey.startsWith("tir:chords:L1:" + ChordParser.formatRoman(chord) + ":"))
        }
        assertTrue(tasks.drop(1).any { it.over != first.over }, "the harmony moves")
        tasks.drop(1).forEach { assertEquals(it.question, it.prompt.audio, "no scale inside the block") }
    }

    @Test
    fun `the answer is the degree in the key, whatever chord is under it`() {
        val d = driver(3)
        repeat(20) {
            val t = d.nextTask(DriverContext(Random(it), 2, emptyList()))
            val right = d.evaluate(t, Answer.DegreeChoice(t.notes.single().degree))
            assertTrue(right.correct)
            assertNotNull(right.confirmation)
            val wrong = d.evaluate(t, Answer.Pass)
            assertNotNull(wrong.correction)
        }
    }

    @Test
    fun `over chords is not a spaced review item and the classic Tir is untouched`() {
        assertNull(TirDriver.reviewKey("tir:chords:L1:IV:5"))
        val classic = TirDriver(KeyPicker(Random(1))).nextTask(DriverContext(Random(1), 2, emptyList()))
        assertNull(classic.over)
    }
}
