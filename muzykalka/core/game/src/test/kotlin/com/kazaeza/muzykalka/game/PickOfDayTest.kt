package com.kazaeza.muzykalka.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** O-44 «Песня дня»: the same all day, another tomorrow, everyone gets a turn. */
class PickOfDayTest {
    @Test
    fun `the pick holds for the day and does not depend on the order of the list`() {
        val ids = (1..37).map { "song$it" }
        assertEquals(PickOfDay.pick(ids, 20_000), PickOfDay.pick(ids.shuffled(kotlin.random.Random(3)), 20_000))
        assertNotEquals(PickOfDay.pick(ids, 20_000), PickOfDay.pick(ids, 20_001), "another song tomorrow")
    }

    @Test
    fun `every song gets its turn before one comes round again`() {
        for (n in listOf(1, 2, 3, 4, 7, 10, 130)) {
            val ids = (1..n).map { "s$it" }
            val round = (5_000 until 5_000 + n).map { PickOfDay.pick(ids, it) }
            assertEquals(ids.toSet(), round.toSet(), "$n songs: a round of $n days shows them all")
        }
        val ids = (1..10).map { "s$it" }
        val days = (100 until 110).map { PickOfDay.pick(ids, it)!! }
        assertNotEquals(ids, days, "not simply down the alphabet")
    }

    @Test
    fun `nothing to pick from is no pick, and negative days are days too`() {
        assertNull(PickOfDay.pick(emptyList(), 1))
        assertEquals("a", PickOfDay.pick(listOf("a"), -5))
    }
}
