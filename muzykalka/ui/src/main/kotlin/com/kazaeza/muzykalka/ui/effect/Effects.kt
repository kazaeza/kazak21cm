package com.kazaeza.muzykalka.ui.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sqrt
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * O-24 «Обертон»: the base layer of every screen. Near-black, and over it soft lights in the accent
 * colours that drift like overtones of one tone: the k-th light circles k times while the first
 * one circles once (k = 1…5), each one fainter and smaller, all added together where they overlap.
 * The whole picture repeats once a minute, so it never jumps. A fine film grain lies over everything.
 * [intensity] scales the light; the grain stays the same.
 */
@Composable
fun AuroraBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    intensity: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val lights = overtoneColors(colors)
    val grain = rememberGrain()
    Box(
        modifier
            .background(Juicy.Ink)
            .drawBehind {
                val w = size.width
                val h = size.height
                val r = max(w, h)
                val k = intensity.coerceIn(0f, 1f)
                lights.forEachIndexed { i, c ->
                    val n = i + 1
                    val a = 2 * PI * (n * phase + i * 0.17)
                    val centre = Offset(
                        w * (0.5f + (0.36f / n + 0.06f) * sin(a).toFloat()),
                        h * (0.20f + 0.05f * n + 0.06f * cos(a * 0.5 + i).toFloat()),
                    )
                    val rr = r * (0.58f / sqrt(n.toFloat())) * (1f + 0.08f * sin(a + i).toFloat())
                    val alpha = 0.40f * k / sqrt(n.toFloat())
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * 0.35f), Color.Transparent), centre, rr),
                        rr, centre, blendMode = BlendMode.Screen,
                    )
                }
                // the dark falls back in at the bottom, so content reads on calm ink
                drawRect(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Juicy.Ink.copy(alpha = 0.8f)))
                drawGrain(grain)
            },
        content = content,
    )
}

/** Five lights for the background: the accent colours first, then neighbours from the neon palette. */
private fun overtoneColors(colors: List<Color>): List<Color> {
    val extra = listOf(Juicy.Magenta, Juicy.Blue, Juicy.Cyan, Juicy.Violet, Juicy.Hot)
    return (colors + extra).distinct().take(5)
}

/**
 * A sphere of coloured light, like an overtone made visible: [colors] flow inside it, each one
 * circling at its own harmonic speed, under a soft, slightly blurred edge with a coloured rim.
 * [moving] = false freezes it (small marks in lists). [glow] adds a halo, [dim] fades it.
 */
@Composable
fun OvertoneOrb(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    moving: Boolean = true,
    glow: Float = 0.6f,
    dim: Float = 1f,
    periodMs: Int = 18_000,
) {
    val cs = if (colors.size >= 3) colors else (colors + listOf(Juicy.Magenta, Juicy.Blue, Juicy.Cyan)).take(3)
    val phase = if (moving) {
        val t = rememberInfiniteTransition(label = "orb")
        val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "p")
        p
    } else {
        0.13f
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value, maxHeight.value)
        Canvas(Modifier.fillMaxSize().blur((side * 0.035f).dp, BlurredEdgeTreatment.Unbounded)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 * 0.84f
            if (glow > 0f) {
                drawCircle(Brush.radialGradient(listOf(cs[0].copy(alpha = 0.40f * glow * dim), cs[1].copy(alpha = 0.12f * glow * dim), Color.Transparent), c, r * 1.7f), r * 1.7f, c)
            }
            val circle = Path().apply { addOval(Rect(c, r)) }
            clipPath(circle) {
                drawCircle(cs.last().copy(alpha = dim), r, c)
                cs.forEachIndexed { i, col ->
                    val n = i + 1
                    val a = 2 * PI * (n * phase + i * 0.29)
                    val at = c + Offset((r * 0.55f * cos(a)).toFloat(), (r * 0.45f * sin(a * 1.3 + i)).toFloat())
                    val rr = r * (1.25f - 0.15f * i)
                    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = dim), col.copy(alpha = 0.55f * dim), Color.Transparent), at, rr), rr, at)
                }
                // a light band across, and a coloured rim where the sphere turns away
                val band = 2 * PI * phase
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f * dim), Color.Transparent), c + Offset((-r * 0.35f * cos(band)).toFloat(), (-r * 0.4f).toFloat()), r * 0.6f),
                    r, c, blendMode = BlendMode.Screen,
                )
                drawCircle(Brush.radialGradient(0.72f to Color.Transparent, 1f to cs[1 % cs.size].copy(alpha = 0.9f * dim), center = c, radius = r), r, c)
            }
        }
    }
}

/** A small, still overtone sphere (marks of modes, empty states). */
@Composable
fun GlowOrb(colors: List<Color>, modifier: Modifier = Modifier, glow: Float = 0.5f, dim: Float = 1f) =
    OvertoneOrb(colors, modifier, moving = false, glow = glow, dim = dim)

/** Film grain tile: fixed noise, so it never crawls. null where bitmaps cannot be made (JVM tests). */
@Composable
fun rememberGrain(): ImageBitmap? = remember { runCatching { grainTile() }.getOrNull() }

