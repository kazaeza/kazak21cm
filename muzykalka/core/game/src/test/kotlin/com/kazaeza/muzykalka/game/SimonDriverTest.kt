package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Mode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-46 «Саймон» (ADR-045): a chain of notes over the drone grows by one with every right answer. */
class SimonDriverTest {
    private fun driver(seed: Int = 1) = TirDriver(KeyPicker(Random(seed)), variant = Variant.TIR_SIMON)

    private val TirTask.chain: TirSimon get() = requireNotNull(simon) { "a Simon question carries its chain" }

    private fun record(task: TirTask, correct: Boolean) =
        AttemptRecord(Variant.TIR_SIMON, task.level, task.itemKey, "x", if (correct) "x" else "?", correct, 1_000, null, 0, task.key.toString(), 0)

    private fun play(d: TirDriver, answers: List<Boolean>, level: Int = 1, seed: Int = 5): List<TirTask> {
        val history = ArrayList<AttemptRecord>()
        val tasks = ArrayList<TirTask>()
        tasks += d.nextTask(DriverContext(Random(seed), level, history))
        answers.forEachIndexed { i, right ->
            history += record(tasks.last(), right)
            tasks += d.nextTask(DriverContext(Random(seed + i + 1), level, history.toList()))
        }
        return tasks
    }

    @Test
    fun `a right answer makes the next question the same chain plus one note`() {
        val tasks = play(driver(), List(5) { true })
        assertEquals((2..7).toList(), tasks.map { it.notes.size })
        tasks.zipWithNext().forEach { (a, b) -> assertEquals(a.notes, b.notes.dropLast(1), "the chain is kept, a note is added") }
        tasks.forEach { t ->
            assertEquals(TirKind.MELODY, t.kind)
            assertEquals(t.notes.size, t.answerLength)
            assertEquals("tir:simon:L1:len${t.notes.size}", t.itemKey)
            assertEquals(t.notes.size, t.chain.length)
            assertNotNull(t.drone)
            assertTrue(t.notes.zipWithNext().none { (x, y) -> x == y }, "a tune moves")
        }
        assertEquals(listOf(0, 2, 3, 4, 5, 6), tasks.map { it.chain.best }, "the longest chain answered right so far")
        assertTrue(tasks.drop(1).all { it.chain.grew })
        assertFalse(tasks.first().chain.grew)
    }

    @Test
    fun `one key for the whole round, its scale only at the start`() {
        val tasks = play(driver(3), listOf(true, true, false, true, false, true))
        assertEquals(1, tasks.map { it.key }.toSet().size)
        val scale = tasks.first().prompt.audio!!.notes.size - tasks.first().question.notes.size
        assertTrue(scale > 0, "the new key opens with its scale")
        tasks.drop(1).forEach { assertEquals(it.question, it.prompt.audio, "then only the chain") }
    }

    @Test
    fun `a wrong answer breaks the chain and a new one begins`() {
        val tasks = play(driver(), listOf(true, true, false, true))
        assertEquals(listOf(2, 3, 4, 2, 3), tasks.map { it.notes.size })
        assertTrue(tasks[3].chain.broke)
        assertFalse(tasks[3].chain.grew)
        assertEquals(3, tasks[3].chain.best, "the best chain is remembered")
        assertEquals(tasks[3].notes, tasks[4].notes.dropLast(1))
    }

    @Test
    fun `a chain played through to the end is won and a new one begins`() {
        val toTheEnd = TirDriver.SIMON_MAX - TirDriver.SIMON_START + 1
        val tasks = play(driver(), List(toTheEnd) { true })
        assertEquals(TirDriver.SIMON_MAX, tasks[toTheEnd - 1].notes.size)
        val after = tasks.last()
        assertEquals(TirDriver.SIMON_START, after.notes.size)
        assertEquals(1, after.chain.wins)
        assertEquals(TirDriver.SIMON_MAX, after.chain.best)
        assertFalse(after.chain.broke)
    }

