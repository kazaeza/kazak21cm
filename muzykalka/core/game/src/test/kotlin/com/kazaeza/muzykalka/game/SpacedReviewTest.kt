package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.KeyPicker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SpacedReviewTest {
    private val day = 86_400_000L
    private val today = 100
    private val dayOf = { ms: Long -> (ms / day).toInt() }

    private fun a(key: String, given: String, ok: Boolean, onDay: Int, n: Int = 0, level: Int = 1) = AttemptRecord(
        Variant.TIR_CLASSIC, level, key, key.substringAfterLast(':'), given, ok, 1500, null, 0, null, onDay * day + n * 1000L,
    )

    @Test
    fun `yesterday's misses come back, freshest first, until answered right today`() {
        val history = listOf(
            a("tir:L1:3", "5", false, today - 1),
            a("tir:L1:3", "3", true, today - 1, n = 1),
            a("tir:L1:5", "3", false, today - 3),
            a("tir:L1:5", "?", false, today - 3, n = 1),
            a("tir:L1:1", "1", true, today - 1),
        )
        val q = SpacedReview.queue(history, today, dayOf)
        assertEquals(listOf("tir:L1:3", "tir:L1:5"), q.map { it.key })
        assertEquals(1, q[0].daysAgo)
        assertEquals(listOf("5"), q[0].confusedWith)
        assertEquals(listOf("3"), q[1].confusedWith, "«не знаю» is not a confusion")
        assertEquals(2, q[1].errors)

        val after = SpacedReview.queue(history + a("tir:L1:3", "3", true, today), today, dayOf)
        assertEquals(listOf("tir:L1:5"), after.map { it.key })
    }

    @Test
    fun `a clean later day clears an old miss, and the lookback is a week`() {
        val history = listOf(
            a("tir:L1:3", "5", false, today - 4),
            a("tir:L1:3", "3", true, today - 2),
            a("tir:L1:6", "5", false, today - 9),
        )
        assertTrue(SpacedReview.queue(history, today, dayOf).isEmpty())
    }

    @Test
    fun `Tir review merges single notes across levels and leaves melodies out`() {
        assertEquals("note:♭3", TirDriver.reviewKey("tir:L2:♭3"))
        assertEquals("note:♭3", TirDriver.reviewKey("tir:L7:DORIAN:♭3"))
        assertEquals("chord:ii", TirDriver.reviewKey("tir:L5:ii"))
        assertNull(TirDriver.reviewKey("tir:L4:len3"))
        val history = listOf(a("tir:L1:3", "5", false, today - 1), a("tir:L2:3", "3", true, today, level = 2))
        assertTrue(SpacedReview.queue(history, today, dayOf, TirDriver::reviewKey).isEmpty(), "right on level 2 today counts for level 1")
    }

    @Test
    fun `today's Tir opens with the review, flags it, and drops it once answered right`() {
        val review = SpacedReview.queue(listOf(a("tir:L2:3", "5", false, today - 1)), today, dayOf, TirDriver::reviewKey)
        val driver = TirDriver(KeyPicker(Random(3)), review = review)
        val history = mutableListOf<AttemptRecord>()
        val random = Random(5)
        var asked = 0
        var first: TirTask? = null
        repeat(12) {
            val task = driver.nextTask(DriverContext(random, 2, history))
            if (task.review != null) {
                asked++
                if (first == null) first = task
                assertEquals(Degree.THREE, task.notes.single().degree)
                assertEquals(listOf("5"), task.review.confusedWith)
            }
            history += AttemptRecord(Variant.TIR_CLASSIC, 2, task.itemKey, "", "", true, 1500, null, 0, null, today * day + it)
        }
        assertNotNull(first, "the miss came back within the first questions")
        assertEquals(1, asked, "a right answer takes it off today's list")
    }

    @Test
    fun `a stubborn review item is asked at most three times a round`() {
        val review = SpacedReview.queue(listOf(a("tir:L2:3", "5", false, today - 1)), today, dayOf, TirDriver::reviewKey)
        val driver = TirDriver(KeyPicker(Random(3)), review = review)
        val history = mutableListOf<AttemptRecord>()
        var asked = 0
        repeat(40) {
            val task = driver.nextTask(DriverContext(Random(it), 2, history))
            if (task.review != null) asked++
            history += AttemptRecord(Variant.TIR_CLASSIC, 2, task.itemKey, "", "", false, 1500, null, 0, null, today * day + it)
        }
        assertEquals(TirDriver.MAX_REVIEW_ASKS, asked)
    }

    @Test
    fun `no review list - the driver draws exactly as before`() {
        val a = TirDriver(KeyPicker(Random(9)))
        val b = TirDriver(KeyPicker(Random(9)), review = emptyList())
        repeat(20) { assertEquals(a.nextTask(DriverContext(Random(it), 2, emptyList())).itemKey, b.nextTask(DriverContext(Random(it), 2, emptyList())).itemKey) }
    }
}
