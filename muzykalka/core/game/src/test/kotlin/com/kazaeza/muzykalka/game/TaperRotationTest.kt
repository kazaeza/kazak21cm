package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.ProgressionText
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.srs.CardState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TaperRotationTest {
    private val song = Song(
        id = "s", title = "s", mode = Mode.MAJOR, sections = ProgressionText.parseSections("A: | I | V |"), form = listOf("A"),
    )

    private fun item(id: String, dueDay: Int?) =
        TaperItem(id, id, song = song, card = CardState(stability = dueDay?.let { 1.0 }, dueDay = dueDay))

    @Test
    fun `a song played lately goes to the back instead of opening every evening`() {
        val items = listOf(item("autumn-leaves", 1), item("a", 5), item("b", 9), item("c", 5), item("d", 5))
        repeat(20) { seed ->
            val queue = TaperSession.rotation(items, today = 10, recent = setOf("autumn-leaves"), random = Random(seed)).map { it.id }
            assertEquals("autumn-leaves", queue.last(), "seed $seed: $queue")
            assertEquals(5, queue.size)
        }
    }

    @Test
    fun `due songs come first, other songs fill up to the limit, nothing due means an empty queue`() {
        val items = listOf(item("due", 3)) + (1..8).map { item("later$it", 30) }
        val queue = TaperSession.rotation(items, today = 10, recent = emptySet(), random = Random(1), filler = 5).map { it.id }
        assertEquals("due", queue.first())
        assertEquals(6, queue.size)
        assertTrue(queue.drop(1).all { it.startsWith("later") })
        assertTrue(TaperSession.rotation(items.drop(1), today = 10, recent = emptySet(), random = Random(1)).isEmpty())
    }

    @Test
    fun `the order varies from one evening to the next`() {
        val items = (1..6).map { item("s$it", 2) }
        val firsts = (0 until 30).map { TaperSession.rotation(items, 10, emptySet(), Random(it)).first().id }.toSet()
        assertTrue(firsts.size >= 4, "first songs seen: $firsts")
    }

    /** O-42 (ADR-041): the book sits on the shelf; it fills the bar only after what is being learned, and is never due itself. */
    @Test
    fun `shelved songs fill the bar only after the songs in learning and never count as due`() {
        val shelved = (1..40).map { TaperItem("book$it", "book$it", song = song, card = CardState.shelved()) }
        val learning = (1..3).map { item("mine$it", 30) }
        val items = listOf(item("due", 3)) + learning + shelved
        repeat(10) { seed ->
            val queue = TaperSession.rotation(items, today = 10, recent = emptySet(), random = Random(seed), filler = 5).map { it.id }
            assertEquals("due", queue.first())
            assertEquals(6, queue.size)
            assertTrue(queue.containsAll(learning.map { it.id }), "seed $seed: songs in learning come before the shelf: $queue")
            assertEquals(2, queue.count { it.startsWith("book") })
        }
        assertTrue(TaperSession.rotation(learning + shelved, today = 10, recent = emptySet(), random = Random(1)).isEmpty(), "a shelf alone is not a review")
    }
}
