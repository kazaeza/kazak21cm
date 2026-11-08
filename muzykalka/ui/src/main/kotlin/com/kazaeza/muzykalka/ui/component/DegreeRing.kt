package com.kazaeza.muzykalka.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.ui.effect.GlowOrb
import com.kazaeza.muzykalka.ui.effect.breathe
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Motion
import kotlin.math.cos
import kotlin.math.sin

/**
 * O-23: Tir's answer pad as a dial. The tonic glows in the centre, the way the drone sits under
 * every note; the degrees sit around it like hours on a clock, one hour per semitone, clockwise
 * from 1 at the top — so 7 is next to 1, ♭3 and 3 are neighbours, and the distance on the dial is
 * the distance in sound. Each degree is a key of its own colour, at least [GameTouchTarget] wide.
 *
 * [centre] colours the orb (the expected degree once the answer is shown). Two spellings of one
 * sound (♯4 / ♭5) share an hour: the second one sits on an inner ring.
 */
@Composable
fun DegreeRing(
    degrees: List<Degree>,
    notation: DegreeNotation,
    onDegree: (Degree) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlight: (Degree) -> KeyHighlight = { KeyHighlight.NONE },
    centre: Degree? = null,
    breathing: Boolean = true,
    key: Dp = GameTouchTarget,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth, maxHeight)
        val radius = side / 2 - key / 2
        val tint = centre?.let { DegreeColors.of(it) } ?: Juicy.Gold
        val c1 by animateColorAsState(tint, tween(Motion.MEDIUM), label = "orb1")
        val c2 by animateColorAsState(if (centre != null) DegreeColors.gradient(tint).last() else Juicy.Orange, tween(Motion.MEDIUM), label = "orb2")
        Canvas(Modifier.size(side)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = radius.toPx()
            val inner = r - key.toPx() / 2 - 8.dp.toPx()
            // the dial: 60 fine ticks, the twelve hours longer
            for (i in 0 until 60) {
                val a = Math.toRadians((i * 6 - 90).toDouble())
                val hour = i % 5 == 0
                val len = if (hour) 9.dp.toPx() else 4.dp.toPx()
                val col = if (hour) Juicy.TextDim.copy(alpha = 0.6f) else Juicy.TextFaint.copy(alpha = 0.45f)
                drawLine(
                    col,
                    Offset(c.x + (inner * cos(a)).toFloat(), c.y + (inner * sin(a)).toFloat()),
                    Offset(c.x + ((inner - len) * cos(a)).toFloat(), c.y + ((inner - len) * sin(a)).toFloat()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            drawCircle(Color.White.copy(alpha = 0.06f), radius = r, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        val orb = (side / 2 - key - 24.dp) * 0.9f
        if (orb > 0.dp) {
            GlowOrb(listOf(c1, c2), Modifier.size(orb).breathe(enabled = breathing, amount = 0.05f, periodMs = 2400), glow = 0.8f)
        }
        val seen = HashMap<Int, Int>()
        degrees.forEach { d ->
            val slot = seen.getOrDefault(d.semitones, 0)
            seen[d.semitones] = slot + 1
            val at = ringOffset(d.semitones, radius - (key * 0.9f) * slot)
            JuicyKey(
                label = DegreeFormatter.degree(d, notation),
                colors = DegreeColors.gradient(d),
                onClick = { onDegree(d) },
                modifier = Modifier.offset(at.first, at.second).size(key),
                enabled = enabled,
                highlight = highlight(d),
                height = key,
                textStyle = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

/** The smallest square a [DegreeRing] needs so that keys a semitone apart do not overlap. */
fun degreeRingMinSide(key: Dp = GameTouchTarget): Dp = key + key / sin(Math.PI / 12).toFloat() + 4.dp

/** Where an hour of the dial is, from the centre: 0 semitones at the top, clockwise. */
private fun ringOffset(semitones: Int, radius: Dp): Pair<Dp, Dp> {
    val a = Math.toRadians((Math.floorMod(semitones, 12) * 30 - 90).toDouble())
    return radius * cos(a).toFloat() to radius * sin(a).toFloat()
}
