package com.kazaeza.muzykalka

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.feature.loops.LoopsScreen
import com.kazaeza.muzykalka.game.AttemptRecord
import com.kazaeza.muzykalka.game.Variant
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

/** O-47 «Коллекция ходов»: the album lists the loops of the library, plays one, marks the known, opens a song. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class LoopsFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theAlbumShowsTheLoopsPlaysOneAndOpensASong() {
        val audio = FakeAudioOutput()
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        val standByMe = SongBook.byId("stand-by-me")!! // I – vi – IV – V
        runBlocking {
            graph.library.seedBook(emptySet(), listOf(standByMe))
            // three right answers on its loop in «Чья гармония?»: the loop is known
            graph.practice.saveAttempts(List(3) { AttemptRecord(Variant.TIR_SONG, 1, "song:L1:I-vi-IV-V", "I-vi-IV-V", "I-vi-IV-V", true, 4_000, null, 0, null, 1_000L + it) }, null)
        }
        var opened: String? = null
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { LoopsScreen(onBack = {}, onOpenSong = { opened = it }) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("узнаю 1 из 1").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("узнаю").fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("►").onFirst().performClick()
        rule.waitUntil(5_000) { audio.played.isNotEmpty() }
        val heard = audio.played.single()
        assertTrue(heard.notes.all { it.voice == Voice.PIANO || it.voice == Voice.BASS })
        assertEquals("the cadence and the four chords of the loop", 8, heard.notes.map { it.startBeat }.toSet().size)
        rule.onAllNodesWithText(standByMe.song.title, substring = true).onFirst().performClick()
        rule.waitUntil(5_000) { opened != null }
        assertEquals(SongBook.libraryId(standByMe), opened)
    }
}
