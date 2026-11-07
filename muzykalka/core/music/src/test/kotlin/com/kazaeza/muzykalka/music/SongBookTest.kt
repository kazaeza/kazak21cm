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
    fun `my likes are a full shelf, stored in degrees with the tonic of the record`() {
        val liked = SongBook.ENTRIES.filter { it.genre == BookGenre.LIKED }
        assertTrue(liked.size >= 41, "liked shelf has ${liked.size}")
        val opening = { id: String, bars: Int -> SongBook.byId(id)!!.song.sections[0].bars.take(bars).flatMap { it.chords } }
        val parse = { text: String -> text.split(' ').map(ChordParser::parse) }
        assertEquals(parse("I V ii IV"), opening("just-like-heaven", 4))
        assertEquals(parse("bIII6 bVI IVsus2 I"), opening("something-changed", 4))
        assertEquals(parse("i bvii i bvii"), opening("in-the-afternoon", 4))
        assertEquals(Mode.PHRYGIAN, SongBook.byId("in-the-afternoon")!!.song.mode)
        assertEquals(parse("bVI iv bIII bVI"), SongBook.byId("lotus-flower")!!.song.section("B").bars.flatMap { it.chords }.take(4))
        assertEquals(parse("i v bIII iv"), SongBook.byId("if-you-really-love-nothing")!!.song.section("B").bars.flatMap { it.chords }.take(4))
        assertEquals(parse("i bVI v bVII V7"), SongBook.byId("igors-theme")!!.song.section("A").bars.flatMap { it.chords }.take(5))
        assertEquals(parse("V bVII I"), SongBook.byId("toni")!!.song.section("A").bars.first().chords)
        liked.forEach { e -> assertTrue(e.song.artist != null, "${e.id} has an artist") }
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
