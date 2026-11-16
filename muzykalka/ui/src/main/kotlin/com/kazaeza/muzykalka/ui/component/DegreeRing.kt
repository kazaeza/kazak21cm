package com.kazaeza.muzykalka.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.ui.orb.OrbAnswer
import com.kazaeza.muzykalka.ui.orb.OrbSphere
import com.kazaeza.muzykalka.ui.effect.PulseBoard
import com.kazaeza.muzykalka.ui.effect.breathe
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Doto
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Manrope
import com.kazaeza.muzykalka.ui.theme.Motion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** The dial has a fine tick every 3°, ten to an hour. */
private const val TICKS = 120

/** A degree lights the ticks of its hour: this many on each side of the middle one. */
private const val ARC = 3

/**
 * O-52: a figure inscribed in the dial — the sounds that are held, joined by lines. An interval is a chord of the
 * circle, a triad a triangle, a diminished seventh a square: the shape IS the body of the sound, and turning it round
 * the dial is moving the same chord to another degree. [hours] are the corners in the order they are joined (a
 * fractional hour is fine), [colors] one per corner; [closed] joins the last corner to the first; [home] rings an hour
 * that is the tonic when the figure does not start there.
 */
data class RingFigure(val hours: List<Int>, val colors: List<Color>, val closed: Boolean = true, val home: Int? = null) {
    init {
        require(hours.size == colors.size) { "one colour per corner" }
    }
}

