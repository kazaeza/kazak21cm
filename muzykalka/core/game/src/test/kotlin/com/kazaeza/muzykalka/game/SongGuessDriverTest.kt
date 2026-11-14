package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.ProgressionText
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.music.SongBook
import com.kazaeza.muzykalka.music.SongOpenings
import com.kazaeza.muzykalka.music.Voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-43 «Чья гармония?» (ADR-042): the opening of a library song is played, four songs are offered. */
class SongGuessDriverTest {
    private val book = SongBook.ENTRIES.map { GuessSong(SongBook.libraryId(it), it.song) }

    private fun driver(seed: Int = 1, songs: List<GuessSong> = book) = SongGuessDriver(songs, KeyPicker(Random(seed)))

    private fun song(id: String, progression: String, mode: Mode = Mode.MAJOR, learning: Boolean = false) = GuessSong(
        id,
        Song(id = id, title = "Песня $id", mode = mode, sections = ProgressionText.parseSections("A: $progression"), form = listOf("A")),
        learning,
    )

    private fun tasks(level: Int, n: Int, seed: Int = 1, songs: List<GuessSong> = book): List<SongGuessTask> {
        val d = driver(seed, songs)
        return List(n) { d.nextTask(DriverContext(Random(seed * 1000 + it), level, emptyList())) }
    }

    @Test
    fun `four songs are offered, the asked one among them, and no two open alike`() {
        for (level in 1..3) tasks(level, 60).forEach { t ->
            assertEquals(SongGuessDriver.OPTIONS, t.options.size)
            assertTrue(t.answer in t.options)
            assertEquals(4, t.options.map { it.id }.toSet().size, "four different songs")
            assertEquals(4, t.options.map { it.shape }.toSet().size, "the answer can be heard: ${t.options.map { it.signature }}")
            assertTrue(t.answer.opening.size in SongGuessDriver.MIN_CHANGES..SongGuessDriver.OPENING)
            assertEquals(level < 3, t.showsDegrees)
            assertEquals(level, t.level)
            assertNull(t.drone, "chords have the cadence for a home, not a drone")
        }
    }

    @Test
    fun `the cadence comes first, then the opening of the song in the key of the task`() {
        tasks(1, 30).forEach { t ->
            val audio = t.prompt.audio!!
            assertTrue(audio.lengthSeconds > t.question.lengthSeconds + 1.5, "the cadence sets the key before the question")
            assertEquals(t.question, t.replay, "«once more» plays the question, not the cadence")
            assertEquals(t.key, t.prompt.key)
            val firstChord = t.question.notes.filter { it.voice == Voice.PIANO && it.startBeat == 0.0 }.map { it.note!!.pitchClass.value }.toSet()
            val first = t.answer.opening.first()
            assertEquals(first.quality.intervals.map { (t.key.pitchClassOf(first.root) + it).value }.toSet(), firstChord)
            val starts = t.question.notes.map { it.startBeat }.toSet()
            assertEquals(t.answer.opening.size, starts.size, "one chord per change")
            // the question sounds the same inside the prompt as on its own: its length in seconds is kept
            val tail = audio.lengthSeconds - (4 * 60.0 / SongGuessDriver.CADENCE_TEMPO) - (1.5 * 60.0 / SongGuessDriver.CADENCE_TEMPO)
            assertEquals(t.question.lengthSeconds, tail, 0.05) // a chord rings for 0.95 of its beats, hence the slack
        }
    }

