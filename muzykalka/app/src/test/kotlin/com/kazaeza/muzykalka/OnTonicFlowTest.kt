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
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O-58 «Аккорд над тоникой» (ADR-057): the tonic hums, a chord sounds over it, a step of the key is the answer. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class OnTonicFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aChordOverTheHummingTonicIsAnsweredByItsStep() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { GameScreen(RoundSpec(Variant.TIR_ON_TONIC, level = 1), onExit = {}) }
            }
        }
        rule.waitUntil(15_000) { has("Какой аккорд?") }
        assertTrue("the tonic hums", audio.drones.lastOrNull()?.isNotEmpty() == true)
        assertTrue("the scale and the chord were played", audio.played.isNotEmpty())
        val before = audio.played.size
        rule.onAllNodesWithText("IV", ignoreCase = true).onFirst().performClick() // IV, or iv in minor
        rule.waitUntil(10_000) { audio.played.size > before } // the chord goes home, or the chord again (rule 5)
        rule.waitUntil(10_000) { has("+") || has("→") }
    }
}
