package com.kazaeza.muzykalka.ui.effect

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.min
import com.kazaeza.muzykalka.music.PulseKind
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Motion

/**
 * O-26: the sphere from the reference posters, as one fragment shader (AGSL, Android 13+).
 *
 * What it paints, back to front:
 *  - a colour field of [uC0], [uC1], [uC3] bent by four harmonics of one slow wave (the k-th moves k times
 *    faster and is k√k times weaker), seen through a lens: the picture swells towards the edge;
 *  - deep shadow where the field folds away, so the sphere has an inside;
 *  - overtones: three soft lobes of light, the k-th circling k times faster, smaller and fainter, added
 *    like light (Screen);
 *  - one fold: a film of light lying across the sphere, crisp on one edge and dissolving on the other,
 *    with a thin chromatic fringe (the three colour channels are taken at slightly different places);
 *  - a rim that changes hue around the sphere, a soft lopsided halo outside it, and fine grain over all.
 *
 * Colours come in as sRGB and are mixed as light (squared in, square root out). Output is premultiplied.
 * Do not put a dollar sign in this text: it is a Kotlin string.
 */
const val LENS_ORB_AGSL = """
uniform vec2 iResolution;
uniform float iTime;
uniform float uRadius;
uniform float uEnergy;
uniform float uDim;
uniform float uSeed;
uniform vec3 uC0;
uniform vec3 uC1;
uniform vec3 uC2;
uniform vec3 uC3;
uniform vec4 uPulseAge;
uniform vec4 uPulseAmp;
uniform vec2 uTouch;
uniform float uLobes;
uniform float uLock;
uniform float uTension;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 lin(vec3 c) { return c * c; }

vec3 field(vec2 p, float t, float shift) {
    float r2 = min(dot(p, p), 1.0);
    float z = sqrt(1.0 - r2);
    vec2 q = p * (0.80 + 0.35 * (1.0 - z));
    for (int k = 1; k <= 4; k++) {
        float fk = float(k);
        q += (0.26 / (fk * sqrt(fk))) * sin(fk * 1.45 * q.yx + t * 0.19 * fk + vec2(1.3, 4.1) * fk + uSeed);
    }
    float a = 0.5 + 0.5 * sin(1.7 * q.x + 1.1 * q.y + t * 0.09);
    float b = 0.5 + 0.5 * sin(1.2 * q.y - 1.4 * q.x + 2.1 - t * 0.06);
    vec3 col = mix(lin(uC0), lin(uC1), smoothstep(0.1, 0.9, a));
    col = mix(col, lin(uC3), 0.55 * smoothstep(0.35, 1.0, b));
    float depth = 0.5 + 0.5 * sin(1.1 * q.x - 0.8 * q.y + 3.7 + t * 0.05);
    col *= mix(0.10, 1.0, smoothstep(0.0, 0.85, depth));

    // overtones: soft lobes of light, the k-th circling k times faster, smaller and fainter
    for (int k = 2; k <= 4; k++) {
        float fk = float(k);
        float th = t * 0.06 * fk + uSeed * 2.3 + fk * 1.9;
        vec2 c = (0.62 - 0.07 * fk) * vec2(cos(th), sin(th * 0.83 + fk));
        float w = 1.0 - smoothstep(0.0, 1.05 - 0.14 * fk, length(p - c));
        vec3 tint = mix(lin(uC2), lin(uC3), fract(0.41 * fk));
        vec3 add = tint * (w * w * 1.5 / fk);
        col = 1.0 - (1.0 - col) * (1.0 - min(add, 1.0));
    }
    // O-35: after the answer the degree stands in the sphere as lobes: p of them for the ratio p:q.
    // A consonance stands, a dissonance turns slowly.
    float ang = atan(p.y, p.x);
    float stand = cos(uLobes * ang + t * 0.9 * uTension) * smoothstep(0.12, 0.85, length(p));
    col *= 1.0 + 0.5 * uLock * stand;
    // the fold: one film of light lying over it all - crisp on one edge, dissolving on the other
    float th = t * 0.04 + uSeed * 1.7 + 0.6;
    vec2 dir = vec2(cos(th), sin(th));
    vec2 dv = p - 2.2 * dir;
    float dl = length(dv);
    float s = dl - (2.2 + 0.5 * sin(t * 0.09 + uSeed * 3.1)) + shift;
    float along = 0.5 + 0.5 * sin(dot(p, vec2(-dir.y, dir.x)) * 1.6 + t * 0.05 + 1.0);
    float crisp = mix(0.02, 0.16, along);
    float body = smoothstep(-0.65, 0.0, s);
    float film = (1.0 - smoothstep(0.0, crisp, s)) * body * body;
    vec3 over = mix(mix(lin(uC2), lin(uC1), b), vec3(1.0), 0.42);
    float drop = exp(-max(s, 0.0) * 5.0) * step(0.0, s) * (1.0 - along);
    col *= 1.0 - 0.5 * drop;
    col = mix(col, over, film * 0.72);
    col += vec3(1.0) * exp(-(s * s) / 0.0008) * 0.20 * (1.0 - along);
    return col;
}

vec4 main(vec2 fragCoord) {
    vec2 p = (fragCoord - 0.5 * iResolution) / uRadius;
    float r = length(p);
    float t = iTime;
    // O-35: every pluck sends a ring from the centre to the edge; the lens swells under its front.
    float ring = 0.0;
    float kick = 0.0;
    for (int i = 0; i < 4; i++) {
        float age = uPulseAge[i];
        float fade = uPulseAmp[i] * exp(-age * 2.0);
        float dd = (r - age * 1.5) / 0.12;
        ring += exp(-dd * dd) * fade;
        kick += fade;
    }
    p -= (p / max(r, 0.001)) * ring * 0.045;
    // the sphere leans towards the pressed degree
    p -= uTouch * 0.07 * (1.0 - min(r * r, 1.0));
    r = length(p);
    float e = 0.045;
    float mask = 1.0 - smoothstep(1.0 - e, 1.0 + e, r);
    vec2 pc = p / max(r, 1.0);
    float rr = min(r, 1.0);
    float ca = 0.06 * rr * rr;
    vec3 inside = vec3(
        field(pc * (1.0 + ca), t, 0.018).r,
        field(pc, t, 0.0).g,
        field(pc * (1.0 - ca), t, -0.018).b
    );
    float z = sqrt(max(0.0, 1.0 - rr * rr));
    vec3 n = vec3(pc, z);
    vec3 L = normalize(vec3(-0.45 + 0.5 * uTouch.x, -0.6 + 0.5 * uTouch.y, 0.66));
    float lit = dot(n, L);
    inside *= mix(0.50, 1.08, smoothstep(-0.7, 0.8, lit));
    inside = inside * (1.0 + 0.9 * ring) + vec3(0.16) * ring;

    vec2 u = p / max(r, 0.001);
    float sA = 0.5 + 0.5 * dot(u, vec2(cos(t * 0.07 + uSeed), sin(t * 0.07 + uSeed)));
    float sB = 0.5 + 0.5 * dot(u, vec2(cos(2.1 - t * 0.05), sin(2.1 - t * 0.05)));
    vec3 rimA = mix(lin(uC1), lin(uC3), sA);
    vec3 rimB = mix(lin(uC0), lin(uC2), sB);
    float rimK = smoothstep(0.74, 1.0, rr);
    float away = smoothstep(0.9, -0.6, lit);
    inside = mix(inside, rimA, rimK * rimK * (0.15 + 0.45 * away));

    float d = max(r - 1.0, 0.0);
    float side = 0.55 + 0.45 * sB;
    float h = (0.42 * exp(-d * 5.5) + 0.10 * exp(-d * 1.8)) * side * (0.7 + 0.6 * uEnergy) * (1.0 + 0.7 * min(kick, 1.0));
    float edge = 0.5 * min(iResolution.x, iResolution.y) / uRadius;
    h *= smoothstep(edge, edge * 0.7, r);
    vec3 halo = mix(rimA, rimB, 0.4) * h;

    vec3 col = sqrt(max(inside * mask + halo * (1.0 - mask), 0.0));
    float g = hash12(fragCoord) - 0.5;
    col += g * 0.045 * (mask + 0.6 * min(2.0 * h, 1.0));
    col = clamp(col, 0.0, 1.0);
    float alpha = clamp(max(mask, max(col.r, max(col.g, col.b))), 0.0, 1.0);
    return vec4(col * uDim, alpha * uDim);
}
"""

