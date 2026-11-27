package com.kazaeza.muzykalka.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TracksTest {
    private fun attempt(
        variant: Variant,
        level: Int,
        correct: Boolean,
        latencyMs: Long = 1_200L,
        atMs: Long = 0L,
    ) = AttemptRecord(
        variant = variant,
        level = level,
        itemKey = "test:${variant.name}:L$level",
        expected = "5",
        given = if (correct) "5" else "4",
        correct = correct,
        latencyMs = latencyMs,
        centsError = null,
        hintLevel = 0,
        keyLabel = "C MAJOR",
        atEpochMs = atMs,
    )

    @Test
    fun `track catalog satisfies non-guitar core, Rule 6, and lookup invariants`() {
        assertEquals(5, Tracks.NON_GUITAR.size)
        assertEquals(1, Tracks.GUITAR.size)
        assertEquals(Tracks.NON_GUITAR + Tracks.GUITAR, Tracks.ALL)
        assertEquals(Tracks.ALL.size, Tracks.ALL.map { it.id }.toSet().size)
        assertEquals(Tracks.STEPS_BASE, Tracks.byId(null))
        assertEquals(Tracks.STEPS_BASE, Tracks.byId("UNKNOWN"))
        for (track in Tracks.ALL) {
            assertEquals(track, Tracks.byId(track.id))
            assertTrue(track.stages.size in 3..4, "${track.id} stage count")
            assertEquals(track.stages.size, track.stages.map { it.id }.toSet().size)
            for (stage in track.stages) {
                assertTrue(stage.drills.isNotEmpty(), stage.id)
                assertTrue(stage.criteria.isNotEmpty(), stage.id)
                assertTrue(stage.drills.all { it.minutes >= 1.0 }, "${stage.id}: Rule 6")
                assertEquals(stage.drills.size, stage.drills.map { it.variant }.toSet().size, "${stage.id}: unique drills")
                assertEquals(stage.drills, DailySet.available(stage.drills, "0.1"), "${stage.id}: shipped in 0.1")
            }
        }
        for (track in Tracks.NON_GUITAR) {
            assertEquals(GuitarNeed.NONE, track.guitar, track.id)
            for (stage in track.stages) {
                assertTrue(stage.drills.none { it.variant.guitar == GuitarNeed.REQUIRED }, "${stage.id}: no guitar drills")
                assertTrue(stage.criteria.none { it.variant.guitar == GuitarNeed.REQUIRED }, "${stage.id}: no guitar criteria")
            }
        }
        assertTrue(Tracks.VOICE_EAR.requiresVoice)
        assertEquals(GuitarNeed.REQUIRED, Tracks.GUITAR_HANDS.guitar)
    }

    @Test
    fun `evaluate computes live mastery from attempts, speed limit, and unlocked levels`() {
        val empty = Tracks.evaluate(Tracks.STEPS_BASE, emptyList())
        assertEquals(0, empty.completedStages)
        assertEquals(0, empty.currentStageIndex)
        assertEquals(0.0, empty.overallFraction, 1e-9)
        assertFalse(empty.stages[0].done)

        val partial = List(10) { i -> attempt(Variant.TIR_CLASSIC, 1, correct = true, latencyMs = 1_500L, atMs = i * 1000L) }
        val p1 = Tracks.evaluate(Tracks.STEPS_BASE, partial)
        assertFalse(p1.stages[0].done)
        assertTrue(p1.stages[0].fraction in 0.3..0.5, "got ${p1.stages[0].fraction}")

        val tooSlow = List(25) { i -> attempt(Variant.TIR_CLASSIC, 1, correct = true, latencyMs = 4_000L, atMs = i * 1000L) }
        val pSlow = Tracks.evaluate(Tracks.STEPS_BASE, tooSlow)
        assertFalse(pSlow.stages[0].done)
        assertTrue(pSlow.stages[0].summary.contains("ускорить"))

        val fastEnough = List(25) { i -> attempt(Variant.TIR_CLASSIC, 1, correct = true, latencyMs = 1_500L, atMs = i * 1000L) }
        val pDone1 = Tracks.evaluate(Tracks.STEPS_BASE, fastEnough)
        assertTrue(pDone1.stages[0].done)
        assertEquals(1, pDone1.completedStages)
        assertEquals(1, pDone1.currentStageIndex)

        val l2Fails = List(10) { i -> attempt(Variant.TIR_CLASSIC, 2, correct = false, latencyMs = 3_000L, atMs = 50_000L + i * 1000L) }
        val noRegress = Tracks.evaluate(Tracks.STEPS_BASE, fastEnough + l2Fails)
        assertTrue(noRegress.stages[0].done, "struggling at level 2 must not un-complete level 1")

        val byUnlock = Tracks.evaluate(Tracks.STEPS_BASE, emptyList(), mapOf(Variant.TIR_CLASSIC to 2))
        assertTrue(byUnlock.stages[0].done)
        assertFalse(byUnlock.stages[1].done)
        assertEquals(1, byUnlock.currentStageIndex)
    }

    @Test
    fun `split exam and sound-to-name link coverage`() {
        val earStages = Exam.stages(ExamKind.EAR)
        val guitarStages = Exam.stages(ExamKind.GUITAR)
        assertEquals(listOf(Skill.EAR_DEGREES, Skill.EAR_PROGRESSIONS), earStages.map { it.skill })
        assertEquals(listOf(Skill.NECK_LOW_STRINGS, Skill.NECK_FULL), guitarStages.map { it.skill })
        assertTrue(earStages.none { it.variant.guitar == GuitarNeed.REQUIRED })
        assertTrue(guitarStages.all { it.variant.guitar == GuitarNeed.REQUIRED })
        assertEquals(guitarStages + earStages, Exam.STAGES_V01)

        val soundToName = Metrics.variantsOf(Link.SOUND_TO_NAME)
        val expected = listOf(
            Variant.TIR_COLOR, Variant.TIR_LAYERS, Variant.TIR_LOOP3, Variant.TIR_MODE,
            Variant.TIR_SIMON, Variant.TIR_SONG, Variant.TIR_NEXT, Variant.TIR_TRANSFER,
        )
        for (v in expected) {
            assertTrue(v in soundToName, "${v.name} must contribute to SOUND_TO_NAME")
        }
    }
}
