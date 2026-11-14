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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.kazaeza.muzykalka.music.Overtones
import com.kazaeza.muzykalka.music.PulseKind
import com.kazaeza.muzykalka.music.SoundPulse
import com.kazaeza.muzykalka.ui.effect.HarmonicWaves
import com.kazaeza.muzykalka.ui.effect.LensOrb
import com.kazaeza.muzykalka.ui.effect.PulseBoard
import com.kazaeza.muzykalka.ui.effect.WaveReveal
import com.kazaeza.muzykalka.ui.theme.DegreeColors
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
    fun today() {
        runBlocking { graph.bookSeeder.run() } // O-44: with a library there is a song of the day
        shot("01-today", { TodayScreen({}, {}, {}, {}, {}, {}) }, "Начать сет")
    }

    @Test
    fun modes() = shot("02-modes", { ModesScreen(onPlay = { _, _ -> }) }, "Над аккордами")

    @Test
    fun library() {
        runBlocking {
            graph.library.addFromBook(SongBook.byId("autumn-leaves")!!)
            graph.bookSeeder.run() // O-42: the rest of the book lies on the shelf
        }
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
    fun underNote() = shot("04e-under-note", { GameScreen(RoundSpec(Variant.TIR_UNDER_NOTE, level = 2), onExit = {}) }, "Какой аккорд сыграли?")

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

    /** S-03: «Дуэль» — two keys on the dial and the count of the row. */
    @Test
    fun duel() = shot("04j-duel", { GameScreen(RoundSpec(Variant.TIR_DUEL), onExit = {}) }, "Какая ступень?")

    /** O-43: «Чья гармония?» — the question with four songs, and the answer with the opening in degrees. */
    @Test
    fun songQuiz() {
        runBlocking { graph.library.seedBook(emptySet()) }
        shot("04h-song-quiz", { GameScreen(RoundSpec(Variant.TIR_SONG, level = 1), onExit = {}) }, "Чья это гармония?")
    }

    @Test
    fun songQuizAnswered() {
        runBlocking { graph.library.seedBook(emptySet()) }
        shot("04i-song-quiz-answer", { GameScreen(RoundSpec(Variant.TIR_SONG, level = 3), onExit = {}) }, "Чья это гармония?") {
            rule.onAllNodes(hasClickLabel("Выбрать песню")).onFirst().performClick()
        }
    }

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

    /** O-35 / O-37: the sphere with a wave on its way out — a pluck 0.03 … 0.55 s ago, side by side. */
    @Test
    fun orbRing() = shot("11-orb-ring", {
        val colors = listOf(Juicy.Blue, Juicy.Violet, Juicy.Magenta, Juicy.Cyan)
        Column(Modifier.fillMaxSize().background(Juicy.Ink).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // O-37: the wave as it runs over the drop (compare docs/design/lab/drop-pluck.jpg)
            listOf(listOf(0f, 0.1f, 0.3f, 0.55f), listOf(0.03f, 0.14f, 0.28f, 0.46f)).forEach { ages ->
                Row {
                    ages.forEach { age ->
                        val board = PulseBoard.frozen(age to SoundPulse(PulseKind.HOME, 0L, 1f, 0, 0.5f))
                        LensOrb(colors, Modifier.size(96.dp), pulses = board, moving = false, sphere = 0.7f)
                    }
                }
            }
            // the same, big, to judge the swell
            LensOrb(colors, Modifier.size(300.dp), pulses = PulseBoard.frozen(0.28f to SoundPulse(PulseKind.HOME, 0L, 1f, 0, 0.5f)), moving = false, sphere = 0.7f)
            // leaning towards a pressed degree (top right), and two plucks at once
            LensOrb(
                colors, Modifier.size(200.dp), moving = false, sphere = 0.7f, touch = Offset(0.7f, -0.7f),
                pulses = PulseBoard.frozen(0.1f to SoundPulse(PulseKind.TOUCH, 0L, 0.8f, null, 0.2f), 0.35f to SoundPulse(PulseKind.REVEAL, 0L, 1f, 7, 0.31f)),
            )
        }
    })

    /** O-35: the pattern a degree leaves in the sphere after the answer: a fifth (3 lobes), a third (5), a minor second (16). */
    @Test
    fun orbLobes() = shot("12-orb-lobes", {
        Column(Modifier.fillMaxSize().background(Juicy.Ink).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            listOf(listOf(0, 7, 4), listOf(1, 3, 2)).forEach { row ->
                Row {
                    row.forEach { semitones ->
                        val d = com.kazaeza.muzykalka.music.Degree.canonical(semitones)
                        val tint = DegreeColors.of(d)
                        val (p, _) = Overtones.ratio(semitones)
                        LensOrb(
                            listOf(tint, lerp(tint, Color.White, 0.4f), Juicy.Magenta, tint), Modifier.size(130.dp), moving = false, sphere = 0.7f,
                            lobes = p, tension = Overtones.tension(semitones),
                        )
                    }
                }
            }
            LensOrb(listOf(Juicy.Blue, Juicy.Violet, Juicy.Magenta, Juicy.Cyan), Modifier.size(130.dp), moving = false, sphere = 0.7f)
        }
    })

    /** O-35: the string — a pluck 0.05, 0.4 and 1.5 s ago at 0.31 and at the middle; a fifth and a minor second after the answer. */
    @Test
    fun strings() = shot("13-strings", {
        fun at(age: Float, x: Float, kind: PulseKind = PulseKind.REVEAL) = PulseBoard.frozen(age to SoundPulse(kind, 0L, 1f, null.takeIf { kind == PulseKind.QUESTION } ?: 0, x))
        val accent = listOf(Juicy.Cyan, Juicy.Violet)
        Column(Modifier.fillMaxSize().background(Juicy.Ink).padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.31f, 0.5f).forEach { x ->
                listOf(0.05f, 0.4f, 1.5f).forEach { age ->
                    HarmonicWaves(accent, Modifier.fillMaxWidth().height(44.dp), energy = 0.3f, pulses = at(age, x))
                }
            }
            HarmonicWaves(accent, Modifier.fillMaxWidth().height(44.dp), energy = 0.3f, pulses = PulseBoard.frozen(), reveal = WaveReveal.of(7, DegreeColors.of(com.kazaeza.muzykalka.music.Degree.FIVE)))
            HarmonicWaves(accent, Modifier.fillMaxWidth().height(44.dp), energy = 0.3f, pulses = PulseBoard.frozen(), reveal = WaveReveal.of(4, DegreeColors.of(com.kazaeza.muzykalka.music.Degree.THREE)))
            HarmonicWaves(accent, Modifier.fillMaxWidth().height(44.dp), energy = 0.3f, pulses = PulseBoard.frozen(), reveal = WaveReveal.of(1, Juicy.Bad))
        }
    })

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
