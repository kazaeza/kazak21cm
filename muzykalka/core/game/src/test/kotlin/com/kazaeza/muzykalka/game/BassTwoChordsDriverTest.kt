package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-62 «Бас» and «Два аккорда» (ADR-061): links between a chord over the tonic and «Куда пойдёт?». */
class BassTwoChordsDriverTest {
    private fun driver(variant: Variant, seed: Int = 1) = TirDriver(KeyPicker(Random(seed)), variant = variant)

    @Test
    fun `the bass is heard alone, low, over a humming tonic, and the chord is what is asked`() {
        val d = driver(Variant.TIR_BASS)
        for (level in 1..4) repeat(TirDriver.KEY_BLOCK) {
            val t = d.nextTask(DriverContext(Random(level * 31 + it), level, emptyList()))
            assertTrue(t.bassOnly)
            assertEquals(TirKind.CHORD, t.kind)
            assertEquals(TirDriver.onTonicPad(t.key.mode, level), t.chordChoices)
            assertTrue(t.chords.single() in t.chordChoices)
            assertTrue(t.question.notes.isNotEmpty() && t.question.notes.all { n -> n.voice == Voice.BASS && n.startBeat == 0.0 }, "only the bass")
            val root = Arranger().bassNote(t.chords.single(), t.key)
            assertTrue(t.question.notes.any { n -> n.note == root + 12 }, "an octave up: a phone speaker has it")
            assertEquals(Arranger().drone(t.key), t.drone)
            assertEquals("tir:bass:L$level:" + ChordParser.formatRoman(t.chords.single()), t.itemKey)
            assertNull(t.from)
        }
    }

    @Test
    fun `a missed bass is heard again and then as the whole chord, rule 5`() {
        val d = driver(Variant.TIR_BASS, 4)
        repeat(10) {
            val t = d.nextTask(DriverContext(Random(it), 2, emptyList()))
            val wrong = d.evaluate(t, Answer.ChordChoice(t.chordChoices.first { c -> c != t.chords.single() }))
            assertTrue(!wrong.correct)
            val correction = requireNotNull(wrong.correction)
            assertTrue(correction.notes.any { n -> n.voice == Voice.PIANO }, "the chord itself is in the correction")
            assertTrue(correction.lengthBeats > t.question.lengthBeats)
            assertNotNull(d.evaluate(t, Answer.ChordChoice(t.chords.single())).confirmation)
        }
    }

    @Test
    fun `two chords - the first is given, the second is asked, never the same twice`() {
        val d = driver(Variant.TIR_TWO_CHORDS)
        for (level in 1..4) repeat(TirDriver.KEY_BLOCK * 2) {
            val t = d.nextTask(DriverContext(Random(level * 17 + it), level, emptyList()))
            val from = requireNotNull(t.from)
            val to = t.chords.single()
            assertTrue(!(from.root sameSoundAs to.root && from.quality == to.quality), "a move is a move")
            assertEquals(TirDriver.TWO_STEPS[level - 1].size, t.chordChoices.size)
            assertTrue(to in t.chordChoices)
            if (level <= 2) assertEquals(ChordParser.formatRoman(from).lowercase(), "i", "the first levels leave from home")
            assertEquals(2, t.question.notes.map { n -> n.startBeat }.toSet().size, "two chords sound")
            assertEquals(Arranger().drone(t.key), t.drone)
            assertTrue(t.itemKey.startsWith("tir:two:L$level:") && t.itemKey.contains(">"))
        }
    }

    @Test
    fun `two chords - a miss hears both again and then home`() {
        val d = driver(Variant.TIR_TWO_CHORDS, 2)
        repeat(10) {
            val t = d.nextTask(DriverContext(Random(it), 3, emptyList()))
            val right = d.evaluate(t, Answer.ChordChoice(t.chords.single()))
            assertTrue(right.correct)
            assertNull(right.correction)
            val wrong = d.evaluate(t, Answer.Pass)
            assertTrue(requireNotNull(wrong.correction).lengthBeats > t.question.lengthBeats)
        }
    }

    @Test
    fun `the levels and the sets are in order`() {
        assertEquals(4, Levels.TIR_TWO_CHORDS.size)
        assertEquals(TirDriver.TWO_STEPS.size, Levels.TIR_TWO_CHORDS.size)
        assertEquals(TirDriver.TWO_FROM.size, Levels.TIR_TWO_CHORDS.size)
        assertEquals(Levels.TIR_ON_TONIC.size, Levels.TIR_BASS.size)
        assertTrue(Variant.TIR_BASS in Training.EAR.plan.map { it.variant })
        assertTrue(Variant.TIR_TWO_CHORDS in Training.SONGS.plan.map { it.variant })
    }
}
