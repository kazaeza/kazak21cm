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

/** O-65 «Созвучие» (ADR-064): two sounds together, no hum; after the answer the pair has a name and a colour. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class DyadFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aPairStruckTogetherIsNamedFromItsLowerSound() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { GameScreen(RoundSpec(Variant.TIR_DYAD, level = 1), onExit = {}) }
            }
        }
        rule.waitUntil(15_000) { has("Какое созвучие?") }
        assertTrue("nothing hums", audio.drones.lastOrNull().isNullOrEmpty())
        val pair = audio.played.last()
        assertTrue("two sounds at once", pair.notes.size == 2 && pair.notes.all { it.startBeat == 0.0 })
        val before = audio.played.size
        rule.onAllNodesWithText("5").onFirst().performClick()
        rule.waitUntil(10_000) { audio.played.size > before }
        rule.waitUntil(10_000) { has("+") || has("→") }
        assertTrue("the pair is named", has("терция") || has("квинта") || has("октава"))
    }
}
