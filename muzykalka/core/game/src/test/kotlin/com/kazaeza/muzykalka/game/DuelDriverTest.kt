package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Mode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** S-03 «Дуэль» (ADR-043): the two degrees taken for each other most often, until ten in a row are right. */
class DuelDriverTest {
    private fun driver(seed: Int = 1, pairs: List<Pair<Degree, Degree>> = emptyList()) =
        TirDriver(KeyPicker(Random(seed)), variant = Variant.TIR_DUEL, duelPairs = pairs)

    private val TirTask.dueling: TirDuel get() = requireNotNull(duel) { "a duel question carries its duel" }

    private fun record(task: TirTask, correct: Boolean) = AttemptRecord(
        Variant.TIR_DUEL, 1, task.itemKey, task.notes.single().degree.toString(), if (correct) task.notes.single().degree.toString() else "?",
        correct, 1_000, null, 0, task.key.toString(), 0,
    )

    /** Plays [answers] (true = right) and returns the tasks asked, the one after the last answer included. */
    private fun play(d: TirDriver, answers: List<Boolean>, seed: Int = 7): List<TirTask> {
        val history = ArrayList<AttemptRecord>()
        val tasks = ArrayList<TirTask>()
        tasks += d.nextTask(DriverContext(Random(seed), 1, history))
        answers.forEachIndexed { i, right ->
            history += record(tasks.last(), right)
            tasks += d.nextTask(DriverContext(Random(seed + i + 1), 1, history.toList()))
        }
        return tasks
    }

    @Test
    fun `only the two degrees of the pair are asked and can be answered, over the drone`() {
        val tasks = play(driver(), List(9) { true })
        val pair = listOf(Degree.THREE, Degree.FIVE)
        tasks.forEach { t ->
            assertEquals(TirKind.NOTE, t.kind)
            assertEquals(pair, t.noteChoices, "the pad is the pair")
            assertTrue(t.notes.single().degree in pair)
            assertNotNull(t.drone)
            assertEquals("tir:duel:3~5:" + t.notes.single().degree, t.itemKey)
            assertEquals(pair, t.dueling.pair)
            assertEquals(TirDriver.DUEL_GOAL, t.dueling.goal)
            assertNull(t.review)
        }
        assertEquals((0..9).toList(), tasks.map { it.dueling.streak }, "the count before each question")
        assertTrue(tasks.map { it.notes.single().degree }.toSet().size == 2, "both degrees come up")
        assertNotNull(tasks.first().prompt.audio, "a new key opens with its scale")
    }

    @Test
    fun `ten in a row beat the pair and the next one comes, a miss starts the count again`() {
        val answers = List(4) { true } + false + List(10) { true } + true
        val tasks = play(driver(), answers)
        assertEquals(listOf(0, 1, 2, 3, 4, 0), tasks.take(6).map { it.dueling.streak }, "the miss sends the count to zero")
        val afterWin = tasks[15]
        assertEquals(listOf(Degree.THREE, Degree.FIVE), afterWin.dueling.beaten, "ten in a row: the pair is beaten")
        assertEquals(listOf(Degree.ONE, Degree.FIVE), afterWin.dueling.pair, "and the next duel begins")
        assertEquals(0, afterWin.dueling.streak)
        assertEquals(1, afterWin.dueling.wins)
        assertNull(tasks[14].dueling.beaten)
        val next = tasks[16]
        assertNull(next.dueling.beaten, "the win is announced once")
        assertEquals(1, next.dueling.streak)
        assertEquals("tir:duel:1~5:", next.itemKey.substringBeforeLast(':') + ":")
    }

    @Test
    fun `the round is not ended by a win and the pairs go round`() {
        val d = driver()
        val all = play(d, List(TirDriver.DUEL_GOAL * TirDriver.DUEL_DEFAULTS.size) { true })
        val pairs = all.map { it.dueling.pair }.distinct()
        assertEquals(TirDriver.DUEL_DEFAULTS.size, pairs.size, "every pair has its duel")
        assertEquals(pairs.first(), all.last().dueling.pair, "after the last pair the first comes again")
        assertEquals(TirDriver.DUEL_DEFAULTS.size, all.last().dueling.wins)
        assertEquals(all.size, all.count { it.kind == TirKind.NOTE }, "a win is not the end: the questions go on")
    }

