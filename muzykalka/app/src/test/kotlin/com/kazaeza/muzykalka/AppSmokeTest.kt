package com.kazaeza.muzykalka

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsActions
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
import org.junit.Rule
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
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** Screens are lazy lists: items below the fold exist only after scrolling to them. */
    private fun scrollTo(text: String) {
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
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
        rule.onNodeWithText("ИГРАЮ С ХОДУ").assertIsDisplayed()
        rule.onAllNodesWithText("Лексикон").assertCountEquals(0)
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

    @Test
    fun songbookPieceCanBeAddedAndOpened() {
        rule.onNodeWithContentDescription("Библиотека").performClick()
        rule.waitForIdle()
        scrollTo("Covet")
        click("В мои")
        // The piece lands in «my songs» at the top of the list.
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("повтор:", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        click("Covet")
        rule.waitForIdle()
        rule.onNodeWithText("Сыграть сейчас").assertIsDisplayed()
    }

    /** O-18: the «хиты 60–80-х» shelf is one chip away, and a piece can be read before it is added. */
    @Test
    fun hitsShelfIsOneChipAway() {
        rule.onNodeWithContentDescription("Библиотека").performClick()
        rule.waitForIdle()
        scrollTo("хиты 60–80-х")
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
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(t, substring = true))
            rule.waitForIdle()
        }
        rule.onAllNodesWithText("КАК ИГРАТЬ").assertCountEquals(0)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Вайб ступеней", substring = true))
        rule.onNodeWithContentDescription("Играть: Вайб ступеней").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText("Ступень — это не высота, а ощущение").assertIsDisplayed()
    }
}
