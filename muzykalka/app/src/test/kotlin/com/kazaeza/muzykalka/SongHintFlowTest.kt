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
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.HintStyle
import com.kazaeza.muzykalka.feature.lab.HarmonyLabScreen
import com.kazaeza.muzykalka.feature.lab.HarmonyLabViewModel
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O-57 (ADR-056): the switch of the hints — a feel in words, or a song that has the sound — and the song heard. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SongHintFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val audio = FakeAudioOutput()

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true, ignoreCase = true))
        rule.waitForIdle()
    }

    private fun click(label: String) {
        scrollTo(label)
        rule.onAllNodes(hasClickAction() and hasText(label)).onFirst().performClick()
        rule.waitForIdle()
    }

    @Test
    fun theIntervalNamesASongOnlyWhereTheSongsAreChosenAndPlaysIt() {
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { HarmonyLabScreen(onBack = {}) }
            }
        }
        rule.waitUntil(10_000) { has("Интервал") }
        click("5")
        rule.waitUntil(5_000) { audio.drones.isNotEmpty() }
        scrollTo("квинта")
        assertTrue("by default the card speaks of the feel only", !has("как в «"))

        click("Песни")
        scrollTo("как в «")
        assertTrue("the fifth is hung on a tune: ${has("Twinkle")}", has("как в «Twinkle, twinkle»"))
        assertTrue("with its notes in degrees", has("1 1 5 5"))
        val before = audio.played.size
        click("Послушать")
        // the hum of the lower sound first, then the tune: the view model's delay needs the looper moved
        org.robolectric.shadows.ShadowLooper.idleMainLooper(HarmonyLabViewModel.SONG_SETTLE_MS + 200, java.util.concurrent.TimeUnit.MILLISECONDS)
        rule.waitUntil(10_000) { audio.played.size > before }
        assertTrue("four notes", audio.played.last().notes.size == 4)

        click("Ощущение") // leaves the switch where a fresh install has it
        rule.waitUntil(5_000) { !has("как в «") }
    }

    /** A miss in the Tir, where songs are chosen: after the engine's correction a few notes of a song play (rule 5 stays). */
    @Test
    fun aMissInTheTirIsFollowedByASong() {
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        kotlinx.coroutines.runBlocking { graph.settings.update { it.copy(hintStyle = HintStyle.SONGS) } }
        try {
            rule.setContent {
                CompositionLocalProvider(LocalAppGraph provides graph) {
                    MuzykalkaTheme { GameScreen(RoundSpec(Variant.TIR_CLASSIC, level = 1), onExit = {}) }
                }
            }
            var missed = false
            for (round in 1..8) {
                rule.waitUntil(15_000) { has("Какая ступень?") }
                val before = audio.played.size
                rule.onAllNodesWithText("1").onFirst().performClick()
                rule.waitUntil(10_000) { has("→") || has("+") }
                if (has("→")) {
                    missed = true
                    org.robolectric.shadows.ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS)
                    rule.waitUntil(10_000) { audio.played.size >= before + 2 }
                    val song = audio.played.last()
                    assertTrue("a few notes of a song after the correction: ${song.notes.size}", song.notes.size in 3..4)
                    assertTrue("and its line on the screen", has("как в «"))
                    break
                }
                org.robolectric.shadows.ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS)
            }
            assertTrue("a miss came within eight rounds of answering «1»", missed)
        } finally {
            kotlinx.coroutines.runBlocking { graph.settings.update { it.copy(hintStyle = HintStyle.FEEL) } }
        }
    }
}
