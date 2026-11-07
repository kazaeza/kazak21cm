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

    private fun chordsOf(id: String, label: String) = SongBook.byId(id)!!.song.section(label).bars.flatMap { it.chords }

    private fun List<ChordDegree>.collapsed() = filterIndexed { i, c -> i == 0 || c != this[i - 1] }

    private fun parse(text: String) = text.split(' ').map(ChordParser::parse)

    @Test
    fun `the hits shelf is about a hundred chart records, each short enough to learn`() {
        val hits = SongBook.ENTRIES.filter { it.genre == BookGenre.HITS }
        assertTrue(hits.size >= 90, "hits shelf has ${hits.size}")
        val others = SongBook.ENTRIES.filter { it.genre != BookGenre.HITS }.map { it.song.title.lowercase() }.toSet()
        hits.forEach { e ->
            val song = e.song
            assertTrue(song.artist != null, "${e.id} has an artist")
            assertTrue(song.title.lowercase() !in others, "${e.id} is already on another shelf")
            // sections are labelled A, B, C … with no hole, every one is played, and none is a whole song long
            assertEquals(('A'..'D').take(song.sections.size).map { it.toString() }, song.sections.map { it.label }, "${e.id} labels")
            assertTrue(song.sections.size in 1..4, "${e.id} has ${song.sections.size} sections")
            assertEquals(song.sections.map { it.label }.toSet(), song.form.toSet(), "${e.id} has a section that is never played")
            assertTrue(song.form.size <= 8, "${e.id} form ${song.formString}")
            song.sections.forEach { assertTrue(it.bars.size in 1..12, "${e.id} ${it.label} has ${it.bars.size} bars") }
        }
        assertEquals(setOf(Mode.MAJOR, Mode.MINOR, Mode.MIXOLYDIAN, Mode.DORIAN), hits.map { it.song.mode }.toSet())
        assertEquals(setOf(1, 2, 3), hits.map { it.difficulty }.toSet())
        assertEquals(3, SongBook.byId("goodbye-yellow-brick-road")!!.difficulty)
    }

    @Test
    fun `hits are stored the way the records play them`() {
        assertEquals(parse("I V vi IV"), chordsOf("with-or-without-you", "A"))
        assertEquals(parse("I vi IV V"), chordsOf("last-kiss", "A"))
        assertEquals(parse("I V IV I"), chordsOf("bad-moon-rising", "A").collapsed())
        assertEquals(parse("i bVII bVI7 V7"), chordsOf("happy-together", "A").collapsed())
        assertEquals(parse("III7 VI7 II7 V"), chordsOf("be-my-baby", "B").collapsed())
        assertEquals(parse("i IV7 V7"), chordsOf("evil-ways", "A").collapsed())
        assertEquals(parse("I IV I bVII"), chordsOf("a-hard-days-night", "A").collapsed().take(4))
        assertEquals(Mode.MIXOLYDIAN, SongBook.byId("a-hard-days-night")!!.song.mode)
        assertEquals(Mode.DORIAN, SongBook.byId("evil-ways")!!.song.mode)
        assertEquals(Mode.MINOR, SongBook.byId("happy-together")!!.song.mode)
    }

    @Test
    fun `twelve-bar hits keep all twelve bars on one section`() {
        listOf("treat-her-right", "the-thrill-is-gone", "reelin-and-rockin", "baby-what-you-want-me-to-do", "i-got-you-i-feel-good").forEach { id ->
            assertEquals(12, SongBook.byId(id)!!.song.section("A").bars.size, id)
        }
        // the blues in sevenths: I⁷ for four bars, then IV⁷ for two
        assertEquals(parse("I7 I7 I7 I7 IV7 IV7"), SongBook.byId("treat-her-right")!!.song.section("A").bars.take(6).flatMap { it.chords })
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
