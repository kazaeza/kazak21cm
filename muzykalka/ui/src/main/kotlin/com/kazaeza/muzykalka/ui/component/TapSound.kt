package com.kazaeza.muzykalka.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf

/**
 * O-36: what plays when a button is pressed — a small wooden tap. `low = true` is the second voice (back, cancel,
 * pause): the same stick, a little lower and softer. The default does nothing; `app` provides the real one from the
 * settings (and keeps it quiet while a question is being asked or the microphone is open). `ui` does not depend on
 * `audio`: it only calls this.
 */
val LocalTapSound = compositionLocalOf<(low: Boolean) -> Unit> { {} }

/**
 * Plays the tap the moment [source] sees a press — on the finger going down, not on its going up, so the sound is
 * where the finger is. A press that turns into a scroll is cancelled before it counts, so lists stay quiet.
 */
@Composable
fun TapOnPress(source: MutableInteractionSource, low: Boolean = false) {
    val tap = LocalTapSound.current
    LaunchedEffect(source, tap, low) {
        source.interactions.collect { if (it is PressInteraction.Press) tap(low) }
    }
}
