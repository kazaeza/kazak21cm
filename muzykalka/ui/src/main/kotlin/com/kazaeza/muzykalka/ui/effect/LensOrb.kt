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
import com.kazaeza.muzykalka.music.Overtones
import com.kazaeza.muzykalka.music.PulseKind
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Motion

/**
 * O-26 + O-37: the sphere from the reference posters as one fragment shader (AGSL, Android 13+) — a drop of liquid light.
 *
 * A drop has ONE surface height, and everything optical is taken from it, nothing is drawn on top:
 *  - a pluck is a wave that runs over the surface from the point nearest to the eye round to the back (an angle,
 *    not a screen radius: it slows and narrows at the limb, as on a real ball); the drop itself rings after it, in
 *    four slow modes l = 2..5 about the line of sight ([uMode]);
 *  - the colour field inside is seen through that tilted surface: the sample point moves with the tilt (the lens),
 *    each colour channel a little differently (the fringe), and the same normal lights it (the relief);
 *  - a pluck also stirs the field: its folds deepen and its flow runs ahead ([uKick]);
 *  - the answered degree stands in the drop as a star of p petals inside and as p soft rays in the glow;
 *    the outline stays a circle and only breathes;
 *  - the field: a colour field of [uC0], [uC1], [uC3] bent by four harmonics of one slow wave, deep shadow where
 *    it folds away, three overtone lobes of light, one fold (a film of light, crisp on one edge, dissolving on the
 *    other); then a rim that changes hue, a soft lopsided halo and fine grain.
 *
 * Colours come in as sRGB and are mixed as light (squared in, square root out). Output is premultiplied.
 * The numbers worth turning are listed in docs/design/O-37-drop.md; the shader is the same text that was run
 * in Skia and in the lab page (docs/design/lab/drop.agsl).
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
uniform vec4 uMode;
uniform float uKick;
uniform vec2 uTouch;
uniform float uLobes;
uniform float uLock;
uniform float uSpin;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 lin(vec3 c) { return c * c; }

// A pluck sends one wave over the drop, from the point nearest to the eye round to the back: a crest with a
// shallow trough on either side, th radians from that point. Returns (height, slope along the meridian).
vec2 wave(float th) {
    float h = 0.0;
    float dh = 0.0;
    for (int i = 0; i < 4; i++) {
        float age = uPulseAge[i];
        float x = (th - 2.2 * age) / 0.21;
        float a = uPulseAmp[i] * exp(-age * 1.7) * (1.0 - exp(-age / 0.03));
        float gs = exp(-x * x);
        float c = cos(1.9 * x);
        h += a * gs * c;
        dh += a * gs * (-2.0 * x * c - 1.9 * sin(1.9 * x)) / 0.21;
    }
    return vec2(h, dh);
}

// And the drop itself rings: its slow modes l = 2..5 about the line of sight, sum A_l P_l(z), z = cos th.
// Returns (height, d height / dz).
vec2 wobble(float z) {
    float p0 = 1.0;
    float p1 = z;
    float d0 = 0.0;
    float d1 = 1.0;
    float h = 0.0;
    float dh = 0.0;
    for (int i = 0; i < 4; i++) {
        float fl = float(i + 2);
        float p2 = ((2.0 * fl - 1.0) * z * p1 - (fl - 1.0) * p0) / fl;
        float d2 = d0 + (2.0 * fl - 1.0) * p1;
        h += uMode[i] * p2;
        dh += uMode[i] * d2;
        p0 = p1; p1 = p2; d0 = d1; d1 = d2;
    }
    return vec2(h, dh);
}

vec3 field(vec2 p, float t, float shift) {
    float r2 = min(dot(p, p), 1.0);
    float z = sqrt(1.0 - r2);
    vec2 q = p * (0.80 + 0.35 * (1.0 - z));
    // a pluck stirs the field itself: its folds deepen for a moment
    float stir = 1.0 + 0.45 * uKick;
    for (int k = 1; k <= 4; k++) {
        float fk = float(k);
        q += (0.26 * stir / (fk * sqrt(fk))) * sin(fk * 1.45 * q.yx + t * 0.19 * fk + vec2(1.3, 4.1) * fk + uSeed);
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
    float t = iTime;
    // the whole drop gives way towards the finger
    p -= uTouch * 0.035;
    float phi = atan(p.y, p.x);
    // its outline breathes with the wobble and with the wave as it passes the limb; it stays a circle
    float rimH = dot(uMode, vec4(-0.5, 0.0, 0.375, 0.0)) + 0.6 * wave(1.5708).x;
    float outline = 1.0 + 0.030 * rimH;
    // the answered degree stands in the drop as a star of p petals (p of the ratio p:q); the tonic is all one
    float petals = uLobes < 1.5 ? 1.0 : cos(uLobes * phi + uSpin);
    p /= outline;
    float r = length(p);
    float e = 0.045;
    float mask = 1.0 - smoothstep(1.0 - e, 1.0 + e, r);
    vec2 pc = p / max(r, 1.0);
    float rr = min(r, 1.0);
    float z = sqrt(max(0.0, 1.0 - rr * rr));

    // one height for everything: the wave of each pluck, the wobble of the drop, the star
    float th = asin(rr);
    vec2 wv = wave(th);
    vec2 wb = wobble(z);
    float rm = pow(max(rr, 0.0001), max(uLobes - 1.0, 0.0));
    float starH = uLock * rm * rr * petals;
    float height = wv.x + 0.6 * wb.x + 0.8 * starH;
    // how the surface tilts: along the meridian for the wave and the wobble, by its own slope for the star
    float slope = -0.11 * z * wv.y + 0.16 * z * rr * wb.y;
    slope = slope / (1.0 + 1.6 * abs(slope));
    float sa = (uLobes - 1.0) * phi + uSpin;
    vec2 tilt = slope * (p / max(r, 0.001)) - 0.16 * uLock * rm * vec2(cos(sa), -sin(sa));
    // the lens: what is inside is seen through the tilted surface, each colour bent a little differently
    vec2 ps = pc - 0.42 * tilt - uTouch * 0.07 * (1.0 - rr * rr);
    float ca = 0.06 * rr * rr + 0.16 * length(tilt);
    vec3 inside = vec3(
        field(ps * (1.0 + ca), t, 0.018).r,
        field(ps, t, 0.0).g,
        field(ps * (1.0 - ca), t, -0.018).b
    );
    vec3 n = normalize(vec3(pc + tilt, z));
    vec3 L = normalize(vec3(-0.45 + 0.5 * uTouch.x, -0.6 + 0.5 * uTouch.y, 0.66));
    float lit = dot(n, L);
    inside *= mix(0.50, 1.08, smoothstep(-0.7, 0.8, lit));
    // relief: the side of a wave that faces the light is brighter, and more liquid means more light
    inside *= clamp(1.0 + 1.3 * dot(tilt, L.xy) + 0.32 * height, 0.35, 2.4);

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
    float h = (0.42 * exp(-d * 5.5) + 0.10 * exp(-d * 1.8)) * side * (0.7 + 0.6 * uEnergy) * (1.0 + 0.6 * uKick);
    // the star shows in the glow too: p soft rays
    h *= 1.0 + 0.45 * uLock * petals;
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
    var spin = 0f

    /** O-37: how far each slow mode of the drop (l = 2..5) is swung now, the sum over the plucks that still ring. */
    val mode = FloatArray(4)

    /** O-37: how hard the plucks push the drop now, 0…[KICK_MAX]. */
    var kick = 0f

    fun fill(live: List<LivePulse>) {
        for (i in 0 until 4) {
            val lp = live.getOrNull(live.size - 1 - i)
            age[i] = lp?.age ?: NO_PULSE
            amp[i] = if (lp == null) 0f else lp.orbStrength * Overtones.tail(lp.age)
        }
        mode.fill(0f)
        var k = 0f
        for (lp in live) {
            val s = lp.orbStrength
            for (i in 0 until 4) mode[i] += s * Overtones.dropMode(i, lp.age)
            k += s * Overtones.kick(lp.age)
        }
        kick = minOf(k, KICK_MAX)
    }
}