/**
 * O-23, redrawn in O-26 after the references: Tir's answer pad as an instrument dial.
 *
 * In the centre the tonic is a sphere of light ([OrbSphere], O-38; the drop of O-37 is one of its bodies), the way the drone sits under every note. Around
 * it runs a ring of fine ticks, and the degrees stand on it like hours on a clock — one hour per semitone,
 * clockwise from 1 at the top, so 7 is next to 1, ♭3 and 3 are neighbours, and the distance on the dial is
 * the distance in sound. A degree is a dot-matrix numeral with a short arc of longer ticks in its own colour;
 * there is no button shape, but each one answers to touch in a square of [key] (at least [GameTouchTarget]).
 *
 * After the answer the right degree lights up — longer ticks, a glow, and a thin hand from the sphere — and
 * [centre] floods the sphere with its colour; [answer] (O-38) is the same for the bodies that are drawn by OpenGL. [energy] 0…1 is how loud the sphere is (the question is sounding).
 * Two spellings of one sound (♯4 / ♭5) share an hour: the second one stands on an inner circle.
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
    energy: Float = 0.5f,
    pulses: PulseBoard? = null,
    answer: OrbAnswer? = null,
    onPress: (hour: Int) -> Unit = {},
    figure: RingFigure? = null,
) {
    // O-52: the corners of the figure glide to their new hours along the shorter way round the dial, so a voice that
    // moves is seen to move and a chord taken to another degree is seen to turn
    val target = figure?.hours?.map { it.toFloat() } ?: emptyList()
    var corners by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        val start = if (corners.size == target.size) corners else target
        if (start == target) {
            corners = target
        } else {
            animate(0f, 1f, animationSpec = tween(Motion.MEDIUM)) { t, _ ->
                corners = start.indices.map { i -> start[i] + shorterWay(target[i] - start[i]) * t }
            }
        }
    }
    // O-35: the sphere leans towards the key under the finger; after the answer it leans to the right one
    var pressedHour by remember { mutableStateOf<Int?>(null) }
    val leanHour = pressedHour ?: centre?.let { Math.floorMod(it.semitones, 12) }
    val touch = leanHour?.let { h ->
        val a = Math.toRadians((h * 30 - 90).toDouble())
        val reach = if (pressedHour != null) 1f else 0.6f
        Offset(cos(a).toFloat() * reach, sin(a).toFloat() * reach)
    } ?: Offset.Zero
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth, maxHeight)
        val half = side / 2
        val rHit = half - key / 2 // where the numerals stand
        val rOut = half - 3.dp // the outer end of the ticks
        val rOrb = rHit - 50.dp // the sphere itself; its halo reaches further

        if (rOrb > 0.dp) {
            OrbSphere(
                Modifier.size(side).breathe(enabled = breathing, amount = 0.03f, periodMs = 3200),
                energy = energy,
                sphere = rOrb / half,
                pulses = pulses,
                touch = touch,
                answer = answer,
            )
        }
        Canvas(Modifier.size(side)) {
            val out = rOut.toPx()
            for (i in 0 until TICKS) {
                val a = Math.toRadians(i * 360.0 / TICKS - 90.0)
                val hour = i % (TICKS / 12) == 0
                val len = (if (hour) 7.dp else 5.dp).toPx()
                drawLine(
                    Color.White.copy(alpha = if (hour) 0.32f else 0.16f),
                    polar(center, out - len, a),
                    polar(center, out, a),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            if (rOrb > 0.dp) {
                drawCircle(Color.White.copy(alpha = 0.07f), radius = (rOrb + 17.dp).toPx(), center = center, style = Stroke(1.dp.toPx()))
            }
            if (figure != null && corners.size == figure.hours.size && corners.isNotEmpty() && rOrb > 0.dp) {
                val rv = (rOrb + 17.dp).toPx()
                val at = { hour: Float -> polar(center, rv, Math.toRadians(hour * 30.0 - 90.0)) }
                val points = corners.map(at)
                val last = points.size - 1
                val edges = (0 until last).map { it to it + 1 } + if (figure.closed && points.size > 2) listOf(last to 0) else emptyList()
                edges.forEach { (a, b) ->
                    if ((points[a] - points[b]).getDistance() > 1f) {
                        val brush = Brush.linearGradient(listOf(figure.colors[a], figure.colors[b]), points[a], points[b])
                        drawLine(brush, points[a], points[b], 8.dp.toPx(), StrokeCap.Round, alpha = 0.16f)
                        drawLine(brush, points[a], points[b], 1.8.dp.toPx(), StrokeCap.Round, alpha = 0.95f)
                    }
                }
                points.forEachIndexed { i, p ->
                    drawCircle(figure.colors[i].copy(alpha = 0.25f), 9.dp.toPx(), p)
                    drawCircle(figure.colors[i], 4.dp.toPx(), p)
                    drawCircle(Color.White.copy(alpha = 0.9f), 1.5.dp.toPx(), p)
                }
                figure.home?.let { h -> drawCircle(Color.White.copy(alpha = 0.75f), 8.dp.toPx(), at(h.toFloat()), style = Stroke(1.2.dp.toPx())) }
            }
        }
        val seen = HashMap<Int, Int>()
        degrees.forEach { d ->
            val slot = seen.getOrDefault(d.semitones, 0)
            seen[d.semitones] = slot + 1
            val hour = Math.floorMod(d.semitones, 12)
            val r = rHit - (key * 0.9f) * slot
            val at = ringOffset(hour, r)
            RingKey(
                text = DegreeFormatter.degree(d, notation),
                color = DegreeColors.of(d),
                state = highlight(d),
                enabled = enabled,
                onClick = { onDegree(d) },
                hour = hour,
                at = at,
                rOut = rOut,
                handFrom = rOrb + 4.dp,
                // with a figure on the dial the corners say where the sounds are: no hands from the sphere
                handTo = if (figure != null) rOrb + 4.dp else r - 24.dp,
                arc = slot == 0,
                onPressed = { down ->
                    if (down) {
                        pressedHour = hour
                        onPress(hour)
                    } else if (pressedHour == hour) {
                        pressedHour = null
                    }
                },
                modifier = Modifier.offset(at.first, at.second).size(key),
            )
        }
    }
}

/**
 * One degree on the dial. It has no body: a numeral, and behind it — drawn around the centre of the dial,
 * which lies outside this box — the arc of its ticks and, when it is the right answer, the hand.
 */
