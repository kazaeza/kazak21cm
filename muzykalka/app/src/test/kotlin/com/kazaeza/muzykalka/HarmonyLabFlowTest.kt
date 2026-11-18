package com.kazaeza.muzykalka

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
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
import com.kazaeza.muzykalka.feature.lab.HarmonyLabScreen
import com.kazaeza.muzykalka.feature.lab.HarmonyLabViewModel
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O-48 «Созвучия» (ADR-047): an interval is held and described; a chord is built by hand and its pull is heard. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HarmonyLabFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val audio = FakeAudioOutput()

    private fun open() {
        val graph = object : AppGraph(ApplicationProvider.getApplicationContext()) {
            override val audioOutput: AudioOutput = audio
        }
        rule.setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                MuzykalkaTheme { HarmonyLabScreen(onBack = {}) }
            }
        }
    }

    /** The held sounds as semitones above the lowest of them. */
    private fun held(): List<Int> = audio.drones.last().orEmpty().let { notes -> notes.map { it.value - notes.minOf { n -> n.value } } }

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true, ignoreCase = true))
        rule.waitForIdle()
    }

    private fun choose(label: String) {
        scrollTo(label)
        rule.onAllNodes(hasClickAction() and hasText(label)).onFirst().performClick()
        rule.waitForIdle()
    }

    @Test
    fun anIntervalIsHeldAndTold() {
        open()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Интервал").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("silent until something is picked", audio.drones.isEmpty())
        rule.onAllNodesWithText("5").onFirst().performClick()
        rule.waitUntil(5_000) { audio.drones.isNotEmpty() }
        assertEquals("the lower sound and the fifth above it, held", listOf(0, 7), held())
        scrollTo("квинта")
        rule.onNodeWithText("3 : 2").assertExists()
        rule.onNodeWithText("каркас").assertExists()
        rule.onNodeWithText("3-я гармоника нижнего звука — это 2-я верхнего", substring = true).assertExists()
        // the «1» of the dial is the octave: a unison would be one sound
        scrollTo("Интервал")
        rule.onAllNodesWithText("1").onFirst().performClick()
        rule.waitUntil(5_000) { held() == listOf(0, 12) }
        scrollTo("октава")
        rule.onNodeWithText("2 : 1").assertExists()
        scrollTo("■")
        rule.onNodeWithText("■").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("►").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aChordIsBuiltByHandAndItsPullIsHeard() {
        open()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Аккорд").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Аккорд").performClick()
        rule.waitForIdle()
        assertTrue("a switch has to be touched first", audio.drones.filterNotNull().isEmpty())
        choose("♭3")
        rule.waitUntil(5_000) { audio.drones.lastOrNull() != null }
        assertEquals("the bass, its octave and the minor triad", listOf(0, 12, 15, 19), held())
        // a major third with a minor seventh: the tritone inside, the dominant
        choose("3")
        choose("♭7")
        rule.waitUntil(5_000) { held() == listOf(0, 16, 19, 22) }
        scrollTo("Внутри тритон")
        scrollTo("Послушать, куда тянет")
        val before = audio.drones.size
        rule.onNodeWithText("Послушать, куда тянет").performClick()
        // the chord a fourth above sounds, then the built chord comes back
        rule.waitUntil(5_000) { audio.drones.size >= before + 1 }
        val target = audio.drones[before].orEmpty().map { it.value }
        // the hold is a delay on the main looper; with nothing animating on the screen its clock stands still in a test
        org.robolectric.shadows.ShadowLooper.idleMainLooper(HarmonyLabViewModel.PULL_HOLD_MS + 200, java.util.concurrent.TimeUnit.MILLISECONDS)
        rule.waitUntil(5_000) { audio.drones.size >= before + 2 }
        val back = audio.drones[before + 1].orEmpty().map { it.value }
        assertEquals("a major triad on its own bass", listOf(0, 12, 16, 19), target.map { it - target.min() })
        assertEquals("a fourth above the built chord's bass", 5, target.min() - back.min())
        assertEquals(listOf(0, 16, 19, 22), back.map { it - back.min() })
        // a hanging fourth in the place of the third: another pull, told in words
        choose("4")
        rule.waitUntil(5_000) { held() == listOf(0, 17, 19, 22) }
        scrollTo("Кварта висит над терцией")
    }

    /** O-49: the chord of a key held over the humming tonic — the body stays, the role changes. */
    @Test
    fun aChordOfTheKeyIsHeldOverTheTonicAndItsRoleIsTold() {
        open()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("В ладу").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("В ладу").performClick()
        rule.waitForIdle()
        assertTrue("silent until a chord is picked", audio.drones.filterNotNull().isEmpty())
        choose("5") // O-52: the chord is picked on the dial, by the degree it stands on
        rule.waitUntil(5_000) { audio.drones.lastOrNull() != null }
        assertEquals("the tonic low, the V triad above it", listOf(0, 19, 23, 26), held())
        scrollTo("доминанта")
        scrollTo("Та же фигура стоит на")
        choose("4")
        rule.waitUntil(5_000) { held() == listOf(0, 17, 21, 24) }
        scrollTo("Домой")
        rule.onAllNodes(hasClickAction() and hasText("Домой", substring = true)).onFirst().performClick()
        rule.waitUntil(5_000) { held() == listOf(0, 12, 16, 19) }
        choose("минор")
        rule.waitUntil(5_000) { held() == listOf(0, 12, 15, 19) }
    }

    /** O-60: the chord is on three visible tracks; a preset sets all voices; a puzzle hums unnamed until it is opened. */
    @Test
    fun theChordIsOnTracksWithPresetsAndAPuzzleToBuildByEar() {
        open()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Аккорд").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Аккорд").performClick()
        rule.waitForIdle()
        scrollTo("корень — звучит всегда")
        scrollTo("квинта")
        scrollTo("Загадай аккорд")
        rule.onNodeWithText("Загадай аккорд").assertExists()
        choose("минор") // a preset: the minor triad in one press
        rule.waitUntil(5_000) { audio.drones.lastOrNull() != null }
        assertEquals("the bass, its octave and the minor triad", listOf(0, 12, 15, 19), held())
        choose("доминантсептаккорд")
        rule.waitUntil(5_000) { held() == listOf(0, 16, 19, 22) } // the dominant: bass, third, fifth, seventh
        val before = audio.drones.size
        choose("Загадай аккорд")
        rule.waitUntil(5_000) { audio.drones.size > before }
        assertTrue("the puzzle hums, and has no name yet", !rule.onAllNodesWithText("Это был", substring = true).fetchSemanticsNodes().isNotEmpty())
        choose("Открыть")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Это был", substring = true).fetchSemanticsNodes().isNotEmpty() }
        choose("Хватит")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Загадай аккорд").fetchSemanticsNodes().isNotEmpty() }
    }
}
