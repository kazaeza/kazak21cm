package com.kazaeza.muzykalka.ui.orb

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.kazaeza.muzykalka.music.OrbPhysics
import com.kazaeza.muzykalka.music.PulseKind
import com.kazaeza.muzykalka.music.ThreadBody
import com.kazaeza.muzykalka.ui.effect.PulseBoard
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlin.math.atan2
import kotlin.math.hypot

/** Points of the thread drawn by Compose: fewer than on the GPU, enough for a smooth winding. */
private const val CANVAS_POINTS = 720

/** Points of a still mark (a few dozen dp wide). */
private const val MARK_POINTS = 480

/**
 * O-54 (ADR-053): the thread drawn by Compose, the same [ThreadBody] as the GPU's. It is the sphere wherever OpenGL is
 * not there — an old phone, a body that failed, tests, previews — so the app has one look, the thread, everywhere.
 * It has no afterglow (that needs a layer kept between frames); the string is drawn wide and faint, then thin and bright.
 *
 * [sphere] is the share of the half-side of the box the ball takes, as in [OrbSphere]. [pulses] pluck it, a finger's
 * pluck strikes towards [touch]; [answer] winds it up in the ratio of the degree and colours it.
 */
@Composable
fun ThreadCanvasOrb(
    modifier: Modifier = Modifier,
    energy: Float = 0.5f,
    sphere: Float = 0.62f,
    pulses: PulseBoard? = null,
    touch: Offset = Offset.Zero,
    answer: OrbAnswer? = null,
) {
    val body = remember { ThreadBody(CANVAS_POINTS) }
    var frame by remember { mutableIntStateOf(0) }
    var color by remember { mutableStateOf(answer?.color) }
    LaunchedEffect(answer) {
        if (answer == null) {
            body.clear()
        } else {
            body.answer(answer.semitones)
            color = answer.color
        }
        frame++
    }
    val lean by rememberUpdatedState(touch)
    DisposableEffect(pulses) {
        val off = pulses?.addListener { p ->
            val l = lean
            val (x, y) = if (p.kind == PulseKind.TOUCH && (l.x != 0f || l.y != 0f)) {
                val n = hypot(l.x, l.y)
                (l.x / n) to (-l.y / n)
            } else {
                OrbPhysics.pluckPoint(p.x)
            }
            body.pluck(p.strength * (if (p.kind == PulseKind.TOUCH) 0.6f else 1f), atan2(y, x))
        }
        onDispose { off?.invoke() }
    }
    // where endless animation is off (tests, «убрать анимации») the thread stands still in its last state
    LaunchedEffect(body) {
        var last = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (last >= 0) body.step((now - last) / 1e9)
                last = now
                frame++
            }
        }
    }
    val gold = DegreeColors.ofSemitone(0)
    Canvas(modifier) {
        frame // read: a new frame redraws
        body.trace()
        val answered = body.semitone > 0
        val near = if (answered) lerp(gold, color ?: gold, 0.75f) else gold
        val far = if (answered) lerp(gold, color ?: gold, 0.35f) else gold
        drawThreadBall(
            xs = body.xs,
            ys = body.ys,
            depths = body.depths,
            count = body.points,
            sphere = sphere,
            near = near,
            far = far,
            light = (0.35f + 0.65f * (body.energy.toFloat() + energy).coerceAtMost(1.4f) / 1.4f) * body.fold.toFloat(),
            dense = body.dense.toFloat(),
            halo = 0.25f + 0.35f * energy,
        )
    }
}

/**
 * O-54: a still thread — the mark of a mode, an empty state. The colours pick the ratio it is wound in, so two modes
 * never show the same figure, unless [semitone] names it; [dim] fades it, [glow] is its halo.
 */
@Composable
fun ThreadMark(colors: List<Color>, modifier: Modifier = Modifier, glow: Float = 0.5f, dim: Float = 1f, semitone: Int? = null) {
    val a = colors.firstOrNull() ?: Juicy.Cyan
    val b = colors.getOrNull(1) ?: a
    val figure = semitone ?: markSemitone(a)
    val trace = remember(figure) { stillTrace(figure) }
    Canvas(modifier) {
        drawThreadBall(
            xs = trace[0],
            ys = trace[1],
            depths = trace[2],
            count = MARK_POINTS,
            sphere = 0.8f,
            near = lerp(a, Color.White, 0.25f).copy(alpha = dim),
            far = b.copy(alpha = dim),
            light = 0.9f * dim,
            dense = 1f,
            halo = glow * dim,
            mark = true,
        )
    }
}

