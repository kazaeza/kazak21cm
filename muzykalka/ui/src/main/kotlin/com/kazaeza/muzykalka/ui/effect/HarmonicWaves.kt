package com.kazaeza.muzykalka.ui.effect

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.music.Overtones
import com.kazaeza.muzykalka.music.PulseKind
import com.kazaeza.muzykalka.music.SoundPulse
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * O-35: after the answer the string shows how the note stands against the tonic — two waves, the tonic's
 * (white, [TONIC_PERIODS] periods across the width) and the note's, in [tint], [periods] periods across.
 * The note's wave creeps against the tonic's at [drift] rad/s: a fifth stands still, a minor second crawls.
 */
data class WaveReveal(val tint: Color, val periods: Float, val drift: Float) {
    companion object {
        /** Periods of the tonic's wave across the width. */
        const val TONIC_PERIODS = 2f

        /** The two waves of a note [semitones] above the tonic: p:q periods and a drift that follows the tension. */
        fun of(semitones: Int, tint: Color, driftPerTension: Float = 0.6f): WaveReveal {
            val (p, q) = Overtones.ratio(semitones)
            return WaveReveal(tint, TONIC_PERIODS * p / q, driftPerTension * Overtones.tension(semitones))
        }
    }
}

/** How high the resting string breathes, against the full swing of a plucked one. */
private const val REST_SWING = 0.12f

/** A plucked string rings at [STRING_HZ] times the number of its harmonic: slow, so the eye can follow. */
private const val STRING_HZ = 1.1f

/** Harmonics of a plucked string; the first two are white, the rest take the accent. */
private const val STRING_HARMONICS = 6

/**
 * O-26: a sound drawn as the references draw a reading — a hairline, and on it thin waves lying over each
 * other. They are a tone and its overtones: the k-th wave is k times shorter, runs k times faster and is √k
 * times lower (k = 1…[harmonics]); the first one is white, the rest take [colors]. The waves swell in the
 * middle and die away to the hairline at both ends.
 *
 * O-35: with a [pulses] board it is a string instead. At rest it only breathes; a pluck sets six standing
 * harmonics ringing — they fall the faster the higher they are, as on a real string — and the plucks add up.
 * A [reveal] draws the tonic and a note over each other on the same line. Without [pulses] it is the old picture.
 *
 * [energy] 0…1 is how loud the sound is: at 0 the waves are half as high and slow, at 1 they fill the height and hurry.
 * The height of the box is the room for the swing; the hairline runs through its middle.
 */
