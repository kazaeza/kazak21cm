package com.kazaeza.muzykalka.srs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-42 (ADR-041): a shelved card is a new card that is never due; the first review takes it off the shelf. */
class ShelvedCardTest {
    @Test
    fun `a shelved card is new, never due, and not a fresh card`() {
        val card = CardState.shelved()
        assertTrue(card.isNew)
        assertTrue(card.isShelved)
        for (day in listOf(0, 1, 20_000, 1_000_000)) assertFalse(card.isDue(day), "day $day")
        assertFalse(CardState().isShelved)
        assertTrue(CardState().isDue(0), "an ordinary new card is due at once")
    }

    @Test
    fun `the first review takes a card off the shelf`() {
        val fsrs = Fsrs()
        val reviewed = fsrs.review(CardState.shelved(), Rating.GOOD, today = 100)
        assertFalse(reviewed.isShelved)
        assertFalse(reviewed.isNew)
        assertTrue(reviewed.dueDay!! > 100)
        assertEquals(fsrs.review(CardState(), Rating.GOOD, today = 100), reviewed, "the shelf leaves no trace in the schedule")
    }

    @Test
    fun `a card that was learned is never shelved`() {
        val card = Fsrs().review(CardState(), Rating.AGAIN, today = 5)
        assertFalse(card.isShelved)
    }
}