/** O-37: how strongly a pluck moves the sphere. 1 is the strength the lab page opened with; the slider there goes 0.4…1.6. */
internal const val ORB_PLUCK_GAIN = 1.0f

/** What the shader may be asked for as a push: a few plucks at once do not turn the drop inside out. */
internal const val KICK_MAX = 1.5f

/** What one pluck is worth to the sphere: a finger's pluck rings it more softly than a sound does. */
internal val LivePulse.orbStrength: Float
    get() = strength * (if (kind == PulseKind.TOUCH) 0.6f else 1f) * ORB_PLUCK_GAIN

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
        shader.setFloatUniform("uMode", frame.mode[0], frame.mode[1], frame.mode[2], frame.mode[3])
        shader.setFloatUniform("uKick", frame.kick)
        shader.setFloatUniform("uTouch", frame.touchX, frame.touchY)
        shader.setFloatUniform("uLobes", frame.lobes)
        shader.setFloatUniform("uLock", frame.lock)
        shader.setFloatUniform("uSpin", frame.spin)
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
 * O-35 / O-37, all optional: every pluck of [pulses] sends a wave over the drop, rings it, and stirs and speeds up
 * the flow inside; the sphere leans towards [touch] (a unit direction, the pressed degree) and springs back;
 * [lobes] > 0 shows the degree that has been answered — a star of p petals inside and p rays in the glow for the
 * ratio p:q — which stands when [tension] is 0 and turns when it is 1. [lock] 0…1 is how much of that pattern
 * shows (it grows by itself when [lobes] is given).
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
                    val k = 1f + 0.03f * minOf(pulses?.live()?.kick() ?: 0f, 1f)
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
    // the inner flow runs ahead while a pluck pushes (read from the board in the frame callback, not in composition)
    val time = if (moving) {
        rememberFlowSeconds(start) { 0.55f + 1.1f * e + Overtones.DROP_SURGE * (pulses?.live()?.kick() ?: 0f) }
    } else {
        remember { mutableFloatStateOf(start) }
    }
    // the star turns by this phase: it stands for a consonance and creeps for a dissonance
    val spin = if (moving) rememberFlowSeconds(0f) { 0.9f * tense } else remember { mutableFloatStateOf(0f) }
    Canvas(modifier) {
        frame.fill(pulses?.live() ?: emptyList())
        frame.touchX = lean.x
        frame.touchY = lean.y
        frame.lobes = keptLobes.toFloat()
        frame.lock = locked
        frame.spin = spin.floatValue
        painter.paint(this, time.floatValue, sphere * size.minDimension / 2, e, dim, seed, cs, frame)
    }
}