@Composable
fun HarmonicWaves(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    energy: Float = 0.5f,
    harmonics: Int = 4,
    pulses: PulseBoard? = null,
    reveal: WaveReveal? = null,
) {
    val e by animateFloatAsState(energy.coerceIn(0f, 1f), tween(Motion.LONG), label = "waves")
    val time = rememberFlowSeconds(2.4f) { 0.35f + 1.1f * e }
    val path = remember { Path() }
    val tints = colors.ifEmpty { listOf(Juicy.Cyan) }

    // the reveal fades in and out; its last shape is kept while it fades, and a new one flows out of the old
    var kept by remember { mutableStateOf(reveal) }
    if (reveal != null) kept = reveal
    val shown = reveal ?: kept
    val revealAlpha by animateFloatAsState(if (reveal != null) 1f else 0f, tween(Motion.LONG), label = "revealAlpha")
    val flow = tween<Float>(400)
    val periods by animateFloatAsState(shown?.periods ?: WaveReveal.TONIC_PERIODS, flow, label = "periods")
    val drift by animateFloatAsState(shown?.drift ?: 0f, flow, label = "drift")
    val revealTint by animateColorAsState(shown?.tint ?: Juicy.Cyan, tween(400), label = "revealTint")
    val creep = rememberFlowSeconds(0f) { drift }

    Canvas(modifier) {
        val w = size.width
        val mid = size.height / 2
        val room = mid - 1.dp.toPx()
        val t = time.floatValue
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.16f), Color.Transparent)),
            Offset(0f, mid),
            Offset(w, mid),
            1.dp.toPx(),
        )
        val steps = 120
        if (pulses == null) {
            val swing = room * (0.5f + 0.5f * e)
            for (k in harmonics downTo 1) {
                path.rewind()
                for (i in 0..steps) {
                    val x = i / steps.toFloat()
                    val bell = sin(PI * x).toFloat()
                    val y = mid + swing / sqrt(k.toFloat()) * bell * sin(2 * PI * (1.25 * k * x) - t * 1.6 * k + k * 1.7).toFloat()
                    if (i == 0) path.moveTo(0f, y) else path.lineTo(x * w, y)
                }
                val colour = if (k == 1) Juicy.Text else tints[(k - 2) % tints.size]
                val alpha = (if (k == 1) 0.95f else 0.85f) * (0.55f + 0.45f * e)
                drawPath(path, colour.copy(alpha = alpha), style = Stroke(1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        } else {
            // at rest: the same slow picture, only a breath of it
            val rest = room * REST_SWING * (0.6f + 0.4f * e)
            for (k in harmonics downTo 1) {
                path.rewind()
                for (i in 0..steps) {
                    val x = i / steps.toFloat()
                    val bell = sin(PI * x).toFloat()
                    val y = mid + rest / sqrt(k.toFloat()) * bell * sin(2 * PI * (1.25 * k * x) - t * 1.6 * k + k * 1.7).toFloat()
                    if (i == 0) path.moveTo(0f, y) else path.lineTo(x * w, y)
                }
                val colour = if (k == 1) Juicy.Text else tints[(k - 2) % tints.size]
                drawPath(path, colour.copy(alpha = if (k == 1) 0.7f else 0.55f), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawStrings(pulses.live(), room, mid, w, steps, path, tints[0])
        }
        if (revealAlpha > 0.01f) drawReveal(revealTint, periods, creep.floatValue, revealAlpha, room, mid, w, steps, path)
    }
}

/** The plucked strings: the sum of the standing harmonics of every live pluck, low ones white, high ones in [tint]. */
private fun DrawScope.drawStrings(live: List<LivePulse>, room: Float, mid: Float, w: Float, steps: Int, path: Path, tint: Color) {
    if (live.isEmpty()) return
    // the weights for one pluck are at most about 1.8 in sum: scale so that a hard pluck fills the box and no more
    val scale = room * 0.85f
    for (high in listOf(true, false)) {
        path.rewind()
        var any = false
        for (i in 0..steps) {
            val x = i / steps.toFloat()
            var y = 0f
            for (lp in live) {
                val x0 = lp.pulse.x
                for (k in (if (high) 3 else 1)..(if (high) STRING_HARMONICS else 2)) {
                    val a = lp.strength * Overtones.harmonicWeight(k, x0) * Overtones.envelope(k, lp.age)
                    if (a != 0f) any = true
                    y += a * sin(k * PI * x).toFloat() * cos(2 * PI * STRING_HZ * k * lp.age).toFloat()
                }
            }
            val py = mid + (y * scale).coerceIn(-room, room)
            if (i == 0) path.moveTo(0f, py) else path.lineTo(x * w, py)
        }
        if (!any) continue
        val colour = if (high) tint else Juicy.Text
        drawPath(path, colour.copy(alpha = if (high) 0.85f else 0.95f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The tonic's wave (white) and the note's (coloured) over each other; the note's creeps by [creep] radians. */
private fun DrawScope.drawReveal(tint: Color, periods: Float, creep: Float, alpha: Float, room: Float, mid: Float, w: Float, steps: Int, path: Path) {
    val amp = room * 0.75f
    for (note in listOf(false, true)) {
        path.rewind()
        for (i in 0..steps) {
            val x = i / steps.toFloat()
            val bell = sin(PI * x).toFloat()
            val phase = if (note) 2 * PI * periods * x - creep else 2 * PI * WaveReveal.TONIC_PERIODS * x
            val y = mid + amp * bell * sin(phase).toFloat()
            if (i == 0) path.moveTo(0f, y) else path.lineTo(x * w, y)
        }
        val colour = if (note) tint else Juicy.Text
        drawPath(path, colour.copy(alpha = (if (note) 0.95f else 0.7f) * alpha), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * O-35: the strings can be strummed. Every time a finger crosses the line [line] of the box (a fraction of its
 * height, 1 = the bottom edge) a [PulseKind.TOUCH] pluck goes to [onPluck] at the finger's x, the harder the
 * quicker the finger. It never consumes the touch: whatever lies under still gets its taps. [onTick] is for a
 * haptic tick, asked at most every [TICK_GAP_MS].
 */
fun Modifier.strum(onPluck: (SoundPulse) -> Unit, onTick: () -> Unit = {}, line: Float = 1f): Modifier = pointerInput(line) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val lineY = size.height * line
        var prev = down.position
        var prevAt = down.uptimeMillis
        var lastTick = -TICK_GAP_MS
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val here = change.position
            if (sign(prev.y - lineY) != sign(here.y - lineY) && here.y != lineY) {
                val dt = (change.uptimeMillis - prevAt).coerceAtLeast(1L)
                val speed = abs(here.y - prev.y) / dt / density // dp per ms
                val strength = (0.4f + 0.3f * speed).coerceIn(0.4f, 1f)
                onPluck(SoundPulse(PulseKind.TOUCH, 0L, strength, null, (here.x / size.width).coerceIn(0.02f, 0.98f)))
                if (change.uptimeMillis - lastTick >= TICK_GAP_MS) {
                    onTick()
                    lastTick = change.uptimeMillis
                }
            }
            prev = here
            prevAt = change.uptimeMillis
        }
    }
}

/** A haptic tick no more often than this. */
private const val TICK_GAP_MS = 40L