    @Test
    fun `level 1 is made of five degrees in major, level 2 of the whole scale in either mode`() {
        repeat(10) { seed ->
            val l1 = play(driver(seed), List(6) { true }, level = 1, seed = seed)
            assertEquals(Mode.MAJOR, l1.first().key.mode)
            assertEquals(listOf("1", "2", "3", "5", "6"), l1.first().noteChoices.map { it.toString() })
            l1.forEach { t -> assertTrue(t.notes.all { it.degree in t.noteChoices }) }
        }
        val l2 = (0 until 40).map { play(driver(it), emptyList(), level = 2, seed = it).first() }
        assertTrue(l2.all { it.noteChoices.size == 7 })
        assertEquals(setOf(Mode.MAJOR, Mode.MINOR), l2.map { it.key.mode }.toSet())
        assertEquals(listOf("1", "2", "♭3", "5", "♭7"), TirDriver.simonPad(Mode.MINOR, 1).map { it.toString() }, "the minor's five")
    }

    @Test
    fun `the chain is judged whole and stays out of the Tir's statistics`() {
        val d = driver()
        val t = play(d, listOf(true, true)).last()
        assertTrue(d.evaluate(t, Answer.Formula(t.notes.map { DegreeNote(it.degree) })).correct, "octaves do not matter")
        val swapped = t.notes.map { DegreeNote(it.degree) }.reversed()
        val wrong = d.evaluate(t, Answer.Formula(swapped))
        assertEquals(swapped == t.notes.map { DegreeNote(it.degree) }, wrong.correct)
        assertFalse(d.evaluate(t, Answer.Formula(t.notes.dropLast(1))).correct)
        assertNotNull(d.evaluate(t, Answer.Pass).correction, "rule 5")
        assertNull(TirDriver.reviewKey(t.itemKey))
        assertEquals(0, Metrics.confusion(listOf(record(t, false))).counts.sumOf { it.sum() })
    }

    @Test
    fun `the tonic sounds alone before the chain on the first two levels, not on the third`() {
        val l1 = play(driver(2), listOf(true, true), level = 1).last()
        val l3 = play(driver(2), listOf(true, true), level = 3).last()
        assertEquals(l1.notes.size + 1, l1.question.notes.size, "the tonic and the chain")
        assertEquals(l3.notes.size, l3.question.notes.size, "no crutch on level 3")
        val home = l1.question.notes.first()
        assertEquals(l1.drone!!.first().pitchClass, home.note!!.pitchClass, "the first sound is the tonic")
        assertTrue(l1.question.notes.drop(1).all { it.startBeat >= home.endBeat }, "the chain begins after the tonic has sounded")
    }

    @Test
    fun `the tempo goes up with the level and the tune leaps from level 2`() {
        assertTrue(TirDriver.simonBeats(1) > TirDriver.simonBeats(2) && TirDriver.simonBeats(2) > TirDriver.simonBeats(3))
        assertTrue(TirDriver.simonMoves(1).all { kotlin.math.abs(it) <= 2 }, "level 1 walks")
        assertTrue(TirDriver.simonMoves(2).any { kotlin.math.abs(it) >= 3 }, "level 2 leaps")
        assertEquals(3, Levels.TIR_SIMON.size)
        val chain = play(driver(4), List(6) { true }, level = 2, seed = 9).last()
        val places = chain.notes.map { chain.noteChoices.indexOf(it.degree) + 7 * it.octave }
        assertTrue(places.zipWithNext().any { (a, b) -> kotlin.math.abs(a - b) >= 3 }, "a leap in $places")
    }

    @Test
    fun `a miss is answered with the chain again, slowly (rule 5)`() {
        val d = driver(6)
        val t = play(d, listOf(true, true)).last()
        val wrong = d.evaluate(t, Answer.Pass)
        val fix = requireNotNull(wrong.correction)
        val gaps = fix.notes.map { it.startBeat }.sorted().zipWithNext { a, b -> b - a }.filter { it > 0.0 }
        assertTrue(gaps.max() >= TirDriver.SIMON_SLOW_BEATS - 1e-9, "a note at a time: $gaps")
        assertTrue(fix.notes.size > t.notes.size, "the chain and the way home")
    }
}
