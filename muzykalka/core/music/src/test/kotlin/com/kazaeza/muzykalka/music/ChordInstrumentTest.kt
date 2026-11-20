package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-82 (ADR-078): chords are struck on the piano unless the guitar is chosen. */
class ChordInstrumentTest {
    private val c = Key(NoteName.parse("C"), Mode.MAJOR)
    private val chords = listOf("I", "ii", "IV", "V", "V7", "vi", "I/3", "bVII").map(ChordParser::parse)

    @Test
    fun `the piano is the default, the guitar the other choice`() {
        assertEquals(ChordInstrument.PIANO, Arranger().chordInstrument)
        assertEquals(listOf(ChordInstrument.PIANO, ChordInstrument.GUITAR), ChordInstrument.entries)
    }

    @Test
    fun `a hit on the guitar is the strum, on the piano a piano chord`() {
        val guitar = Arranger(chordInstrument = ChordInstrument.GUITAR)
        val piano = Arranger(chordInstrument = ChordInstrument.PIANO)
        for (chord in chords) {
            assertEquals(guitar.strum(chord, c), guitar.hit(chord, c), "the guitar's hit is its strum")
            val hit = piano.hit(chord, c)
            assertEquals(piano.pianoChord(chord, c), hit)
            assertTrue(hit.notes.all { it.voice == Voice.PIANO }, "one voice: the piano")
        }
    }

    @Test
    fun `a piano chord is its bass low and its tones above, rolled upward and left to ring`() {
        val arranger = Arranger()
        for (chord in chords) {
            val hit = arranger.pianoChord(chord, c)
            val keys = hit.notes
            assertEquals(arranger.bassNote(chord, c), keys.first().note, "the bass is struck first")
            assertTrue(keys.first().note!! < keys[1].note!!, "and it is the lowest key")
            assertEquals(chord.quality.intervals.map { (c.pitchClassOf(chord.root) + it).value }.toSet(), keys.drop(1).map { it.note!!.pitchClass.value }.toSet())
            assertTrue(keys.zipWithNext().all { (a, b) -> b.startBeat > a.startBeat }, "a hand rolls it from the bottom up")
            assertTrue(keys.last().startBeat * 60.0 / hit.tempoBpm < 0.1, "within a tenth of a second")
            assertTrue(keys.all { it.durationBeats * 60.0 / hit.tempoBpm > 1.5 }, "and it rings")
        }
        // the same each time it is tapped
        assertEquals(arranger.pianoChord(chords[3], c), arranger.pianoChord(chords[3], c))
    }

    @Test
    fun `the band's chord is the same two strikes on either instrument, the bass and the drums do not change`() {
        val loop = listOf("I", "V", "vi", "IV").map(ChordParser::parse)
        val guitar = Arranger(chordInstrument = ChordInstrument.GUITAR).groove(loop, c, beatsEach = 2.0)
        val piano = Arranger(chordInstrument = ChordInstrument.PIANO).groove(loop, c, beatsEach = 2.0)
        assertTrue(piano.notes.none { it.voice == Voice.GUITAR } && guitar.notes.none { it.voice == Voice.PIANO })
        for (voice in listOf(Voice.BASS, Voice.KICK, Voice.SNARE, Voice.HAT)) {
            assertEquals(guitar.notes.filter { it.voice == voice }, piano.notes.filter { it.voice == voice }, "$voice is the band's, whoever strikes the chord")
        }
        assertEquals(guitar.lengthBeats, piano.lengthBeats, 0.3, "the same length")
        // the first beat of each chord is struck, and the «and» of the second beat again, softer
        for (i in loop.indices) {
            val strikes = piano.notes.filter { it.voice == Voice.PIANO && it.startBeat >= i * 2.0 && it.startBeat < (i + 1) * 2.0 }
            assertTrue(strikes.any { it.startBeat == i * 2.0 } && strikes.any { it.startBeat > i * 2.0 + 1.0 })
            assertTrue(strikes.filter { it.startBeat > i * 2.0 + 1.0 }.all { it.velocity < strikes.first { s -> s.startBeat == i * 2.0 }.velocity })
        }
    }
}
