package com.kazaeza.muzykalka.game

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AdaptiveEarTest {
    private fun note(expected: String, given: String, day: Int, index: Int) = AttemptRecord(
        Variant.TIR_CLASSIC, 2, "tir:L2:$expected", expected, given, expected == given,
        900, null, 0, "C", day * 1000L + index)
    private fun day(ms: Long) = (ms / 1000).toInt()
    private fun set(day: Int) = List(8) { i ->
        val target = if (i % 2 == 0) "4" else "5"
        note(target, if (i < 2) (if (target == "4") "5" else "4") else target, day, i)
    }

    @Test fun repeatedPairAndTimeBudget() {
        val oneDay = set(1)
        assertNull(AdaptiveEar.choose(oneDay, 1, ::day))
        val rows = oneDay + set(2)
        val focus = AdaptiveEar.choose(rows, 2, ::day)!!
        assertEquals(Variant.TIR_DUEL, focus.variant)
        assertEquals(listOf("4", "5"), focus.pair)
        val plan = AdaptiveEar.apply(DailySet.DEFAULT, focus)
        assertEquals(20.0, plan.sumOf { it.minutes })
        assertEquals(1.5, plan.single { it.variant == Variant.TIR_DUEL }.minutes)
        assertNull(AdaptiveEar.choose(rows, 20, ::day))
    }

    @Test fun waitForIndependentAnswersBeforeChangingContext() {
        val duel = note("4", "4", 2, 20).copy(variant = Variant.TIR_DUEL, itemKey = "tir:duel:4~5:4")
        val rows = set(1) + set(2) + duel
        assertNull(AdaptiveEar.choose(rows, 2, ::day))
        assertEquals(Variant.TIR_CLASSIC, AdaptiveEar.choose(rows, 3, ::day)?.variant)
        val probe = List(8) { i -> (if (i % 2 == 0) "4" else "5").let { note(it, it, 3, i) } }
        assertEquals(Variant.TIR_OVER_CHORDS, AdaptiveEar.choose(rows + probe, 4, ::day)?.variant)
        assertNull(AdaptiveEar.choose(rows + probe + duel.copy(variant = Variant.TIR_OVER_CHORDS, atEpochMs = 4100), 5, ::day))
    }

    @Test fun contextRecommendationNeedsComparableMaterialAcrossDays() {
        val base = (1..2).flatMap { day -> List(9) { i ->
            val d = listOf("1", "3", "5")[i % 3]
            note(d, d, day, i).copy(keyLabel = "C MAJOR")
        } }
        val hard = base.map { it.copy(variant = Variant.TIR_OVER_CHORDS, level = 1,
            correct = false, given = "?", itemKey = "tir:chords:L1:I:" + it.expected) }
        assertEquals(Variant.TIR_OVER_CHORDS, AdaptiveEar.choose(base + hard, 4, ::day)?.variant)
        assertNull(AdaptiveEar.choose(base + hard, 3, ::day))
        assertNull(AdaptiveEar.choose(base + hard.map { it.copy(keyLabel = "A MINOR") }, 4, ::day))
    }

    @Test fun medianUsesBothCentralResponses() {
        assertNull(Metrics.median(emptyList()))
        assertEquals(1500L, Metrics.median(listOf(2000L, 1000L)))
    }
}
