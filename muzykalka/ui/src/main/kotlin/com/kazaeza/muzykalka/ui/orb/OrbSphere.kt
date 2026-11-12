package com.kazaeza.muzykalka.ui.orb

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import com.kazaeza.muzykalka.music.OrbBody
import com.kazaeza.muzykalka.music.OrbPhysics
import com.kazaeza.muzykalka.music.PulseKind
import com.kazaeza.muzykalka.ui.effect.LensOrb
import com.kazaeza.muzykalka.ui.effect.PulseBoard
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import kotlin.math.hypot

/** O-38: which body the sphere is made of; the app puts the setting here, everything else (tests, previews) gets the drop. */
val LocalOrbBody = staticCompositionLocalOf { OrbBody.DROP }

/** The degree that has been answered, for the sphere: its semitone and the colour it takes (red for a wrong choice). */
data class OrbAnswer(val semitones: Int, val color: Color)

/** Where the first pluck of a finger strikes when the screen gives no direction: lower half, like the sounds. */
private const val TOUCH_REACH = 0.7f

/** What a finger's pluck is worth to a body (the drop's own is the same 0.6, see LensOrb). */
private const val TOUCH_GAIN = 0.6f

/**
 * O-38 (ADR-040): the sphere in the middle of the dial. [body] chooses what it is made of: thread, sand, ink (OpenGL ES 3,
 * drawn on a thread of its own) or the drop ([LensOrb], one AGSL shader). If OpenGL is not there, a body fails, or the
 * preview/test has no GPU, it is the drop whatever was asked — the screen never goes empty.
 *
 * The sphere takes [sphere] of the half-side of its box, the same as [LensOrb]; the box of the OpenGL bodies is drawn
 * a little larger around it (the ball is 0.42 of it) so that the halo has room. [pulses] are the plucks to show,
 * [answer] is the degree that has been answered, [touch] is the direction (screen, y down) of the key under the finger.
 */
@Composable
fun OrbSphere(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    energy: Float = 0.5f,
    sphere: Float = 0.62f,
    pulses: PulseBoard? = null,
    touch: Offset = Offset.Zero,
    lobes: Int = 0,
    tension: Float = 0f,
    answer: OrbAnswer? = null,
    body: OrbBody = LocalOrbBody.current,
) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val capable = remember(context) { !preview && OrbGlSupport.available(context) }
    var failed by remember(body) { mutableStateOf(OrbGlSupport.failed(body)) }
    if (body == OrbBody.DROP || !capable || failed) {
        LensOrb(colors, modifier, energy = energy, sphere = sphere, pulses = pulses, touch = touch, lobes = lobes, tension = tension)
        return
    }
    val input = remember { OrbInput() }
    LaunchedEffect(answer) {
        if (answer == null) {
            input.clearAnswer()
        } else {
            input.setAnswer(answer.semitones, answer.color.red, answer.color.green, answer.color.blue)
        }
    }
    // a pluck of the sound is a pluck of the body: where it is struck follows the sound, a finger strikes towards its key
    val lean by rememberUpdatedState(touch)
    DisposableEffect(pulses, input) {
        val off = pulses?.addListener { p ->
            val l = lean
            val (x, y) = if (p.kind == PulseKind.TOUCH && (l.x != 0f || l.y != 0f)) {
                val n = hypot(l.x, l.y)
                (l.x / n * TOUCH_REACH) to (-l.y / n * TOUCH_REACH)
            } else {
                OrbPhysics.pluckPoint(p.x)
            }
            input.pluck(p.strength * (if (p.kind == PulseKind.TOUCH) TOUCH_GAIN else 1f), x, y)
        }
        onDispose { off?.invoke() }
    }
    val gold = remember { DegreeColors.ofSemitone(0).let { floatArrayOf(it.red, it.green, it.blue) } }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val box = min(maxWidth, maxHeight) * (sphere / (2f * OrbPhysics.RADIUS.toFloat()))
        // another body is another surface: the factory of an AndroidView runs once, so the body is its key
        key(body) {
            AndroidView(
                factory = { ctx ->
                    OrbSurfaceView(
                        ctx,
                        make = { gl -> createRenderer(body, gl, input, gold) },
                        onFailed = {
                            OrbGlSupport.markFailed(body)
                            failed = true
                        },
                    )
                },
                modifier = Modifier.requiredSize(box),
            )
        }
    }
}
