package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-35: the numbers behind the live sphere and strings. */
class OvertonesTest {
    @Test
    fun `just ratios of the twelve degrees`() {
        assertEquals(12, Overtones.RATIO.size)
        assertEquals(1 to 1, Overtones.ratio(0))
        assertEquals(3 to 2, Overtones.ratio(7))
        assertEquals(5 to 4, Overtones.ratio(4))
        assertEquals(6 to 5, Overtones.ratio(3))
        assertEquals(16 to 15, Overtones.ratio(1))
        assertEquals(3 to 2, Overtones.ratio(19), "folded into one octave")
        // every ratio is a real interval inside one octave, in ascending order
        val values = Overtones.RATIO.map { (p, q) -> p.toDouble() / q }
        assertEquals(values.sorted(), values)
        assertTrue(values.first() == 1.0 && values.last() < 2.0)
    }

    @Test
    fun `tension is nil at home, full at the minor second and follows the ratio`() {
        assertEquals(0f, Overtones.tension(0))
        assertEquals(1f, Overtones.tension(1))
        assertTrue(Overtones.tension(7) < Overtones.tension(4), "a fifth rests more than a third")
        assertTrue(Overtones.tension(4) < Overtones.tension(1))
        val bySum = (0..11).sortedBy { Overtones.ratio(it).let { (p, q) -> p + q } }
        assertEquals(bySum.map { Overtones.tension(it) }.sorted(), bySum.map { Overtones.tension(it) }, "monotone in p + q")
        assertTrue((0..11).all { Overtones.tension(it) in 0f..1f })
    }

    @Test
    fun `a pluck in the middle mutes the even harmonics`() {
        assertEquals(0f, Overtones.harmonicWeight(2, 0.5f), 1e-6f)
        assertEquals(0f, Overtones.harmonicWeight(4, 0.5f), 1e-6f)
        assertEquals(1f, Overtones.harmonicWeight(1, 0.5f), 1e-6f)
        assertTrue(Overtones.harmonicWeight(2, 0.31f) != 0f)
    }

    @Test
    fun `envelope rises at once, falls softly, dies at two and a half seconds`() {
        for (k in 1..6) {
            val peak = (1..2500).maxByOrNull { Overtones.envelope(k, it / 1000f) }!!
            assertTrue(peak <= 60, "harmonic $k peaks at $peak ms")
            assertTrue((0..3000).all { Overtones.envelope(k, it / 1000f) >= 0f })
            assertTrue(Overtones.envelope(k, 2.5f) < 0.02f)
            assertEquals(0f, Overtones.envelope(k, 0f))
        }
        assertTrue(Overtones.envelope(1, 1f) > Overtones.envelope(6, 1f), "high harmonics die first")
        assertTrue(Overtones.envelope(1, 0.8f) > 0.4f, "the fundamental has a long soft tail")
    }

    private fun note(beat: Double, midi: Int, voice: Voice = Voice.PIANO) = ScoreNote(beat, 1.0, voice, MidiNote(midi))

    @Test
    fun `a chord is one stronger pluck and drums are skipped`() {
        val score = Score(
            120.0,
            listOf(
                note(0.0, 60), note(0.0, 64), note(0.0, 67),
                ScoreNote(0.0, 0.5, Voice.KICK),
                note(1.0, 62),
            ),
        )
        val p = Overtones.pulses(score, PulseKind.REVEAL, MidiNote(60))
        assertEquals(2, p.size)
        assertEquals(0L, p[0].atMs)
        assertEquals(500L, p[1].atMs)
        assertTrue(p[0].strength > p[1].strength)
        assertTrue(p.all { it.strength in 0f..1f })
        assertEquals(0, p[0].pitch)
        assertEquals(2, p[1].pitch)
    }

