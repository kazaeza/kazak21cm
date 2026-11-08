package com.kazaeza.muzykalka.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Manrope
import kotlin.math.max

/** Red → amber → green for a 0..1 score; grey when unknown. */
fun scoreColor(score: Double?): Color = when {
    score == null -> Juicy.Ink4
    score < 0.5 -> lerp(Juicy.Hot, Juicy.Gold, (score / 0.5).toFloat())
    else -> lerp(Juicy.Gold, Juicy.Good, ((score - 0.5) / 0.5).toFloat())
}

/**
 * The triangle sound / name / place. [scores] holds the six directed links in this order:
 * sound→name, name→sound, name→place, place→name, place→sound, sound→place (null = never trained).
 * Each side is two parallel strands, thicker and greener the stronger the link.
 */
@Composable
fun TriangleChart(scores: List<Double?>, modifier: Modifier = Modifier, compact: Boolean = false) {
    require(scores.size == 6)
    val measurer = rememberTextMeasurer()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(scores) { grow.animateTo(1f, tween(900)) }
    val label = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 11.sp else 14.sp, color = Juicy.Text)
    Canvas(modifier.fillMaxWidth().aspectRatio(if (compact) 1.6f else 1.25f)) {
        val pad = size.minDimension * 0.16f
        val sound = Offset(size.width / 2, pad)
        val name = Offset(pad * 1.2f, size.height - pad)
        val place = Offset(size.width - pad * 1.2f, size.height - pad)
        fun strand(a: Offset, b: Offset, score: Double?, side: Float) {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            val nx = -dy / len * side * 5.dp.toPx()
            val ny = dx / len * side * 5.dp.toPx()
            val start = Offset(a.x + nx, a.y + ny)
            val end = Offset(a.x + nx + dx * grow.value, a.y + ny + dy * grow.value)
            val col = scoreColor(score)
            val w = (2.5f + 6f * (score ?: 0.0).toFloat()).dp.toPx()
            if (score != null) drawLine(col.copy(alpha = 0.25f), start, end, strokeWidth = w + 8.dp.toPx(), cap = StrokeCap.Round)
            drawLine(col, start, end, strokeWidth = w, cap = StrokeCap.Round)
        }
        strand(sound, name, scores[0], 1f)
        strand(name, sound, scores[1], 1f)
        strand(name, place, scores[2], 1f)
        strand(place, name, scores[3], 1f)
        strand(place, sound, scores[4], 1f)
        strand(sound, place, scores[5], 1f)
        val nodeR = size.minDimension * (if (compact) 0.085f else 0.092f)
        listOf(sound to "звук", name to "имя", place to "место").forEach { (p, text) ->
            drawCircle(Brush.radialGradient(listOf(Juicy.Ink4, Juicy.Ink2), center = p, radius = nodeR * 1.4f), nodeR * 1.4f, p)
            drawCircle(Juicy.Line, nodeR * 1.4f, p, style = Stroke(1.5.dp.toPx()))
            val layout = measurer.measure(text, label)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2, p.y - layout.size.height / 2))
        }
    }
}

/** Area line chart for a small integer series (e.g. «Играю с ходу» by day). */
@Composable
fun LineChart(values: List<Int>, modifier: Modifier = Modifier, colors: List<Color> = listOf(Juicy.Orange, Juicy.Hot), height: Dp = 140.dp) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Juicy.TextFaint)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) { grow.animateTo(1f, tween(800)) }
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val maxV = max(1, values.max())
        val left = 28f
        val w = size.width - left
        val h = size.height - 8f
        val step = if (values.size == 1) w else w / (values.size - 1)
        fun pt(i: Int) = Offset(left + i * step, h - h * (values[i].toFloat() / maxV) * grow.value)
        for (g in listOf(0, maxV / 2, maxV).distinct()) {
            val y = h - h * g / maxV
            drawLine(Juicy.Line, Offset(left, y), Offset(size.width, y), strokeWidth = 1f)
            val layout = measurer.measure(g.toString(), style)
            drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2))
        }
        val line = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until values.size) lineTo(pt(i).x, pt(i).y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(pt(values.size - 1).x, h)
            lineTo(pt(0).x, h)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(colors.first().copy(alpha = 0.45f), Color.Transparent)))
        drawPath(line, Brush.horizontalGradient(colors), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(colors.last(), 6.dp.toPx(), pt(values.size - 1))
    }
}

/**
 * Confusion grid: rows = what sounded, columns = what was answered. Diagonal (right) cells are
 * green, mistakes glow red by frequency. [labelColor] colours the axis labels (degree colours).
 */
@Composable
fun ConfusionGrid(labels: List<String>, counts: List<IntArray>, labelColor: (Int) -> Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
    Canvas(modifier.fillMaxWidth().aspectRatio(1f)) {
        val n = labels.size
        val head = size.width * 0.09f
        val cell = (size.width - head) / n
        val maxOff = counts.withIndex().maxOfOrNull { (i, row) -> row.withIndex().filter { it.index != i }.maxOfOrNull { it.value } ?: 0 }?.coerceAtLeast(1) ?: 1
        for (i in 0 until n) {
            val l = measurer.measure(labels[i], style.copy(color = labelColor(i)))
            drawText(l, topLeft = Offset((head - l.size.width) / 2, head + i * cell + (cell - l.size.height) / 2))
            drawText(l, topLeft = Offset(head + i * cell + (cell - l.size.width) / 2, (head - l.size.height) / 2))
            val total = counts[i].sum()
            for (j in 0 until n) {
                val v = counts[i][j]
                val color = when {
                    total == 0 -> Juicy.Ink3
                    i == j -> lerp(Juicy.Ink3, Juicy.Good, (v.toFloat() / total).coerceIn(0f, 1f))
                    v == 0 -> Juicy.Ink3
                    else -> lerp(Juicy.Ink4, Juicy.Hot, (v.toFloat() / maxOff).coerceIn(0.2f, 1f))
                }
                drawRoundRect(color, Offset(head + j * cell + 1.5f, head + i * cell + 1.5f), Size(cell - 3f, cell - 3f), CornerRadius(5f, 5f))
            }
        }
    }
}
