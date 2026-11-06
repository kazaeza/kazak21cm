package com.kazaeza.muzykalka

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun startsOnTodayAndAllTabsOpen() {
        rule.onNodeWithText("Начать сет", substring = true).assertIsDisplayed()
        for (tab in listOf("Режимы", "Библиотека", "Лексикон", "Прогресс", "Сегодня")) {
            rule.onAllNodesWithText(tab).onFirst().performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText("ИГРАЮ С ХОДУ").assertIsDisplayed()
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
    fun libraryExampleCanBeAddedAndOpened() {
        rule.onAllNodesWithText("Библиотека").onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Пример из плана: Autumn Leaves").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Autumn Leaves").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Autumn Leaves").onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Заказать сейчас").assertIsDisplayed()
    }

    @Test
    fun modesShowTheSixTiles() {
        rule.onAllNodesWithText("Режимы").onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Тир").assertIsDisplayed()
        rule.onNodeWithText("Охота").assertIsDisplayed()
    }
}