@Composable
private fun RingKey(
    text: String,
    color: Color,
    state: KeyHighlight,
    enabled: Boolean,
    onClick: () -> Unit,
    hour: Int,
    at: Pair<Dp, Dp>,
    rOut: Dp,
    handFrom: Dp,
    handTo: Dp,
    arc: Boolean,
    onPressed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    TapOnPress(source)
    val pressed by source.collectIsPressedAsState()
    LaunchedEffect(pressed) { onPressed(pressed) }
    val press by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "press",
    )
    val lit by animateFloatAsState(if (state == KeyHighlight.CORRECT) 1f else 0f, tween(Motion.MEDIUM), label = "lit")
    val presence by animateFloatAsState(
        when {
            state == KeyHighlight.DIM -> 0.26f
            state == KeyHighlight.NONE && !enabled -> 0.6f
            else -> 1f
        },
        tween(Motion.SHORT),
        label = "presence",
    )
    val ink by animateColorAsState(
        when (state) {
            KeyHighlight.CORRECT -> lerp(color, Color.White, 0.3f)
            KeyHighlight.WRONG -> Juicy.Bad
            else -> Juicy.Text
        },
        tween(Motion.SHORT),
        label = "ink",
    )
    val tick by animateColorAsState(if (state == KeyHighlight.WRONG) Juicy.Bad else color, tween(Motion.SHORT), label = "tick")
    val glow = with(LocalDensity.current) { 12.dp.toPx() }
    Box(
        modifier
            .drawBehind {
                val dial = center - Offset(at.first.toPx(), at.second.toPx())
                val a0 = hour * 30.0 - 90.0
                if (lit > 0f) {
                    val r = size.minDimension * 0.8f
                    drawCircle(Brush.radialGradient(listOf(tick.copy(alpha = 0.32f * lit), Color.Transparent), center, r), r, center)
                }
                if (arc) {
                    val out = rOut.toPx()
                    for (j in -ARC..ARC) {
                        val a = Math.toRadians(a0 + j * 360.0 / TICKS)
                        val taper = 1f - abs(j) / (ARC + 1f)
                        val len = 8.dp.toPx() + (5.dp.toPx() + 3.dp.toPx() * press + 5.dp.toPx() * lit) * taper
                        val p0 = polar(dial, out - len, a)
                        val p1 = polar(dial, out, a)
                        if (lit > 0f) drawLine(tick.copy(alpha = 0.22f * lit), p0, p1, 5.dp.toPx(), StrokeCap.Round)
                        drawLine(
                            tick.copy(alpha = presence * (0.8f + 0.2f * max(lit, press))),
                            p0,
                            p1,
                            (1.5f + 0.5f * lit).dp.toPx(),
                            StrokeCap.Round,
                        )
                    }
                }
                if (lit > 0f && handTo > handFrom) {
                    // the hand: from the sphere to the right answer
                    val a = Math.toRadians(a0)
                    val p0 = polar(dial, handFrom.toPx(), a)
                    val p1 = polar(dial, handTo.toPx(), a)
                    drawLine(Brush.linearGradient(listOf(Color.Transparent, tick.copy(alpha = 0.9f * lit)), p0, p1), p0, p1, 1.dp.toPx(), StrokeCap.Round)
                    drawCircle(tick.copy(alpha = lit), 2.dp.toPx(), p1)
                }
            }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            ringLabel(text),
            color = ink,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, shadow = Shadow(tick.copy(alpha = 0.6f), blurRadius = glow)),
            modifier = Modifier.graphicsLayer {
                val s = 1f - 0.1f * press + 0.16f * lit
                scaleX = s
                scaleY = s
                alpha = presence
            },
        )
    }
}

/**
 * The numeral of a degree: digits in the dot-matrix face, everything the face does not have (♭, ♯, Roman
 * numerals, solfège) in the text face, a little smaller so the two sit on one line. Long labels shrink.
 */
private fun ringLabel(text: String): AnnotatedString = buildAnnotatedString {
    val size = when {
        text.length <= 2 -> 30.sp
        text.length == 3 -> 23.sp
        else -> 17.sp
    }
    text.forEach { ch ->
        val style = if (ch.isDigit()) {
            SpanStyle(fontFamily = Doto, fontWeight = FontWeight.Bold, fontSize = size)
        } else {
            SpanStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = size * 0.8f)
        }
        withStyle(style) { append(ch) }
    }
}

/** The signed way from one hour to another that is the shorter one round a dial of twelve. */
private fun shorterWay(delta: Float): Float {
    val turn = ((delta % 12f) + 12f) % 12f
    return if (turn > 6f) turn - 12f else turn
}

/** The smallest square a [DegreeRing] needs so that keys a semitone apart do not overlap. */
fun degreeRingMinSide(key: Dp = GameTouchTarget): Dp = key + key / sin(Math.PI / 12).toFloat() + 4.dp

/** Where an hour of the dial is, from the centre: hour 0 at the top, clockwise. */
private fun ringOffset(hour: Int, radius: Dp): Pair<Dp, Dp> {
    val a = Math.toRadians((hour * 30 - 90).toDouble())
    return radius * cos(a).toFloat() to radius * sin(a).toFloat()
}

private fun polar(centre: Offset, radius: Float, angle: Double): Offset =
    Offset(centre.x + radius * cos(angle).toFloat(), centre.y + radius * sin(angle).toFloat())
