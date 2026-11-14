package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.SongBook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** F-08 «Формула вслух» (ADR-046): a song and its section are named, the degrees are recalled and self-graded. */
class FormulaDriverTest {
    private val book = SongBook.ENTRIES.map { GuessSong(SongBook.libraryId(it), it.song) }
    private val leaves = book.first { it.id == "book:autumn-leaves" }

    private fun tasks(songs: List<GuessSong>, n: Int, seed: Int = 1): List<FormulaTask> {
        val d = FormulaDriver(songs, KeyPicker(Random(seed)))
        return List(n) { d.nextTask(DriverContext(Random(seed * 100 + it), 1, emptyList())) }
    }

    @Test
    fun `a song and one of its sections are named, silently, in a key of chance`() {
        tasks(book, 80).forEach { t ->
            val song = book.first { it.id == t.songId }.song
            assertEquals(song.title, t.prompt.title)
            assertTrue(t.section in song.sections)
            assertNull(t.prompt.audio, "nothing sounds before the answer: the formula is recalled, not heard")
            assertEquals(song.mode, t.key.mode)
            assertEquals("formula:${t.songId}:${t.section.label}", t.itemKey)
            assertEquals(song.formString, t.form)
            assertTrue(t.sound.notes.isNotEmpty())
        }
    }

    @Test
    fun `the songs in learning are asked when there are any, and every section of them`() {
        val mixed = book.take(10) + leaves.copy(learning = true)
        val asked = tasks(mixed, 60)
        assertTrue(asked.all { it.songId == leaves.id }, "a song on the shelf was never learnt")
        assertEquals(leaves.song.sections.map { it.label }.toSet(), asked.map { it.section.label }.toSet())
        assertTrue(asked.zipWithNext().none { (a, b) -> a.itemKey == b.itemKey } || leaves.song.sections.size == 1, "not the same section twice in a row")
        assertTrue(tasks(book.take(10), 40).map { it.songId }.toSet().size > 3, "nothing in learning: the whole library")
    }

    @Test
    fun `the answer is a self-grade and the section sounds after it`() {
        val d = FormulaDriver(listOf(leaves), KeyPicker(Random(1)))
        val t = d.nextTask(DriverContext(Random(1), 1, emptyList()))
        for (grade in listOf(SelfGrade.CLEAN, SelfGrade.STUMBLED)) {
            val ok = d.evaluate(t, Answer.SelfReport(grade))
            assertTrue(ok.correct)
            assertEquals(t.sound, ok.confirmation, "the ear checks the memory")
            assertEquals(grade.name, ok.givenCode)
        }
        val failed = d.evaluate(t, Answer.SelfReport(SelfGrade.FAILED))
        assertFalse(failed.correct)
        assertEquals(t.sound, failed.correction, "rule 5")
        assertNotNull(d.evaluate(t, Answer.Pass).correction)
        assertTrue(failed.expectedText.contains("|"), "the formula in bars: ${failed.expectedText}")
        assertFalse(Regex("[A-H]").containsMatchIn(d.formula(t.section).replace("VII", "").replace("VI", "").replace("IV", "").replace("V", "").replace("I", "")), "degrees, never letters")
    }

    @Test
    fun `a section that was not recalled comes back more often`() {
        val d = FormulaDriver(listOf(leaves.copy(learning = true)), KeyPicker(Random(4)))
        val first = d.nextTask(DriverContext(Random(1), 1, emptyList()))
        val miss = AttemptRecord(Variant.FORMULA_ALOUD, 1, first.itemKey, "x", "FAILED", false, 9_000, null, 0, null, 0)
        val others = leaves.song.sections.filter { it.label != first.section.label }
            .map { AttemptRecord(Variant.FORMULA_ALOUD, 1, "formula:${leaves.id}:${it.label}", "x", "CLEAN", true, 9_000, null, 0, null, 0) }
        val history = List(4) { miss } + others
        val again = List(300) { d.nextTask(DriverContext(Random(it), 1, history)).itemKey }.count { it == first.itemKey }
        assertTrue(again > 300 / leaves.song.sections.size, "asked $again of 300 with ${leaves.song.sections.size} sections")
    }

    @Test
    fun `no song, no game`() {
        assertFalse(FormulaDriver.playable(emptyList()))
        assertThrows(IllegalArgumentException::class.java) { FormulaDriver(emptyList(), KeyPicker(Random(1))) }
        assertTrue(FormulaDriver.playable(listOf(leaves)))
    }
}
