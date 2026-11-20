package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-73 (ADR-070): a strummed chord is held the way a guitarist holds it. */
class GuitarVoicingTest {
    private val cMajor = Key(NoteName.parse("C"), Mode.MAJOR)

    private fun text(shape: GuitarShape?) =
        shape?.frets?.joinToString("") { f -> f?.let { if (it > 9) "($it)" else "$it" } ?: "x" }

    private fun shape(chord: String, key: Key = cMajor) = GuitarVoicings.shape(ChordParser.parse(chord), key)

    @Test
    fun `the chords of the first lesson are the shapes everyone knows`() {
        assertEquals("x32010", text(shape("I")), "C")
        assertEquals("xx0231", text(shape("ii")), "Dm")
        assertEquals("022000", text(shape("iii")), "Em")
        assertEquals("133211", text(shape("IV")), "F: the barre, not the four-string cut")
        assertEquals("320003", text(shape("V")), "G")
        assertEquals("x02210", text(shape("vi")), "Am")
        assertEquals("320001", text(shape("V7")), "G7")
        val d = Key(NoteName.parse("D"), Mode.MAJOR)
        assertEquals("xx0232", text(shape("I", d)), "D")
        val b = Key(NoteName.parse("B"), Mode.MAJOR)
        assertEquals("x24442", text(shape("I", b)), "B: with no open string left, the barre")
    }

    @Test
    fun `a slash chord has its bass at the bottom`() {
        assertEquals("032010", text(shape("I/3")), "C/E")
        val bass = shape("I/3")!!.notes(Tuning.STANDARD).first()
        assertEquals(cMajor.pitchClassOf(Degree.parse("3")), bass.pitchClass)
    }

    @Test
    fun `every quality in every key has a shape a hand can hold`() {
        val keys = Mode.entries.flatMap { m -> KeyCatalog.practical(m) }.distinct()
        for (q in ChordQuality.entries) for (key in keys) for (root in listOf("1", "4", "5")) {
            val chord = ChordDegree(Degree.parse(root), q)
            val shape = requireNotNull(GuitarVoicings.shape(chord, key)) { "no shape for $q on $root in $key" }
            val notes = shape.notes(Tuning.STANDARD)
            val rootPc = key.pitchClassOf(chord.root)
            val tones = chord.quality.intervals.map { rootPc + it }.toSet()
            assertEquals(rootPc, notes.first().pitchClass, "the root is the bass: $q $root $key")
            assertTrue(notes.all { it.pitchClass in tones }, "only the tones of the chord: $q $root $key")
            val fretted = shape.frets.filterNotNull().filter { it > 0 }
            assertTrue((fretted.maxOrNull() ?: 0) - (fretted.minOrNull() ?: 0) <= 3, "a hand's reach: $q $root $key ${text(shape)}")
            assertTrue(notes.size >= 3, "$q $root $key")
            assertTrue(shape.frets.dropWhile { it == null }.dropLastWhile { it == null }.none { it == null }, "no muted string among the struck ones")
        }
    }

    @Test
    fun `a chord is a real spread - four to six strings well over an octave, not a close triad`() {
        for (c in listOf("I", "IV", "V", "vi", "V7", "ii", "iii")) {
            val notes = shape(c)!!.notes(Tuning.STANDARD).map { it.value }
            assertTrue(notes.size >= 4, "$c: ${notes.size} strings")
            assertTrue(notes.max() - notes.min() >= 14, "$c spans ${notes.max() - notes.min()} semitones")
        }
    }

    @Test
    fun `the same pitch is never struck twice`() {
        for (q in ChordQuality.entries) for (key in KeyCatalog.practical(Mode.MAJOR)) {
            val notes = shape("I", key)!!.notes(Tuning.STANDARD).map { it.value }
            assertEquals(notes.size, notes.toSet().size, "unison in $key: $notes")
            assertNotNull(GuitarVoicings.shape(ChordDegree(Degree.parse("1"), q), key))
        }
    }

    @Test
    fun `a tuning other than the standard one is searched too`() {
        val dropD = GuitarVoicings.shape(ChordParser.parse("V"), cMajor, Tuning.DROP_D)
        assertNotNull(dropD)
        assertEquals(cMajor.pitchClassOf(Degree.parse("5")), dropD!!.notes(Tuning.DROP_D).first().pitchClass)
    }
}
