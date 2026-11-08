package com.kazaeza.muzykalka.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Numbers
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Round timer: gradient arc that drains clockwise and turns red in the last fifth. */
@Composable
fun TimerRing(
    fraction: Float,
    label: String,
    modifier: Modifier = Modifier,
    colors: List<Color> = Accents.Hero,
    diameter: Dp = 64.dp,
    stroke: Dp = 7.dp,
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(120, easing = LinearEasing), label = "timer")
    val urgent = f < 0.2f
    val pulse = rememberInfiniteTransition(label = "urgent")
    val p by pulse.animateFloat(1f, 1.08f, infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "p")
    val ringColors = if (urgent) Accents.Bad else colors
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val scale = if (urgent) p else 1f
            val d = (size.minDimension - s) * scale
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            // the dial: 60 ticks, every fifth longer, the ones still left lit
            val c = Offset(size.width / 2, size.height / 2)
            val outer = d / 2 + s / 2
            for (i in 0 until 60) {
                val a = Math.toRadians((i * 6 - 90).toDouble())
                val len = if (i % 5 == 0) s * 1.0f else s * 0.55f
                val lit = i / 60f < f
                val col = if (lit) Juicy.Text.copy(alpha = 0.55f) else Juicy.TextFaint.copy(alpha = 0.35f)
                drawLine(
                    col,
                    Offset(c.x + ((outer) * cos(a)).toFloat(), c.y + ((outer) * sin(a)).toFloat()),
                    Offset(c.x + ((outer - len) * cos(a)).toFloat(), c.y + ((outer - len) * sin(a)).toFloat()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            drawArc(
                Brush.sweepGradient(ringColors + ringColors.first()),
                -90f, 360f * f, false, tl, Size(d, d), style = Stroke(s * 0.35f, cap = StrokeCap.Round),
            )
        }
        Text(label, style = Numbers.Small.copy(fontSize = Numbers.Small.fontSize * 0.8f), color = if (urgent) Juicy.Hot else Juicy.Text)
    }
}

/** Thin shrinking bar for per-task deadlines (Hunt). */
@Composable
fun DeadlineBar(fraction: Float, modifier: Modifier = Modifier, colors: List<Color> = Accents.Hunt) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(90, easing = LinearEasing), label = "deadline")
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(Juicy.Ink4),
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(if (f < 0.25f) Accents.Bad else colors)),
        )
    }
}

/** Streak with the ×N multiplier that pops when it grows: dot-matrix numbers in a hairline. */
@Composable
fun ComboBadge(streak: Int, multiplier: Int, modifier: Modifier = Modifier) {
    if (streak < 2) return
    Row(
        modifier
            .pop(multiplier, 0.35f)
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val glow = if (multiplier >= 3) Accents.Hero else Accents.Tir
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(glow.first()))
        Text("$streak", style = Numbers.Small, color = Juicy.Text)
        if (multiplier > 1) Text("×$multiplier", style = Numbers.Small, color = Juicy.Gold)
    }
}

/** Lives as dots of light; the lost ones go hollow. */
@Composable
fun LivesRow(lives: Int?, max: Int = 3, modifier: Modifier = Modifier) {
    if (lives == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(max) { i ->
            val alive = i < lives
            val s by animateFloatAsState(if (alive) 1f else 0.7f, label = "heart")
            Text(
                if (alive) "●" else "○",
                style = MaterialTheme.typography.titleMedium,
                color = if (alive) Juicy.Hot else Juicy.TextFaint,
                modifier = Modifier.size((22 * s).dp),
            )
        }
    }
}

/**
 * Microphone activity: concentric rings that grow with the input level; [label] shows the note
 * being heard ("слышу G3"). [color] is the heard note's degree colour when known.
 */
@Composable
fun MicPulse(levelDb: Double, label: String?, modifier: Modifier = Modifier, color: Color = Juicy.Cyan, diameter: Dp = 120.dp) {
    val level = ((levelDb + 60.0) / 50.0).coerceIn(0.0, 1.0).toFloat()
    val l by animateFloatAsState(level, tween(80), label = "level")
    val t = rememberInfiniteTransition(label = "mic")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "phase")
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2
            for (k in 0 until 3) {
                val q = (phase + k / 3f) % 1f
                drawCircle(color.copy(alpha = (1f - q) * (0.15f + 0.5f * l)), radius = maxR * (0.35f + 0.65f * q), style = Stroke(3.dp.toPx()))
            }
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.2f))), radius = maxR * (0.28f + 0.18f * l))
        }
        Text(label ?: "", style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
    }
}

/** Tuner needle: −50…+50 cents on an arc, green when within ±5. */
@Composable
fun TunerGauge(cents: Double?, modifier: Modifier = Modifier) {
    val target = (cents ?: 0.0).coerceIn(-50.0, 50.0).toFloat()
    val c by animateFloatAsState(target, tween(90), label = "needle")
    val inTune = cents != null && abs(cents) <= 5.0
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        val cx = size.width / 2
        val cy = size.height * 0.95f
        val r = min(size.width / 2, size.height) * 0.9f
        val tl = Offset(cx - r, cy - r)
        drawArc(Brush.sweepGradient(listOf(Juicy.Hot, Juicy.Gold, Juicy.Good, Juicy.Gold, Juicy.Hot), Offset(cx, cy)), 200f, 140f, false, tl, Size(2 * r, 2 * r), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
        for (tick in -5..5) {
            val a = Math.toRadians((270 + tick * 14).toDouble())
            val inner = r - 22.dp.toPx()
            drawLine(Juicy.TextFaint, Offset(cx + (inner * cos(a)).toFloat(), cy + (inner * sin(a)).toFloat()), Offset(cx + ((r - 12.dp.toPx()) * cos(a)).toFloat(), cy + ((r - 12.dp.toPx()) * sin(a)).toFloat()), strokeWidth = 2.dp.toPx())
        }
        if (cents != null) {
            val a = Math.toRadians((270 + c / 50f * 70f).toDouble())
            val tip = Offset(cx + ((r - 6.dp.toPx()) * cos(a)).toFloat(), cy + ((r - 6.dp.toPx()) * sin(a)).toFloat())
            val col = if (inTune) Juicy.Good else lerp(Juicy.Gold, Juicy.Hot, (abs(c) / 50f).coerceIn(0f, 1f))
            drawLine(col, Offset(cx, cy), tip, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(col, radius = 9.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

/** 0–3 stars for a round summary. */
@Composable
fun StarRow(stars: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Text(
                if (i < stars) "★" else "☆",
                style = MaterialTheme.typography.displaySmall,
                color = if (i < stars) Juicy.Gold else Juicy.TextFaint,
                modifier = Modifier.pop(if (i < stars) stars * 10 + i else 0, 0.4f),
            )
        }
    }
}

/** Label + big number, for summaries. */
@Composable
fun BigStat(value: String, label: String, modifier: Modifier = Modifier, colors: List<Color> = Accents.Hero) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        GradientText(value, Numbers.Medium, colors)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Juicy.TextDim, textAlign = TextAlign.Center)
    }
}
