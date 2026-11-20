package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-17: a tap on a chord of the song sheet — how it sounds ([Arranger.strum]) and what it is called ([ChordVibes]). */
class ChordAuditionTest {
    private val cMajor = Key(NoteName(Letter.C, Accidental.NATURAL), Mode.MAJOR)
    private val aMinor = Key(NoteName(Letter.A, Accidental.NATURAL), Mode.MINOR)
    private val arranger = Arranger()

    private fun pitchClasses(score: Score) = score.notes.map { it.note!!.pitchClass }.toSet()

    private fun expected(chord: ChordDegree, key: Key) =
        chord.quality.intervals.map { key.pitchClassOf(chord.root) + it }.toSet()

    @Test
    fun `a strummed chord is the chord of the key, on the guitar, a downstroke from the bass string up`() {
        ChordQuality.entries.forEach { q ->
            listOf(cMajor, aMinor).forEach { key ->
                Degree.parse("5").let { root ->
                    val chord = ChordDegree(root, q)
                    val score = arranger.strum(chord, key)
                    assertEquals(expected(chord, key), pitchClasses(score), "$q in $key")
                    assertTrue(score.notes.all { it.voice == Voice.GUITAR }, "one voice: the guitar")
                    assertEquals(key.pitchClassOf(chord.root), score.notes.first().note!!.pitchClass, "the root is struck first")
                    assertTrue(score.notes.zipWithNext().all { (a, b) -> b.startBeat > a.startBeat }, "a downstroke, not a cluster")
                }
            }
        }
    }

    @Test
    fun `O-73 - a strum is the shape a hand holds, struck like a hand`() {
        val score = arranger.strum(ChordParser.parse("I"), cMajor, tempoBpm = 100.0)
        assertEquals(listOf(48, 52, 55, 60, 64), score.notes.map { it.note!!.value }, "C as x32010: five strings over two octaves")
        val gaps = score.notes.zipWithNext { a, b -> (b.startBeat - a.startBeat) * 60.0 / score.tempoBpm }
        assertTrue(gaps.all { it in 0.008..0.03 }, "a gap of ~16 ms between the strings: $gaps")
        assertTrue(gaps.distinct().size > 1, "uneven, like a hand")
        assertTrue(score.notes.first().velocity > score.notes.last().velocity, "the bass string firmest")
        assertEquals(score.notes.first().velocity, arranger.strum(ChordParser.parse("I"), cMajor, tempoBpm = 100.0).notes.first().velocity, "the same each time")
    }

    @Test
    fun `the root of a plain chord is its lowest string, a slash bass goes below it`() {
        val v7 = ChordParser.parse("V7")
        val plain = arranger.strum(v7, cMajor).notes.map { it.note!! }
        assertEquals(cMajor.pitchClassOf(v7.root), plain.first().pitchClass)

        val one3 = ChordParser.parse("I/3")
        val slash = arranger.strum(one3, cMajor).notes.map { it.note!! }
        assertEquals(cMajor.pitchClassOf(Degree.parse("3")), slash.first().pitchClass, "the third is the bass")
        assertEquals(expected(ChordDegree(one3.root, one3.quality), cMajor), slash.map { it.pitchClass }.toSet(), "the third is a tone of the chord already")

        val over = ChordParser.parse("I/2")
        val low = arranger.strum(over, cMajor).notes.map { it.note!! }
        assertEquals(cMajor.pitchClassOf(Degree.parse("2")), low.first().pitchClass, "a bass that is not a tone of the chord is still the bass")
        assertTrue(low.any { it.pitchClass == cMajor.pitchClassOf(Degree.parse("1")) }, "and the chord is above it")
    }

    @Test
    fun `the same chord is held in every key of the book, within the hand's reach and the guitar's range`() {
        val chord = ChordParser.parse("ii7")
        KeyCatalog.practical(Mode.MAJOR).forEach { k ->
            val midi = arranger.strum(chord, k).notes.map { it.note!!.value }
            assertTrue(midi.min() >= 40 && midi.max() <= 40 + 12 + 24 + 4, "$k: $midi")
        }
    }