    @Test
    fun `the user's own confusions come first, each pair once, whichever way round`() {
        val own = listOf(Degree.parse("b7") to Degree.parse("b6"), Degree.FIVE to Degree.THREE, Degree.parse("4") to Degree.parse("4"))
        val tasks = play(driver(pairs = own), List(20) { true })
        assertEquals(listOf(Degree.parse("b6"), Degree.parse("b7")), tasks.first().dueling.pair, "low to high")
        assertEquals(listOf(Degree.THREE, Degree.FIVE), tasks[10].dueling.pair, "then the second of the user's own")
        assertEquals(listOf(Degree.ONE, Degree.FIVE), tasks[20].dueling.pair, "3–5 is not played twice: the defaults go on from 1–5")
    }

    @Test
    fun `a pair is heard in the mode that has both degrees`() {
        repeat(8) { seed ->
            val minor = driver(seed, listOf(Degree.parse("b3") to Degree.FIVE)).nextTask(DriverContext(Random(seed), 1, emptyList()))
            assertEquals(Mode.MINOR, minor.key.mode)
            val split = driver(seed, listOf(Degree.parse("b3") to Degree.THREE)).nextTask(DriverContext(Random(seed), 1, emptyList()))
            assertEquals(Mode.MAJOR, split.key.mode, "neither mode has both: major, the drone is neutral anyway")
            val major = driver(seed, listOf(Degree.THREE, Degree.parse("6")).let { listOf(it[0] to it[1]) }).nextTask(DriverContext(Random(seed), 1, emptyList()))
            assertEquals(Mode.MAJOR, major.key.mode)
        }
        val both = (0 until 60).map { driver(it, listOf(Degree.ONE to Degree.FIVE)).nextTask(DriverContext(Random(it), 1, emptyList())).key.mode }.toSet()
        assertEquals(setOf(Mode.MAJOR, Mode.MINOR), both, "1 and 5 live in both")
    }

    @Test
    fun `a duel is judged like the Tir and stays out of its statistics`() {
        val d = driver()
        val t = d.nextTask(DriverContext(Random(1), 1, emptyList()))
        val asked = t.notes.single().degree
        val other = t.noteChoices.first { it != asked }
        assertTrue(d.evaluate(t, Answer.DegreeChoice(asked)).correct)
        val wrong = d.evaluate(t, Answer.DegreeChoice(other))
        assertFalse(wrong.correct)
        assertNotNull(wrong.correction, "rule 5")
        assertNotNull(d.evaluate(t, Answer.Pass).correction)
        assertNull(TirDriver.reviewKey(t.itemKey), "not a spaced review item")
        val attempts = listOf(record(t, false).copy(expected = asked.toString(), given = other.toString()))
        assertEquals(0, Metrics.confusion(attempts).counts.sumOf { it.sum() }, "the duel does not feed the matrix it is built from")
    }

    @Test
    fun `the confused pairs are ranked by both directions together and a single slip does not count`() {
        fun miss(expected: String, given: String) = AttemptRecord(Variant.TIR_CLASSIC, 2, "tir:L2:$expected", expected, given, false, 1_000, null, 0, "C MAJOR", 0)
        val attempts = List(3) { miss("3", "5") } + List(2) { miss("5", "3") } + List(4) { miss("2", "4") } + miss("6", "7") +
            AttemptRecord(Variant.TIR_CLASSIC, 2, "tir:L2:1", "1", "1", true, 900, null, 0, "C MAJOR", 0)
        val pairs = Metrics.confusedPairs(Metrics.confusion(attempts))
        assertEquals(listOf(Metrics.ConfusedPair("3", "5", 5), Metrics.ConfusedPair("2", "4", 4)), pairs)
        assertTrue(Metrics.confusedPairs(Metrics.confusion(emptyList())).isEmpty())
    }
}
