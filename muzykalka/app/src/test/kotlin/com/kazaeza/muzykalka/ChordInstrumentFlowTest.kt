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
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.feature.game.Drills
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.feature.settings.SettingsScreen
import com.kazaeza.muzykalka.game.Answer
import com.kazaeza.muzykalka.game.LoopTask
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

    @Test
    fun transferCheckAlwaysUsesPianoEvenWhenGuitarIsChosen() {
        val guitarSettings = Settings(chordInstrument = ChordInstrument.GUITAR)
        val engine = Drills.engine(RoundSpec(Variant.TIR_TRANSFER), 1, guitarSettings, emptyList(), 0L)
        assertTrue("transfer check has no lives", Drills.noLives(Variant.TIR_TRANSFER))
        assertTrue("transfer check uses long round", Drills.longRound(Variant.TIR_TRANSFER))
        assertTrue("loop3 uses long round", Drills.longRound(Variant.TIR_LOOP3))
        assertTrue("bassline uses long round", Drills.longRound(Variant.TIR_BASSLINE))
        var t = 0L
        engine.start(t)
        val seenVoices = mutableSetOf<Voice>()
        repeat(4) {
            val task = engine.state.task as LoopTask
            seenVoices += task.question.notes.map { it.voice }
            assertTrue("no guitar in transfer check", task.question.notes.none { it.voice == Voice.GUITAR })
            assertTrue("no guitar in transfer intro", task.intro?.notes.orEmpty().none { it.voice == Voice.GUITAR })
            t += 1_000L
            engine.promptFinished(t)
            t += 200L
            engine.answer(Answer.Pass, t)
            t += 5_000L
            engine.tick(t)
        }
        assertEquals("no lives lost, round stays open", null, engine.state.livesLeft)
        assertTrue("band texture uses piano + bass + kick", Voice.PIANO in seenVoices && Voice.BASS in seenVoices && Voice.KICK in seenVoices)
    }
}
