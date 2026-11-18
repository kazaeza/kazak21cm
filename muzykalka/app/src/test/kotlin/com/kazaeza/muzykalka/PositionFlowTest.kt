package com.kazaeza.muzykalka

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.feature.position.PositionScreen
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** O-21 «Позиция» (ADR-050): the map of a key's chords in one zone — a chord is picked, shown and strummed. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PositionFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true, ignoreCase = true))
        rule.waitForIdle()
    }

    @Test
    fun aChordOfTheKeyIsShownInTheZoneAndStrummed() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { PositionScreen(onBack = {}) }
            }
        }
        rule.waitUntil(10_000) { has("как читать") }
        assertTrue("a major key to begin with", has("мажор"))
        scrollTo("Корень:")
        assertTrue("the first chord is already drawn, nothing to find first", rule.onAllNodesWithContentDescription("Схема хвата", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("the rule from the tonic is under it", has("Это дом"))
        assertTrue("silent until a chord is picked", audio.played.isEmpty())
        scrollTo("аккорды лада")
        rule.onAllNodes(hasClickAction() and hasText("V")).onFirst().performClick()
        rule.waitUntil(5_000) { audio.played.isNotEmpty() }
        assertTrue("strummed on the guitar", audio.played.single().notes.all { it.voice == Voice.GUITAR })
        scrollTo("Корень:")
        assertTrue("its job in the key", has("доминанта"))
        assertTrue("its tones as degrees of the key", has("ноты в ладу"))
        assertTrue("the rule says how far it is from the tonic chord", has("струну тоньше"))
        // a question to oneself: the answer is shown, never judged
        scrollTo("Проверь себя: где аккорд?")
        rule.onAllNodes(hasClickAction() and hasText("Проверь себя: где аккорд?")).onFirst().performClick()
        rule.waitForIdle()
        scrollTo("Отсчитай от корня")
        assertTrue("asks for a chord", has("Где "))
        rule.onAllNodes(hasClickAction() and hasText("Показать")).onFirst().performClick()
        rule.waitForIdle()
        assertTrue("the answer is shown and strummed", audio.played.size == 2)
        // the whole neck is behind a button; the zone moves a fret up with the chord still picked and lit
        scrollTo("Весь гриф")
        assertTrue("the zone is hidden at first", !has("домой"))
        rule.onAllNodes(hasClickAction() and hasText("Весь гриф", substring = true)).onFirst().performClick()
        rule.waitForIdle()
        scrollTo("домой")
        val before = rule.onAllNodesWithText("лады", substring = true).fetchSemanticsNodes().size
        rule.onAllNodes(hasClickAction() and hasText("›")).onFirst().performClick()
        rule.waitForIdle()
        assertTrue(before > 0 && has("лады"))
        scrollTo("Корень:")
    }
}