    @Test
    fun `level 1 offers songs that start differently and level 2 the closest relatives`() {
        fun meanPrefix(level: Int) = tasks(level, 200).flatMap { t ->
            t.options.filter { it != t.answer }.map { SongOpenings.commonPrefix(it.opening, t.answer.opening) }
        }.average()
        val easy = meanPrefix(1)
        val hard = meanPrefix(2)
        assertTrue(easy < 0.2, "level 1: the first chord already tells ($easy)")
        assertTrue(hard > 1.0, "level 2: the start is the same ($hard)")
        // wherever the book has a relative of the answer — the same first chord, the same kind of mode, another loop —
        // level 2 puts one on the screen
        tasks(2, 120).forEach { t ->
            val minorish = book.first { it.id == t.answer.id }.song.mode.hasMinorThird
            val relatives = book.filter { it.song.mode.hasMinorThird == minorish }
                .mapNotNull { s -> SongOpenings.changes(s.song, 4).takeIf { it.size >= 3 } }
                .filter { SongOpenings.commonPrefix(it, t.answer.opening) >= 1 }
                .map(SongOpenings::shape).toSet()
            if (relatives.size > 1) {
                assertTrue(t.options.count { SongOpenings.commonPrefix(it.opening, t.answer.opening) >= 1 } >= 2, "an option starts as ${t.answer.signature} does")
            }
        }
    }

    @Test
    fun `the right song goes on, a wrong one hears the opening again and comes home`() {
        val d = driver(3)
        repeat(40) {
            val t = d.nextTask(DriverContext(Random(it), 1 + it % 3, emptyList()))
            val right = d.evaluate(t, Answer.SongChoice(t.answer.id))
            assertTrue(right.correct)
            assertNull(right.correction)
            assertNotNull(right.confirmation, "the song goes on")
            assertEquals(t.answer.title, right.expectedText)
            assertEquals(t.answer.signature, right.expectedCode)

            val other = t.options.first { it != t.answer }
            val wrong = d.evaluate(t, Answer.SongChoice(other.id))
            assertFalse(wrong.correct)
            assertEquals(other.title, wrong.givenText)
            assertEquals(other.signature, wrong.givenCode)
            val heard = wrong.correction!!
            assertEquals(t.answer.opening.size + 1, heard.notes.map { n -> n.startBeat }.toSet().size, "rule 5: the opening and its home")
            assertNull(wrong.confirmation)

            val pass = d.evaluate(t, Answer.Pass)
            assertFalse(pass.correct)
            assertNotNull(pass.correction)
            assertFalse(d.evaluate(t, Answer.SongChoice("not on the screen")).correct)
        }
    }

    @Test
    fun `the same opening is not asked twice in a row and every opening gets its turn`() {
        val asked = tasks(1, 300).map { it.answer.shape }
        assertTrue(asked.zipWithNext().none { (a, b) -> a == b })
        val all = book.mapNotNull { s -> SongOpenings.changes(s.song, 4).takeIf { it.size >= 3 }?.let(SongOpenings::shape) }.toSet()
        assertTrue(asked.toSet().size > all.size * 0.8, "asked ${asked.toSet().size} of ${all.size} openings")
    }

    @Test
    fun `an opening that is missed comes back more often`() {
        val d = driver(5)
        val first = d.nextTask(DriverContext(Random(1), 1, emptyList()))
        val miss = AttemptRecord(Variant.TIR_SONG, 1, first.itemKey, first.answer.signature, "?", false, 9_000, null, 0, null, 0)
        val history = List(6) { miss }
        val again = List(400) { d.nextTask(DriverContext(Random(it), 1, history)).itemKey }.count { it == first.itemKey }
        val fresh = driver(5).let { f -> List(400) { f.nextTask(DriverContext(Random(it), 1, emptyList())).itemKey }.count { it == first.itemKey } }
        assertTrue(again > fresh, "missed: $again, never asked: $fresh")
    }

