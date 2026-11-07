package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.BookGenre
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.SongBook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BlindSessionTest {
    private val pool = BlindSession.pool()

    private fun session(history: List<AttemptRecord> = emptyList(), budgetMs: Long = 10 * 60_000L, seed: Int = 1) =
        BlindSession(pool, history, KeyPicker(Random(seed)), Random(seed), budgetMs)

    private fun asked(entryId: String, atMs: Long) =
        AttemptRecord(Variant.TAPER_BLIND, 1, "blind:$entryId", entryId, "CLEAN", true, 1000, null, 3, null, atMs)

    @Test
    fun `the pool is the likes shelf, not the user's library`() {
        assertTrue(pool.isNotEmpty())
        assertTrue(pool.all { it.genre == BookGenre.LIKED })
        assertEquals(SongBook.ENTRIES.count { it.genre == BookGenre.LIKED }, pool.size)
        // O-11: the pieces added from the open chart dataset widen the pool, the hits shelf does not
        assertTrue(pool.size >= 80, "the pool has ${pool.size} pieces")
    }

    @Test
    fun `an order shows the degrees and nothing above them (no letters since O-13)`() {
        assertEquals(HintStep.DEGREES, BlindSession.BASE_HINT)
        assertEquals(null, BlindSession.BASE_HINT.next())
    }

    @Test
    fun `no song twice before the rest have had a turn, and the ones asked longest ago come last`() {
        val seen = pool.take(3)
        val history = seen.mapIndexed { i, e -> asked(e.id, 1_000L * (i + 1)) }
        val s = session(history, budgetMs = Long.MAX_VALUE)
        val order = buildList { while (s.next(0)) { add(requireNotNull(s.current).id); s.finishPlaying(1); s.grade(SelfGrade.CLEAN) } }
        assertEquals(pool.size, order.size)
        assertEquals(pool.size, order.toSet().size)
        assertEquals(seen.map { it.id }, order.takeLast(3), "oldest first among the seen")
    }

    @Test
    fun `different seeds open with different songs`() {
        val firsts = (1..8).map { seed -> session(seed = seed).also { it.next(0) }.current!!.id }.toSet()
        assertTrue(firsts.size > 1)
    }

    @Test
    fun `the reveal follows the grade, with tips`() {
        val s = session()
        s.next(0)
        assertEquals(BlindPhase.PLAYING, s.phase)
        s.finishPlaying(42_000)
        assertEquals(BlindPhase.SELF_GRADE, s.phase)
        val r = s.grade(SelfGrade.CLEAN)
        assertEquals(BlindPhase.RESULT, s.phase)
        assertEquals(100, r.tips)
        assertTrue(r.playableNow)
        assertEquals(42_000, r.elapsedMs)
        assertNotNull(s.lastResult)
        assertEquals(100, s.totalTips)
    }

    @Test
    fun `tips and the clean flag follow the self-grade`() {
        assertEquals(100, BlindGrading.tips(SelfGrade.CLEAN))
        assertEquals(50, BlindGrading.tips(SelfGrade.STUMBLED))
        assertEquals(0, BlindGrading.tips(SelfGrade.FAILED))
        assertTrue(BlindGrading.playableNow(SelfGrade.CLEAN))
        assertFalse(BlindGrading.playableNow(SelfGrade.STUMBLED))
    }

    @Test
    fun `the session ends after the order that crosses the budget`() {
        val s = session(budgetMs = 60_000)
        assertTrue(s.next(0))
        s.finishPlaying(10); s.grade(SelfGrade.CLEAN)
        assertTrue(s.next(30_000))
        s.finishPlaying(31_000); s.grade(SelfGrade.CLEAN)
        assertFalse(s.next(70_000))
        assertEquals(BlindPhase.FINISHED, s.phase)
        assertEquals(2, s.results.size)
    }

    @Test
    fun `the attempt row keeps the song id and counts a failure as wrong`() {
        val s = session()
        s.next(0)
        s.finishPlaying(5_000)
        val r = s.grade(SelfGrade.FAILED)
        val row = BlindGrading.attempt(r, 99)
        assertEquals(Variant.TAPER_BLIND, row.variant)
        assertEquals("blind:${r.entry.id}", row.itemKey)
        assertFalse(row.correct)
        assertEquals(HintStep.DEGREES.ordinal, row.hintLevel)
        assertEquals(5_000, row.latencyMs)
    }
}
