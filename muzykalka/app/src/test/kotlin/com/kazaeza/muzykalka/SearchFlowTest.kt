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
    override fun start(deviceId: Int?, a4Hz: Double, minHoldMs: Int, boostDb: Double) {
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

    /** PR-01 «Попугай», step 1 (ADR-051): the tonic and a note sound, a note played into the microphone is the answer. */
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun theParrotHearsTheGuitarAndShowsWhereTheNoteLies() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val audio = FakeAudioOutput()
        val mic = FakePitchInput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
            override val pitchInput: PitchInput = mic
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { GameScreen(RoundSpec(Variant.PARROT_ECHO, level = 1), onExit = {}) }
            }
        }
        rule.waitUntil(10_000) { has("Найди эту ноту на гитаре") }
        assertTrue("the microphone is open", mic.isRunning.value)
        assertTrue("no drone: it would leak into the microphone", audio.drones.filterNotNull().isEmpty())
        assertTrue("the scale, the tonic and the note were played", audio.played.isNotEmpty())
        assertTrue("nothing names the note before the answer", !has("нашёл") && !has("ты сыграл"))
        val before = audio.played.size
        mic.sing(MidiNote(52))
        rule.waitUntil(5_000) { has("нашёл") || has("ты сыграл") }
        rule.waitUntil(5_000) { audio.played.size > before } // the note walks home (rule 5)
    }

    /** O-77 «Бас-линия» (ADR-075): only the bass sounds, the line is played into the microphone note by note, then the band. */
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun theBassLineIsPlayedNoteByNoteAndThenTheBandAnswers() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val audio = FakeAudioOutput()
        val mic = FakePitchInput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
            override val pitchInput: PitchInput = mic
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { GameScreen(RoundSpec(Variant.TIR_BASSLINE, level = 1), onExit = {}) }
            }
        }
        rule.waitUntil(10_000) { has("Сыграй этот бас") }
        assertTrue("the microphone is open", mic.isRunning.value)
        assertTrue("no drone: it would leak into the microphone", audio.drones.filterNotNull().isEmpty())
        val question = audio.played.last()
        assertTrue("only the bass sounds", question.notes.all { it.voice == com.kazaeza.muzykalka.music.Voice.BASS })
        // the line as a guitar plays it: the loud notes of the question, in order
        val line = question.notes.filter { it.velocity > 0.8f }.sortedBy { it.startBeat }.map { it.note!! }
        val before = audio.played.size
        line.forEach { mic.sing(it) }
        rule.waitUntil(5_000) { audio.played.size > before } // the band (the right line), or the bass and the band (a miss)
        assertTrue("the band has a piano (O-82: the default), a bass and drums", audio.played.last().notes.let { n -> n.any { it.voice == com.kazaeza.muzykalka.music.Voice.PIANO } && n.any { it.voice == com.kazaeza.muzykalka.music.Voice.KICK } })
        assertTrue("a right line is rewarded, a wrong one corrected", has("+") || has("→"))
    }
}
