package com.kazaeza.muzykalka.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SingUnlockTest {
    private fun tries(v: Variant, right: Int, wrong: Int) =
        (List(right) { true } + List(wrong) { false }).mapIndexed { i, ok ->
            AttemptRecord(v, 1, "pevka:L1:5", "5", if (ok) "5" else "3", ok, 1000, null, 0, null, i * 1000L)
        }

    @Test
    fun `closed until half of at least twenty notes were found on the first try`() {
        assertFalse(SingUnlock.status(emptyList()).open)
        assertFalse(SingUnlock.status(tries(Variant.KAMERTON_SEARCH, 10, 5)).open) // too few notes
        assertFalse(SingUnlock.status(tries(Variant.KAMERTON_SEARCH, 9, 11)).open)
        assertTrue(SingUnlock.status(tries(Variant.KAMERTON_SEARCH, 10, 10)).open)
        assertEquals(30, SingUnlock.status(tries(Variant.KAMERTON_SEARCH, 5, 40)).tries) // only the last 30 count
    }

    @Test
    fun `only the search counts, and the daily set swaps the closed block`() {
        assertFalse(SingUnlock.status(tries(Variant.KAMERTON_SING, 30, 0)).open)
        val closed = SingUnlock.Status(false, 0, 0)
        val plan = SingUnlock.plan(DailySet.DEFAULT, closed)
        assertTrue(plan.none { it.variant == Variant.KAMERTON_SING })
        assertEquals(3.0, plan.first { it.variant == Variant.KAMERTON_SEARCH }.minutes)
        assertEquals(DailySet.DEFAULT, SingUnlock.plan(DailySet.DEFAULT, SingUnlock.Status(true, 20, 12)))
    }
}
