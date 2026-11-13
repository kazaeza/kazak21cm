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
import androidx.compose.ui.test.performScrollTo
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
        // O-41: the empty slot offers the songs of the library first; with none to offer, «Свой якорь» leads to the editor
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("♫ песни на", substring = true))
        rule.onAllNodesWithText("♫ песни на", substring = true).onFirst().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Свой якорь").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Свой якорь").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Сохранить").fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText("Песня").performTextInput("Моя песня")
        rule.onNodeWithText("Мелодия ступенями").performTextReplacement("1 2 ля")
        rule.onNodeWithText("Сохранить").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Не разобрал", substring = true).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText("Мелодия ступенями").performTextReplacement("1 3 5 1'")
        rule.onNodeWithText("Сохранить").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Сохранить").fetchSemanticsNodes().isEmpty() }

        val saved = runBlocking { graph.anchors.current() }.values.single()
        assertEquals("Моя песня", saved.title)
        assertEquals("1 3 5 1'", saved.melodyText)
    }

    /** O-41: behind the empty slot of a degree — a song of the library that starts on its chord; it plays, goes round, opens. */
    @Test
    fun aSongThatStartsOnTheChordOfTheDegreeIsOfferedPlayedAndOpened() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        runBlocking { graph.library.seedBook(emptySet()) }
        var opened: String? = null
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { FeelScreen(onBack = {}, onTir = {}, onOpenSong = { opened = it }) }
            }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("♫ песни на", substring = true))
        rule.onAllNodesWithText("♫ песни на", substring = true).onFirst().performClick() // the card of the tonic
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Песня, что начинается с", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("другая ·", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("the first of many", true, rule.onAllNodesWithText("1 из", substring = true).fetchSemanticsNodes().isNotEmpty())

        val before = audio.played.size
        rule.onNodeWithText("Послушать").performClick()
        rule.waitUntil(5_000) { audio.played.size > before }
        val heard = audio.played.last()
        assertEquals("the opening chords, nothing else", true, heard.notes.all { it.voice == com.kazaeza.muzykalka.music.Voice.PIANO || it.voice == com.kazaeza.muzykalka.music.Voice.BASS })

        rule.onAllNodesWithText("другая ·", substring = true).onFirst().performClick() // not the «другая» that changes the key
        rule.waitUntil(5_000) { rule.onAllNodesWithText("2 из", substring = true).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText("Открыть в библиотеке").performScrollTo().performClick()
        rule.waitUntil(5_000) { opened != null }
        assertEquals(true, opened!!.startsWith("book:"))
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Песня, что начинается с", substring = true).fetchSemanticsNodes().isEmpty() }
    }
}
