package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-08: a degree over chords of the key, not only over the tonic drone. */
class OverChordTest {
    private val c = Key(NoteName.parse("C"), Mode.MAJOR)
    private val iv = Harmony.diatonicChord(Mode.MAJOR, 3)

    @Test
    fun `the chord drone is the root low and the triad above, four notes at most`() {
        val drone = Arranger().chordDrone(iv, c)
        assertEquals(4, drone.size)
        assertTrue(drone[0] >= Arranger.DRONE_FLOOR && drone[0].value < Arranger.DRONE_FLOOR.value + 12)
        assertEquals(listOf(5, 5, 9, 0), drone.map { it.pitchClass.value }, "F · F A C")
        val i = Arranger().chordDrone(Harmony.diatonicChord(Mode.MAJOR, 0), c)
        assertEquals(listOf(0, 0, 4, 7), i.map { it.pitchClass.value })
    }

    @Test
    fun `chord tones rest, other degrees lean to the nearest chord tone`() {
        val n = DegreeNotation.ARABIC
        assertEquals("над IV — тянет вниз к 4", DegreeVibes.overChord(Degree.FIVE, iv, Mode.MAJOR, n))
        assertEquals("над IV — опора: терция аккорда", DegreeVibes.overChord(Degree.SIX, iv, Mode.MAJOR, n))
        assertEquals("над I — опора: квинта аккорда", DegreeVibes.overChord(Degree.FIVE, Harmony.diatonicChord(Mode.MAJOR, 0), Mode.MAJOR, n))
        // 7 over I: a semitone under home — leans up.
        assertEquals("над I — тянет вверх к 1", DegreeVibes.overChord(Degree.SEVEN, Harmony.diatonicChord(Mode.MAJOR, 0), Mode.MAJOR, n))
        // 4 over V (5 7 2): a whole tone under 5, a minor third above 2 — leans up.
        assertEquals("над V — тянет вверх к 5", DegreeVibes.overChord(Degree.FOUR, Harmony.diatonicChord(Mode.MAJOR, 4), Mode.MAJOR, n))
    }
}
