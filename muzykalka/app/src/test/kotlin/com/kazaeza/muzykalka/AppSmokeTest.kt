package com.kazaeza.muzykalka

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.kazaeza.muzykalka.data.settings.BookSeedStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Startup guard: the APK must open and every tab must render. Runs on the JVM (Robolectric) in CI
 * on every push — the app has no other way to catch a crash before it hits the phone.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AppSmokeTest {
    private val rule = createAndroidComposeRule<MainActivity>()

    /**
     * O-42: the app puts the songbook into the library at start, once per piece; the ledger of what was put in lives in a
     * DataStore that the test JVM keeps from one test to the next while every test has a new database — so it starts blank.
     */
    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            TestRule { base, _ ->
                object : Statement() {
                    override fun evaluate() {
                        runBlocking { BookSeedStore(ApplicationProvider.getApplicationContext()).importJson(null) }
                        base.evaluate()
                    }
                }
            },
        )
        .around(rule)

    /**
     * The list of the tab on screen. The tabs are pages of a pager (O-29): the pager scrolls too, and a
     * neighbouring page may still be composed, so «the scrollable» is no longer one node.
     */
    private fun list(): SemanticsNodeInteraction {
        val lists = rule.onAllNodes(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        return lists.fetchSemanticsNodes().indices.map { lists[it] }.first { it.isDisplayed() }
    }

    /** Screens are lazy lists: items below the fold exist only after scrolling to them. */
    private fun scrollTo(text: String) {
        list().performScrollToNode(hasText(text))
        rule.waitForIdle()
    }

    /** By semantics, not by touch: the floating tab bar may cover the bottom of the list. */
    private fun click(text: String) {
        rule.onAllNodes(hasClickAction() and hasText(text, substring = true)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    @Test
    fun startsOnTodayAndAllTabsOpen() {
        rule.onNodeWithText("Начать сет", substring = true).assertIsDisplayed()
        // Four tabs only, found by their description (O-24: the tabs are words, the text is not in semantics twice).
        for (tab in listOf("Режимы", "Библиотека", "Прогресс", "Сегодня")) {
            rule.onNodeWithContentDescription(tab).performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText("ЗА НЕДЕЛЮ").assertIsDisplayed()
        rule.onAllNodesWithText("Лексикон").assertCountEquals(0)
    }

    /** O-29: the tabs are pages — a swipe across the screen turns them, and the bar follows. */
    @Test
    fun swipeTurnsTheTabs() {
        rule.onNodeWithText("Начать сет", substring = true).assertIsDisplayed()
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("всё в одном месте: выбирай и играй").assertIsDisplayed()
        rule.onNodeWithContentDescription("Режимы").assertIsSelected()
        rule.onRoot().performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithText("Начать сет", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Сегодня").assertIsSelected()
    }

    @Test
    fun settingsOpenAndAcceptAChoice() {
        rule.onNodeWithContentDescription("Настройки").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Drop D (D A D G B E)").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("СТРОЙ").assertIsDisplayed()
        rule.onNodeWithText("←").performClick()
        rule.onNodeWithText("Начать сет", substring = true).assertIsDisplayed()
    }

    /** O-42: the songbook is put into the library at start (in the background); the shelf chips show up with the first piece. */
    private fun waitForTheBook() {
        rule.waitUntil(30_000) { rule.onAllNodes(hasText("учу 0")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** O-42 (ADR-041): a piece of the book is in the library by itself; open it, put it in the repeats, play it. */
    @Test
    fun songbookPieceIsInTheLibraryAndCanBeStartedAndOpened() {
        rule.onNodeWithContentDescription("Библиотека").performClick()
        rule.waitForIdle()
        waitForTheBook()
        scrollTo("Covet")
        click("Covet")
        rule.waitForIdle()
        rule.onNodeWithText("повтор: пока не учу", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Учить: поставить в повторы").assertIsDisplayed()
        click("Учить: поставить в повторы")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("повтор: при первой возможности", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Сыграть сейчас").assertIsDisplayed()
        rule.onAllNodesWithText("Учить: поставить в повторы").assertCountEquals(0)
    }

    /** O-18, O-42: the «хиты 60–80-х» shelf is one chip away, and a piece can be read before it is learned. */
    @Test
    fun hitsShelfIsOneChipAway() {
        rule.onNodeWithContentDescription("Библиотека").performClick()
        rule.waitForIdle()
        waitForTheBook()
        list().performScrollToNode(hasText("хиты 60–80-х", substring = true))
        rule.waitForIdle()
        click("хиты 60–80-х")
        rule.onAllNodesWithText("Covet").assertCountEquals(0)
        scrollTo("With or Without You")
        rule.onNodeWithText("With or Without You").assertIsDisplayed()
        rule.onNodeWithText("I–V–vi–IV: четыре аккорда, по такту на каждый").assertIsDisplayed()
    }

    @Test
    fun feelLessonOpensFromToday() {
        scrollTo("Вайб ступеней")
        rule.onNodeWithText("Вайб ступеней").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Ступень — это не высота, а ощущение").assertIsDisplayed()
    }

    /** O-12: every mode is on the «Режимы» tab itself — «Вслепую» and «Вайб ступеней» included. */
    @Test
    fun modesListEveryModeOnOneScreen() {
        rule.onNodeWithContentDescription("Режимы").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Тир").onFirst().assertIsDisplayed()
        for (t in listOf("Над аккордами", "Вайб ступеней", "Спой ступень", "Ходы", "Песни", "Вслепую", "Обратная охота")) {
            list().performScrollToNode(hasText(t, substring = true))
            rule.waitForIdle()
        }
        rule.onAllNodesWithText("КАК ИГРАТЬ").assertCountEquals(0)
        list().performScrollToNode(hasText("Вайб ступеней", substring = true))
        rule.onNodeWithContentDescription("Играть: Вайб ступеней").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText("Ступень — это не высота, а ощущение").assertIsDisplayed()
    }
}
