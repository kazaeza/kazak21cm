package com.kazaeza.muzykalka

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** ADR-019: inside a set the back gesture pauses and asks — it never skips to the next game. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PauseFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun backPausesTheRoundAndOnlyTheOverlayMovesOn() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        var exited = false
        var next: Int? = null
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme {
                    GameScreen(
                        RoundSpec(Variant.TIR_CLASSIC, level = 1),
                        onExit = { exited = true },
                        onNext = { next = it },
                        exitLabel = "Выйти из сета",
                    )
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Какая ступень?").fetchSemanticsNodes().isNotEmpty() }

        back()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Продолжить").fetchSemanticsNodes().isNotEmpty() }
        assertFalse("back must not leave the game", exited)
        assertEquals("back must not skip to the next game", null, next)
        rule.onNodeWithText("Следующее упражнение").assertExists()
        rule.onNodeWithText("Выйти из сета").assertExists()

        // Back again = continue: the overlay closes and the question sounds again.
        val playedBefore = audio.played.size
        back()
        rule.onAllNodesWithText("Продолжить").assertCountEquals(0)
        rule.waitUntil(8_000) {
            rule.onAllNodesWithText("Какая ступень?").fetchSemanticsNodes() // keeps the main looper (and its delays) moving
            audio.played.size > playedBefore
        }
        assertFalse(exited)

        // ⏸ in the top bar opens the same question; only «Следующее упражнение» moves on.
        rule.onAllNodesWithText("⏸").onFirst().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Следующее упражнение").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Следующее упражнение").performClick()
        rule.waitForIdle()
        assertEquals(0, next)
        assertTrue(!exited)
    }
}
