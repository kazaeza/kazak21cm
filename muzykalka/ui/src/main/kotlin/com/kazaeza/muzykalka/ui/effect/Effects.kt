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
import androidx.compose.ui.graphics.graphicsLayer
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Slowly drifting coloured light behind a screen — the "juicy" base layer. Three big radial blobs
 * in the accent colours over deep ink; cheap (one draw call per blob).
 */
@Composable
fun AuroraBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    intensity: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c1 = colors.getOrElse(0) { Juicy.Violet }
    val c2 = colors.getOrElse(1) { c1 }
    val c3 = colors.getOrElse(2) { Juicy.Blue }
    Box(
        modifier
            .background(Juicy.Ink)
            .drawBehind {
                val w = size.width
                val h = size.height
                val r = max(w, h)
                drawCircle(
                    Brush.radialGradient(listOf(c1.copy(alpha = 0.55f * intensity), Color.Transparent), Offset(w * (0.1f + 0.5f * a), h * 0.08f), r * 0.75f),
                    radius = r * 0.75f, center = Offset(w * (0.1f + 0.5f * a), h * 0.08f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(c2.copy(alpha = 0.45f * intensity), Color.Transparent), Offset(w * (0.95f - 0.4f * b), h * (0.45f + 0.2f * a)), r * 0.7f),
                    radius = r * 0.7f, center = Offset(w * (0.95f - 0.4f * b), h * (0.45f + 0.2f * a)),
                )
                drawCircle(
                    Brush.radialGradient(listOf(c3.copy(alpha = 0.35f * intensity), Color.Transparent), Offset(w * (0.2f + 0.3f * b), h * 1.02f), r * 0.8f),
                    radius = r * 0.8f, center = Offset(w * (0.2f + 0.3f * b), h * 1.02f),
                )
            },
        content = content,
    )
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
