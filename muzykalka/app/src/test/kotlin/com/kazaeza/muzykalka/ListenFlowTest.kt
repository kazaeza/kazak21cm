package com.kazaeza.muzykalka

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.Announcer
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.feature.listen.ListenScreen
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Voice that remembers what it was asked to say. */
class FakeAnnouncer : Announcer {
    val said = mutableListOf<String>()
    override suspend fun say(text: String) {
        said += text
    }
    override val isSpeaking: StateFlow<Boolean> = MutableStateFlow(false)
    override val lastSpeechEndNanos: Long = 0
    override val available: StateFlow<Boolean?> = MutableStateFlow(true)
}

/** O-16 (ADR-030): the note, a silent pause, then the answer — shown, said and walking home. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ListenFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aQuestionIsHeardThenAnsweredAloudAfterThePause() {
        val audio = FakeAudioOutput()
        val voice = FakeAnnouncer()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
            override val announcer: Announcer = voice
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { ListenScreen(onBack = {}) }
            }
        }
        // The controls sit below the stage in a lazy list: scroll to them first.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("2 с"))
        rule.onNodeWithText("2 с").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Слушать"))
        rule.onNodeWithText("Слушать").performClick()
        rule.onNode(hasScrollAction()).performScrollToIndex(0) // back to the stage
        rule.waitUntil(10_000) { has("Назови про себя") }
        assertTrue("nothing is said before the pause", voice.said.isEmpty())
        assertTrue("the chord drone is on", audio.drones.lastOrNull()?.size == 4)

        rule.waitUntil(10_000) {
            has("Назови") // keeps the main looper (and the pause's delay) moving
            voice.said.isNotEmpty()
        }
        val said = voice.said.first()
        assertTrue("a degree read aloud: $said", said.split(' ').last() in setOf("один", "два", "три", "четыре", "пять", "шесть", "семь"))
        rule.waitUntil(10_000) { has("услышано: 1") }
        assertTrue("question and the walk home", audio.played.size >= 2)

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Стоп"))
        rule.onNodeWithText("Стоп").performClick()
        rule.onNode(hasScrollAction()).performScrollToIndex(0)
        rule.waitUntil(5_000) { has("Нажми «Слушать»") }
    }
}
