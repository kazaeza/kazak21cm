package com.kazaeza.muzykalka

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.feature.set.SetScreen
import com.kazaeza.muzykalka.feature.today.TodayScreen
import com.kazaeza.muzykalka.game.Tracks
import com.kazaeza.muzykalka.game.Training
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O-55 / «Обертон 2.0»: «Сегодня» leads with a non-guitar daily set, duration/quiet controls, tracks, and a bottom guitar block. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TrainingFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun graph() = object : AppGraph(ApplicationProvider.getApplicationContext()) {
        override val audioOutput: AudioOutput = FakeAudioOutput()
    }

    private fun scrollTo(text: String, substring: Boolean = false, ignoreCase: Boolean = false) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring, ignoreCase = ignoreCase))
        rule.waitForIdle()
    }

    @Test
    fun todayOffersNonGuitarControlsTracksAndSeparateGuitarBlock() {
        val graph = graph()
        runBlocking { graph.settings.update { Settings() } }
        var chosenTraining: Training? = null
        var chosenTrack: String? = null
        var chosenMinutes: Int? = null
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme {
                    TodayScreen(
                        onStartSet = {},
                        onShortSet = {},
                        onSettings = {},
                        onPlay = {},
                        onFeel = {},
                        onLibrary = {},
                        onTraining = { chosenTraining = it },
                        onTrack = { chosenTrack = it },
                        onStartDaily = { chosenMinutes = it },
                    )
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("ЗА НЕДЕЛЮ").fetchSemanticsNodes().isNotEmpty() }

        scrollTo("15 мин")
        rule.onAllNodesWithText("15 мин").onFirst().performClick()
        rule.waitUntil(5_000) {
            runCatching { scrollTo("Начать сет · 15 мин") }.isSuccess
        }
        assertTrue("daily set says guitar is not needed", rule.onAllNodesWithText("Гитара не нужна", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("Начать сет · 15 мин").onFirst().performClick()
        rule.waitUntil(5_000) { chosenMinutes == 15 }

        scrollTo("С голосом")
        rule.onAllNodesWithText("С голосом").onFirst().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Тихо · без голоса").fetchSemanticsNodes().isNotEmpty() }

        scrollTo("Программы без гитары", ignoreCase = true)
        for (track in Tracks.NON_GUITAR) {
            scrollTo(track.ru)
            assertTrue("${track.ru} is offered", rule.onAllNodesWithText(track.ru).fetchSemanticsNodes().isNotEmpty())
        }
        scrollTo(Tracks.CHORDS_FUNC.ru)
        rule.onAllNodesWithText(Tracks.CHORDS_FUNC.ru).onFirst().performClick()
        rule.waitUntil(5_000) { chosenTrack == Tracks.CHORDS_FUNC.id }

        scrollTo("С физической гитарой", ignoreCase = true)
        scrollTo(Tracks.GUITAR_HANDS.ru)
        assertTrue("guitar track is in the bottom section", rule.onAllNodesWithText(Tracks.GUITAR_HANDS.ru).fetchSemanticsNodes().isNotEmpty())
        scrollTo("Сет с гитарой", substring = true)
        rule.onAllNodesWithText("Сет с гитарой", substring = true).onFirst().performClick()
        rule.waitUntil(5_000) { chosenTraining == Training.GUITAR }
        assertEquals(Training.GUITAR, chosenTraining)
    }

    @Test
    fun aSetOpensWithItsNameAndItsParts() {
        val graph = graph()
        runBlocking { graph.settings.update { Settings() } }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { SetScreen(minimal = false, onExit = {}, onLibrary = {}, training = Training.SONGS) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(Training.SONGS.ru).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("says the guitar is not needed", rule.onAllNodesWithText("гитара не нужна", substring = true).fetchSemanticsNodes().isNotEmpty())
        for (part in Training.SONGS.plan) {
            scrollTo(part.variant.ru)
            assertTrue("${part.variant.ru} is a part", rule.onAllNodesWithText(part.variant.ru).fetchSemanticsNodes().isNotEmpty())
        }
    }
}
