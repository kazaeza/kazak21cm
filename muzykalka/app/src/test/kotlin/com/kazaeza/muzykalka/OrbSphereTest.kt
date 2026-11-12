package com.kazaeza.muzykalka

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.music.OrbBody
import com.kazaeza.muzykalka.ui.orb.LocalOrbBody
import com.kazaeza.muzykalka.ui.orb.OrbAnswer
import com.kazaeza.muzykalka.ui.orb.OrbSphere
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * O-38: where there is no OpenGL (these tests, previews, an emulator without ES 3), whatever body is asked for the
 * sphere is the drop: the screen is never empty and nothing throws.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class OrbSphereTest {
    @get:Rule
    val rule = createComposeRule()

    private val colors = listOf(Color.Blue, Color.Magenta, Color.Red, Color.Cyan)

    @Test
    fun everyBodyIsPaintedWithoutOpenGL() {
        var body by mutableStateOf(OrbBody.THREAD)
        rule.setContent {
            CompositionLocalProvider(LocalOrbBody provides body) {
                OrbSphere(colors, Modifier.size(240.dp), sphere = 0.7f, answer = OrbAnswer(7, Color.Green))
            }
        }
        for (b in OrbBody.entries) {
            body = b
            rule.waitForIdle()
        }
    }

    @Test
    fun theDropIsTheDefaultWhereNothingElseIsProvided() {
        var seen: OrbBody? = null
        rule.setContent { seen = LocalOrbBody.current }
        rule.waitForIdle()
        assertEquals(OrbBody.DROP, seen)
    }
}