/** The figures a mark is wound in: the consonances first — calm windings read best at a few dozen dp. */
private val MARK_FIGURES = intArrayOf(7, 5, 4, 3, 9, 8, 2, 10)

private fun markSemitone(c: Color): Int {
    val seed = ((c.red * 31f + c.green * 17f + c.blue * 7f) * 97f).toInt()
    return MARK_FIGURES[Math.floorMod(seed, MARK_FIGURES.size)]
}

/** The winding of [semitone] once it has settled (two seconds of the spring), as three arrays: x, y, depth. */
private fun stillTrace(semitone: Int): Array<FloatArray> {
    val body = ThreadBody(MARK_POINTS)
    body.answer(semitone)
    repeat(60) { body.step(1.0 / 30) }
    body.trace()
    return arrayOf(body.xs.copyOf(), body.ys.copyOf(), body.depths.copyOf())
}

/**
 * The ball and the string: a dark glass with a coloured rim and a halo, the far half of the string dim in [far],
 * the near half in [near], each drawn wide to thin with light that adds up. The points are in clip space ([-1, 1]²
 * of the box the GPU draws in), y up; the ball is [sphere] of the half-side of this canvas.
 */
internal fun DrawScope.drawThreadBall(
    xs: FloatArray,
    ys: FloatArray,
    depths: FloatArray,
    count: Int,
    sphere: Float,
    near: Color,
    far: Color,
    light: Float,
    dense: Float,
    halo: Float,
    mark: Boolean = false,
) {
    val side = size.minDimension
    val c = center
    val r = side / 2f * sphere
    // a clip unit of the GPU's box: the ball (2·RADIUS of it) is r
    val unit = r / (2f * OrbPhysics.RADIUS.toFloat())
    if (halo > 0f) {
        drawCircle(Brush.radialGradient(listOf(near.copy(alpha = 0.28f * halo), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
    }
    drawCircle(Brush.radialGradient(listOf(Juicy.Ink3.copy(alpha = 0.85f), Juicy.Ink.copy(alpha = 0.95f)), c + Offset(-r * 0.25f, -r * 0.3f), r * 1.3f), r, c)
    drawCircle(Brush.radialGradient(0.78f to Color.Transparent, 1f to far.copy(alpha = 0.35f), center = c, radius = r), r, c)
    val back = Path()
    val front = Path()
    var penBack = false
    var penFront = false
    for (i in 0 until count) {
        val x0 = c.x + xs[i] * unit
        val y0 = c.y - ys[i] * unit
        val x1 = c.x + xs[i + 1] * unit
        val y1 = c.y - ys[i + 1] * unit
        val inFront = depths[i] + depths[i + 1] >= 1f
        if (inFront) {
            if (!penFront) front.moveTo(x0, y0)
            front.lineTo(x1, y1)
            penFront = true
            penBack = false
        } else {
            if (!penBack) back.moveTo(x0, y0)
            back.lineTo(x1, y1)
            penBack = true
            penFront = false
        }
    }
    // a mark is a few dozen dp: its string is thicker, its glow narrower, or the figure drowns in light
    val thin = if (mark) side * 0.016f else side * 0.006f
    val wide = if (mark) 5f else 9f
    fun strand(path: Path, color: Color, gain: Float) {
        val g = (gain * light).coerceIn(0f, 1f)
        drawPath(path, color.copy(alpha = color.alpha * 0.10f * g * dense), style = Stroke(thin * wide, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
        drawPath(path, color.copy(alpha = color.alpha * 0.32f * g * dense), style = Stroke(thin * wide / 3f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
        drawPath(path, lerp(color, Color.White, 0.35f).copy(alpha = color.alpha * 0.95f * g), style = Stroke(thin, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
    }
    strand(back, far, 0.45f)
    strand(front, near, 1f)
    // a glint where the glass turns to the light
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), c + Offset(-r * 0.4f, -r * 0.45f), r * 0.35f), r * 0.35f, c + Offset(-r * 0.4f, -r * 0.45f))
}
