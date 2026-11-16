package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-48 «Созвучия» (ADR-047): the body of an interval and of a chord, apart from any key. */
class HarmonyLabTest {
    @Test
    fun `every interval of the octave has its ratio, its word and its roughness`() {
        val all = (0..12).map(Dyads::of)
        assertEquals(13, all.map { it.word }.toSet().size, "thirteen different words")
        assertEquals(3 to 2, Dyads.of(7).ratio)
        assertEquals(5 to 4, Dyads.of(4).ratio)
        assertEquals(2 to 1, Dyads.of(12).ratio)
        assertEquals(0f, Dyads.of(0).rough)
        assertEquals(0f, Dyads.of(12).rough)
        assertEquals(1f, Dyads.of(1).rough, "the minor second is the tightest rub")
        assertTrue(Dyads.of(7).rough < Dyads.of(4).rough && Dyads.of(4).rough < Dyads.of(2).rough, "fifth, third, second: rougher and rougher")
        assertTrue(Dyads.of(6).rough > Dyads.of(5).rough && Dyads.of(6).rough > Dyads.of(7).rough, "the tritone between the fourth and the fifth is the restless one")
        assertThrows(IllegalArgumentException::class.java) { Dyads.of(13) }
        all.forEach { v ->
            assertTrue(v.line.isNotBlank() && v.meeting.isNotBlank())
            assertFalse(Regex("[A-H]").containsMatchIn(v.word + v.line + v.meeting), "no note letters (rule 1): $v")
        }
    }

    @Test
    fun `the meeting of the harmonics is told by the ratio`() {
        assertTrue(Dyads.of(7).meeting.contains("3-я гармоника нижнего звука — это 2-я верхнего"), Dyads.of(7).meeting)
        assertTrue(Dyads.of(7).meeting.startsWith("Встречаются рано"))
        assertTrue(Dyads.of(1).meeting.startsWith("Встречаются поздно") && Dyads.of(1).meeting.contains("16-я"), Dyads.of(1).meeting)
        assertTrue(Dyads.of(0).meeting.contains("один звук"))
        assertTrue(Dyads.of(12).meeting.contains("2-я нижнего"), Dyads.of(12).meeting)
    }

    @Test
    fun `the chord built by hand has its tones and, where there is one, its name`() {
        fun q(third: LabThird, fifth: LabFifth = LabFifth.PERFECT, top: LabTop = LabTop.NONE) = LabChord(third, fifth, top).quality
        assertEquals(ChordQuality.MAJOR, LabChord().quality, "the lab opens on a major triad")
        assertEquals(ChordQuality.MINOR, q(LabThird.MINOR))
        assertEquals(ChordQuality.POWER, q(LabThird.NONE))
        assertEquals(ChordQuality.SUS2, q(LabThird.SUS2))
        assertEquals(ChordQuality.SUS4, q(LabThird.SUS4))
        assertEquals(ChordQuality.DIMINISHED, q(LabThird.MINOR, LabFifth.DIMINISHED))
        assertEquals(ChordQuality.AUGMENTED, q(LabThird.MAJOR, LabFifth.AUGMENTED))
        assertEquals(ChordQuality.DOMINANT7, q(LabThird.MAJOR, top = LabTop.MINOR7))
        assertEquals(ChordQuality.MAJOR7, q(LabThird.MAJOR, top = LabTop.MAJOR7))
        assertEquals(ChordQuality.MINOR7, q(LabThird.MINOR, top = LabTop.MINOR7))
        assertEquals(ChordQuality.HALF_DIMINISHED7, q(LabThird.MINOR, LabFifth.DIMINISHED, LabTop.MINOR7))
        assertEquals(ChordQuality.DIMINISHED7, q(LabThird.MINOR, LabFifth.DIMINISHED, LabTop.SIXTH))
        assertEquals(ChordQuality.MAJOR6, q(LabThird.MAJOR, top = LabTop.SIXTH))
        assertEquals(ChordQuality.DOMINANT7_SUS4, q(LabThird.SUS4, top = LabTop.MINOR7))
        assertNull(q(LabThird.SUS2, LabFifth.AUGMENTED), "not every stack has a name")
        for (t in LabThird.entries) for (f in LabFifth.entries) for (top in LabTop.entries) {
            val c = LabChord(t, f, top)
            assertTrue(c.tones.size in 2..4, "the drone bank holds four")
            assertEquals(c.intervals.sorted(), c.intervals, "low to high")
            assertEquals(Degree.ONE, c.tones.first())
        }
        assertEquals(listOf("1", "♭3", "♭5", "♭7"), LabChord(LabThird.MINOR, LabFifth.DIMINISHED, LabTop.MINOR7).tones.map { it.toString() })
    }

