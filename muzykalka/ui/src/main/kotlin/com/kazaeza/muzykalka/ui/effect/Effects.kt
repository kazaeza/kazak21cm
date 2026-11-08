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
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * O-24 «Обертон»: the base layer of every screen. Warm near-black, one big soft orb of light in the
 * accent colours that drifts and breathes very slowly, and a fine film grain over everything.
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
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(31_000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c1 = colors.getOrElse(0) { Juicy.Violet }
    val c2 = colors.getOrElse(1) { c1 }
    val c3 = colors.getOrElse(2) { c2 }
    val grain = rememberGrain()
    Box(
        modifier
            .background(Juicy.Ink)
            .drawBehind {
                val w = size.width
                val h = size.height
                val r = max(w, h)
                // the orb: three overlapping lights, high on the screen, slightly off-centre
                val center = Offset(w * (0.62f - 0.18f * a), h * (0.16f + 0.05f * b))
                val k = intensity.coerceIn(0f, 1f)
                drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.42f * k), c1.copy(alpha = 0.10f * k), Color.Transparent), center, r * 0.62f), r * 0.62f, center)
                val c2c = center + Offset(w * (0.10f - 0.20f * b), h * 0.07f)
                drawCircle(Brush.radialGradient(listOf(c2.copy(alpha = 0.34f * k), Color.Transparent), c2c, r * 0.42f), r * 0.42f, c2c)
                val c3c = center + Offset(-w * 0.16f * a, -h * 0.04f)
                drawCircle(Brush.radialGradient(listOf(c3.copy(alpha = 0.26f * k), Color.Transparent), c3c, r * 0.30f), r * 0.30f, c3c)
                // the dark falls back in at the bottom, so content reads on calm ink
                drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Juicy.Ink.copy(alpha = 0.85f)))
                drawGrain(grain)
            },
        content = content,
    )
}

/**
 * A soft sphere of light (the «шар» of O-24): a body of [colors] blurred at the edge, lit from the
 * upper left. [glow] 0..1 adds a halo around it — used to show the tonic, a right answer, a sound.
 */
@Composable
fun GlowOrb(colors: List<Color>, modifier: Modifier = Modifier, glow: Float = 0.5f, dim: Float = 1f) {
    val c1 = colors.getOrElse(0) { Juicy.Gold }
    val c2 = colors.getOrElse(1) { c1 }
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        if (glow > 0f) {
            drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.45f * glow * dim), Color.Transparent), c, r * 1.9f), r * 1.9f, c)
        }
        drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = dim), c2.copy(alpha = 0.85f * dim), c2.copy(alpha = 0f)), c + Offset(-r * 0.25f, -r * 0.25f), r * 1.35f), r, c)
        // the lit side: a pale highlight, and the far side sinking into the dark
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f * dim), Color.Transparent), c + Offset(-r * 0.35f, -r * 0.4f), r * 0.7f), r, c)
        drawCircle(Brush.radialGradient(0.55f to Color.Transparent, 1f to Juicy.Ink.copy(alpha = 0.55f), center = c + Offset(-r * 0.3f, -r * 0.3f), radius = r * 1.5f), r, c)
    }
}

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
            alpha.snapTo(0.22f)
            alpha.animateTo(0f, tween(420))
        }
    }
    if (alpha.value > 0f) {
        Box(modifier.fillMaxSize().background(color.copy(alpha = alpha.value)))
    }
}
