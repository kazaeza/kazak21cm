package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-21 «Позиция» (ADR-050): the chords of a key in one five-fret zone of the neck. */
class PositionsTest {
    private val board = Fretboard(Tuning.STANDARD)
    private val gMajor = KeyCatalog.practical(PitchClass.of(7), Mode.MAJOR)

    @Test
    fun `any zone holds the roots of all seven chords on the three bass strings`() {
        for (mode in listOf(Mode.MAJOR, Mode.MINOR)) for (key in KeyCatalog.practical(mode)) for (start in 0..Positions.lastStart(board)) {
            val roots = Positions.roots(board, key, start)
            assertEquals((0..6).toList(), roots.map { it.step })
            roots.forEach { r ->
                assertTrue(r.place.string in listOf(6, 5, 4), "a bass string: ${r.place}")
                assertTrue(r.place.fret in Positions.zone(start), "inside the zone")
                assertEquals(key.pitchClassOf(r.chord.root), board.noteAt(r.place).pitchClass, "the root sounds there")
                // the lowest string that has the root: nothing lower in the zone
                val lower = (r.place.string + 1..6).none { s -> board.fretsOf(key.pitchClassOf(r.chord.root), s).any { it in Positions.zone(start) } }
                assertTrue(lower, "the fullest shape: $r")
            }
        }
    }

    @Test
    fun `the home zone puts the tonic on the lowest string, a fret into the zone`() {
        assertEquals(2, Positions.homeZone(board, gMajor), "G: the 3rd fret of the 6th string, the zone from the 2nd")
        val e = KeyCatalog.practical(PitchClass.of(4), Mode.MAJOR)
        assertEquals(0, Positions.homeZone(board, e), "the open position")
        val fSharp = KeyCatalog.practical(PitchClass.of(6), Mode.MAJOR)
        assertEquals(1, Positions.homeZone(board, fSharp))
        for (key in KeyCatalog.practical(Mode.MINOR)) {
            val start = Positions.homeZone(board, key)
            val tonic = Positions.roots(board, key, start).first()
            assertEquals(6, tonic.place.string)
            assertTrue(tonic.place.fret - start <= 1)
        }
    }

    @Test
    fun `the shape grows from where the root is`() {
        val roots = Positions.roots(board, gMajor, 2).associateBy { it.step }
        assertEquals(FretPosition(6, 3), roots.getValue(0).place)
        assertEquals(ChordForm.E, roots.getValue(0).form, "I: the barre from the 6th string")
        assertEquals(FretPosition(5, 3), roots.getValue(3).place)
        assertEquals(ChordForm.A, roots.getValue(3).form, "IV: the barre from the 5th")
        assertEquals(FretPosition(5, 5), roots.getValue(4).place)
        assertEquals(ChordForm.C, roots.getValue(4).form, "V: the root under the little finger on the 5th")
        assertEquals(FretPosition(4, 2), roots.getValue(5).place)
        assertEquals(ChordForm.D, roots.getValue(5).form, "vi: from the 4th string")
        assertEquals(FretPosition(6, 5), roots.getValue(1).place)
        assertNull(roots.getValue(1).form, "ii is minor with its root under the little finger: no handy shape, move the zone")
        // a drop tuning breaks the fourths between the bass strings: the map stays, the shapes are not named
        val drop = Fretboard(Tuning.DROP_D)
        assertTrue(Positions.roots(drop, gMajor, 2).all { it.form == null })
        assertEquals(7, Positions.roots(drop, gMajor, 2).size)
    }

    @Test
    fun `the tones of a chord fill the zone and carry their degree in the key`() {
        val v = Harmony.diatonicChord(Mode.MAJOR, 4) // V of G: its tones are 5, 7 and 2 of the key
        val tones = Positions.tones(board, gMajor, v, 2)
        assertEquals(setOf("5", "7", "2"), tones.map { it.degree.toString() }.toSet())
        assertTrue(tones.all { it.place.fret in 2..6 })
        assertTrue(tones.any { it.isRoot && it.place == FretPosition(5, 5) }, "the root of the map is among them")
        assertTrue(tones.map { it.place.string }.toSet().size >= 5, "across the neck: ${tones.map { it.place }}")
        tones.forEach { t ->
            val pc = board.noteAt(t.place).pitchClass
            assertEquals(gMajor.pitchClassOf(t.degree), pc)
            assertEquals(t.degree sameSoundAs Degree.FIVE, t.isRoot)
        }
        // the E-shape of the tonic: root on the 6th string, 3rd fret, and its octaves on the 4th and the 1st
        val one = Positions.tones(board, gMajor, Harmony.diatonicChord(Mode.MAJOR, 0), 2).filter { it.isRoot }.map { it.place }.toSet()
        assertEquals(setOf(FretPosition(6, 3), FretPosition(4, 5), FretPosition(1, 3)), one)
        // a minor key spells its tones the minor's way
        val aMinor = KeyCatalog.practical(PitchClass.of(9), Mode.MINOR)
        val i = Positions.tones(board, aMinor, Harmony.diatonicChord(Mode.MINOR, 0), 4)
        assertEquals(setOf("1", "♭3", "5"), i.map { it.degree.toString() }.toSet())
    }
}
