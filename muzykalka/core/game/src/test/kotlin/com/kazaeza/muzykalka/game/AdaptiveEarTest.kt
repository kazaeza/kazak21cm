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
        assertNull(AdaptiveEar.choose(oneDay, 1, dayOf = ::day))
        val rows = oneDay + set(2)
        val focus = AdaptiveEar.choose(rows, 2, dayOf = ::day)!!
        assertEquals(Variant.TIR_DUEL, focus.variant)
        assertEquals(listOf("4", "5"), focus.pair)
        val plan = AdaptiveEar.apply(DailySet.DEFAULT, focus)
        assertEquals(20.0, plan.sumOf { it.minutes })
        assertEquals(1.5, plan.single { it.variant == Variant.TIR_DUEL }.minutes)
        assertNull(AdaptiveEar.choose(rows, 20, dayOf = ::day))
    }

    @Test fun onlyCompletedLinkedRoundsAdvancePractice() {
        val pair = listOf("4", "5")
        val baseline = set(1) + set(2)
        val duel = FocusSession(20L, 2, EarFocus(Variant.TIR_DUEL, pair, 2), "COMPLETED")
        val practice = pair.mapIndexed { i, d -> note(d, d, 2, i).copy(variant = Variant.TIR_DUEL,
            itemKey = "tir:duel:4~5:$d", focusSessionId = duel.id) }
        val rows = baseline + practice
        fun pick(day: Int, more: List<AttemptRecord>, sessions: List<FocusSession>) =
            AdaptiveEar.choose(rows + more, day, sessions, ::day)
        assertEquals(Variant.TIR_DUEL, pick(3, emptyList(), emptyList())?.variant)
        assertEquals(Variant.TIR_DUEL, pick(3, emptyList(), listOf(duel.copy(status = "SKIPPED")))?.variant)
        assertNull(pick(2, emptyList(), listOf(duel)))
        val probe = EarFocus(Variant.TIR_CLASSIC, pair, 2, duel.id)
        assertEquals(probe, pick(3, emptyList(), listOf(duel)))
        val checks = (3..5).map { FocusSession(it.toLong(), it, probe, "COMPLETED") }
        val answers = checks.flatMap { session -> pair.mapIndexed { i, d ->
            note(d, d, session.day, i).copy(focusSessionId = session.id, replayCount = 0)
        } }
        assertEquals(probe, pick(6, answers + answers, listOf(duel) + checks.take(1)))
        assertEquals(probe, pick(6, answers.map { it.copy(focusSessionId = null) }, listOf(duel) + checks))
        assertEquals(probe, pick(6, answers.map { it.copy(replayCount = 1) }, listOf(duel) + checks))
        assertEquals(Variant.TIR_OVER_CHORDS, pick(6, answers, listOf(duel) + checks)?.variant)
        val oneMiss = listOf(answers.first().copy(correct = false)) + answers.drop(1)
        assertEquals(probe, pick(6, oneMiss, listOf(duel) + checks))
        val delayed = probe.copy(retention = true)
        assertEquals(delayed, pick(16, answers, listOf(duel) + checks))
        val later = FocusSession(21, 16, delayed, "COMPLETED")
        val missed = pair.mapIndexed { i, d -> note(d, "?", 16, i).copy(focusSessionId = 21, replayCount = 0) }
        assertEquals(delayed, pick(17, answers + missed, listOf(duel, later) + checks))
        val retest = later.copy(id = 22, day = 17)
        assertEquals(Variant.TIR_DUEL, pick(18, answers + missed + missed.map { it.copy(focusSessionId = 22, atEpochMs = 17001) },
            listOf(duel, later, retest) + checks)?.variant)
        assertEquals(Variant.TIR_DUEL, pick(6, answers.map { it.copy(correct = false) }, listOf(duel) + checks)?.variant)
    }

    @Test fun contextRecommendationNeedsComparableMaterialAcrossDays() {
        val base = (1..2).flatMap { day -> List(9) { i ->
            val d = listOf("1", "3", "5")[i % 3]
            note(d, d, day, i).copy(keyLabel = "C MAJOR")
        } }
        val hard = base.map { it.copy(variant = Variant.TIR_OVER_CHORDS, level = 1,
            correct = false, given = "?", itemKey = "tir:chords:L1:I:" + it.expected) }
        assertEquals(Variant.TIR_OVER_CHORDS, AdaptiveEar.choose(base + hard, 4, dayOf = ::day)?.variant)
        assertEquals("I", AdaptiveEar.choose(base + hard, 4, dayOf = ::day)?.chord)
        assertNull(AdaptiveEar.choose(base + hard, 3, dayOf = ::day))
        assertNull(AdaptiveEar.choose(base + hard.map { it.copy(keyLabel = "A MINOR") }, 4, dayOf = ::day))
    }

    @Test fun medianUsesBothCentralResponses() {
        assertNull(Metrics.median(emptyList()))
        assertEquals(1500L, Metrics.median(listOf(2000L, 1000L)))
    }
}
