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
import com.kazaeza.muzykalka.feature.taper.TaperScreen
import com.kazaeza.muzykalka.feature.taper.TaperSpec
import com.kazaeza.muzykalka.game.HintStep
import com.kazaeza.muzykalka.game.SelfGrade
import com.kazaeza.muzykalka.music.SongBook
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * O-13: a new song opens with its degrees on the screen (vanishing cues), there is no hint above
 * them (no letters), and «Как прошло?» closes the order — no «Что это было?» to type.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TaperFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aNewSongOpensOnTheDegreesAndTheGradeClosesTheOrder() {
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = FakeAudioOutput()
        }
        val id = runBlocking { graph.library.addFromBook(SongBook.byId("stand-by-me")!!) }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { TaperScreen(TaperSpec(onlyItemId = id), onExit = {}) }
            }
        }
        rule.waitUntil(10_000) { has("СЫГРАЙ") }

        // New song: the sheet is open from the start, and there is nothing above it.
        assertEquals(true, has("подсказка · ступени"))
        assertEquals(false, has("Подсказка:"))
        assertEquals(false, has("буквами"))

        rule.onNodeWithText("Сыграл").performScrollTo().performClick()
        rule.waitUntil(5_000) { has("Как прошло?") }
        rule.onNodeWithText("С ходу").performScrollTo().performClick()
        rule.waitUntil(5_000) { has("Снова попрошу") }
        assertEquals(false, has("Что это было?"))
        assertEquals(true, has("В следующий раз начнём с подсказки «${HintStep.FIRST_CHORD.ru}»"))

        // Saved at once, and the next order of this song starts one step lower.
        rule.waitUntil(5_000) { runBlocking { graph.practice.lastOrders()[id] != null } }
        assertEquals(HintStep.DEGREES to SelfGrade.CLEAN, runBlocking { graph.practice.lastOrders()[id] })
    }
}