private fun grainTile(size: Int = 160): ImageBitmap {
    val bmp = ImageBitmap(size, size)
    val canvas = androidx.compose.ui.graphics.Canvas(bmp)
    val paint = Paint()
    val rnd = Random(7)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val v = rnd.nextFloat()
            if (v > 0.5f) {
                paint.color = if (rnd.nextBoolean()) Color.White.copy(alpha = 0.05f + 0.06f * (v - 0.5f)) else Color.Black.copy(alpha = 0.10f)
                canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
            }
        }
    }
    return bmp
}

/** Tiles [grain] over the whole draw area. */
fun DrawScope.drawGrain(grain: ImageBitmap?) {
    if (grain == null) return
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)))
}

/** Paints the content (text, icons) with a gradient. */
fun Modifier.gradientTint(colors: List<Color>): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val brush = Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, size.height))
        onDrawWithContent {
            drawContent()
            drawRect(brush, blendMode = BlendMode.SrcAtop)
        }
    }

/** Horizontal shake on every change of [trigger] (> 0): the "wrong!" gesture. */
fun Modifier.shake(trigger: Int): Modifier = composed {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            for (x in listOf(22f, -18f, 14f, -10f, 6f, -3f, 0f)) offset.animateTo(x, tween(38))
        }
    }
    graphicsLayer { translationX = offset.value }
}

/** A springy "pop" (scale 1 → 1.18 → 1) on every change of [trigger] (> 0). */
fun Modifier.pop(trigger: Int, amount: Float = 0.18f): Modifier = composed {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            scale.snapTo(1f + amount)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Gentle breathing scale, for "listening" and call-to-action elements. */
fun Modifier.breathe(enabled: Boolean = true, amount: Float = 0.04f, periodMs: Int = 1400): Modifier = composed {
    if (!enabled) return@composed this
    val t = rememberInfiniteTransition(label = "breathe")
    val s by t.animateFloat(1f, 1f + amount, infiniteRepeatable(tween(periodMs / 2), RepeatMode.Reverse), label = "s")
    graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    val life: Float,
    var age: Float = 0f,
    val square: Boolean,
    var rot: Float,
    val spin: Float,
)

/** Fireworks/confetti controller. Call [burst]; draw with [ParticleLayer]. */
@Stable
class ParticleState {
    private val particles = ArrayList<Particle>()
    internal var frame by mutableLongStateOf(0L)
    internal var generation by mutableIntStateOf(0)
    private val random = Random(System.nanoTime())

    val active: Boolean get() = particles.isNotEmpty()

    /** Radial burst of [count] particles from [at] (px). */
    fun burst(at: Offset, colors: List<Color>, count: Int = 26, power: Float = 1f, confetti: Boolean = false) {
        repeat(count) {
            val angle = random.nextFloat() * 6.2832f
            val speed = (380f + random.nextFloat() * 620f) * power
            particles += Particle(
                x = at.x, y = at.y,
                vx = cos(angle) * speed, vy = sin(angle) * speed - (if (confetti) 600f else 220f),
                color = colors[random.nextInt(colors.size)],
                size = (if (confetti) 9f else 6f) + random.nextFloat() * 8f,
                life = 0.7f + random.nextFloat() * (if (confetti) 1.1f else 0.5f),
                square = confetti && random.nextBoolean(),
                rot = random.nextFloat() * 360f,
                spin = (random.nextFloat() - 0.5f) * 720f,
            )
        }
        generation++
    }

    internal fun step(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age >= p.life) {
                it.remove()
                continue
            }
            p.vy += 1500f * dt
            p.vx *= 0.985f
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.spin * dt
        }
    }

    internal fun forEach(block: (x: Float, y: Float, color: Color, size: Float, alpha: Float, square: Boolean, rot: Float) -> Unit) {
        for (p in particles) {
            val k = p.age / p.life
            block(p.x, p.y, p.color, p.size * (1f - 0.5f * k), min(1f, 1.6f * (1f - k)), p.square, p.rot)
        }
    }
}

@Composable
fun rememberParticleState(): ParticleState = remember { ParticleState() }

/** Full-size overlay that animates particles only while there are any. */
@Composable
fun ParticleLayer(state: ParticleState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.generation) {
        var last = 0L
        while (state.active) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                state.step(dt)
                state.frame = now
            }
        }
        state.frame = 0L
    }
    Canvas(modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val tick = state.frame
        state.forEach { x, y, color, size, alpha, square, rot ->
            if (square) {
                val half = size / 2
                rotate(degrees = rot, pivot = Offset(x, y)) {
                    drawRect(color.copy(alpha = alpha), topLeft = Offset(x - half, y - half * 0.6f), size = androidx.compose.ui.geometry.Size(size, size * 0.6f))
                }
            } else {
                drawCircle(color.copy(alpha = alpha * 0.35f), radius = size * 1.8f, center = Offset(x, y))
                drawCircle(color.copy(alpha = alpha), radius = size / 2, center = Offset(x, y))
            }
        }
    }
}

/** Full-screen colour flash that fades out on every change of [trigger]. */
@Composable
fun FlashOverlay(trigger: Int, color: Color, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            alpha.animateTo(0.45f, tween(140))
            alpha.animateTo(0f, tween(900))
        }
    }
    if (alpha.value > 0f) {
        // a soft light from the edges of the screen, the middle stays clear
        Box(
            modifier.fillMaxSize().drawBehind {
                drawRect(Brush.radialGradient(0.45f to Color.Transparent, 1f to color.copy(alpha = alpha.value), center = Offset(size.width / 2, size.height / 2), radius = max(size.width, size.height) * 0.75f))
            },
        )
    }
}
