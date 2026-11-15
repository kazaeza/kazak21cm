package com.kazaeza.muzykalka

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.feature.backing.BackingScreen
import com.kazaeza.muzykalka.feature.library.ItemScreen
import com.kazaeza.muzykalka.music.Backing
import com.kazaeza.muzykalka.music.SongBook
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** X-03 «Под бэкинг», step 1 (ADR-052): a library song sounds bar by bar and the screen says which chord is on. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BackingFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theBackingPlaysTheWholeFormAndSaysWhichChordIsOn() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        val entry = SongBook.byId("stand-by-me")!!
        val id = runBlocking { graph.library.addFromBook(entry) }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { BackingScreen(id, onBack = {}) }
            }
        }
        rule.waitUntil(10_000) { has("Играть") }
        assertTrue("the song is named", has(entry.song.title))
        assertTrue("silent until asked", audio.played.isEmpty())
        rule.onNodeWithText("Играть").performClick()
        rule.waitUntil(5_000) { audio.played.isNotEmpty() }
        val score = audio.played.first()
        assertTrue(score.notes.all { it.voice == Voice.PIANO || it.voice == Voice.BASS })
        assertEquals("the whole form, four beats a bar", Backing.lengthBeats(Backing.steps(entry.song)), score.lengthBeats, 0.2)
        assertEquals(80.0, score.tempoBpm, 0.0)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("сейчас", substring = true, ignoreCase = true))
        assertTrue("which chord is on, and its job in the key", has("такт 1") && has("тоника"))
        assertTrue(has("дальше"))
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Стоп"))
        rule.onNodeWithText("Стоп").performClick()
        rule.waitUntil(5_000) { has("Играть") && !has("такт 1") }
    }

    @Test
    fun theSongScreenLeadsToTheBacking() {
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = FakeAudioOutput()
        }
        val id = runBlocking { graph.library.addFromBook(SongBook.byId("stand-by-me")!!) }
        var asked: String? = null
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { ItemScreen(id, onBack = {}, onEdit = {}, onOrder = {}, onBacking = { asked = it }) }
            }
        }
        rule.waitUntil(10_000) { has("Играть под бэкинг") }
        rule.onAllNodesWithText("Играть под бэкинг").onFirst().performClick()
        rule.waitUntil(5_000) { asked == id }
    }
}
