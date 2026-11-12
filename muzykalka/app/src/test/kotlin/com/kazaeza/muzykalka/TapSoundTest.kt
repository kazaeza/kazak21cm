package com.kazaeza.muzykalka

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.audio.PitchInput
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.TapSound
import com.kazaeza.muzykalka.music.ToneTimbre
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.JuicyKey
import com.kazaeza.muzykalka.ui.component.LocalTapSound
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * O-36: the wooden tap. It sounds as the finger lands, through one door (AppGraph.tap), with the sound and volume
 * of the settings; it is silent while a question is asked and while the microphone is open.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TapSoundTest {
    @get:Rule
    val rule = createComposeRule()

    private fun graphOf(audio: AudioOutput, pitch: PitchInput = FakePitchInput()): AppGraph =
        object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
            override val pitchInput: PitchInput = pitch
        }

    @Test
    fun theGraphTapsWithTheChosenSoundAndIsQuietWhenItShouldBe() {
        val audio = FakeAudioOutput()
        val pitch = FakePitchInput()
        val graph = graphOf(audio, pitch)

        graph.applySound(Settings(tapSound = TapSound.TICK, toneTimbre = ToneTimbre.HUM, room = true))
        assertEquals(ToneTimbre.HUM, audio.toneTimbre)
        assertTrue(audio.room)
        graph.tap(false)
        graph.tap(true)
        assertEquals(listOf(TapSound.TICK to false, TapSound.TICK to true), audio.taps)

        pitch.isRunning.value = true
        graph.tap(false)
        assertEquals("the microphone is open: no tap", 2, audio.taps.size)
        pitch.isRunning.value = false

        graph.applySound(Settings(tapSound = null))
        graph.tap(false)
        assertEquals("taps are off", 2, audio.taps.size)
    }

    @Test
    fun theOutputStaysOpenForTapsOnlyWhileTheAppIsOnScreen() {
        val audio = FakeAudioOutput()
        val graph = graphOf(audio)
        graph.applySound(Settings(tapSound = TapSound.BLOCK))
        assertFalse("not on screen yet", audio.kept)
        graph.setOnScreen(true)
        assertTrue(audio.kept)
        graph.setOnScreen(false)
        assertFalse(audio.kept)
        graph.setOnScreen(true)
        graph.applySound(Settings(tapSound = null))
        assertFalse("no taps: nothing to keep the output open for", audio.kept)
    }

    @Test
    fun aButtonTapsWhenTheFingerLandsNotWhenItLeaves() {
        val taps = mutableListOf<Boolean>()
        var clicks = 0
        rule.setContent {
            CompositionLocalProvider(LocalTapSound provides { low -> taps += low }) {
                MuzykalkaTheme {
                    JuicyKey("5", DegreeColors.gradient(Degree.FIVE), onClick = { clicks++ })
                    GhostButton("Дальше", onClick = {})
                }
            }
        }
        rule.onNodeWithText("5").performTouchInput { down(center) }
        rule.waitForIdle()
        assertEquals("the tap is on the press", listOf(false), taps)
        assertEquals("the click is not yet", 0, clicks)
        rule.onNodeWithText("5").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(1, clicks)
        assertEquals("and the release does not tap again", 1, taps.size)
    }

    @Test
    fun nothingTapsWhileTheQuestionSounds() {
        val release = CompletableDeferred<Unit>()
        val base = FakeAudioOutput()
        val audio = object : AudioOutput by base {
            override suspend fun play(score: Score) {
                release.await() // the question never ends: the round stays in PROMPTING
            }
        }
        val taps = mutableListOf<Boolean>()
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graphOf(audio), LocalTapSound provides { low -> taps += low }) {
                MuzykalkaTheme { GameScreen(RoundSpec(Variant.TIR_CLASSIC, level = 1), onExit = {}) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("❙❙").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("❙❙").performTouchInput { down(center) }
        rule.waitForIdle()
        assertTrue("the pause button was pressed during the question: $taps", taps.isEmpty())
        rule.onNodeWithText("❙❙").performTouchInput { up() }
        release.complete(Unit)
    }

    @Test
    fun aTapSoundsWhileTheAnswerIsAwaited_andPauseIsTheLowOne() {
        val audio = FakeAudioOutput()
        val taps = mutableListOf<Boolean>()
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graphOf(audio), LocalTapSound provides { low -> taps += low }) {
                MuzykalkaTheme { GameScreen(RoundSpec(Variant.TIR_CLASSIC, level = 1), onExit = {}) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Какая ступень?").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("❙❙").performTouchInput { down(center) }
        rule.waitForIdle()
        assertEquals("pause is the low voice", listOf(true), taps)
        rule.onNodeWithText("❙❙").performTouchInput { up() }
        rule.onAllNodesWithText("5").onFirst() // the keys exist
    }
}
