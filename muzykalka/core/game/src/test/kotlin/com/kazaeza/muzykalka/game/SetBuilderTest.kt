package com.kazaeza.muzykalka.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SetBuilderTest {
    private val singClosed = SingUnlock.Status(open = false, tries = 0, correct = 0)
    private val singOpen = SingUnlock.Status(open = true, tries = 20, correct = 16)
    private val duelFocus = EarFocus(Variant.TIR_DUEL, listOf("4", "5"), level = 2)

    @Test
    fun `scale hits every budget option with Rule 6 minimum and unique variants`() {
        for (budget in SetBuilder.BUDGET_OPTIONS) {
            val scaled = SetBuilder.scale(DailySet.DEFAULT, budget)
            assertEquals(scaled, SetBuilder.scale(DailySet.DEFAULT, budget), "deterministic")
            assertTrue(abs(scaled.sumOf { it.minutes } - budget) <= 0.5, "budget=$budget sum=${scaled.sumOf { it.minutes }}")
            assertEquals(budget.toDouble(), scaled.sumOf { it.minutes }, 1e-9)
            assertTrue(scaled.all { it.minutes >= 1.0 }, "budget=$budget Rule 6")
            assertEquals(scaled.size, scaled.map { it.variant }.toSet().size, "budget=$budget unique")
        }
        val five = SetBuilder.scale(DailySet.DEFAULT, 5)
        assertEquals(listOf(2.0, 1.5, 1.5), five.map { it.minutes })
        val ten = SetBuilder.scale(DailySet.DEFAULT, 10)
        assertEquals(4, ten.size)
    }

    @Test
    fun `applyQuiet swaps voice blocks to non-duplicate silent ear drills preserving minutes`() {
        assertEquals(DailySet.DEFAULT, SetBuilder.applyQuiet(DailySet.DEFAULT, quiet = false))
        val quietDaily = SetBuilder.applyQuiet(DailySet.DEFAULT, quiet = true)
        assertEquals(DailySet.DEFAULT.sumOf { it.minutes }, quietDaily.sumOf { it.minutes }, 1e-9)
        assertTrue(quietDaily.none { it.variant in Training.VOICE })
        assertEquals(quietDaily.size, quietDaily.map { it.variant }.toSet().size)

        val voiceStage = Tracks.VOICE_EAR.stages.last().drills
        val quietVoice = SetBuilder.applyQuiet(voiceStage, quiet = true)
        assertEquals(voiceStage.sumOf { it.minutes }, quietVoice.sumOf { it.minutes }, 1e-9)
        assertTrue(quietVoice.none { it.variant in Training.VOICE })
        assertEquals(quietVoice.size, quietVoice.map { it.variant }.toSet().size)
    }

    @Test
    fun `buildDaily is always non-guitar, honors quiet, singing unlock, transfer gap, and focus`() {
        for (budget in SetBuilder.BUDGET_OPTIONS) {
            for (quiet in listOf(false, true)) {
                for (track in listOf<Track?>(null) + Tracks.ALL) {
                    val plan = SetBuilder.buildDaily(
                        targetMinutes = budget,
                        quiet = quiet,
                        activeTrack = track,
                        singing = singClosed,
                        focus = duelFocus,
                        transferGapBig = true,
                    )
                    assertEquals(budget.toDouble(), plan.sumOf { it.minutes }, 1e-9)
                    assertTrue(plan.all { it.minutes >= 1.0 }, "Rule 6 budget=$budget track=${track?.id}")
                    assertTrue(plan.none { it.variant.guitar == GuitarNeed.REQUIRED }, "no physical guitar")
                    assertEquals(plan.size, plan.map { it.variant }.toSet().size, "unique variants")
                    if (quiet) assertTrue(plan.none { it.variant in Training.VOICE }, "quiet has no voice")
                }
            }
        }

        val closedPlan = SetBuilder.buildDaily(20, quiet = false, Tracks.CHORDS_FUNC, singClosed, null, transferGapBig = false)
        assertTrue(closedPlan.any { it.variant == Variant.KAMERTON_SEARCH })
        assertTrue(closedPlan.none { it.variant == Variant.KAMERTON_SING })

        val openPlan = SetBuilder.buildDaily(20, quiet = false, Tracks.CHORDS_FUNC, singOpen, null, transferGapBig = false)
        assertTrue(openPlan.any { it.variant == Variant.KAMERTON_SING })

        val transferShort = SetBuilder.buildDaily(5, quiet = false, null, singOpen, null, transferGapBig = true)
        assertTrue(transferShort.any { it.variant == Variant.TIR_LOOP3 })

        val focusedShort = SetBuilder.buildDaily(5, quiet = true, Tracks.STEPS_BASE, singOpen, duelFocus, transferGapBig = false)
        assertEquals(5.0, focusedShort.sumOf { it.minutes }, 1e-9)
        assertTrue(focusedShort.any { it.variant == Variant.TIR_DUEL })
    }

    @Test
    fun `buildStage scales every track stage cleanly across budgets`() {
        for (track in Tracks.ALL) {
            for (stage in track.stages) {
                for (budget in listOf(5, 10, 20)) {
                    val quiet = track.guitar == GuitarNeed.NONE
                    val plan = SetBuilder.buildStage(stage, budget, quiet = quiet, singing = singClosed, focus = null)
                    assertEquals(budget.toDouble(), plan.sumOf { it.minutes }, 1e-9)
                    assertTrue(plan.all { it.minutes >= 1.0 }, "${stage.id} budget=$budget Rule 6")
                    assertEquals(plan.size, plan.map { it.variant }.toSet().size, "${stage.id} unique")
                    if (quiet) assertTrue(plan.none { it.variant in Training.VOICE }, "${stage.id} quiet")
                }
            }
        }
    }
}
