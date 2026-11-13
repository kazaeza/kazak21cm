package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-41: the songs behind the anchor slot of a degree are the ones that open on its chord. */
class SongOpeningsTest {
    private val book = SongBook.ENTRIES

    private fun song(progression: String, mode: Mode = Mode.MAJOR) =
        Song(id = "t", title = "t", mode = mode, sections = ProgressionText.parseSections("A: $progression"), form = listOf("A"))

    @Test
    fun `a song opens on the root of its first chord, whatever the quality or the bass`() {
        assertTrue(SongOpenings.opens(song("| V7 | I |"), Degree.FIVE))
        assertTrue(SongOpenings.opens(song("| vi | IV |"), Degree.parse("6")))
        assertTrue(SongOpenings.opens(song("| I/3 | V |"), Degree.ONE), "a slash bass is not the root")
        assertTrue(!SongOpenings.opens(song("| I | V |"), Degree.FIVE))
        assertTrue(SongOpenings.opens(song("| #IV | V |"), Degree.FLAT_FIVE), "♯4 and ♭5 are one degree")
    }

    @Test
    fun `only songs that open on the degree are offered, and the degrees that songs open on have some in the book`() {
        // nothing opens on the vii° — the card of the 7 says so in words (a song that starts on a leading-tone chord is rare)
        for (d in Mode.MAJOR.degrees.filter { it.number != 7 }) {
            val found = SongOpenings.candidates(book, { it.song }, d, Mode.MAJOR, Random(1))
            assertTrue(found.isNotEmpty(), "no song of the book opens on $d")
            assertTrue(found.all { SongOpenings.opens(it.song, d) })
        }
        assertEquals(
            book.count { SongOpenings.opens(it.song, Degree.FIVE) },
            SongOpenings.candidates(book, { it.song }, Degree.FIVE, Mode.MAJOR, Random(1)).size,
            "nothing is left out",
        )
    }

    @Test
    fun `the songs that fit the mode and the chord of the degree come first`() {
        val items = listOf(
            "minor-v" to song("| v | i |", Mode.MINOR),
            "major-v" to song("| V | I |", Mode.MAJOR),
            "minor-V" to song("| V | i |", Mode.MINOR),
            "major-v7" to song("| V7 | I |", Mode.MAJOR),
            "other" to song("| I | V |", Mode.MAJOR),
        )
        repeat(20) { seed ->
            val order = SongOpenings.candidates(items, { it.second }, Degree.FIVE, Mode.MAJOR, Random(seed)).map { it.first }
            assertEquals(setOf("major-v", "major-v7"), order.take(2).toSet(), "seed $seed: $order")
            assertEquals(setOf("minor-V"), order.drop(2).take(1).toSet(), "a major V in a minor song has the right third but the wrong mode: $order")
            assertTrue("other" !in order)
        }
        val minor = SongOpenings.candidates(items, { it.second }, Degree.FIVE, Mode.MINOR, Random(1)).map { it.first }
        assertEquals("minor-v", minor.first(), "in the minor scale the v is the minor one")
    }

    @Test
    fun `a fresh look offers another song first, and no song is offered twice`() {
        val firsts = (0 until 30).map { SongOpenings.candidates(book, { it.song }, Degree.ONE, Mode.MAJOR, Random(it)).first().id }.toSet()
        assertTrue(firsts.size >= 3, "first songs seen: $firsts")
        val all = SongOpenings.candidates(book, { it.song }, Degree.ONE, Mode.MAJOR, Random(7))
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertNotEquals(0, all.size)
    }

    @Test
    fun `an item without a song is skipped`() {
        val items = listOf(null, song("| V | I |"), null)
        assertEquals(1, SongOpenings.candidates(items, { it }, Degree.FIVE, Mode.MAJOR, Random(1)).size)
    }
}