    @Test
    fun `every chord quality has words and every root in every mode has a job`() {
        ChordQuality.entries.forEach { q ->
            val v = ChordVibes.of(ChordDegree(Degree.parse("1"), q), Mode.MAJOR)
            assertTrue(v.quality.isNotBlank(), "$q has a quality")
        }
        Mode.entries.forEach { mode ->
            for (semitones in 0..11) {
                listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DIMINISHED, ChordQuality.DOMINANT7, ChordQuality.POWER, ChordQuality.SUS4)
                    .forEach { q ->
                        val v = ChordVibes.of(ChordDegree(Degree.canonical(semitones), q), mode)
                        assertTrue(v.role.isNotBlank() && v.line.isNotBlank(), "$q on $semitones in $mode")
                    }
            }
        }
    }

    @Test
    fun `the words the user confuses are told apart`() {
        fun role(text: String, mode: Mode = Mode.MAJOR) = ChordVibes.of(ChordParser.parse(text), mode).role
        fun quality(text: String) = ChordVibes.of(ChordParser.parse(text), Mode.MAJOR).quality

        assertEquals("тоника", role("I"))
        assertEquals("доминанта", role("V"))
        assertEquals("доминанта", role("V7"))
        assertEquals("доминанта", role("V9"))
        assertEquals("субдоминанта", role("IV"))
        assertEquals("разбег к доминанте", role("ii"))
        assertEquals("параллельный минор", role("vi"))
        assertEquals("ведущий тон", role("vii°"))
        assertEquals("рок-ход", role("bVII"))
        assertEquals("тоника", role("i", Mode.MINOR))
        assertEquals("параллельный мажор", role("bIII", Mode.MINOR))
        assertEquals("доминанта параллельного мажора", role("bVII7", Mode.MINOR), "Autumn Leaves: the 7th chord on bVII leads to bIII")
        assertEquals("седьмая ступень минора", role("bVII", Mode.MINOR))
        assertEquals("рок-ход", role("bVII7"))

        assertTrue(quality("V9").startsWith("Нонаккорд"), quality("V9"))
        assertTrue(quality("Iadd9").contains("нона"), quality("Iadd9"))
        assertTrue(quality("ii").startsWith("Минорный"), quality("ii"))
        assertTrue(quality("V7").contains("септима"), quality("V7"))
        assertTrue(quality("IV").startsWith("Мажорный"), quality("IV"))
    }

    @Test
    fun `the dominant of a minor key says where its leading tone comes from`() {
        val v7 = ChordVibes.of(ChordParser.parse("V7"), Mode.MINOR)
        assertTrue(v7.line.contains("гармонического минора"), v7.line)
        val inMajor = ChordVibes.of(ChordParser.parse("V7"), Mode.MAJOR)
        assertTrue(!inMajor.line.contains("гармонического"), inMajor.line)
        val harmonic = ChordVibes.of(ChordParser.parse("V7"), Mode.HARMONIC_MINOR)
        assertTrue(!harmonic.line.contains("гармонического"), "already has the leading tone: ${harmonic.line}")
    }

    @Test
    fun `the title names the chord in the user's notation, never in letters`() {
        assertEquals("V⁷ — доминанта", ChordVibes.title(ChordParser.parse("V7"), Mode.MAJOR, DegreeNotation.ROMAN))
        assertEquals("V⁷ — доминанта", ChordVibes.title(ChordParser.parse("V7"), Mode.MAJOR, DegreeNotation.ARABIC))
        assertEquals("соль⁷ — доминанта", ChordVibes.title(ChordParser.parse("V7"), Mode.MAJOR, DegreeNotation.SOLFEGE))
    }
}
