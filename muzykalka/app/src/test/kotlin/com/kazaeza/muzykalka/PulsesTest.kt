package com.kazaeza.muzykalka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.music.Overtones
import com.kazaeza.muzykalka.music.PulseKind
import com.kazaeza.muzykalka.music.SoundPulse
import com.kazaeza.muzykalka.ui.effect.PulseBoard
import com.kazaeza.muzykalka.ui.effect.rememberPulses
import com.kazaeza.muzykalka.ui.effect.strum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O-35: the board of plucks and the strummable string. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PulsesTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aFrozenBoardKeepsTheAgesItWasGiven() {
        val p = SoundPulse(PulseKind.HOME, 0L, 1f, 0, 0.5f)
        val board = PulseBoard.frozen(0.4f to p, 0.1f to p)
        val live = board.live()
        assertEquals(2, live.size)
        assertEquals(0.4f, live[0].age, 1e-4f)
        assertEquals(0.1f, live[1].age, 1e-4f)
        assertTrue(board.active)
        assertFalse(PulseBoard.frozen().active)
    }

    @Test
    fun aListenerHearsEveryPluckUntilItIsTakenAway() {
        val board = rememberedBoard()
        val heard = mutableListOf<SoundPulse>()
        val off = board.addListener { heard.add(it) }
        val a = SoundPulse(PulseKind.HOME, 0L, 1f, 0, 0.5f)
        val b = SoundPulse(PulseKind.TOUCH, 0L, 0.8f, null, 0.2f)
        board.add(a)
        board.add(b)
        assertEquals(listOf(a, b), heard)
        off()
        board.add(a)
        assertEquals(2, heard.size)
    }

    @Test
    fun aBoardKeepsOnlyTheLastFourAndForgetsTheDead() {
        val p = SoundPulse(PulseKind.REVEAL, 0L, 1f, 3, 0.31f)
        val board = PulseBoard.frozen(*Array(6) { 0.2f to p })
        assertEquals(PulseBoard.MAX, board.live().size)
        val old = PulseBoard.frozen(Overtones.LIFE + 1f to p)
        assertTrue(old.live().isEmpty())
    }

    @Test
    fun aFingerCrossingTheStringPlucksItAndASlowOneDoesNotRepeat() {
        val plucks = mutableListOf<SoundPulse>()
        var ticks = 0
        rule.setContent {
            Box(Modifier.testTag("strip").fillMaxWidth().height(72.dp).background(Color.Black).strum({ plucks += it }, { ticks++ }))
        }
        // down through the line (the bottom edge) at x = a quarter of the width, then back up: two plucks
        rule.onNodeWithTag("strip").performTouchInput {
            val x = width / 4f
            swipe(Offset(x, height * 0.6f), Offset(x, height + 20f), 80)
        }
        assertEquals(1, plucks.size)
        assertEquals(PulseKind.TOUCH, plucks[0].kind)
        assertEquals(0.25f, plucks[0].x, 0.02f)
        assertTrue(plucks[0].strength in 0.4f..1f)
        assertTrue(ticks >= 1)
        // a swipe that stays above the line is no pluck
        rule.onNodeWithTag("strip").performTouchInput { swipe(Offset(10f, 5f), Offset(width - 10f, height * 0.5f), 80) }
        assertEquals(1, plucks.size)
    }

    private fun rememberedBoard(): PulseBoard {
        lateinit var board: PulseBoard
        rule.setContent { board = rememberPulses(null) }
        rule.waitForIdle()
        return board
    }
}