/** Where the picture starts, in seconds: a moment when the fold lies well across the sphere. */
private const val LENS_START = 12f

/** Everything the shader needs besides the colours; the arrays are reused from frame to frame. */
private class LensFrame {
    val age = FloatArray(4) { NO_PULSE }
    val amp = FloatArray(4)
    var touchX = 0f
    var touchY = 0f
    var lobes = 0f
    var lock = 0f
    var tension = 0f

    fun fill(live: List<LivePulse>) {
        for (i in 0 until 4) {
            val lp = live.getOrNull(live.size - 1 - i)
            age[i] = lp?.age ?: NO_PULSE
            // a finger's pluck rings the sphere more softly than a sound does
            amp[i] = if (lp == null) 0f else lp.strength * (if (lp.kind == PulseKind.TOUCH) 0.6f else 1f)
        }
    }
}

/** An age no pluck has: the ring is long gone. */
private const val NO_PULSE = 99f

private fun interface LensPainter {
    fun paint(scope: DrawScope, time: Float, radius: Float, energy: Float, dim: Float, seed: Float, colors: List<Color>, frame: LensFrame)
}

/** Null when the device cannot compile it; the caller then paints [OvertoneOrb]. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun lensPainter(): LensPainter? = runCatching {
    val shader = RuntimeShader(LENS_ORB_AGSL)
    val brush = ShaderBrush(shader)
    LensPainter { scope, time, radius, energy, dim, seed, colors, frame ->
        shader.setFloatUniform("iResolution", scope.size.width, scope.size.height)
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("uRadius", radius)
        shader.setFloatUniform("uEnergy", energy)
        shader.setFloatUniform("uDim", dim)
        shader.setFloatUniform("uSeed", seed)
        colors.forEachIndexed { i, c -> shader.setFloatUniform("uC$i", c.red, c.green, c.blue) }
        shader.setFloatUniform("uPulseAge", frame.age[0], frame.age[1], frame.age[2], frame.age[3])
        shader.setFloatUniform("uPulseAmp", frame.amp[0], frame.amp[1], frame.amp[2], frame.amp[3])
        shader.setFloatUniform("uTouch", frame.touchX, frame.touchY)
        shader.setFloatUniform("uLobes", frame.lobes)
        shader.setFloatUniform("uLock", frame.lock)
        shader.setFloatUniform("uTension", frame.tension)
        scope.drawRect(brush)
    }
}.getOrNull()

/** Four lights for the sphere: the given colours first, then neighbours from the neon palette. */
private fun lensColors(colors: List<Color>): List<Color> =
    if (colors.size >= 4) colors.take(4) else (colors + listOf(Juicy.Magenta, Juicy.Cyan, Juicy.Violet, Juicy.Blue)).distinct().take(4)

