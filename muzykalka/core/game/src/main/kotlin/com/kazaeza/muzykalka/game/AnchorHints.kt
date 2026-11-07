package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Degree

/**
 * Ticket O-09: which degrees a round got wrong, so the summary can offer the user's own anchor
 * song for each («5 — как начало …»). Only drills whose answer is one degree of the key count:
 * a chord, a melody or a fret is not something a one-degree anchor helps with.
 */
object AnchorHints {
    private val DEGREE_ANSWERS = setOf(Variant.TIR_CLASSIC, Variant.TIR_OVER_CHORDS, Variant.KAMERTON_SING)

    /** Missed degrees, most missed first (ties: in the order they were first missed), one per pitch. */
    fun missed(attempts: List<AttemptRecord>): List<Degree> {
        val misses = attempts
            .filter { !it.correct && it.variant in DEGREE_ANSWERS && !it.expected.contains(' ') }
            .mapNotNull { a -> runCatching { Degree.parse(a.expected) }.getOrNull() }
        val counts = misses.groupingBy { it.semitones }.eachCount()
        return misses.distinctBy { it.semitones }.sortedByDescending { counts.getValue(it.semitones) }
    }
}
