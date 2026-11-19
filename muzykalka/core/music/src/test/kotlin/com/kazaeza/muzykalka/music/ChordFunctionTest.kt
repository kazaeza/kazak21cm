package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-67: the job of a chord in the key — home, away, tension — and where it pulls. */
class ChordFunctionTest {
    private fun f(text: String, mode: Mode = Mode.MAJOR) = ChordFunction.of(ChordDegree.parse(text), mode)
    private fun pull(text: String, mode: Mode = Mode.MAJOR) = ChordFunction.pullsTo(ChordDegree.parse(text), mode)

    @Test
    fun `the diatonic chords of major fall into the three jobs`() {
        assertEquals(ChordFunction.HOME, f("I"))
        assertEquals(ChordFunction.HOME, f("vi"))
        assertEquals(ChordFunction.HOME, f("iii"))
        assertEquals(ChordFunction.AWAY, f("IV"))
        assertEquals(ChordFunction.AWAY, f("ii"))
        assertEquals(ChordFunction.TENSION, f("V"))
        assertEquals(ChordFunction.TENSION, f("V7"))
        assertEquals(ChordFunction.TENSION, f("viio"))
    }

    @Test
    fun `tension pulls home, a step away goes on, home goes anywhere`() {
        assertEquals(listOf(ChordDegree.parse("I")), pull("V7"))
        assertEquals(listOf(ChordDegree.parse("i")), pull("V", Mode.MINOR))
        assertEquals(listOf(ChordDegree.parse("V")), pull("II"))
        assertEquals(listOf(ChordDegree.parse("vi")), pull("III"))
        assertEquals(listOf(ChordDegree.parse("V"), ChordDegree.parse("I")), pull("IV"))
        assertTrue(pull("I").isEmpty())
    }

    @Test
    fun `in minor the relative major is a home too`() {
        assertEquals(ChordFunction.HOME, f("i", Mode.MINOR))
        assertEquals(ChordFunction.HOME, f("♭III", Mode.MINOR))
        assertEquals(ChordFunction.AWAY, f("iv", Mode.MINOR))
        assertEquals(ChordFunction.AWAY, f("♭VI", Mode.MINOR))
    }
}