/**
 * Seconds that flow at [speed] (asked every frame, so the flow can speed up and slow down without a jump).
 * Stands still at [start] where endless animation is switched off (tests, «убрать анимации»).
 */
@Composable
fun rememberFlowSeconds(start: Float = 0f, speed: () -> Float = { 1f }): FloatState {
    val time = remember { mutableFloatStateOf(start) }
    val rate by rememberUpdatedState(speed)
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (last >= 0) time.floatValue += ((now - last) / 1e9f).coerceAtMost(0.1f) * rate()
                last = now
            }
        }
    }
    return time
}

/**
 * O-26: the overtone sphere of the references — see [LENS_ORB_AGSL] for what is in it.
 *
 * [colors] are its four lights (fewer are topped up from the neon palette). The sphere takes [sphere] of the
 * half-side of its box; the rest is room for the halo, so give it a box bigger than the ball you want.
 * [energy] 0…1 is how loud it is: the halo grows and the lights flow faster. [seed] makes two spheres on one
 * screen differ. [moving] = false is a still picture (marks in lists).
 *
 * O-35, all optional: every pluck of [pulses] sends a ring from the centre to the edge; the sphere leans
 * towards [touch] (a unit direction, the pressed degree) and springs back; [lobes] > 0 shows the degree
 * that has been answered — p lobes for the ratio p:q — which stands when [tension] is 0 and turns when it
 * is 1. [lock] 0…1 is how much of that pattern shows (it grows by itself when [lobes] is given).
 *
 * Android 12 has no runtime shaders: there it is the painted [OvertoneOrb] of the same size, which only
 * swells a little on a pluck.
 */
@Composable
fun LensOrb(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    energy: Float = 0.5f,
    dim: Float = 1f,
    seed: Float = 0f,
    sphere: Float = 0.62f,
    moving: Boolean = true,
    pulses: PulseBoard? = null,
    touch: Offset = Offset.Zero,
    lobes: Int = 0,
    lock: Float = if (lobes > 0) 1f else 0f,
    tension: Float = 0f,
) {
    val cs = lensColors(colors)
    val painter = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) lensPainter() else null }
    if (painter == null) {
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            OvertoneOrb(
                cs,
                Modifier.requiredSize(min(maxWidth, maxHeight) * (sphere / 0.84f)).graphicsLayer {
                    val k = 1f + 0.03f * (pulses?.live()?.kick() ?: 0f)
                    scaleX = k
                    scaleY = k
                },
                moving = moving,
                glow = 0.3f + 0.5f * energy,
                dim = dim,
            )
        }
        return
    }
    val e by animateFloatAsState(energy.coerceIn(0f, 1f), tween(Motion.LONG), label = "energy")
    val lean by animateValueAsState(touch, Offset.VectorConverter, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "lean")
    // the pattern keeps its shape while it fades out, and the shape snaps (a half lobe would show a seam)
    var keptLobes by remember { mutableIntStateOf(lobes) }
    if (lobes > 0) keptLobes = lobes
    val locked by animateFloatAsState(if (lobes > 0) lock.coerceIn(0f, 1f) else 0f, tween(Motion.LONG), label = "lock")
    val tense by animateFloatAsState(tension.coerceIn(0f, 1f), tween(Motion.LONG), label = "tension")
    val frame = remember { LensFrame() }
    val start = LENS_START + seed * 7f
    val time = if (moving) rememberFlowSeconds(start) { 0.55f + 1.1f * e } else remember { mutableFloatStateOf(start) }
    Canvas(modifier) {
        frame.fill(pulses?.live() ?: emptyList())
        frame.touchX = lean.x
        frame.touchY = lean.y
        frame.lobes = keptLobes.toFloat()
        frame.lock = locked
        frame.tension = tense
        painter.paint(this, time.floatValue, sphere * size.minDimension / 2, e, dim, seed, cs, frame)
    }
}
