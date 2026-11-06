package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.random.Random

class MusicTheoryTest {

    @Nested
    inner class Pitches {
        @Test
        fun `midi octave and frequency`() {
            assertEquals(4, MidiNote(60).octave)
            assertEquals(3, MidiNote(59).octave)
            assertEquals(440.0, MidiNote(69).frequency(), 1e-9)
            assertEquals(82.4069, MidiNote(40).frequency(), 1e-3) // low E
        }

        @Test
        fun `nearest note and cents`() {
            val r = MidiNote.nearest(445.0)
            assertEquals(MidiNote(69), r.note)
            assertEquals(19.56, r.cents, 0.01)
            val flat = MidiNote.nearest(MidiNote(45).frequency() * Math.pow(2.0, -30.0 / 1200))
            assertEquals(MidiNote(45), flat.note)
            assertEquals(-30.0, flat.cents, 1e-6)
        }

        @Test
        fun `note names parse and spell`() {
            assertEquals(PitchClass.of(6), NoteName.parse("F#").pitchClass)
            assertEquals(PitchClass.of(6), NoteName.parse("G♭").pitchClass)
            assertEquals("B♭", NoteName.parse("Bb").toString())
            assertEquals(MidiNote(60), MidiNote.of(NoteName.parse("C"), 4))
            assertEquals(MidiNote(60), MidiNote.of(NoteName.parse("B#"), 3))
        }
    }