    @Test
    fun `the tritone is found wherever it hides`() {
        assertNull(LabChord().tritone)
        assertEquals(listOf("3", "♭7"), LabChord(top = LabTop.MINOR7).tritone!!.toList().map { it.toString() })
        assertEquals(listOf("1", "♭5"), LabChord(LabThird.MINOR, LabFifth.DIMINISHED).tritone!!.toList().map { it.toString() })
        assertNull(LabChord(LabThird.MINOR, top = LabTop.MINOR7).tritone, "a minor seventh chord has none: that is why it is soft")
    }

    @Test
    fun `a chord pulls by its body or stands`() {
        assertEquals(LabChord().intervals, LabChord(LabThird.SUS4).pull.target, "the fourth falls into the third")
        assertEquals(LabChord().intervals, LabChord(LabThird.SUS2).pull.target, "the second rises to it")
        val dominant = LabChord(top = LabTop.MINOR7).pull
        assertEquals(listOf(5, 9, 12), dominant.target, "the chord a fourth above")
        assertTrue(dominant.line.contains("тритон"))
        // the voice leading the line promises: 3 goes up a half step, ♭7 down a half step
        val to = requireNotNull(dominant.target)
        assertTrue(4 + 1 in to && 10 - 1 in to)
        assertEquals(listOf(1, 5, 8), LabChord(LabThird.MINOR, LabFifth.DIMINISHED).pull.target, "up a half step, into a major triad")
        assertEquals(listOf(0, 4, 9), LabChord(fifth = LabFifth.AUGMENTED).pull.target)
        for (stable in listOf(LabChord(), LabChord(LabThird.MINOR), LabChord(LabThird.NONE), LabChord(top = LabTop.MAJOR7), LabChord(LabThird.MINOR, top = LabTop.MINOR7))) {
            assertNull(stable.pull.target, "$stable stands")
            assertTrue(stable.pull.line.isNotBlank())
        }
        for (t in LabThird.entries) for (f in LabFifth.entries) for (top in LabTop.entries) {
            val pull = LabChord(t, f, top).pull
            assertNotNull(pull.line)
            assertFalse(Regex("[A-H]").containsMatchIn(pull.line.replace("V ступени", "")), "no note letters: ${pull.line}")
            pull.target?.let { target -> assertTrue(target.size in 2..4 && target == target.sorted()) }
        }
    }

    @Test
    fun `the sounds fit the drone bank, the bass below the chord`() {
        val root = MidiNote(48)
        assertEquals(listOf(60, 67), LabSound.dyad(MidiNote(60), 7).map { it.value })
        assertEquals(listOf(60), LabSound.dyad(MidiNote(60), 0).map { it.value }, "a unison is one sound")
        assertEquals(listOf(48, 60, 64, 67), LabSound.chord(root, LabChord().intervals).map { it.value })
        assertEquals(listOf(48, 60, 67), LabSound.chord(root, LabChord(LabThird.NONE).intervals).map { it.value })
        assertEquals(listOf(48, 64, 67, 70), LabSound.chord(root, LabChord(top = LabTop.MINOR7).intervals).map { it.value }, "four tones: the bass is not doubled")
        assertEquals(listOf(53, 65, 69, 72), LabSound.chord(root, listOf(5, 9, 12)).map { it.value }, "the chord a fourth above stands on its own bass")
        for (t in LabThird.entries) for (f in LabFifth.entries) for (top in LabTop.entries) {
            val c = LabChord(t, f, top)
            assertTrue(LabSound.chord(root, c.intervals).size <= 4)
            c.pull.target?.let { assertTrue(LabSound.chord(root, it).size <= 4) }
        }
    }

