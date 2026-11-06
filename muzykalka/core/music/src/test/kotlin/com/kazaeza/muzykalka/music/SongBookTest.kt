package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SongBookTest {

    @Test
    fun `every entry parses, ids are unique and every shelf has pieces`() {
        val ids = SongBook.ENTRIES.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate ids")
        assertTrue(SongBook.ENTRIES.size >= 20)
        BookGenre.entries.forEach { g -> assertTrue(SongBook.ENTRIES.any { it.genre == g }, "empty shelf $g") }
        SongBook.ENTRIES.forEach { e ->
            assertEquals(SongBook.libraryId(e), e.song.id)
            assertTrue(e.song.chordsInOrder.isNotEmpty())
            assertTrue(e.teaches.isNotBlank())
        }
    }

    @Test
    fun `book text round-trips through the progression format`() {
        SongBook.ENTRIES.forEach { e ->
            val again = ProgressionText.parseSections(ProgressionText.format(e.song.sections))
            assertEquals(e.song.sections, again, e.id)
        }
    }

    @Test
    fun `known harmony is stored in degrees`() {
        val letItBe = SongBook.byId("let-it-be")!!.song
        assertEquals("I V vi IV", letItBe.sections[0].bars.take(4).joinToString(" ") { ChordParser.formatRoman(it.chords[0]) })
        val blues = SongBook.byId("blues-12")!!.song
        assertEquals(12, blues.barCount)
        val jazz = SongBook.byId("jazz-blues")!!.song
        assertEquals(ChordDegree(Degree.SHARP_FOUR, ChordQuality.DIMINISHED7), jazz.sections[0].bars[5].chords[0])
    }

    @Test
    fun `every degree has its own vibe and the path home matches the resolution`() {
        val words = Degree.CHROMATIC.map { DegreeVibes.of(it).word }
        assertEquals(12, words.toSet().size)
        assertEquals(DegreeVibes.of(Degree.SHARP_FOUR), DegreeVibes.of(Degree.FLAT_FIVE))
        assertEquals("5 → 6 → 7 → 1", DegreeVibes.pathHome(Degree.FIVE, Mode.MAJOR, DegreeNotation.ARABIC))
        assertEquals("4 → 3 → 2 → 1", DegreeVibes.pathHome(Degree.FOUR, Mode.MAJOR, DegreeNotation.ARABIC))
        assertEquals("уже дома", DegreeVibes.pathHome(Degree.ONE, Mode.MAJOR, DegreeNotation.ARABIC))
    }
}
