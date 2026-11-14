package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.ProgressionText
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.music.SongBook
import com.kazaeza.muzykalka.music.SongOpenings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-47 «Коллекция ходов»: the library's opening loops, their songs, and which of them the ear knows. */
class LoopAlbumTest {
    private fun song(id: String, progression: String, mode: Mode = Mode.MAJOR, learning: Boolean = false) = GuessSong(
        id,
        Song(id = id, title = "Песня $id", mode = mode, sections = ProgressionText.parseSections("A: $progression"), form = listOf("A")),
        learning,
    )

    private val songs = listOf(
        song("a", "| I | V | vi | IV |"), song("b", "| I | I | V | V | vi | IV |", learning = true), song("c", "| vi | IV | I | V |"),
        song("d", "| i | bVI | bIII | bVII |", Mode.MINOR), song("e", "| I | IV | V | I |"), song("x", "| I | I |"),
    )

    private var clock = 0L

    private fun answer(key: String, right: Boolean) = AttemptRecord(Variant.TIR_SONG, 1, key, "x", "x", right, 4_000, null, 0, null, clock++)

    @Test
    fun `songs that open with the same loop share a card, the most common loop first`() {
        val album = LoopAlbum.build(songs, emptyList())
        assertEquals(3, album.size, "a one-chord song has no loop")
        assertEquals(listOf("b", "a"), album[0].songs.map { it.id }, "two songs: the one in learning first")
        assertEquals("I-V-vi-IV", SongOpenings.signature(album[0].chords))
        val relative = album.first { c -> c.songs.any { it.id == "c" } }
        assertEquals(setOf("c", "d"), relative.songs.map { it.id }.toSet(), "the relative minor's way of writing it is the same loop")
        assertTrue(album.all { it.asked == 0 && !it.known })
    }

    @Test
    fun `a loop is known when its last three answers were right, by the loop or by the song`() {
        val loop = "song:L1:I-V-vi-IV"
        val twoRight = listOf(answer(loop, true), answer(loop, true))
        assertFalse(LoopAlbum.build(songs, twoRight)[0].known, "two are not three")
        val known = twoRight + answer("song:L3:b", true)
        val card = LoopAlbum.build(songs, known)[0]
        assertTrue(card.known, "level 3 records the song: it counts for the song's loop")
        assertEquals(3, card.asked)
        assertEquals(3, card.right)
        val slipped = known + answer("song:L2:I-V-vi-IV", false)
        val after = LoopAlbum.build(songs, slipped)[0]
        assertFalse(after.known, "the latest answers decide")
        assertEquals(4, after.asked)
        assertEquals(3, after.right)
        val back = slipped + List(3) { answer(loop, true) }
        assertTrue(LoopAlbum.build(songs, back.shuffled(kotlin.random.Random(1)))[0].known, "the order of time, not of the list")
    }

    @Test
    fun `answers of other games and of songs that are gone do not count`() {
        val stray = listOf(
            AttemptRecord(Variant.TIR_CLASSIC, 1, "tir:L1:3", "3", "3", true, 900, null, 0, null, 1),
            answer("song:L3:deleted", true), answer("song:L1:IV-IV-IV-IV", true), answer("next:L1:a", true),
        )
        assertTrue(LoopAlbum.build(songs, stray).all { it.asked == 0 })
    }

    @Test
    fun `the book makes a real album`() {
        val book = SongBook.ENTRIES.map { GuessSong(SongBook.libraryId(it), it.song) }
        val album = LoopAlbum.build(book, emptyList())
        assertTrue(album.size >= 30, "loops in the book: ${album.size}")
        assertEquals(album.sumOf { it.songs.size }, album.flatMap { it.songs }.map { it.id }.toSet().size, "every song on one card")
        assertTrue(album.first().songs.size >= album.last().songs.size)
        assertEquals(album.map { it.shape }.toSet().size, album.size)
    }
}
