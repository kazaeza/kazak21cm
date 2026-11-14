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

    /** O-43: an opening is its changes — held chords are one, and the text of it does not depend on spelling or bass. */
    @Test
    fun `the changes of a song skip held chords, and their signature ignores the bass and the spelling`() {
        val blues = song("| I | I | I | I | IV | IV | I | I | V | IV | I | V |")
        assertEquals("I-IV-I-V", SongOpenings.signature(SongOpenings.changes(blues, 4)))
        assertEquals(2, SongOpenings.changes(song("| I | V | I | V |"), 2).size)
        assertEquals(1, SongOpenings.changes(song("| i | i | i |", Mode.MINOR), 4).size, "a one-chord song has one change")
        val slash = SongOpenings.changes(song("| I/3 | V/7 | vi |"), 4)
        assertEquals("I-V-vi", SongOpenings.signature(slash))
        assertEquals(
            SongOpenings.signature(SongOpenings.changes(song("| I | #IV | V |"), 3)),
            SongOpenings.signature(SongOpenings.changes(song("| I | bV | V |"), 3)),
        )
        assertEquals(2, SongOpenings.commonPrefix(SongOpenings.changes(song("| I | V | vi | IV |"), 4), SongOpenings.changes(song("| I | V | IV | I |"), 4)))
        assertEquals(0, SongOpenings.commonPrefix(SongOpenings.changes(song("| vi | IV |"), 4), SongOpenings.changes(song("| I | V |"), 4)))
    }

    @Test
    fun `the shape of an opening is the same loop in a major and in its relative minor`() {
        val major = SongOpenings.changes(song("| vi | IV | I | V |"), 4)
        val minor = SongOpenings.changes(song("| i | bVI | bIII | bVII |", Mode.MINOR), 4)
        assertNotEquals(SongOpenings.signature(major), SongOpenings.signature(minor), "written differently")
        assertEquals(SongOpenings.shape(major), SongOpenings.shape(minor), "heard the same")
        assertNotEquals(SongOpenings.shape(major), SongOpenings.shape(SongOpenings.changes(song("| I | V | vi | IV |"), 4)))
        assertNotEquals(SongOpenings.shape(SongOpenings.changes(song("| I | IV |"), 2)), SongOpenings.shape(SongOpenings.changes(song("| i | iv |", Mode.MINOR), 2)), "the quality is heard")
    }

    @Test
    fun `the book has plenty of different openings`() {
        val shapes = book.map { SongOpenings.shape(SongOpenings.changes(it.song, 4)) }.toSet()
        assertTrue(shapes.size >= 30, "different openings in the book: ${shapes.size}")
    }
}
