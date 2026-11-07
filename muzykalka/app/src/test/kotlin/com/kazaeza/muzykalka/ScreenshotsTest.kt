package com.kazaeza.muzykalka

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.feature.feel.FeelScreen
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.feature.modes.ModesScreen
import com.kazaeza.muzykalka.feature.progress.ProgressScreen
import com.kazaeza.muzykalka.feature.taper.TaperScreen
import com.kazaeza.muzykalka.feature.taper.TaperSpec
import com.kazaeza.muzykalka.feature.today.TodayScreen
import com.kazaeza.muzykalka.feature.library.LibraryScreen
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.SongBook
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Visual QA only: renders key screens to PNG (build/screenshots). Skipped unless the environment
 * variable MUZYKALKA_SCREENSHOTS=1 is set, so CI does not pay for it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotsTest {
    @get:Rule
    val rule = createComposeRule()

    private val audio = FakeAudioOutput()
    private val mic = FakePitchInput()
    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        assumeTrue(System.getenv("MUZYKALKA_SCREENSHOTS") == "1")
        graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
            override val pitchInput: com.kazaeza.muzykalka.audio.PitchInput = mic
        }
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit, waitFor: String? = null, then: () -> Unit = {}) {
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) { MuzykalkaTheme { content() } }
        }
        if (waitFor != null) rule.waitUntil(10_000) { rule.onAllNodesWithText(waitFor, substring = true).fetchSemanticsNodes().isNotEmpty() }
        then()
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun today() = shot("01-today", { TodayScreen({}, {}, {}, {}, {}, {}) }, "Начать сет")

    @Test
    fun modes() = shot("02-modes", { ModesScreen(onPlay = { _, _ -> }) }, "Над аккордами")

    @Test
    fun library() {
        runBlocking { graph.library.addFromBook(SongBook.byId("autumn-leaves")!!) }
        shot("03-library", { LibraryScreen(onCapture = {}, onOpen = {}) }, "Autumn Leaves")
    }

    @Test
    fun steps() = shot("04b-steps", { GameScreen(RoundSpec(Variant.TAPER_STEPS, level = 2), onExit = {}) }, "Получилось")

    @Test
    fun feel() = shot("04c-feel", { FeelScreen(onBack = {}, onTir = {}) }, "Вайб ступеней")

    /** «Пропевка» after one sung note: a miss shows what it was, a hit shows the try. */
    @Test
    fun search() {
        org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        shot("04d-search", { GameScreen(RoundSpec(Variant.KAMERTON_SEARCH, level = 1), onExit = {}) }, "Не спеши") {
            mic.sing(com.kazaeza.muzykalka.music.MidiNote(50))
            rule.waitUntil(5_000) { rule.onAllNodesWithText("попытк", substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun tir() = shot("04-tir", { GameScreen(RoundSpec(Variant.TIR_CLASSIC, level = 2), onExit = {}) }, "Какая ступень?")

    @Test
    fun reverseHunt() = shot("05-reverse-hunt", { GameScreen(RoundSpec(Variant.HUNT_REVERSE, level = 1), onExit = {}) }, "лад")

    @Test
    fun taper() {
        runBlocking { graph.library.addFromBook(SongBook.byId("autumn-leaves")!!) }
        shot("06-taper", { TaperScreen(TaperSpec(), onExit = {}) }, "СЫГРАЙ")
    }

    @Test
    fun progress() = shot("07-progress", { ProgressScreen(onExam = {}) }, "Прогресс")
}
