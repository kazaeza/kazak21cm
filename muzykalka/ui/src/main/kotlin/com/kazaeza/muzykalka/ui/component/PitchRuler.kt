package com.kazaeza.muzykalka.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * O-33: where the voice is — a ruler of ticks with a cursor, after the reference. There are 30 ticks to each
 * side ([rangeCents] / 30 apiece: 10 cents by default, 20 when the ruler spans half an octave), the long ones are
 * semitones, the tall one in the middle is the note asked for (in [tint]). The ticks within [hitCents]
 * of it are the zone that counts as a hit: they stand taller and take the colour, and grow when the cursor is
 * among them — the same gesture as a right answer on the Tir's dial. No letters and no numbers.
 *
 * [offsetCents] is how far the voice is from the note asked for (+ is sharp); null is silence — the cursor fades
 * where it stood. Beyond [rangeCents] the cursor waits at the edge.
 */
private const val TICKS_EACH_SIDE = 30

@Composable
fun PitchRuler(
    offsetCents: Float?,
    tint: Color,
    modifier: Modifier = Modifier,
    rangeCents: Float = 300f,
    hitCents: Float = 50f,
) {
    val held = remember { floatArrayOf(0f) }
    if (offsetCents != null) held[0] = offsetCents.coerceIn(-rangeCents, rangeCents)
    val heard = offsetCents != null
    val pos by animateFloatAsState(held[0], spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow), label = "cursor")
    val presence by animateFloatAsState(if (heard) 1f else 0f, tween(Motion.MEDIUM), label = "voice")
    val hit by animateFloatAsState(if (heard && abs(held[0]) <= hitCents) 1f else 0f, tween(Motion.MEDIUM), label = "hit")
    val marker = remember { Path() }
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        val mid = size.height / 2 + 5.dp.toPx()
        val each = TICKS_EACH_SIDE
        val step = rangeCents / each
        val span = size.width / 2 - 6.dp.toPx()
        fun x(cents: Float) = size.width / 2 + cents / rangeCents * span
        if (hit > 0f) {
            val glow = 44.dp.toPx()
            drawCircle(
                Brush.radialGradient(listOf(tint.copy(alpha = 0.3f * hit), Color.Transparent), Offset(size.width / 2, mid), glow),
                glow, Offset(size.width / 2, mid),
            )
        }
        for (i in -each..each) {
            val cents = i * step
            val fade = ((1f - abs(i) / each.toFloat()) * 3f).coerceIn(0f, 1f)
            val zone = abs(cents) <= hitCents
            val len: Float
            val color: Color
            val width: Float
            when {
                i == 0 -> {
                    len = (28.dp + 6.dp * hit).toPx(); color = tint; width = 2.dp.toPx()
                }
                zone -> {
                    val taper = cos(PI / 2 * abs(cents) / (hitCents + 10f)).toFloat()
                    len = (10.dp + (8.dp + 6.dp * hit) * taper).toPx(); color = tint.copy(alpha = 0.6f + 0.4f * hit); width = 1.25.dp.toPx()
                }
                cents.roundToInt() % 100 == 0 -> {
                    len = 14.dp.toPx(); color = Color.White.copy(alpha = 0.34f * fade); width = 1.dp.toPx()
                }
                else -> {
                    len = 7.dp.toPx(); color = Color.White.copy(alpha = 0.17f * fade); width = 1.dp.toPx()
                }
            }
            drawLine(color, Offset(x(cents), mid - len / 2), Offset(x(cents), mid + len / 2), width, StrokeCap.Round)
        }
        if (presence > 0.01f) {
            val cx = x(pos)
            val top = mid - 20.dp.toPx()
            val bottom = mid + 20.dp.toPx()
            val gold = Juicy.Gold
            drawLine(gold.copy(alpha = 0.13f * presence), Offset(cx, top), Offset(cx, bottom), 6.dp.toPx(), StrokeCap.Round)
            drawLine(gold.copy(alpha = presence), Offset(cx, top), Offset(cx, bottom), 2.dp.toPx(), StrokeCap.Round)
            marker.rewind()
            marker.moveTo(cx - 5.dp.toPx(), top - 10.dp.toPx())
            marker.lineTo(cx + 5.dp.toPx(), top - 10.dp.toPx())
            marker.lineTo(cx, top - 3.dp.toPx())
            marker.close()
            drawPath(marker, gold.copy(alpha = presence))
        }
    }
}
