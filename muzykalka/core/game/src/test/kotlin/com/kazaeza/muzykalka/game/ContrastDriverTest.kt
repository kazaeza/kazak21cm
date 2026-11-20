package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-74 (ADR-071): after a miss the chosen sound is heard first, then the right one — and the contrast ends on the right one. */
class ContrastDriverTest {
    private fun driver(variant: Variant) = TirDriver(KeyPicker(Random(4)), variant = variant)

    private fun task(d: TirDriver, level: Int = 1, seed: Int = 1) = d.nextTask(DriverContext(Random(seed), level, emptyList()))

    @Test
    fun `a wrong degree is heard first, then the right one with its way home, ending on home`() {
        val d = driver(Variant.TIR_CLASSIC)
        repeat(20) { seed ->
            val t = task(d, 2, seed)
            val asked = t.notes.single().degree
            val chosen = t.noteChoices.first { !(it sameSoundAs asked) }
            val ev = d.evaluate(t, Answer.DegreeChoice(chosen))
            assertFalse(ev.correct)
            val fix = requireNotNull(ev.correction) { "rule 5" }
            assertNotNull(ev.contrast)
            assertTrue(ev.contrast!!.contains("—"), ev.contrast)
            val pitchClasses = fix.notes.sortedBy { it.startBeat }.map { it.note!!.pitchClass }
            assertEquals(t.key.pitchClassOf(chosen), pitchClasses.first(), "what was chosen comes first")
            assertEquals(t.key.tonic.pitchClass, pitchClasses.last(), "and the right answer walks home last")
            assertTrue(pitchClasses.contains(t.key.pitchClassOf(asked)), "the right one is in it")
            assertTrue(fix.notes.all { it.voice == Voice.TONE }, "in the voice of the question")
        }
    }

    @Test
    fun `a wrong chord is heard first, then the right one, then home`() {
        val d = driver(Variant.TIR_ON_TONIC)
        repeat(20) { seed ->
            val t = task(d, 1, seed)
            val asked = t.chords[0]
            val chosen = t.chordChoices.first { it.root != asked.root }
            val ev = d.evaluate(t, Answer.ChordChoice(chosen))
            val fix = requireNotNull(ev.correction)
            assertTrue(ev.contrast!!.startsWith(com.kazaeza.muzykalka.music.DegreeFormatter.chord(chosen, com.kazaeza.muzykalka.music.DegreeNotation.ARABIC)), ev.contrast)
            val firstChord = fix.notes.filter { it.startBeat < 0.5 && it.voice == Voice.PIANO }.map { it.note!!.pitchClass }.toSet()
            assertEquals(chosen.quality.intervals.map { t.key.pitchClassOf(chosen.root) + it }.toSet(), firstChord, "the chosen chord first")
            assertTrue(fix.lengthBeats > t.question.lengthBeats * 2, "the chosen one, the right one and home")
        }
    }

    @Test
    fun `no contrast for a right answer, a pass, a chain, a pair or a bass - they keep their own correction`() {
        val classic = driver(Variant.TIR_CLASSIC)
        val t = task(classic)
        assertNull(classic.evaluate(t, Answer.DegreeChoice(t.notes.single().degree)).contrast)
        val pass = classic.evaluate(t, Answer.Pass)
        assertNull(pass.contrast)
        assertNotNull(pass.correction, "rule 5")

        val simon = driver(Variant.TIR_SIMON)
        val st = task(simon)
        assertNull(simon.evaluate(st, Answer.Pass).contrast)

        val bass = driver(Variant.TIR_BASS)
        val bt = task(bass)
        val other = bt.chordChoices.first { it.root != bt.chords[0].root }
        val ev = bass.evaluate(bt, Answer.ChordChoice(other))
        assertNull(ev.contrast, "a bass has no single wrong sound")
        assertNotNull(ev.correction)

        val dyad = driver(Variant.TIR_DYAD)
        val dt = task(dyad)
        assertNull(dyad.evaluate(dt, Answer.DegreeChoice(Degree.parse(if (dt.notes.single().degree.toString() == "5") "3" else "5"))).contrast)
    }

    @Test
    fun `over a chord the chosen note is heard over that same chord`() {
        val d = driver(Variant.TIR_OVER_CHORDS)
        repeat(10) { seed ->
            val t = task(d, 1, seed)
            val asked = t.notes.single().degree
            val chosen = t.noteChoices.first { !(it sameSoundAs asked) }
            val ev = d.evaluate(t, Answer.DegreeChoice(chosen))
            val fix = requireNotNull(ev.correction)
            assertTrue(ev.contrast!!.contains("над"), "what the note does against the chord: ${ev.contrast}")
            val piano = fix.notes.filter { it.voice == Voice.PIANO }
            assertTrue(piano.isNotEmpty(), "the chord sounds in the contrast")
            assertTrue(fix.lengthBeats > t.question.lengthBeats * 1.5)
        }
    }

    @Test
    fun `two chords - the given first chord stays in front of the wrong second one`() {
        val d = driver(Variant.TIR_TWO_CHORDS)
        val t = task(d)
        val chosen = t.chordChoices.first { it.root != t.chords[0].root }
        val ev = d.evaluate(t, Answer.ChordChoice(chosen))
        val fix = requireNotNull(ev.correction)
        val from = t.from!!
        val first = fix.notes.filter { it.voice == Voice.PIANO && it.startBeat < 0.1 }.map { it.note!!.pitchClass }.toSet()
        assertEquals(from.quality.intervals.map { t.key.pitchClassOf(from.root) + it }.toSet(), first, "the pair starts with the given chord")
        assertNotNull(ev.contrast)
        assertTrue(ChordParser.formatRoman(chosen).isNotBlank())
    }
}
