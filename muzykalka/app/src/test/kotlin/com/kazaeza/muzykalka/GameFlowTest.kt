package com.kazaeza.muzykalka

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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
}
