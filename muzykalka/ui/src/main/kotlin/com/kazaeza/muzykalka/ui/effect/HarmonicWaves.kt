package com.kazaeza.muzykalka.ui.effect

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * O-26: a sound drawn as the references draw a reading — a hairline, and on it thin waves lying over each
 * other. They are a tone and its overtones: the k-th wave is k times shorter, runs k times faster and is √k
 * times lower (k = 1…[harmonics]); the first one is white, the rest take [colors]. The waves swell in the
 * middle and die away to the hairline at both ends.
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
) {
    val e by animateFloatAsState(energy.coerceIn(0f, 1f), tween(Motion.LONG), label = "waves")
    val time = rememberFlowSeconds(2.4f) { 0.35f + 1.1f * e }
    val path = remember { Path() }
    val tints = colors.ifEmpty { listOf(Juicy.Cyan) }
    Canvas(modifier) {
        val w = size.width
        val mid = size.height / 2
        val swing = (mid - 1.dp.toPx()) * (0.5f + 0.5f * e)
        val t = time.floatValue
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.16f), Color.Transparent)),
            Offset(0f, mid),
            Offset(w, mid),
            1.dp.toPx(),
        )
        val steps = 120
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
    }
}
