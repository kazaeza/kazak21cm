package com.kazaeza.muzykalka.game

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** O-55 (ADR-054): the sets to choose from. */
class TrainingTest {
    @Test
    fun `the daily set and minimal day are non-guitar by default`() {
        assertEquals(DailySet.DEFAULT, Training.DAILY.plan)
        assertEquals(GuitarNeed.NONE, Training.DAILY.guitar)
        assertEquals(20.0, Training.DAILY.plan.sumOf { it.minutes })
        assertTrue(Training.DAILY.plan.none { it.variant.guitar == GuitarNeed.REQUIRED })
        assertTrue(Training.DAILY.plan.any { it.variant == Variant.KAMERTON_SING })

        assertEquals(5.0, DailySet.MINIMAL.sumOf { it.minutes })
        assertTrue(DailySet.MINIMAL.none { it.variant.guitar == GuitarNeed.REQUIRED })
        assertTrue(DailySet.MINIMAL.all { it.minutes >= 1.0 })
        assertEquals(DailySet.MINIMAL.size, DailySet.MINIMAL.map { it.variant }.toSet().size)
    }

    @Test
    fun `one set is for the guitar and the others need none`() {
        assertEquals(GuitarNeed.REQUIRED, Training.GUITAR.guitar)
        val without = Training.OTHERS.filter { it.guitar == GuitarNeed.NONE }
        assertTrue(without.size >= 3, "several sets without the guitar")
        for (t in without) {
            assertTrue(t.plan.none { it.variant.guitar == GuitarNeed.REQUIRED }, "${t.name} asks for no guitar in any part")
        }
        assertTrue(Training.GUITAR.plan.count { it.variant.guitar == GuitarNeed.REQUIRED } >= Training.GUITAR.plan.size - 1, "the guitar set is the guitar")
    }

    @Test
    fun `the road is silent and short`() {
        assertTrue(Training.ROAD.silent)
        assertTrue(Training.ROAD.plan.sumOf { it.minutes } <= 8.0)
        assertTrue(!Training.DAILY.silent)
    }

    @Test
    fun `every part has shipped, every set fits a sitting, and no set repeats a part`() {
        for (t in Training.entries) {
            assertEquals(t.plan, DailySet.available(t.plan, "0.1"), "${t.name}: everything in it is in 0.1")
            val minutes = t.plan.sumOf { it.minutes }
            assertTrue(minutes in 5.0..20.0, "${t.name}: $minutes min")
            assertEquals(t.plan.size, t.plan.map { it.variant }.toSet().size, "${t.name}: no part twice")
            // a part is at least one round of a minute (RoundConfig: 60–90 s)
            assertTrue(t.plan.all { it.minutes >= 1.0 }, t.name)
        }
    }

    @Test
    fun `a route's name finds its set, anything else is the daily one`() {
        for (t in Training.entries) assertEquals(t, Training.of(t.name))
        assertEquals(Training.DAILY, Training.of(null))
        assertEquals(Training.DAILY, Training.of("NO_SUCH_SET"))
    }

    @Test
    fun `the others put non-guitar sets first and helpers split cleanly`() {
        assertEquals(GuitarNeed.NONE, Training.OTHERS.first().guitar)
        assertEquals(Training.GUITAR, Training.OTHERS.last())
        assertTrue(Training.DAILY !in Training.OTHERS)
        assertEquals(Training.entries.size - 1, Training.OTHERS.size)
        assertTrue(Training.NON_GUITAR.all { it.guitar == GuitarNeed.NONE })
        assertTrue(Training.DAILY in Training.NON_GUITAR)
        assertEquals(listOf(Training.GUITAR), Training.WITH_GUITAR)
    }
}
