package com.kazaeza.muzykalka.ui.effect

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlin.math.pow
import kotlin.math.sin

/**
 * O-52: why an interval sounds the way it does, drawn. Two combs of harmonics on one axis of pitch — the lower sound's
 * below ([lower]), the upper sound's above ([upper], standing at [ratio] of the lower one). Where a tooth of one comb
 * stands exactly over a tooth of the other the two are one sound: a bright link ([meetings], pairs of harmonic numbers
 * lower-to-upper). Where they stand close but not together they beat: a red tremble ([rubs]). A fifth is a row of
 * calm links; a minor second trembles from end to end. Higher harmonics are quieter, so their teeth are shorter.
 */
@Composable
fun HarmonicLadder(
    ratio: Pair<Int, Int>,
    meetings: List<Pair<Int, Int>>,
    rubs: List<Pair<Int, Int>>,
    lower: Color,
    upper: Color,
    modifier: Modifier = Modifier,
    harmonics: Int = 16,
) {
    val t = rememberInfiniteTransition(label = "ladder")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "breath")
    val shake by t.animateFloat(0f, 1f, infiniteRepeatable(tween(260, easing = LinearEasing)), label = "shake")
    Canvas(modifier) {
        val left = 6.dp.toPx()
        val span = size.width - 2 * left
        val x = { pitch: Float -> left + (pitch - 0.5f) / harmonics * span }
        val mid = size.height / 2
        val gap = 7.dp.toPx()
        val tall = mid - gap - 2.dp.toPx()
        val loud = { n: Int -> n.toFloat().pow(-0.45f) } // the n-th harmonic is quieter than the first
        val step = ratio.first.toFloat() / ratio.second
        // the two rails
        drawLine(lower.copy(alpha = 0.18f), Offset(left, mid + gap), Offset(size.width - left, mid + gap), 1.dp.toPx())
        drawLine(upper.copy(alpha = 0.18f), Offset(left, mid - gap), Offset(size.width - left, mid - gap), 1.dp.toPx())
        // the teeth of the lower sound hang down, the upper sound's stand up
        for (n in 1..harmonics) {
            val px = x(n.toFloat())
            drawLine(lower.copy(alpha = 0.35f + 0.55f * loud(n)), Offset(px, mid + gap), Offset(px, mid + gap + tall * loud(n)), 2.dp.toPx(), StrokeCap.Round)
        }
        var k = 1
        while (k * step <= harmonics + 0.01f) {
            val px = x(k * step)
            drawLine(upper.copy(alpha = 0.35f + 0.55f * loud(k)), Offset(px, mid - gap), Offset(px, mid - gap - tall * loud(k)), 2.dp.toPx(), StrokeCap.Round)
            k++
        }
        // near misses: the two teeth tremble against each other
        rubs.forEach { (n, kk) ->
            val a = Offset(x(n.toFloat()), mid + gap)
            val b = Offset(x(kk * step), mid - gap)
            val wobble = sin((shake * 2f * Math.PI).toFloat() + n) * 2.dp.toPx()
            val knee = Offset((a.x + b.x) / 2 + wobble, mid)
            drawLine(Juicy.Bad.copy(alpha = 0.9f), a, knee, 1.6.dp.toPx(), StrokeCap.Round)
            drawLine(Juicy.Bad.copy(alpha = 0.9f), knee, b, 1.6.dp.toPx(), StrokeCap.Round)
            drawCircle(Juicy.Bad.copy(alpha = 0.22f), 5.dp.toPx(), knee)
        }
        // meetings: one tooth over the other — one sound
        meetings.forEach { (n, _) ->
            val px = x(n.toFloat())
            val glow = 0.55f + 0.45f * breath
            val top = Offset(px, mid - gap)
            val bottom = Offset(px, mid + gap)
            drawLine(Brush.verticalGradient(listOf(upper, Color.White, lower), top.y, bottom.y), top, bottom, 3.dp.toPx(), StrokeCap.Round, alpha = glow)
            drawCircle(Color.White.copy(alpha = 0.16f * glow * (0.5f + loud(n))), 9.dp.toPx(), Offset(px, mid))
            drawCircle(Color.White.copy(alpha = glow), 2.2.dp.toPx(), Offset(px, mid))
        }
    }
}
