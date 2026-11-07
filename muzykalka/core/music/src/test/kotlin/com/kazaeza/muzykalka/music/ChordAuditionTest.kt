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
    fun `a strummed chord is the chord of the key, on the guitar, from low to high`() {
        ChordQuality.entries.forEach { q ->
            listOf(cMajor, aMinor).forEach { key ->
                Degree.parse("5").let { root ->
                    val chord = ChordDegree(root, q)
                    val score = arranger.strum(chord, key)
                    assertEquals(expected(chord, key), pitchClasses(score), "$q in $key")
                    assertTrue(score.notes.all { it.voice == Voice.GUITAR }, "one voice: the guitar")
                    val midi = score.notes.map { it.note!!.value }
                    assertEquals(midi.sorted(), midi, "strings go up")
                    assertTrue(midi.first() >= Arranger.STRUM_FLOOR.value, "not below the floor")
                    assertTrue(score.notes.zipWithNext().all { (a, b) -> b.startBeat > a.startBeat }, "a downstroke, not a cluster")
                }
            }
        }
    }

    @Test
    fun `the root of a plain chord is its lowest string, a slash bass goes below it`() {
        val v7 = ChordParser.parse("V7")
        val plain = arranger.strum(v7, cMajor).notes.map { it.note!! }
        assertEquals(cMajor.pitchClassOf(v7.root), plain.first().pitchClass)

        val one3 = ChordParser.parse("I/3")
        val slash = arranger.strum(one3, cMajor).notes.map { it.note!! }
        assertEquals(cMajor.pitchClassOf(Degree.parse("3")), slash.first().pitchClass, "the third is the bass")
        assertEquals(expected(ChordDegree(one3.root, one3.quality), cMajor) + slash.first().pitchClass, slash.map { it.pitchClass }.toSet())
        assertEquals(one3.quality.intervals.size + 1, slash.size, "the bass is an extra string")
    }

    @Test
    fun `the same chord is the same sound in every key, only moved`() {
        val chord = ChordParser.parse("ii7")
        val keys = KeyCatalog.practical(Mode.MAJOR)
        val shapes = keys.map { k ->
            val m = arranger.strum(chord, k).notes.map { it.note!!.value }
            m.map { it - m.first() }
        }
        assertEquals(1, shapes.distinct().size)
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