    @Nested
    inner class Degrees {
        @Test
        fun `semitones follow the major-relative convention`() {
            assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), Degree.CHROMATIC.map { it.semitones })
            assertEquals(6, Degree.FLAT_FIVE.semitones)
            assertTrue(Degree.SHARP_FOUR sameSoundAs Degree.FLAT_FIVE)
            assertFalse(Degree.SHARP_FOUR == Degree.FLAT_FIVE)
        }

        @Test
        fun `parse degree notes with octaves`() {
            assertEquals(
                listOf(DegreeNote(Degree.THREE), DegreeNote(Degree.FIVE, -1), DegreeNote(Degree.ONE, 1), DegreeNote(Degree.FLAT_SEVEN)),
                DegreeNote.parseLine("3 5, 1' b7"),
            )
            assertEquals(-1, DegreeNote(Degree.SEVEN, -1).semitonesFromTonic)
        }

        @Test
        fun `invalid degrees are rejected`() {
            assertThrows<IllegalArgumentException> { Degree(8) }
            assertThrows<IllegalArgumentException> { Degree(3, Accidental.DOUBLE_FLAT) }
        }
    }

    @Nested
    inner class Keys {
        @Test
        fun `spelling in sharp, flat and minor keys`() {
            val e = Key(NoteName.parse("E"), Mode.MINOR)
            assertEquals(listOf("E", "F♯", "G", "A", "B", "C", "D"), e.mode.degrees.map { e.spell(it).toString() })
            val bb = Key(NoteName.parse("Bb"), Mode.MAJOR)
            assertEquals(listOf("B♭", "C", "D", "E♭", "F", "G", "A"), bb.mode.degrees.map { bb.spell(it).toString() })
            val c = Key(NoteName.parse("C"), Mode.MAJOR)
            assertEquals("F♯", c.spell(Degree.SHARP_FOUR).toString())
            assertEquals("G♭", c.spell(Degree.FLAT_FIVE).toString())
        }

        @Test
        fun `degreeOf prefers the mode's own spelling`() {
            val fLydian = Key(NoteName.parse("F"), Mode.LYDIAN)
            assertEquals(Degree.SHARP_FOUR, fLydian.degreeOf(NoteName.parse("B").pitchClass))
            val bLocrian = Key(NoteName.parse("B"), Mode.LOCRIAN)
            assertEquals(Degree.FLAT_FIVE, bLocrian.degreeOf(NoteName.parse("F").pitchClass))
            val aMinor = Key(NoteName.parse("A"), Mode.MINOR)
            assertEquals(Degree.FLAT_THREE, aMinor.degreeOf(NoteName.parse("C").pitchClass))
            assertEquals(Degree.SIX, aMinor.degreeOf(NoteName.parse("F#").pitchClass)) // chromatic → canonical
        }

        @Test
        fun `practical keys have no double accidentals and at most 6 accidentals`() {
            for (mode in Mode.entries) {
                val keys = KeyCatalog.practical(mode)
                assertEquals(12, keys.map { it.tonic.pitchClass }.toSet().size, "$mode")
                keys.forEach { k ->
                    assertFalse(k.hasDoubleAccidentals, "$k")
                    assertTrue(k.accidentalCount <= 6, "$k has ${k.accidentalCount}")
                }
            }
            assertEquals("D♭", KeyCatalog.practical(PitchClass.of(1), Mode.MAJOR).tonic.toString())
            assertEquals("G♯", KeyCatalog.practical(PitchClass.of(8), Mode.MINOR).tonic.toString())
            assertEquals("B♭", KeyCatalog.practical(PitchClass.of(10), Mode.MAJOR).tonic.toString())
        }

        @Test
        fun `key picker never repeats recent tonics and covers all 12`() {
            val picker = KeyPicker(Random(1), avoidLast = 3)
            val picks = (1..600).map { picker.next(Mode.MINOR).tonic.pitchClass }
            picks.windowed(4).forEach { w -> assertEquals(4, w.toSet().size, "repeat within 4: $w") }
            assertEquals(12, picks.toSet().size)
        }

        @Test
        fun `russian key names`() {
            assertEquals("в ми миноре", RuNames.inKey(Key(NoteName.parse("E"), Mode.MINOR)))
            assertEquals("в си-бемоль мажоре", RuNames.inKey(Key(NoteName.parse("Bb"), Mode.MAJOR)))
            assertEquals("фа-диез дорийский", RuNames.key(Key(NoteName.parse("F#"), Mode.DORIAN)))
        }
    }

    @Nested
    inner class Chords {
        @Test
        fun `roman parsing and formatting round-trip`() {
            val cases = mapOf(
                "I" to "I", "ii7" to "ii⁷", "V7" to "V⁷", "vii°" to "vii°", "bVII" to "♭VII", "iiø7" to "iiø⁷",
                "Imaj7" to "Imaj⁷", "bIIImaj7" to "♭IIImaj⁷", "iv7" to "iv⁷", "V7sus4" to "V⁷sus⁴", "I/3" to "I/3",
                "vii°7" to "vii°⁷", "IV6" to "IV⁶", "I5" to "I⁵",
            )
            cases.forEach { (src, expected) ->
                val parsed = ChordDegree.parse(src)
                assertEquals(expected, ChordParser.formatRoman(parsed), src)
                assertEquals(parsed, ChordDegree.parse(ChordParser.formatRoman(parsed)), "round-trip $src")
            }
        }

        @Test
        fun `arabic parsing`() {
            assertEquals(ChordDegree(Degree.TWO, ChordQuality.MINOR7), ChordDegree.parse("2m7"))
            assertEquals(ChordDegree(Degree.FIVE, ChordQuality.DOMINANT7), ChordDegree.parse("57"))
            assertEquals(ChordDegree(Degree.FLAT_SEVEN, ChordQuality.MAJOR), ChordDegree.parse("b7"))
            assertEquals("2m⁷", ChordParser.formatArabic(ChordDegree.parse("ii7")))
        }

        @Test
        fun `bad chords are rejected`() {
            assertThrows<IllegalArgumentException> { ChordDegree.parse("X7") }
            assertThrows<IllegalArgumentException> { ChordDegree.parse("Vzz") }
            assertThrows<IllegalArgumentException> { ChordDegree.parse("") }
        }

        @Test
        fun `absolute symbols only through a key`() {
            val e = Key(NoteName.parse("E"), Mode.MINOR)
            assertEquals("Am7", ChordDegree.parse("iv7").absoluteSymbol(e))
            assertEquals("F♯m7♭5", ChordDegree.parse("iiø7").absoluteSymbol(e))
            assertEquals("Cmaj7", ChordDegree.parse("bVImaj7").absoluteSymbol(e))
        }

        @Test
        fun `diatonic chords of major and minor`() {
            assertEquals("I ii iii IV V vi vii°", (0..6).joinToString(" ") { Harmony.diatonicChord(Mode.MAJOR, it).toString() })
            assertEquals(
                "Imaj⁷ ii⁷ iii⁷ IVmaj⁷ V⁷ vi⁷ viiø⁷",
                (0..6).joinToString(" ") { Harmony.diatonicChord(Mode.MAJOR, it, sevenths = true).toString() },
            )
            assertEquals("i ii° ♭III iv v ♭VI ♭VII", (0..6).joinToString(" ") { Harmony.diatonicChord(Mode.MINOR, it).toString() })
        }

        @Test
        fun `cadence chords are diatonic except the borrowed dominant`() {
            for (mode in Mode.entries) {
                Harmony.cadence(mode).forEach { chord ->
                    val borrowedV7 = chord == ChordDegree.parse("V7") && mode.hasMinorThird
                    if (!borrowedV7) {
                        chord.semitonesFromTonic().forEach { st ->
                            assertTrue(mode.diatonicSpelling(st) != null, "$chord not diatonic in $mode")
                        }
                    }
                }
            }
        }
    }

    @Nested
    inner class Resolutions {
        private fun res(start: String, mode: Mode = Mode.MAJOR) =
            Harmony.resolution(DegreeNote.parse(start), mode).joinToString(" ")

        @Test
        fun `FET-style resolutions in major`() {
            assertEquals("", res("1"))
            assertEquals("1", res("2"))
            assertEquals("2 1", res("3"))
            assertEquals("3 2 1", res("4"))
            assertEquals("6 7 1'", res("5"))
            assertEquals("7 1'", res("6"))
            assertEquals("1'", res("7"))
        }

        @Test
        fun `chromatic notes step to the adjacent scale tone first`() {
            assertEquals("1", res("b2"))
            assertEquals("2 1", res("b3"))
            assertEquals("5 6 7 1'", res("#4"))
            assertEquals("4 3 2 1", res("b5"))
            assertEquals("6 7 1'", res("b6"))
            assertEquals("7 1'", res("b7"))
        }

        @Test
        fun `minor and modal resolutions use the mode's scale`() {
            assertEquals("♭7 1'", res("b6", Mode.MINOR))
            assertEquals("2 1", res("b3", Mode.MINOR))
            assertEquals("5 6 7 1'", res("#4", Mode.LYDIAN))
            assertEquals("4 ♭3 ♭2 1", res("b5", Mode.LOCRIAN))
            assertEquals("6, 7, 1", res("5,"))
        }

        @Test
        fun `every chromatic degree resolves to a tonic in every mode`() {
            for (mode in Mode.entries) for (d in Degree.CHROMATIC + Degree.FLAT_FIVE) {
                val path = Harmony.resolution(DegreeNote(d), mode)
                if (d.semitones == 0) continue
                val last = path.last()
                assertEquals(Degree.ONE, last.degree, "$d in $mode → $path")
                assertTrue(path.size <= 5, "$d in $mode too long: $path")
            }
        }
    }

    @Nested
    inner class Neck {
        private val board = Fretboard(Tuning.STANDARD, 22)

        @Test
        fun `string numbering is guitarist style`() {
            assertEquals(MidiNote(64), Tuning.STANDARD.open(1))
            assertEquals(MidiNote(40), Tuning.STANDARD.open(6))
            assertEquals(MidiNote(54), board.noteAt(FretPosition(5, 9))) // F♯3 on the A string
        }

        @Test
        fun `F sharp on the 5th string sits at frets 9 and 21`() {
            assertEquals(listOf(9, 21), board.fretsOf(PitchClass.of(6), 5))
        }

        @Test
        fun `positions of E4`() {
            val pos = board.positionsOf(MidiNote(64)).toSet()
            assertEquals(
                setOf(FretPosition(1, 0), FretPosition(2, 5), FretPosition(3, 9), FretPosition(4, 14), FretPosition(5, 19)),
                pos,
            )
        }

        @Test
        fun `drop tunings`() {
            assertEquals(MidiNote(38), Tuning.DROP_D.open(6))
            assertEquals(MidiNote(36), Tuning.DROP_C.open(6))
        }
    }

    @Nested
    inner class Songs {
        private val autumnLeaves = """
            A: | iv7 | bVII7 | bIIImaj7 | bVImaj7 | iiø7 | V7 | i | i |
            B: | iiø7 | V7 | i | i | iv7 | bVII7 | bIIImaj7 | bVImaj7 |
            C: | iiø7 | V7 | i7 | bVI7 | iiø7 V7 | i | i |
        """.trimIndent()

        @Test
        fun `Autumn Leaves in degrees realises correctly in E minor`() {
            val song = Song(
                id = "al", title = "Autumn Leaves", mode = Mode.MINOR,
                sections = ProgressionText.parseSections(autumnLeaves), form = ProgressionText.parseForm("AABC"),
            )
            assertEquals("AABC", song.formString)
            assertEquals(31, song.barCount)
            val e = Key(NoteName.parse("E"), Mode.MINOR)
            assertEquals(
                "Am7 D7 Gmaj7 Cmaj7 F♯m7♭5 B7 Em Em",
                song.section("A").bars.flatMap { it.chords }.joinToString(" ") { it.absoluteSymbol(e) },
            )
            assertEquals(ChordDegree.parse("iv7"), song.firstChord)
            val reparsed = ProgressionText.parseSections(ProgressionText.format(song.sections))
            assertEquals(song.sections, reparsed)
        }

        @Test
        fun `form must reference existing sections`() {
            assertThrows<IllegalArgumentException> {
                Song(id = "x", title = "x", mode = Mode.MAJOR, sections = ProgressionText.parseSections("A: | I |"), form = listOf("A", "B"))
            }
        }
    }

    @Nested
    inner class Arranging {
        @Test
        fun `voicings sound the chord in the right key`() {
            val arranger = Arranger()
            val key = Key(NoteName.parse("E"), Mode.MINOR)
            val v = arranger.voicing(ChordDegree.parse("iv7"), key) // Am7
            assertEquals(listOf(9, 0, 4, 7), v.map { it.pitchClass.value })
            assertTrue(v.first().value in 53..64)
            val bass = arranger.bassNote(ChordDegree.parse("V7"), key)
            assertEquals(11, bass.pitchClass.value)
            assertTrue(bass.value in 36..47)
        }

        @Test
        fun `line places the tonic at or above the floor`() {
            val key = Key(NoteName.parse("D"), Mode.MAJOR)
            val score = Arranger().line(DegreeNote.parseLine("1 3 5 1'"), key, tonicFloor = MidiNote(57))
            assertEquals(listOf(62, 66, 69, 74), score.notes.map { it.note!!.value })
            assertTrue(abs(score.lengthSeconds - 3.95 * 60.0 / 90.0) < 1e-9) // last note lasts 0.95 beat
        }
    }

    @Nested
    inner class Formatting {
        @Test
        fun `notations`() {
            assertEquals("♭3", DegreeFormatter.degree(Degree.FLAT_THREE, DegreeNotation.ARABIC))
            assertEquals("♭III", DegreeFormatter.degree(Degree.FLAT_THREE, DegreeNotation.ROMAN))
            assertEquals("♭ми", DegreeFormatter.degree(Degree.FLAT_THREE, DegreeNotation.SOLFEGE))
            assertEquals("ре m⁷", DegreeFormatter.chord(ChordDegree.parse("ii7"), DegreeNotation.SOLFEGE))
            assertEquals("фа-диез", RuNames.note(NoteName.parse("F#"), NoteNaming.SOLFEGE_RU))
        }
    }
}
