package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** X-03 «Под бэкинг» (ADR-052): a song laid out in time, bar by bar. */
class BackingTest {
    private val song = Song(
        id = "s", title = "s", mode = Mode.MAJOR,
        sections = ProgressionText.parseSections("A: | I | vi | IV V |\nB: | IV | I |"), form = listOf("A", "A", "B"),
    )
    private val key = KeyCatalog.practical(PitchClass.of(0), Mode.MAJOR)

    @Test
    fun `every bar is four beats, the chords of a bar share it, and the form is played through`() {
        val steps = Backing.steps(song)
        assertEquals(4 + 4 + 2, steps.size, "A twice (four chords each), B once")
        assertEquals(listOf(0.0, 4.0, 8.0, 10.0, 12.0, 16.0, 20.0, 22.0, 24.0, 28.0), steps.map { it.startBeat })
        assertEquals(listOf(4.0, 4.0, 2.0, 2.0), steps.take(4).map { it.beats })
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 1, 1, 2, 2), steps.map { it.part })
        assertEquals(listOf(0, 1, 2, 2), steps.take(4).map { it.bar })
        assertEquals("B", steps.last().label)
        assertEquals(32.0, Backing.lengthBeats(steps), "eight bars")
        assertEquals(0, Backing.stepAt(steps, 0.0))
        assertEquals(2, Backing.stepAt(steps, 9.9))
        assertEquals(3, Backing.stepAt(steps, 10.0))
        assertEquals(9, Backing.stepAt(steps, 500.0))
        assertEquals(0, Backing.stepAt(steps, -1.0))
    }

    @Test
    fun `the backing holds the chord and walks the bass on every beat`() {
        val steps = Backing.steps(song)
        val score = Backing.score(steps, key, 90.0)
        assertEquals(90.0, score.tempoBpm)
        assertEquals(32.0, score.lengthBeats, 0.2)
        val bass = score.notes.filter { it.voice == Voice.BASS }
        assertEquals(32, bass.size, "a bass note on every beat")
        assertEquals((0 until 32).map { it.toDouble() }, bass.map { it.startBeat })
        val piano = score.notes.filter { it.voice == Voice.PIANO }
        assertEquals(steps.size * 3, piano.size, "triads, one per chord")
        steps.forEach { s ->
            val heard = piano.filter { it.startBeat == s.startBeat }.map { it.note!!.pitchClass }.toSet()
            assertEquals(s.chord.quality.intervals.map { key.pitchClassOf(s.chord.root) + it }.toSet(), heard)
            assertEquals(key.pitchClassOf(s.chord.root), bass.first { it.startBeat == s.startBeat }.note!!.pitchClass)
        }
        assertTrue(score.notes.all { it.voice == Voice.PIANO || it.voice == Voice.BASS })
    }
}
