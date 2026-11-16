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
import com.kazaeza.muzykalka.feature.set.SetScreen
import com.kazaeza.muzykalka.feature.today.TodayScreen
import com.kazaeza.muzykalka.game.Training
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O-55: «Сегодня» offers sets besides the daily one; a set opens with its own name and parts. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TrainingFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun graph() = object : AppGraph(ApplicationProvider.getApplicationContext()) {
        override val audioOutput: AudioOutput = FakeAudioOutput()
    }

    @Test
    fun todayOffersASetWithoutTheGuitarAndStartsIt() {
        val graph = graph()
        var chosen: Training? = null
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { TodayScreen({}, {}, {}, {}, {}, {}, onTraining = { chosen = it }) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Начать сет", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Без гитары", ignoreCase = true))
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(Training.ROAD.ru))
        for (t in Training.OTHERS) {
            rule.onNode(hasScrollAction()).performScrollToNode(hasText(t.ru))
            assertTrue("${t.ru} is offered", rule.onAllNodesWithText(t.ru).fetchSemanticsNodes().isNotEmpty())
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(Training.EAR.ru))
        rule.onAllNodesWithText(Training.EAR.ru).onFirst().performClick()
        rule.waitUntil(5_000) { chosen != null }
        assertEquals(Training.EAR, chosen)
    }

    @Test
    fun aSetOpensWithItsNameAndItsParts() {
        val graph = graph()
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { SetScreen(minimal = false, onExit = {}, onLibrary = {}, training = Training.SONGS) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(Training.SONGS.ru).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("says the guitar is not needed", rule.onAllNodesWithText("гитара не нужна", substring = true).fetchSemanticsNodes().isNotEmpty())
        for (part in Training.SONGS.plan) {
            rule.onNode(hasScrollAction()).performScrollToNode(hasText(part.variant.ru))
            assertTrue("${part.variant.ru} is a part", rule.onAllNodesWithText(part.variant.ru).fetchSemanticsNodes().isNotEmpty())
        }
    }
}