    /** O-49: the same body on different degrees, each held over the home tone. */
    @Test
    fun `a chord of the key is held over the tonic, all seven in one register`() {
        val tonic = MidiNote(48)
        assertEquals(listOf(48, 60, 64, 67), LabSound.inKey(tonic, Harmony.diatonicChord(Mode.MAJOR, 0)).map { it.value }, "I: home on home")
        assertEquals(listOf(48, 67, 71, 74), LabSound.inKey(tonic, Harmony.diatonicChord(Mode.MAJOR, 4)).map { it.value }, "V over the tonic")
        assertEquals(listOf(48, 57, 60, 64), LabSound.inKey(tonic, Harmony.diatonicChord(Mode.MAJOR, 5)).map { it.value }, "vi comes down an octave")
        for (mode in listOf(Mode.MAJOR, Mode.MINOR)) for (step in 0..6) {
            val notes = LabSound.inKey(tonic, Harmony.diatonicChord(mode, step))
            assertTrue(notes.size in 3..4 && notes.first() == tonic, "$mode $step: $notes")
            assertTrue(notes.drop(1).all { it.value in 56..75 }, "one register: $notes")
        }
        assertEquals(listOf(3, 4), LabSound.sameBody(Mode.MAJOR, 0), "the major triad stands on I, IV and V")
        assertEquals(listOf(1, 2), LabSound.sameBody(Mode.MAJOR, 5), "the minor one on ii, iii and vi")
        assertTrue(LabSound.sameBody(Mode.MAJOR, 6).isEmpty(), "the diminished one stands alone")
    }

    /** O-52: the picture of an interval — which harmonics meet and which rub. */
    @Test
    fun `the harmonics that meet and the ones that rub explain how an interval sounds`() {
        assertEquals(listOf(3 to 2, 6 to 4, 9 to 6, 12 to 8, 15 to 10), Dyads.of(7).meetings(), "a fifth: every third harmonic of the lower sound")
        assertTrue(Dyads.of(7).rubs().isEmpty(), "and nothing rubs: that is why it is smooth")
        assertEquals(16, Dyads.of(0).meetings().size, "one sound: everything meets")
        assertEquals((1..8).map { 2 * it to it }, Dyads.of(12).meetings(), "the octave: every harmonic of the upper sound is in the lower")
        val third = Dyads.of(4)
        assertEquals(listOf(5 to 4, 10 to 8, 15 to 12), third.meetings())
        assertTrue((4 to 3) in third.rubs(), "a major third: the 4th of the lower rubs against the 3rd of the upper")
        val second = Dyads.of(1)
        assertEquals(listOf(16 to 15), second.meetings(), "a minor second meets once, at the very edge")
        assertEquals((1..6).map { it to it }, second.rubs(), "and every loud harmonic rubs against its twin")
        // rougher intervals rub more: the order the ear agrees with
        val rubs = { st: Int -> Dyads.of(st).rubs().size }
        assertTrue(rubs(7) < rubs(4) && rubs(4) < rubs(1), "fifth ${rubs(7)}, third ${rubs(4)}, second ${rubs(1)}")
        assertTrue(rubs(5) <= rubs(6), "the fourth is calmer than the tritone")
        for (st in 0..12) Dyads.of(st).let { v ->
            assertTrue(v.meetings().all { (n, k) -> n * v.ratio.second == k * v.ratio.first }, "a meeting is the same pitch")
            assertTrue(v.rubs().none { it in v.meetings() })
        }
    }

    @Test
    fun `a chord is a stack of steps, and its restlessness is that of its pairs`() {
        assertEquals(listOf(4, 3), LabChord().stack, "major: the big third below")
        assertEquals(listOf(3, 4), LabChord(LabThird.MINOR).stack, "minor: the same two thirds the other way round")
        assertEquals(listOf(4, 3, 3), LabChord(top = LabTop.MINOR7).stack)
        assertEquals(listOf(7), LabChord(LabThird.NONE).stack)
        assertEquals(listOf("б3", "м3"), LabChord().stack.map(RuNames::intervalShort))
        assertEquals("тритон", RuNames.intervalShort(6))
        val calm = LabChord(LabThird.NONE).rough
        val major = LabChord().rough
        val dominant = LabChord(top = LabTop.MINOR7).rough
        val diminished = LabChord(LabThird.MINOR, LabFifth.DIMINISHED).rough
        assertTrue(calm < major && major < dominant, "fifth $calm, major $major, dominant $dominant")
        assertTrue(diminished > major, "a diminished triad is more restless than a major one")
    }
}
