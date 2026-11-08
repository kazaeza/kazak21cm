package com.kazaeza.muzykalka

import android.Manifest
import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.audio.PitchInput
import com.kazaeza.muzykalka.dsp.NoteEvent
import com.kazaeza.muzykalka.dsp.PitchFrame
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Microphone that hears only what the test sings into it. */
class FakePitchInput : PitchInput {
    override val frames = MutableSharedFlow<PitchFrame>(extraBufferCapacity = 64)
    override val notes = MutableSharedFlow<NoteEvent>(extraBufferCapacity = 16)
    override val isRunning = MutableStateFlow(false)
    override val error = MutableStateFlow<String?>(null)
    override fun start(deviceId: Int?, a4Hz: Double, minHoldMs: Int) {
        isRunning.value = true
    }
    override fun stop() {
        isRunning.value = false
    }

    fun sing(note: MidiNote) {
        notes.tryEmit(NoteEvent.Onset(System.nanoTime(), note, 0.0, note.frequency()))
    }
}

/**
 * O-14 (ADR-028): «Пропевка» does not let go of a task until the degree is found. «подскажи»
 * plays the note and the search goes on; a wrong note says what it was; the found one closes it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SearchFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theSearchKeepsListeningUntilTheDegreeIsSung() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val audio = FakeAudioOutput()
        val mic = FakePitchInput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
            override val pitchInput: PitchInput = mic
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { GameScreen(RoundSpec(Variant.KAMERTON_SEARCH, level = 1), onExit = {}) }
            }
        }
        rule.waitUntil(10_000) { has("Не спеши") }
        assertTrue("the order names the interval", has("Спой "))

        // «подскажи»: the note sounds, the task stays open.
        val before = audio.played.size
        rule.onNodeWithText("подскажи").performClick()
        rule.waitUntil(5_000) { has("Послушай и спой так же") && audio.played.size > before }
        assertTrue(has("попытка 2"))

        // Sing every pitch class in turn: misses keep listening, the right one is found.
        var found = false
        for (k in 0 until 12) {
            mic.sing(MidiNote(48 + k))
            val nextTry = "попытка ${k + 3}"
            rule.waitUntil(5_000) {
                found = has("попытки!")
                found || has(nextTry)
            }
            if (found) break
            assertTrue("a miss says what was sung", has("ищем"))
        }
        assertTrue("one of twelve notes is the asked degree", found)
    }
}
