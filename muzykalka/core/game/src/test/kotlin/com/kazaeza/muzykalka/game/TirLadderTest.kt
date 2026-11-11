package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyCatalog
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Mode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-27 (ADR-037): the Tir climbs slowly, and level 2 is learnt in two halves — major, then minor. */
class TirLadderTest {
    private val day = LevelRules.DAY_MS
    private val target = TirDriver.promotionTargetMs(2)

    private fun answers(n: Int, mode: Mode, onDay: Int, ok: Boolean = true) = List(n) {
        AttemptRecord(Variant.TIR_CLASSIC, 2, "tir:L2:5", "5", if (ok) "5" else "4", ok, 1_500, null, 0, "C ${mode.name}", onDay * day + it)
    }

    @Test
    fun `sixty right answers open a level only when they come from three days`() {
        val slow = LevelRules.SLOW
        assertFalse(LevelRules.shouldPromote(answers(60, Mode.MAJOR, 1), target, slow), "one evening")
        assertFalse(LevelRules.shouldPromote(answers(30, Mode.MAJOR, 1) + answers(30, Mode.MAJOR, 2), target, slow), "two days")
        val three = answers(20, Mode.MAJOR, 1) + answers(20, Mode.MAJOR, 2) + answers(20, Mode.MAJOR, 3)
        assertTrue(LevelRules.shouldPromote(three, target, slow))
        assertFalse(LevelRules.shouldPromote(three.drop(1), target, slow), "59 answers")
        assertTrue(LevelRules.shouldPromote(answers(30, Mode.MAJOR, 1), target), "the other modes keep the quick pace")
    }

    @Test
    fun `the day comes from the caller`() {
        val late = answers(20, Mode.MAJOR, 1) + answers(20, Mode.MAJOR, 2) + answers(20, Mode.MAJOR, 3)
        assertFalse(LevelRules.shouldPromote(late, target, LevelRules.SLOW) { 0 }, "all one day by the caller's clock")
    }

    @Test
    fun `a key's label gives its mode back`() {
        for (key in Mode.entries.flatMap { KeyCatalog.practical(it) }) assertEquals(key.mode, Key.modeOfLabel(key.toString()), key.toString())
        assertEquals(null, Key.modeOfLabel(null))
        assertEquals(null, Key.modeOfLabel("нечто"))
    }

    private fun minorShare(history: List<AttemptRecord>, seed: Int): Double {
        val driver = TirDriver(KeyPicker(Random(seed)))
        val random = Random(seed + 1)
        val keys = List(600) { driver.nextTask(DriverContext(random, 2, history)).key }
        // a key holds for a block of questions: count the blocks, not the questions
        val blocks = keys.windowed(2).filter { it[0] != it[1] }.map { it[1] } + keys.first()
        return blocks.count { it.mode == Mode.MINOR }.toDouble() / blocks.size
    }

    @Test
    fun `level 2 is major alone until major is learnt, then mostly minor, then the usual mix`() {
        assertEquals(0.0, minorShare(emptyList(), 1), "nothing learnt yet")
        assertEquals(0.0, minorShare(answers(30, Mode.MAJOR, 1, ok = false), 2), "major answered, but badly")
        val major = answers(30, Mode.MAJOR, 1)
        assertTrue(minorShare(major, 3) in 0.55..0.85, "major learnt: minor leads")
        assertTrue(minorShare(major + answers(30, Mode.MINOR, 2), 4) in 0.15..0.45, "both learnt: the usual mix")
    }

    @Test
    fun `level 3 opens only with both halves of level 2`() {
        val major = answers(30, Mode.MAJOR, 1)
        assertFalse(TirDriver.halvesAllow(2, major + answers(30, Mode.MAJOR, 2)))
        assertTrue(TirDriver.halvesAllow(2, major + answers(30, Mode.MINOR, 2)))
        assertTrue(TirDriver.halvesAllow(3, emptyList()), "the other levels have no halves")
        val h = TirDriver.halves(major + answers(12, Mode.MINOR, 2))
        assertTrue(h.major && !h.minor)
        assertEquals(12, h.minorRight)
    }
}
