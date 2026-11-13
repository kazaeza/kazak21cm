package com.kazaeza.muzykalka

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.feature.game.Drills
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Speaker that "plays" instantly and remembers what it was asked to play. */
class FakeAudioOutput : AudioOutput {
    val played = mutableListOf<Score>()
    override suspend fun play(score: Score) {
        played += score
    }
    val drones = mutableListOf<List<MidiNote>?>()
    override fun stop() = Unit
    override fun drone(notes: List<MidiNote>?) {
        drones += notes
    }
    override val isSounding: StateFlow<Boolean> = MutableStateFlow(false)
    override val lastSoundEndNanos: Long = 0
    override var a4Hz: Double = 440.0
    override var toneTimbre: com.kazaeza.muzykalka.music.ToneTimbre = com.kazaeza.muzykalka.music.ToneTimbre.CLASSIC
    override var room: Boolean = false
    val taps = mutableListOf<Pair<com.kazaeza.muzykalka.music.TapSound, Boolean>>()
    override fun tap(sound: com.kazaeza.muzykalka.music.TapSound, low: Boolean, gain: Float) {
        taps += sound to low
    }
    var kept = false
    override fun keepOpen(on: Boolean) {
        kept = on
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GameFlowTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tirRoundRunsOverTheDroneAndJudgesATap() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { GameScreen(RoundSpec(Variant.TIR_CLASSIC, level = 1), onExit = {}) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Какая ступень?").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("scale + question were played", audio.played.isNotEmpty())
        assertTrue("the tonic drone is on", audio.drones.lastOrNull()?.isNotEmpty() == true)
        rule.onAllNodesWithText("1").onFirst().performClick()
        rule.waitUntil(5_000) { audio.played.size >= 2 } // confirmation or correction (rule 5)
    }

    /** O-28: three misses used to end «Спой ступень»; the sung modes have no lives and the longest round. */
    @Test
    fun sungRoundsHaveNoLives() {
        assertTrue(Drills.relaxed(Variant.KAMERTON_SING))
        assertTrue(Drills.relaxed(Variant.KAMERTON_SEARCH))
        assertTrue(!Drills.relaxed(Variant.TIR_CLASSIC))
    }

    /** O-32: «once more» is a full game target (72 dp), not a small glyph, and plays the question again. */
    @Test
    fun tirReplayKeyIsBigAndPlaysTheQuestionAgain() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { GameScreen(RoundSpec(Variant.TIR_CLASSIC, level = 1), onExit = {}) }
            }
        }
        val replay = hasContentDescription("Повторить вопрос")
        rule.waitUntil(10_000) { rule.onAllNodes(replay and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(replay).assertWidthIsAtLeast(72.dp).assertHeightIsAtLeast(72.dp)
        val before = audio.played.size
        rule.onNode(replay).performClick()
        rule.waitUntil(5_000) { audio.played.size > before }
    }

    /** O-15 (ADR-029) turned round in O-40: the key is set over the tonic, then the shown note hums and the chord is played over it. */
    @Test
    fun underNoteSetsTheKeyOverTheTonicThenAsksTheChord() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { GameScreen(RoundSpec(Variant.TIR_UNDER_NOTE, level = 1), onExit = {}) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Какой аккорд сыграли?").fetchSemanticsNodes().isNotEmpty() }
        val drones = audio.drones.filterNotNull()
        assertTrue("the tonic drone under the scale first", drones.first().size == 2)
        assertTrue("then the note, held low with its octave", drones.last().size == 2)
        assertTrue("scale, then the chord", audio.played.size >= 2)
        val before = audio.played.size
        rule.onAllNodesWithText("iv", ignoreCase = true).onFirst().performClick() // IV in major, iv in minor
        rule.waitUntil(5_000) { audio.played.size > before } // the chord goes home, or the chord over the note again (rule 5)
    }

    /** O-17 (ADR-031): in «Ходы» a chord of the pattern sounds when tapped — while the engine is silent. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aChordOfTheStepsPatternSoundsWhenTapped() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { GameScreen(RoundSpec(Variant.TAPER_STEPS, level = 2), onExit = {}) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Нажми на аккорд", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val before = audio.played.size
        rule.onAllNodes(hasClickLabel("Послушать аккорд")).onFirst().performClick()
        rule.waitUntil(5_000) { audio.played.size > before }
        assertTrue(audio.played.last().notes.all { it.voice == Voice.GUITAR })
        rule.onRoot().captureToImage() // the caption is drawn for real
    }
}
