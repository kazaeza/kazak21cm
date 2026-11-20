package com.kazaeza.muzykalka

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
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
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.feature.settings.SettingsScreen
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.ChordInstrument
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O-82 (ADR-078): what strikes the chords is a setting — the piano by default — and a tap on a chord follows it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ChordInstrumentFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val audio = FakeAudioOutput()

    private val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
        override val audioOutput: AudioOutput = audio
    }

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

    private fun chosen() = runBlocking { graph.settings.settings.first().chordInstrument }

    @Test
    fun theSettingsPageChoosesWhatPlaysChordsAndRemembersIt() {
        try {
            assertEquals("the piano is the default", ChordInstrument.PIANO, chosen())
            rule.setContent {
                CompositionLocalProvider(LocalAppGraph provides graph) {
                    MuzykalkaTheme { SettingsScreen(graph.settings, onBack = {}, onSoundCheck = {}) }
                }
            }
            // the page is a lazy list: the item is composed once it is scrolled to
            rule.waitUntil(10_000) { rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("аккорды играет", ignoreCase = true))
            rule.waitForIdle()
            assertTrue("both instruments are offered", has("пианино") && has("гитара"))
            rule.onAllNodes(hasClickAction() and hasText("гитара", ignoreCase = true)).onFirst().performClick()
            rule.waitUntil(5_000) { chosen() == ChordInstrument.GUITAR }
            rule.onAllNodes(hasClickAction() and hasText("пианино", ignoreCase = true)).onFirst().performClick()
            rule.waitUntil(5_000) { chosen() == ChordInstrument.PIANO }
        } finally {
            runBlocking { graph.settings.update { it.copy(chordInstrument = ChordInstrument.PIANO) } }
        }
    }

    @Test
    fun aTappedChordIsStruckOnTheGuitarWhereTheGuitarIsChosen() {
        runBlocking { graph.settings.update { it.copy(chordInstrument = ChordInstrument.GUITAR) } }
        try {
            rule.setContent {
                CompositionLocalProvider(LocalAppGraph provides graph) {
                    MuzykalkaTheme { GameScreen(RoundSpec(Variant.TAPER_STEPS, level = 2), onExit = {}) }
                }
            }
            rule.waitUntil(10_000) { has("Нажми на аккорд") }
            val before = audio.played.size
            rule.onAllNodes(hasClickLabel("Послушать аккорд")).onFirst().performClick()
            rule.waitUntil(5_000) { audio.played.size > before }
            assertTrue("the guitar, as chosen", audio.played.last().notes.all { it.voice == Voice.GUITAR })
        } finally {
            runBlocking { graph.settings.update { it.copy(chordInstrument = ChordInstrument.PIANO) } }
        }
    }
}