    @Test
    fun `a question carries no pitch at all`() {
        val score = Score(90.0, listOf(note(0.0, 67), note(1.0, 61)))
        val q = Overtones.pulses(score, PulseKind.QUESTION, MidiNote(60))
        assertTrue(q.all { it.pitch == null })
        assertTrue(q.all { it.x == Overtones.QUESTION_X })
        assertThrows(IllegalArgumentException::class.java) { SoundPulse(PulseKind.QUESTION, 0, 1f, pitch = 7) }
        assertNull(Overtones.pulses(score, PulseKind.REVEAL, null).first().pitch)
    }

    @Test
    fun `a pitch is folded to the tonic whatever the octave`() {
        val score = Score(90.0, listOf(note(0.0, 43), note(1.0, 91)))
        val p = Overtones.pulses(score, PulseKind.HOME, MidiNote(48))
        assertEquals(listOf(7, 7), p.map { it.pitch })
    }

    /** The size of mode [i] near [t]: the largest swing in a 100 ms window (the mode itself oscillates). */
    private fun swing(i: Int, t: Float): Float = (0..100).maxOf { kotlin.math.abs(Overtones.dropMode(i, t + it / 1000f)) }

    @Test
    fun `the drop takes the whole pluck, so its weights add up to one`() {
        assertEquals(4, Overtones.DROP_WEIGHTS.size)
        assertEquals(1f, Overtones.DROP_WEIGHTS.sum(), 1e-6f)
        assertTrue(Overtones.DROP_WEIGHTS.zipWithNext().all { (a, b) -> a > b }, "the slowest mode gets the most")
    }

    @Test
    fun `modes of the drop are silent outside a pluck's life and quiet at once`() {
        for (i in 0..3) {
            assertEquals(0f, Overtones.dropMode(i, 0f))
            assertEquals(0f, Overtones.dropMode(i, -1f))
            assertEquals(0f, Overtones.dropMode(i, Overtones.LIFE))
            assertEquals(0f, Overtones.dropMode(i, 9f))
            assertTrue(Overtones.dropMode(i, 0.001f) < 0.05f, "no jump at the pluck")
            assertTrue(swing(i, 2.4f) < 0.01f, "mode $i is almost gone when the pluck is dropped")
        }
    }

    @Test
    fun `slow modes live longer than fast ones and frequencies follow a drop`() {
        // compare how much of each mode is left after a second, against what it started with
        val left = (0..3).map { swing(it, 0.9f) / Overtones.DROP_WEIGHTS[it] }
        assertTrue(left.zipWithNext().all { (a, b) -> a > b }, "the higher the mode the faster it dies: $left")
        val f = (0..3).map { Overtones.dropFrequency(it) }
        assertEquals(Overtones.DROP_F2, f[0], 1e-6f)
        assertTrue(f.zipWithNext().all { (a, b) -> b > a })
        // l = 3: sqrt(3·2·5/8) = 1.936 times the slowest
        assertEquals(Overtones.DROP_F2 * 1.9365f, f[1], 1e-3f)
    }

    @Test
    fun `a kick rises softly, peaks early, stays in 0 to 1 and ends with the pluck`() {
        val ks = (0..2500).map { Overtones.kick(it / 1000f) }
        assertTrue(ks.all { it in 0f..1f })
        val peak = ks.indices.maxByOrNull { ks[it] }!!
        assertTrue(peak in 20..300, "peak at $peak ms")
        assertTrue(Overtones.kick(0.005f) < 0.2f, "a soft rise, not a jump")
        assertEquals(0f, Overtones.kick(0f))
        assertEquals(0f, Overtones.kick(Overtones.LIFE))
        assertTrue(Overtones.kick(1f) < 0.3f * ks[peak])
    }

    @Test
    fun `the tail window is shared by the envelope and the drop`() {
        assertEquals(1f, Overtones.tail(0f))
        assertEquals(1f, Overtones.tail(1.2f))
        assertEquals(0f, Overtones.tail(Overtones.LIFE), 1e-6f)
        assertEquals(0f, Overtones.tail(9f))
        assertEquals(Overtones.envelope(1, 2f), (1f - kotlin.math.exp(-2f / 0.012f)) * kotlin.math.exp(-2f / 1.4f) * Overtones.tail(2f), 1e-6f)
    }
}
