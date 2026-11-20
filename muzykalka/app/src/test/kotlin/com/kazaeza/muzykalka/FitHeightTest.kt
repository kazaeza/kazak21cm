package com.kazaeza.muzykalka

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.ui.component.fitHeight
import com.kazaeza.muzykalka.ui.component.fitScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O-79: an answer taller than its card is drawn smaller to stand in it, never smaller than 0.6, never bigger than itself. */
/** The height of a box in dp. */
private fun DpRect.h() = (bottom - top).value

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class FitHeightTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theScaleIsOneWhereTheBlockFitsAndTheRatioWhereItDoesNot() {
        assertEquals(1f, fitScale(100, 200, 0.6f), 0f)
        assertEquals(1f, fitScale(200, 200, 0.6f), 0f)
        assertEquals(1f, fitScale(0, 0, 0.6f), 0f)
        assertEquals(2f / 3f, fitScale(300, 200, 0.6f), 1e-6f)
        assertEquals("not smaller than the floor: the rest is left to scrolling", 0.6f, fitScale(1000, 200, 0.6f), 0f)
    }

    private fun blockInRoom(content: Int, room: Int, scrolls: Boolean = false) {
        rule.setContent {
            val roomPx = with(LocalDensity.current) { room.dp.roundToPx() }
            // the card of the game scrolls, in a viewport taller than the room it hands to the block: the content is
            // measured without a limit of height, and what is taller than the room is not cut by a fixed parent
            val viewport = if (scrolls) 600.dp else room.dp
            Box(Modifier.width(200.dp).height(viewport).then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                Box(Modifier.testTag("fit").fillMaxWidth().fitHeight(roomPx)) {
                    Box(Modifier.testTag("block").fillMaxWidth().height(content.dp))
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun aBlockThatFitsIsLeftAlone() {
        blockInRoom(content = 100, room = 150)
        assertEquals(100f, rule.onNodeWithTag("fit").getBoundsInRoot().h(), 1f)
        assertEquals(100f, rule.onNodeWithTag("block").getBoundsInRoot().h(), 1f)
    }

    @Test
    fun aTallBlockIsDrawnSmallerAndStandsInTheRoom() {
        blockInRoom(content = 200, room = 150)
        val fit = rule.onNodeWithTag("fit").getBoundsInRoot()
        assertEquals("the slot is the room", 150f, fit.h(), 1f)
        val block = rule.onNodeWithTag("block").getBoundsInRoot()
        assertTrue("and what is drawn lies in it: ${block.top}..${block.bottom}", block.top.value >= -1f && block.bottom.value <= 151f)
    }

    @Test
    fun aBlockBeyondTheFloorTakesItsScaledHeightAndLeavesTheRestToScrolling() {
        blockInRoom(content = 400, room = 150, scrolls = true)
        // 0.6 of 400 = 240 > 150: the slot is as tall as the shrunk block, the card scrolls
        assertEquals(240f, rule.onNodeWithTag("fit").getBoundsInRoot().h(), 2f)
    }

    @Test
    fun theBlockGrowsBackWhenItsContentGetsShorter() {
        var tall by mutableStateOf(true)
        rule.setContent {
            val roomPx = with(LocalDensity.current) { 150.dp.roundToPx() }
            Box(Modifier.width(200.dp).height(150.dp)) {
                Box(Modifier.testTag("fit").fillMaxWidth().fitHeight(roomPx)) {
                    Box(Modifier.fillMaxWidth().height(if (tall) 200.dp else 80.dp))
                }
            }
        }
        rule.waitForIdle()
        assertEquals(150f, rule.onNodeWithTag("fit").getBoundsInRoot().h(), 1f)
        tall = false
        rule.waitForIdle()
        assertEquals(80f, rule.onNodeWithTag("fit").getBoundsInRoot().h(), 1f)
    }
}
