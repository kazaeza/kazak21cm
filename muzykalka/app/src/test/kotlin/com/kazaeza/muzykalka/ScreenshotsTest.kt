package com.kazaeza.muzykalka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.ui.component.FloatingTabBar
import com.kazaeza.muzykalka.ui.component.PitchRuler
import com.kazaeza.muzykalka.ui.component.ReplayKey
import com.kazaeza.muzykalka.ui.component.TabIcon
import com.kazaeza.muzykalka.ui.component.TabSpec
import com.kazaeza.muzykalka.ui.theme.Juicy
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    /** «Угадай про себя» during the pause. */
    @Test
    fun listen() = shot("04f-listen", { com.kazaeza.muzykalka.feature.listen.ListenScreen(onBack = {}) }, "Слушать") {
        rule.onAllNodesWithText("Слушать", substring = false)[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Назови про себя").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun underNote() = shot("04e-under-note", { GameScreen(RoundSpec(Variant.TIR_UNDER_NOTE, level = 2), onExit = {}) }, "Какой аккорд под ней?")

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

    /** O-28, O-33: «Спой ступень» — the ruler waits under the question. */
    @Test
    fun sing() {
        org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        shot("04g-sing", { GameScreen(RoundSpec(Variant.KAMERTON_SING, level = 1), onExit = {}) }, "Найди её") {
            mic.sing(com.kazaeza.muzykalka.music.MidiNote(50))
            rule.waitUntil(5_000) { rule.onAllNodesWithText("спел", substring = true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("+", substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    /** «Обертон» kit (O-31…O-33): the tab bar's marks, the replay key, the singing ruler — to look at, side by side. */
    @Test
    fun kit() = shot("10-kit", {
        val tabs = listOf(
            TabSpec("Сегодня", TabIcon.TODAY), TabSpec("Режимы", TabIcon.MODES),
            TabSpec("Библиотека", TabIcon.LIBRARY), TabSpec("Прогресс", TabIcon.PROGRESS),
        )
        Column(
            Modifier.fillMaxSize().background(Juicy.Ink).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FloatingTabBar(tabs, 0, {})
            FloatingTabBar(tabs, 2, {})
            Row { ReplayKey("Повторить вопрос", {}); ReplayKey("Повторить вопрос", {}, enabled = false) }
            PitchRuler(null, Juicy.Cyan)
            PitchRuler(-180f, Juicy.Cyan)
            PitchRuler(20f, Juicy.Cyan)
            PitchRuler(420f, Juicy.Lime)
        }
    })

    /** O-26: the dial after an answer — the right degree lit, the sphere flooded with its colour. */
    @Test
    fun tirAnswered() = shot("04a-tir-answer", { GameScreen(RoundSpec(Variant.TIR_CLASSIC, level = 2), onExit = {}) }, "Какая ступень?") {
        rule.onAllNodesWithText("5").onFirst().performClick()
    }

    @Test
    fun reverseHunt() = shot("05-reverse-hunt", { GameScreen(RoundSpec(Variant.HUNT_REVERSE, level = 1), onExit = {}) }, "лад")

    @Test
    fun taper() {
        runBlocking { graph.library.addFromBook(SongBook.byId("autumn-leaves")!!) }
        shot("06-taper", { TaperScreen(TaperSpec(), onExit = {}) }, "СЫГРАЙ")
    }

    /** O-17: a tapped chord of the sheet — its words are pinned under the scroll. */
    @Test
    fun taperChordTap() {
        runBlocking { graph.library.addFromBook(SongBook.byId("autumn-leaves")!!) }
        shot("06b-taper-chord", { TaperScreen(TaperSpec(), onExit = {}) }, "СЫГРАЙ") {
            rule.onAllNodesWithText("V⁷", substring = false)[0].performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("доминанта", substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun progress() = shot("07-progress", { ProgressScreen(onExam = {}) }, "Прогресс")
}
