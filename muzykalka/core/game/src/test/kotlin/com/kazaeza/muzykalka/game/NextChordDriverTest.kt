package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.ProgressionText
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.music.SongBook
import com.kazaeza.muzykalka.music.SongOpenings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-45 «Куда пойдёт?» (ADR-044): three chords of a named song are played, the fourth is told ahead. */
class NextChordDriverTest {
    private val book = SongBook.ENTRIES.map { GuessSong(SongBook.libraryId(it), it.song) }

    private fun song(id: String, progression: String, mode: Mode = Mode.MAJOR, learning: Boolean = false) = GuessSong(
        id,
        Song(id = id, title = "Песня $id", mode = mode, sections = ProgressionText.parseSections("A: $progression"), form = listOf("A")),
        learning,
    )

    private fun tasks(level: Int, n: Int, seed: Int = 1, songs: List<GuessSong> = book): List<NextChordTask> {
        val d = NextChordDriver(songs, KeyPicker(Random(seed)))
        return List(n) { d.nextTask(DriverContext(Random(seed * 1000 + it), level, emptyList())) }
    }

    private fun has(list: List<ChordDegree>, c: ChordDegree) = list.any { SongOpenings.sameChord(it, c) }

    @Test
    fun `the song is named, three of its chords are played and the fourth is the answer`() {
        for (level in 1..2) tasks(level, 80).forEach { t ->
            val song = book.first { it.id == t.songId }.song
            val changes = SongOpenings.changes(song, 4)
            assertEquals(song.title, t.title)
            assertEquals(song.title, t.prompt.title)
            assertEquals(changes.take(3), t.shown)
            assertTrue(SongOpenings.sameChord(changes[3], t.answer))
            assertEquals(song.mode, t.key.mode)
            assertEquals(3, t.question.notes.map { it.startBeat }.toSet().size, "three chords sound, the fourth does not")
            assertTrue(t.prompt.audio!!.lengthSeconds > t.question.lengthSeconds + 1.5, "the cadence first")
            assertEquals(1, t.choices.count { SongOpenings.sameChord(it, t.answer) }, "the answer is on the pad once, as the song has it")
            assertEquals(t.choices.size, t.choices.map { it.root.semitones }.toSet().size, "one chord per root: the pick is a degree")
            assertEquals(level == 1, t.showsDegrees)
            assertNull(t.drone)
            assertEquals("next:L$level:" + t.songId, t.itemKey)
        }
    }

    private val seasoned = List(NextChordDriver.GENTLE_ATTEMPTS) { AttemptRecord(Variant.TIR_NEXT, 1, "next:L1:x", "I", "I", true, 3000, null, 0, null, 0) }

    private fun seasonedTasks(level: Int, n: Int): List<NextChordTask> {
        val d = NextChordDriver(book, KeyPicker(Random(5)))
        return List(n) { d.nextTask(DriverContext(Random(it), level, seasoned)) }
    }

    @Test
    fun `level 1 offers four chords and never the one that has just sounded, level 2 the whole pad`() {
        seasonedTasks(1, 120).forEach { t ->
            assertEquals(NextChordDriver.OPTIONS, t.choices.size)
            assertFalse(has(t.choices, t.shown.last()), "a change is a change")
        }
        tasks(2, 120).forEach { t -> assertTrue(t.choices.size >= 7, "every chord of the mode: ${t.choices.size}") }
    }

    @Test
    fun `the first rounds are gentle - two chords to choose from, a major song - and widen by themselves (O-62)`() {
        tasks(1, 120).forEach { t ->
            assertEquals(NextChordDriver.GENTLE_OPTIONS, t.choices.size)
            assertTrue(has(t.choices, t.answer), "the answer is one of the two")
            assertFalse(has(t.choices, t.shown.last()))
            assertTrue(!t.key.mode.hasMinorThird, "a major song")
        }
        assertTrue(seasonedTasks(1, 120).any { it.key.mode.hasMinorThird }, "after the first rounds minor songs come too")
        // level 2 was never gentle
        assertTrue(tasks(2, 60).all { it.choices.size >= 7 })
    }

    @Test
    fun `the pad holds the answer as the song has it`() {
        val v7 = ChordDegree.parse("V7")
        val minor = NextChordDriver.pad(Mode.MINOR, v7)
        assertEquals(7, minor.size)
        assertTrue(has(minor, v7), "the song's V⁷ stands where the minor's own v would")
        assertTrue(minor.all { it.quality.intervals.size >= 4 }, "a seventh among sevenths")
        val flat7 = ChordDegree.parse("bVII")
        val major = NextChordDriver.pad(Mode.MAJOR, flat7)
        assertEquals(8, major.size, "a chord from outside the scale is added")
        assertTrue(has(major, flat7))
        assertEquals(major.map { it.root.semitones }.sorted(), major.map { it.root.semitones }, "in the order of the scale")
        assertTrue(NextChordDriver.pad(Mode.MAJOR, ChordDegree.parse("IV")).all { it.quality.intervals.size == 3 })
    }

    @Test
    fun `the right chord hears the song go on, a wrong one hears how it really goes`() {
        val d = NextChordDriver(book, KeyPicker(Random(2)))
        repeat(40) {
            val t = d.nextTask(DriverContext(Random(it), 1 + it % 2, emptyList()))
            val right = d.evaluate(t, Answer.ChordChoice(t.answer))
            assertTrue(right.correct)
            assertNull(right.correction)
            val heardOn = right.confirmation!!.notes.map { n -> n.startBeat }.toSet().size
            assertTrue(heardOn >= 4, "the three, the answer, and on")
            val other = t.choices.first { c -> !SongOpenings.sameChord(c, t.answer) }
            val wrong = d.evaluate(t, Answer.ChordChoice(other))
            assertFalse(wrong.correct)
            assertEquals(4, wrong.correction!!.notes.map { n -> n.startBeat }.toSet().size, "rule 5: the three and the right fourth")
            assertNotNull(d.evaluate(t, Answer.Pass).correction)
            assertTrue(right.expectedText.isNotBlank())
        }
    }

    @Test
    fun `no song twice in a row, the songs in learning more often, and a miss comes back`() {
        val ids = tasks(1, 200).map { it.songId }
        assertTrue(ids.zipWithNext().none { (a, b) -> a == b })
        val mixed = book.take(20).mapIndexed { i, s -> s.copy(learning = i < 5) }
        val asked = tasks(2, 600, songs = mixed).map { it.songId }
        val learning = mixed.filter { it.learning }.map { it.id }.toSet()
        val share = asked.count { it in learning } / 600.0
        assertTrue(share > 0.33, "5 of 20 songs are in learning and get more than their quarter: $share")
    }

    @Test
    fun `a song needs three chords and a next one, and the game three such songs`() {
        val short = listOf(song("a", "| I | V | I |"), song("b", "| I | IV | V |"), song("c", "| I | I | I |"))
        assertFalse(NextChordDriver.playable(short), "two or three changes have no fourth")
        assertThrows(IllegalArgumentException::class.java) { NextChordDriver(short, KeyPicker(Random(1))) }
        val enough = listOf(song("d", "| I | V | vi | IV |"), song("e", "| I | IV | V | I |"), song("f", "| ii | V | I | vi |"))
        assertTrue(NextChordDriver.playable(enough))
        assertTrue(tasks(1, 30, songs = enough + short).all { it.songId in setOf("d", "e", "f") })
        assertTrue(NextChordDriver.playable(book))
    }
}
