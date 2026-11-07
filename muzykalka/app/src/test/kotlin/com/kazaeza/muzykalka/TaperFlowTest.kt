package com.kazaeza.muzykalka

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
    /**
     * O-17 (ADR-031): a chord of the sheet sounds when tapped, gets its words, and costs nothing.
     * Native graphics + a capture: the caption is really DRAWN (a bad brush only fails at draw time).
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aTappedChordSoundsInTheKeyOfTheOrderAndIsNamedAndTheLadderStaysPut() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        val id = runBlocking { graph.library.addFromBook(SongBook.byId("stand-by-me")!!) } // I I vi vi IV V I I
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { TaperScreen(TaperSpec(onlyItemId = id), onExit = {}) }
            }
        }
        rule.waitUntil(10_000) { has("СЫГРАЙ") }
        assertEquals(true, has("Нажми на аккорд"))
        assertEquals(0, audio.played.size)

        rule.onAllNodes(hasClickLabel("Послушать аккорд")).assertCountEquals(8)
        rule.onAllNodesWithText("V", substring = false)[0].performScrollTo().performClick()
        rule.waitUntil(5_000) { has("V — доминанта") }
        rule.onRoot().captureToImage()
        assertEquals(1, audio.played.size)
        val dominant = audio.played.single()
        assertTrue(dominant.notes.all { it.voice == Voice.GUITAR })
        assertEquals(listOf(0, 4, 7), dominant.notes.map { it.note!!.value - dominant.notes.first().note!!.value })

        rule.onAllNodesWithText("vi", substring = false)[0].performScrollTo().performClick()
        rule.waitUntil(5_000) { has("vi — параллельный минор") }
        assertEquals(2, audio.played.size)
        assertEquals(false, has("V — доминанта"))
        val relative = audio.played.last()
        // vi is a tone and a half... two semitones above V's root, in the same key
        assertEquals(2, Math.floorMod(relative.notes.first().note!!.value - dominant.notes.first().note!!.value, 12))

        // Hearing is not a hint: the order is graded as before and the ladder does not move.
        rule.onNodeWithText("Сыграл").performScrollTo().performClick()
        rule.waitUntil(5_000) { has("Как прошло?") }
        assertEquals(false, has("параллельный минор"))
        rule.onNodeWithText("С ходу").performScrollTo().performClick()
        rule.waitUntil(5_000) { has("Снова попрошу") }
        assertEquals(true, has("В следующий раз начнём с подсказки «${HintStep.FIRST_CHORD.ru}»"))
        assertEquals(HintStep.DEGREES to SelfGrade.CLEAN, runBlocking { graph.practice.lastOrders()[id] })
    }
}
