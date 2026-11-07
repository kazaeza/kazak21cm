package com.kazaeza.muzykalka

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.feature.feel.FeelScreen
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O-09: an anchor is typed in by the user on the degree card, checked, saved and kept. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AnchorFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun anAnchorIsTypedInCheckedAndKept() {
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = FakeAudioOutput()
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { FeelScreen(onBack = {}, onTir = {}) }
            }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("⚓ + якорь"))
        rule.onAllNodesWithText("⚓ + якорь").onFirst().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Сохранить").fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText("Песня").performTextInput("Моя песня")
        rule.onNodeWithText("Мелодия ступенями").performTextReplacement("1 2 ля")
        rule.onNodeWithText("Сохранить").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Не разобрал", substring = true).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText("Мелодия ступенями").performTextReplacement("1 3 5 1'")
        rule.onNodeWithText("Сохранить").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("⚓ Моя песня").fetchSemanticsNodes().isNotEmpty() }

        val saved = runBlocking { graph.anchors.current() }.values.single()
        assertEquals("Моя песня", saved.title)
        assertEquals("1 3 5 1'", saved.melodyText)
    }
}
