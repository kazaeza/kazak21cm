package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Overtones
import com.kazaeza.muzykalka.music.PulseKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * O-35 / ADR-015: the picture of a question must not say which note it is. A pluck of a question carries no pitch,
 * and two questions about different degrees draw the same plucks.
 */
class PulseRuleTest {
    private fun tasks(level: Int, seed: Int, count: Int = 60): List<TirTask> {
        val driver = TirDriver(KeyPicker(Random(seed)))
        return (0 until count).map { driver.nextTask(DriverContext(Random(seed * 100 + it), level, emptyList())) }
    }

    @Test
    fun `the plucks of a question carry no pitch and sit in one place`() {
        for (level in 1..7) {
            for (task in tasks(level, level)) {
                val plucks = Overtones.pulses(requireNotNull(task.prompt.audio), PulseKind.QUESTION, task.tonic)
                assertTrue(plucks.isNotEmpty())
                plucks.forEach {
                    assertNull(it.pitch, "level $level: a question pluck has a pitch")
                    assertEquals(Overtones.QUESTION_X, it.x)
                }
            }
        }
    }

    @Test
    fun `two questions about different degrees draw the same plucks`() {
        val byDegree = HashMap<Int, List<com.kazaeza.muzykalka.music.SoundPulse>>()
        for (seed in 1..6) {
            for (task in tasks(2, seed)) {
                if (task.kind != TirKind.NOTE) continue
                // the question itself; the first one of a key block is preceded by the key's scale, which is the same for any degree
                val plucks = Overtones.pulses(task.question, PulseKind.QUESTION, task.tonic)
                byDegree.putIfAbsent(task.notes.single().degree.semitones, plucks)
            }
        }
        assertTrue(byDegree.size >= 4, "need several degrees to compare, got ${byDegree.size}")
        val distinct = byDegree.values.map { p -> p.map { Triple(it.kind, it.x, it.pitch) } }.toSet()
        assertEquals(1, distinct.size, "the look of a question depends on the degree: $distinct")
    }

    @Test
    fun `after the answer the plucks do carry the degree`() {
        val task = tasks(2, 3).first { it.kind == TirKind.NOTE }
        val plucks = Overtones.pulses(task.question, PulseKind.REVEAL, task.tonic)
        assertEquals(task.notes.single().degree.semitones, plucks.single().pitch)
    }
}
