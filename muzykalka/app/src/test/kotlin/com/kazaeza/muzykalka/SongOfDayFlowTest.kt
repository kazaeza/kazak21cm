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
import com.kazaeza.muzykalka.feature.library.ItemScreen
import com.kazaeza.muzykalka.feature.today.TodayScreen
import com.kazaeza.muzykalka.music.SongBook
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O-44: «Сегодня» shows one song of the library a day; the song's own screen plays its sketch. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SongOfDayFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun todayShowsASongOfTheLibraryAndOpensIt() {
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = FakeAudioOutput()
        }
        runBlocking { graph.library.seedBook(emptySet(), SongBook.ENTRIES.take(12)) }
        var opened: String? = null
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { TodayScreen({}, {}, {}, {}, {}, {}, onOpenSong = { opened = it }) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Начать сет", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Песня дня", ignoreCase = true))
        rule.onAllNodesWithText("Песня дня", ignoreCase = true).onFirst().performClick()
        rule.waitUntil(5_000) { opened != null }
        val entry = runBlocking { graph.library.observeEntries().first() }.firstOrNull { it.id == opened }
        assertNotNull("the song of the day is a song of the library", entry)
        assertTrue("its title is on the card", rule.onAllNodesWithText(entry!!.title).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun anEmptyLibraryHasNoSongOfTheDay() {
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = FakeAudioOutput()
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { TodayScreen({}, {}, {}, {}, {}, {}) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Начать сет", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithText("Песня дня", ignoreCase = true).fetchSemanticsNodes().size)
    }

    @Test
    fun theSongScreenPlaysItsSketch() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        val id = runBlocking { graph.library.addFromBook(SongBook.byId("stand-by-me")!!) }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { ItemScreen(id, onBack = {}, onEdit = {}, onOrder = {}) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Послушать").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Послушать").performClick()
        rule.waitUntil(5_000) { audio.played.isNotEmpty() }
        val sketch = audio.played.single()
        assertTrue("chords with a bass, as the Taper checks a song", sketch.notes.all { it.voice == Voice.PIANO || it.voice == Voice.BASS })
        assertTrue("more than a chord or two", sketch.notes.map { it.startBeat }.toSet().size >= 4)
        rule.onNodeWithText("Послушать").performClick() // another key every time
        rule.waitUntil(5_000) { audio.played.size == 2 }
    }
}
