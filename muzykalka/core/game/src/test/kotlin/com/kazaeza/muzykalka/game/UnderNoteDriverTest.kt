package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Mode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-15 «Под нотой» (ADR-029): the note is shown, the chord droning under it is asked. */
class UnderNoteDriverTest {
    private fun driver(seed: Int = 1) = TirDriver(KeyPicker(Random(seed)), variant = Variant.TIR_UNDER_NOTE)

    @Test
    fun `the chord under a shown note is asked, over its own drone`() {
        val d = driver()
        repeat(3) { block ->
            val tasks = List(TirDriver.KEY_BLOCK) { d.nextTask(DriverContext(Random(block * 100 + it), 1, emptyList())) }
            val first = tasks.first()
            assertTrue(tasks.all { it.key == first.key }, "a key holds for a block")
            val pad = TirDriver.underNotePad(first.key.mode, 1)
            assertEquals(4, pad.size)
            tasks.forEach { t ->
                val chord = t.chords.single()
                val note = t.notes.single().degree
                assertTrue(t.underNote)
                assertEquals(TirKind.CHORD, t.kind)
                assertEquals(pad, t.chordChoices)
                assertTrue(chord in pad)
                assertEquals(Arranger().chordDrone(chord, t.key), t.drone)
                assertTrue(TirDriver.holds(chord, note), "$note is a tone of $chord")
                assertTrue(pad.count { TirDriver.holds(it, note) } >= 2, "$note does not give $chord away")
                assertEquals("tir:under:L1:" + ChordParser.formatRoman(chord), t.itemKey)
                assertNull(t.over, "the chord is not shown")
            }
        }
    }

    @Test
    fun `a new key is set over the tonic, then the drone moves to the chord`() {
        val d = driver(5)
        val tasks = List(TirDriver.KEY_BLOCK + 1) { d.nextTask(DriverContext(Random(it), 2, emptyList())) }
        val first = tasks.first()
        assertNotNull(first.intro, "the scale opens the block")
        assertEquals(Arranger().drone(first.key), first.introDrone)
        assertEquals(first.question, first.prompt.audio, "the prompt is the note alone")
        tasks.drop(1).take(TirDriver.KEY_BLOCK - 1).forEach { assertNull(it.intro) }
        assertNotNull(tasks.last().intro, "the next block opens with its scale again")
        // Over many blocks the first chord is not always I.
        val firsts = List(12) { b -> driver(b).nextTask(DriverContext(Random(b), 1, emptyList())).chords.single() }
        assertTrue(firsts.distinct().size > 1)
    }

    @Test
    fun `every chord of the pad can be asked, in major and minor`() {
        for (mode in listOf(Mode.MAJOR, Mode.MINOR)) for (level in 1..2) {
            val pad = TirDriver.underNotePad(mode, level)
            pad.forEach { c ->
                val shared = TirDriver.chordTones(c, mode).filter { d -> pad.count { TirDriver.holds(it, d) } >= 2 }
                assertTrue(shared.isNotEmpty(), "$mode L$level: $c has a shared tone")
            }
        }
    }

    @Test
    fun `the right chord goes home, a wrong one hears the note over its chord again`() {
        val d = driver(3)
        repeat(20) {
            val t = d.nextTask(DriverContext(Random(it), 2, emptyList()))
            val right = d.evaluate(t, Answer.ChordChoice(t.chords.single()))
            assertTrue(right.correct)
            assertNotNull(right.confirmation)
            val other = t.chordChoices.first { c -> c != t.chords.single() }
            val wrong = d.evaluate(t, Answer.ChordChoice(other))
            assertFalse(wrong.correct)
            assertNotNull(wrong.correction, "rule 5")
            assertNotNull(d.evaluate(t, Answer.Pass).correction)
            assertFalse(d.evaluate(t, Answer.DegreeChoice(t.notes.single().degree)).correct, "naming the note is not the answer")
        }
    }

    @Test
    fun `under note is not a spaced review item and over chords is untouched`() {
        assertNull(TirDriver.reviewKey("tir:under:L1:IV"))
        val over = TirDriver(KeyPicker(Random(1)), variant = Variant.TIR_OVER_CHORDS).nextTask(DriverContext(Random(1), 1, emptyList()))
        assertFalse(over.underNote)
        assertNull(over.intro)
    }
}
