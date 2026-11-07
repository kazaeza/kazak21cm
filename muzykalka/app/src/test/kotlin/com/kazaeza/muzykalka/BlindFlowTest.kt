package com.kazaeza.muzykalka

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.feature.taper.BLIND_TITLE
import com.kazaeza.muzykalka.feature.taper.BlindScreen
import com.kazaeza.muzykalka.game.BlindSession
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O-10: the name stays hidden while the song is played and shows up after the self-grade. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BlindFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theNameIsRevealedOnlyAfterTheGrade() {
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = FakeAudioOutput()
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { BlindScreen(onExit = {}) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(BLIND_TITLE).fetchSemanticsNodes().isNotEmpty() }

        // Playing: no title or artist of any song of the shelf is on the screen.
        val names = BlindSession.pool().flatMap { listOfNotNull(it.song.title, it.song.artist) }.toSet()
        names.forEach { n -> assertEquals("«$n» is on the screen before the reveal", 0, rule.onAllNodesWithText(n).fetchSemanticsNodes().size) }
        rule.onNodeWithText("Сыграл").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Как прошло?", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        names.forEach { n -> assertEquals("«$n» is on the screen before the reveal", 0, rule.onAllNodesWithText(n).fetchSemanticsNodes().size) }

        rule.onNodeWithText("С ходу").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("ЭТО БЫЛА", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        val shown = names.count { n -> rule.onAllNodesWithText(n).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("the song is named after the grade", shown >= 1)
        assertEquals(0, rule.onAllNodesWithText(BLIND_TITLE).fetchSemanticsNodes().size)

        // The attempt reached the statistics (and only there: the library stays empty).
        rule.waitUntil(5_000) { runBlocking { graph.practice.recentAttempts(Variant.TAPER_BLIND, 10).isNotEmpty() } }
        val row = runBlocking { graph.practice.recentAttempts(Variant.TAPER_BLIND, 10) }.single()
        assertTrue(row.itemKey.startsWith("blind:"))
        assertTrue(row.correct)
        assertEquals(0, runBlocking { graph.library.allOrderable().size })
    }
}