    @Test
    fun `level 3 asks the songs in learning when they make a quiz by themselves`() {
        val mine = listOf(
            song("m1", "| I | V | vi | IV |", learning = true),
            song("m2", "| vi | IV | I | V |", learning = true),
            song("m3", "| I | IV | V | I |", learning = true),
            song("m4", "| ii | V | I | vi |", learning = true),
        )
        val shelf = listOf(song("s1", "| I | vi | IV | V |"), song("s2", "| i | bVII | bVI | V |", Mode.MINOR), song("s3", "| IV | I | V | vi |"))
        tasks(3, 80, songs = mine + shelf).forEach { t ->
            assertTrue(t.answer.id.startsWith("m"), "asked: ${t.answer.id}")
            assertEquals("song:L3:" + t.answer.id, t.itemKey, "on level 3 the item is the song")
        }
        // too few in learning: the whole library is asked
        val few = tasks(3, 80, songs = mine.take(2) + shelf)
        assertTrue(few.any { it.answer.id.startsWith("s") })
        // levels 1–2 ask everything, and their item is the progression
        val l1 = tasks(1, 80, songs = mine + shelf)
        assertTrue(l1.any { it.answer.id.startsWith("s") })
        assertTrue(l1.all { it.itemKey == "song:L1:" + it.answer.signature })
    }

    @Test
    fun `songs that open alike are one question, and a quiz needs four different openings`() {
        // the same loop four times: held chords, a slash bass, and the relative minor's way of writing it
        val twins = listOf(
            song("a", "| vi | IV | I | V |"), song("b", "| vi | vi | IV | IV | I | V |"), song("c", "| vi/3 | IV | I | V |"),
            song("g", "| i | bVI | bIII | bVII |", Mode.MINOR),
        )
        assertFalse(SongGuessDriver.playable(twins))
        assertThrows(IllegalArgumentException::class.java) { driver(songs = twins) }
        val short = listOf(song("one", "| I | I |"), song("two", "| I | V | I | V |"))
        assertFalse(SongGuessDriver.playable(short), "one or two changes are not an opening")
        val enough = twins + listOf(song("d", "| I | V | vi | IV |"), song("e", "| I | IV | V | I |"), song("f", "| ii | V | I | vi |"))
        assertTrue(SongGuessDriver.playable(enough))
        tasks(1, 60, songs = enough).forEach { t ->
            assertEquals(4, t.options.map { it.shape }.toSet().size)
            assertTrue(t.options.count { it.id in setOf("a", "b", "c", "g") } <= 1, "twins never meet on one screen")
        }
        assertTrue(SongGuessDriver.playable(book))
    }

    @Test
    fun `the other three songs are of the asked song's kind of mode where the library has them`() {
        var kin = 0
        var all = 0
        for (level in 1..3) tasks(level, 100).forEach { t ->
            val minorish = book.first { it.id == t.answer.id }.song.mode.hasMinorThird
            t.options.filter { it != t.answer }.forEach { o ->
                all++
                if (book.first { it.id == o.id }.song.mode.hasMinorThird == minorish) kin++
            }
        }
        assertEquals(all, kin, "the book has enough songs of either kind: «it is minor, so not this one» never answers")
        // a library with one minor song: it is asked all the same, with major songs around it
        val lone = listOf(song("m", "| i | bVII | bVI | V |", Mode.MINOR), song("d", "| I | V | vi | IV |"), song("e", "| I | IV | V | I |"), song("f", "| ii | V | I | vi |"))
        assertTrue(tasks(1, 60, songs = lone).any { it.answer.id == "m" })
    }

    @Test
    fun `the key follows the mode of the song and changes from question to question`() {
        val ts = tasks(1, 60)
        ts.forEach { t -> assertEquals(book.first { it.id == t.answer.id }.song.mode, t.key.mode) }
        assertTrue(ts.map { it.key.tonic }.toSet().size >= 8, "rule 2: the key is random")
        assertNotEquals(ts[0].key.tonic, ts[1].key.tonic)
    }

    @Test
    fun `the same seed asks the same questions`() {
        assertEquals(tasks(2, 20, seed = 9).map { it.answer.id to it.options.map { o -> o.id } }, tasks(2, 20, seed = 9).map { it.answer.id to it.options.map { o -> o.id } })
    }
}
