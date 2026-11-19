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

/** O-62 «Бас» and «Два аккорда» (ADR-061). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BassTwoChordsFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun start(variant: Variant): FakeAudioOutput {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { GameScreen(RoundSpec(variant, level = 1), onExit = {}) }
            }
        }
        return audio
    }

    @Test
    fun theFirstChordIsOnTheScreenAndTheSecondIsAnswered() {
        val audio = start(Variant.TIR_TWO_CHORDS)
        rule.waitUntil(15_000) { has("Какой второй?") }
        assertTrue("the first chord is named", has("первый аккорд"))
        assertTrue("the tonic hums", audio.drones.lastOrNull()?.isNotEmpty() == true)
        val before = audio.played.size
        rule.onAllNodesWithText("IV", ignoreCase = true).onFirst().performClick() // IV or V are offered on level 1; one of them is right
        rule.waitUntil(10_000) { audio.played.size > before }
        rule.waitUntil(10_000) { has("+") || has("→") }
    }

    @Test
    fun onlyTheBassIsHeardAndTheChordIsNamedAfterwards() {
        val audio = start(Variant.TIR_BASS)
        rule.waitUntil(15_000) { has("Слышен только бас") }
        assertTrue("the tonic hums", audio.drones.lastOrNull()?.isNotEmpty() == true)
        val before = audio.played.size
        rule.onAllNodesWithText("IV", ignoreCase = true).onFirst().performClick()
        rule.waitUntil(10_000) { audio.played.size > before }
        rule.waitUntil(10_000) { has("+") || has("→") }
    }

    /** O-67 (ADR-065): a miss stays on screen with what the chord does, until «Дальше». */
    @Test
    fun aMissWaitsForNextAndShowsWhatTheChordDoes() {
        start(Variant.TIR_BASS)
        rule.waitUntil(15_000) { has("Слышен только бас") }
        rule.onAllNodesWithText("не знаю").onFirst().performClick()
        rule.waitUntil(10_000) { has("Понял — дальше") }
        assertTrue("the three jobs are shown", has("Дом") && has("Уход") && has("Напряжение"))
        rule.waitUntil(10_000) { has("Понял — дальше") } // still there: nothing moves on by itself
        rule.onAllNodesWithText("Понял — дальше", substring = true).onFirst().performClick()
        rule.waitUntil(15_000) { has("Слышен только бас") }
    }
}
