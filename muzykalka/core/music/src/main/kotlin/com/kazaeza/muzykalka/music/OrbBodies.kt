package com.kazaeza.muzykalka.music

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * O-38 (ADR-040): what the tonic sphere is made of. One rule for all of them: nothing is drawn «on top» — a pluck and
 * an answer are forces on the one material the body is made of, and the picture is whatever that material does.
 */
enum class OrbBody {
    /** One closed string wound into a ball, with the afterglow of a phosphor screen. The default. */
    THREAD,

    /** A real fluid (stable fluids) in a glass ball. Needs GLES 3 with float render targets. */
    INK,

    /** Grains on a ringing plate: Chladni figures. */
    SAND,

    /** The drop of O-37, one AGSL shader. The fallback where GLES 3 is not there (and for tests, which have no GPU). */
    DROP,
}

/** The numbers every body shares. */
object OrbPhysics {
    /** Radius of the body in widths of its square box (the box is a little larger than the ball: it holds the halo). */
    const val RADIUS = 0.42

    /**
     * 0 = rest (the tonic), 1 = the most restless (the minor second): the simpler the ratio, the calmer. It is not
     * [Overtones.tension], which is tuned for the drift of the standing waves on the strings; this one is tuned for
     * how fast the bodies creep and flicker (log of p·q, as on the lab page).
     */
    fun tension(semitones: Int): Float {
        val s = Math.floorMod(semitones, 12)
        if (s == 0) return 0f
        val (p, q) = Overtones.RATIO[s]
        return min(1.0, ln((p * q).toDouble()) / ln(240.0)).toFloat()
    }

    /** Where a pluck at [x] (0..1 along the string below the question) strikes the body: a point in the lower half, in unit-disc coordinates, y up. */
    fun pluckPoint(x: Float): Pair<Float, Float> {
        val a = (x - 0.5f) * PI.toFloat() * 0.9f
        return sin(a) * 0.7f to -cos(a) * 0.7f
    }
}

/**
 * Bessel function J_m(x) from its integral — plain and exact enough for a picture (the lab page's own).
 */
fun besselJ(m: Int, x: Double): Double {
    var s = 0.0
    val n = 220
    for (i in 0 until n) {
        val t = PI * (i + 0.5) / n
        s += cos(m * t - x * sin(t))
    }
    return s / n
}

/** The [n]-th positive zero of J_m (n from 1). 10 if none is found below 140 (never for the figures used here). */
fun besselZero(m: Int, n: Int): Double {
    var prev = besselJ(m, 0.4)
    var found = 0
    var x = 0.45
    while (x < 140) {
        val v = besselJ(m, x)
        if (prev * v < 0 && x > m * 0.9) {
            found++
            if (found == n) return x - 0.05 * v / (v - prev)
        }
        prev = v
        x += 0.05
    }
    return 10.0
}

/**
 * THREAD: one closed string of [points] points, wound into a sphere by the ratio p:q of the answered degree
 * (longitude p·u, height cos(q·u)). At rest it lies on itself as one ring; an answer winds it up like a spring let go
 * (a little past, then back); a pluck sends two waves round it, the high harmonics dying first. The renderer draws
 * [ribbon] three times, wide to thin, and keeps an afterglow.
 */
class ThreadBody(val points: Int = THREAD_POINTS) {
    private class Pluck(var t: Double, val s: Double, val u: Double)

    private val plucks = ArrayList<Pluck>()
    private var lockV = 0.0
    private var goal = 0.0
    private var spin = 0.0

    var time = 0.0
        private set

    /** 0..1.5: how loud the body is; halves in a second, plucks and answers raise it. */
    var energy = 0.0
        private set

    /** The answered degree in semitones, or -1. */
    var semitone = -1
        private set

    var p = 1
        private set

    var q = 1
        private set

    /** How far the winding is closed: 0 = one ring, 1 = the full winding; it overshoots a little while it moves. */
    var lock = 0.0
        private set

    private val px = FloatArray(points + 1)
    private val py = FloatArray(points + 1)
    private val pd = FloatArray(points + 1)

    /** Floats in [ribbon]: two vertices of [FLOATS_PER_VERTEX] for each of the points. */
    val ribbonSize: Int get() = (points + 1) * 2 * FLOATS_PER_VERTEX

    /** The share of the winding that shows, 0..1. */
    val lockShown: Double get() = lock.coerceIn(0.0, 1.0)

    /** At rest the string lies on itself p times: keep the light of one string. */
    val fold: Double get() = 1.0 / (1.0 + (p - 1) * (1.0 - lockShown).pow(3))

    /** The more turns, the more strings cross: thin the light. */
    val dense: Double get() = 1.0 / (1.0 + 0.035 * (p + q))

    /** A pluck of [strength] at the angle [angle] (radians, 0 = right, counter-clockwise) of the string. */
    fun pluck(strength: Float, angle: Float) {
        plucks.add(Pluck(0.0, strength.toDouble(), angle.toDouble()))
        if (plucks.size > MAX_PLUCKS) plucks.removeAt(0)
        energy = min(1.5, energy + strength)
    }

    /** The degree [semitones] above the tonic has been answered: wind up in its ratio. The tonic itself only brightens. */
    fun answer(semitones: Int) {
        val s = Math.floorMod(semitones, 12)
        if (s == 0) {
            semitone = 0
            goal = 0.0
            energy = 1.2
            return
        }
        semitone = s
        val (a, b) = Overtones.RATIO[s]
        p = a
        q = b
        lock = 0.0
        lockV = 0.0
        goal = 1.0
    }

    /** Unwind: back to one ring. */
    fun clear() {
        goal = 0.0
        semitone = -1
    }

    /** Advances [dt] seconds (longer than 1/30 s counts as 1/30: a stalled frame must not throw the string). */
    fun step(dt: Double) {
        val d = min(dt, MAX_DT)
        time += d
        energy *= exp(-d * 1.6)
        // the winding opens like a spring let go: a little past, then back
        lockV += (140.0 * (goal - lock) - 15.0 * lockV) * d
        lock += lockV * d
        if (goal == 0.0 && lock < 0.02) {
            p = 1
            q = 1
        }
        spin += d * (0.05 + 0.5 * tensionNow() * lock)
        for (i in plucks.indices.reversed()) {
            plucks[i].t += d
            if (plucks[i].t > 5) plucks.removeAt(i)
        }
    }

    private fun tensionNow(): Double = if (semitone < 0) 0.0 else OrbPhysics.tension(semitone).toDouble()

    private val coef = DoubleArray(MAX_PLUCKS * HARMONICS)

    /**
     * The string in clip space (the box is [-1, 1]²), [points] + 1 points (the last is the first): [px], [py] and a
     * depth 0..1 (the far side of the ball is dim), for the state now.
     */
    fun trace() {
        val te = tensionNow()
        val s = lock.coerceIn(0.0, 1.04) * 0.9
        val tilt = s * (0.62 + 0.06 * sin(time * 0.31))
        val ct = cos(tilt)
        val st = sin(tilt)
        val n = plucks.size
        for (j in 0 until n) {
            val pl = plucks[j]
            for (k in 1..HARMONICS) {
                coef[j * HARMONICS + k - 1] = pl.s * 0.12 / k.toDouble().pow(1.35) *
                    exp(-pl.t * (0.8 + 0.45 * (k - 1))) * cos(9.5 * k * pl.t)
            }
        }
        val scale = OrbPhysics.RADIUS * 2
        for (i in 0..points) {
            val u = 2.0 * PI * i / points
            // a pluck sends two waves round the closed string, the high harmonics dying first (as on the strings of the Tir)
            var disp = 0.0
            for (j in 0 until n) {
                val c = cos(u - plucks[j].u)
                var tPrev = 1.0
                var tCur = c
                var sum = coef[j * HARMONICS] * tCur
                for (k in 2..HARMONICS) {
                    val next = 2.0 * c * tCur - tPrev
                    tPrev = tCur
                    tCur = next
                    sum += coef[j * HARMONICS + k - 1] * tCur
                }
                disp += sum
            }
            disp += 0.006 * te * lock * sin(37.0 * u + 7.0 * time)
            val z0 = s * cos(q * u)
            val rho = sqrt(1.0 - z0 * z0) * (1.0 + disp)
            val a = p * u + spin
            val x = rho * cos(a)
            val y0 = rho * sin(a)
            val z = z0 * (1.0 + disp)
            // lean the pole towards the eye a little, so the winding reads as a ball and not as a flat rose
            val y = y0 * ct - z * st
            val zz = y0 * st + z * ct
            val k = 1.0 / (1.0 - 0.16 * zz)
            px[i] = (x * k * scale).toFloat()
            py[i] = (y * k * scale).toFloat()
            pd[i] = (0.5 + 0.5 * zz).toFloat()
        }
    }

    /** The points of the last [trace] (clip space) and their depth. */
    val xs: FloatArray get() = px
    val ys: FloatArray get() = py
    val depths: FloatArray get() = pd

    /**
     * [trace] as a triangle strip for the line shader: for each point two vertices (one for each side of the string),
     * each of [FLOATS_PER_VERTEX] floats: x, y, normal x, normal y, depth, u (0..2π along the string), side (−1 / +1).
     */
    fun ribbon(out: FloatArray) {
        require(out.size >= ribbonSize) { "ribbon needs ${ribbonSize} floats" }
        trace()
        var o = 0
        val n = points
        for (i in 0..n) {
            val a = if (i == 0) n - 1 else i - 1
            val b = if (i == n) 1 else i + 1
            var tx = px[b] - px[a]
            var ty = py[b] - py[a]
            val l = sqrt(tx * tx + ty * ty).let { if (it == 0f) 1f else it }
            tx /= l
            ty /= l
            val u = (2.0 * PI * i / n).toFloat()
            for (side in -1..1 step 2) {
                out[o++] = px[i]
                out[o++] = py[i]
                out[o++] = -ty
                out[o++] = tx
                out[o++] = pd[i]
                out[o++] = u
                out[o++] = side.toFloat()
            }
        }
    }

    companion object {
        const val THREAD_POINTS = 3200
        const val FLOATS_PER_VERTEX = 7
        const val MAX_PLUCKS = 4
        private const val HARMONICS = 9
        private const val MAX_DT = 1.0 / 30
    }
}

/**
 * SAND: [count] grains on a round plate that rings in a figure — m nodal diameters and n nodal circles,
 * u = J_m(k·r)·cos(m·θ) with k the n-th zero of J_m (Chladni). A grain leaves where the plate moves and stays where it
 * does not: it slides down the slope of u² and is shaken in proportion to |u|. A pluck throws the sand up and it
 * settles again; an answer in the ratio p:q makes p diameters and q circles; the tonic is a plain ring figure.
 * Positions are kept in the unit disc; [out] is what the point shader takes: x, y in clip space and a heat 0..1.
 */
class SandBody(val count: Int = SAND_GRAINS, seed: Int = 12345) {
    private var rnd = seed.toLong() and MASK
    private val gx = FloatArray(count)
    private val gy = FloatArray(count)
    private val gvx = FloatArray(count)
    private val gvy = FloatArray(count)

    /** Per grain: x, y (clip space) and heat. Filled by [step]. */
    val out = FloatArray(count * 3)

    private val table = FloatArray(TABLE + 2)
    private var m = 0
    private var k = 1.0
    private var shake = 0.5
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var fingerDown = false
    private var spin = 0.0

    var time = 0.0
        private set

    var energy = 0.0
        private set

    var semitone = -1
        private set

    init {
        for (i in 0 until count) {
            val r = sqrt(rand()) * 0.97
            val a = rand() * 6.2832
            gx[i] = (r * cos(a)).toFloat()
            gy[i] = (r * sin(a)).toFloat()
        }
        figure(0, 3)
    }

    private fun rand(): Double {
        rnd = (rnd * 1664525L + 1013904223L) and MASK
        return rnd / 4294967296.0
    }

    /** The figure with [mm] nodal diameters and [nn] nodal circles. */
    private fun figure(mm: Int, nn: Int) {
        m = mm
        k = besselZero(mm, nn)
        var peak = 0.0
        for (i in 0 until TABLE + 2) {
            table[i] = besselJ(mm, k * i / TABLE).toFloat()
            peak = max(peak, abs(table[i].toDouble()))
        }
        for (i in 0 until TABLE + 2) table[i] = (table[i] / peak).toFloat()
    }

    /** Nodal diameters of the figure now (0 for the plain rings of the tonic and of rest). */
    val figureDiameters: Int get() = m

    /** The sand jumps in every direction: a gust of [s]. */
    private fun jump(s: Double) {
        for (i in 0 until count) {
            val a = rand() * 6.2832
            val v = (0.25 + rand()) * 1.5 * s
            gvx[i] = (gvx[i] + cos(a) * v).toFloat()
            gvy[i] = (gvy[i] + sin(a) * v).toFloat()
        }
    }

    fun pluck(strength: Float) {
        jump(strength.toDouble())
        shake = min(1.2, shake + 0.8 * strength)
        energy = min(1.5, energy + strength)
    }

    fun answer(semitones: Int) {
        val s = Math.floorMod(semitones, 12)
        semitone = s
        if (s == 0) figure(0, 2) else figure(Overtones.RATIO[s].first, Overtones.RATIO[s].second)
        shake = 1.1
        jump(0.35)
    }

    fun clear() {
        semitone = -1
        figure(0, 3)
        shake = 0.9
    }

    /** A finger on the plate at ([x], [y]) in unit-disc coordinates pushes the sand away; [down] = false lifts it. */
    fun touch(x: Double, y: Double, down: Boolean) {
        fingerX = x
        fingerY = y
        fingerDown = down
    }

    fun step(dt: Double) {
        val d = min(dt, MAX_DT)
        time += d
        energy *= exp(-d * 1.6)
        shake = 0.05 + (shake - 0.05) * exp(-d * 1.3)
        val te = if (semitone < 0) 0.0 else OrbPhysics.tension(semitone).toDouble()
        spin += d * 0.25 * te
        val pull = 15.0 / sqrt(k)
        val jit = (0.9 * shake + 0.25 * te) * sqrt(d) * 0.55
        val damp = exp(-d * 9.0)
        val cs = cos(spin)
        val ss = sin(spin)
        val scale = OrbPhysics.RADIUS * 2
        val mm = m
        val mTable = TABLE.toDouble()
        for (i in 0 until count) {
            var x = gx[i].toDouble()
            var y = gy[i].toDouble()
            val r0 = sqrt(x * x + y * y)
            val r = if (r0 == 0.0) 1e-4 else r0
            // cos and sin of m·(atan2(y, x) − spin) without an atan2: rotate the unit vector, then Chebyshev
            val c1 = if (r0 == 0.0) 1.0 else x / r
            val s1 = if (r0 == 0.0) 0.0 else y / r
            val c = c1 * cs + s1 * ss
            val sn = s1 * cs - c1 * ss
            var cm = 1.0
            var sm = 0.0
            if (mm > 0) {
                var tPrev = 1.0
                var tCur = c
                var uPrev = 0.0
                var uCur = 1.0
                for (j in 2..mm) {
                    val tn = 2.0 * c * tCur - tPrev
                    val un = 2.0 * c * uCur - uPrev
                    tPrev = tCur
                    tCur = tn
                    uPrev = uCur
                    uCur = un
                }
                cm = tCur
                sm = sn * uCur
            }
            val f = min(r, 0.999) * mTable
            val i0 = f.toInt()
            val fr = f - i0
            val j0 = table[i0].toDouble()
            val j1 = table[i0 + 1].toDouble()
            val j = j0 + (j1 - j0) * fr
            val dj = (j1 - j0) * mTable
            val u = j * cm
            // grains leave where the plate moves and stay where it does not: down the slope of u*u, and shaken by |u|
            val gr = dj * cm
            val gt = -mm * j * sm / max(r, 0.04)
            val ex = x / r
            val ey = y / r
            var ax = -2.0 * u * (gr * ex - gt * ey) * pull
            var ay = -2.0 * u * (gr * ey + gt * ex) * pull
            if (fingerDown) {
                val dx = x - fingerX
                val dy = y - fingerY
                val d2 = dx * dx + dy * dy
                if (d2 < 0.05) {
                    val w = 9.0 * (1.0 - d2 / 0.05) / (sqrt(d2) + 0.02)
                    ax += dx * w
                    ay += dy * w
                }
            }
            val a = abs(u) * jit + 0.016
            var vx = (gvx[i] + ax * d) * damp + (rand() - 0.5) * a * 9.0
            var vy = (gvy[i] + ay * d) * damp + (rand() - 0.5) * a * 9.0
            x += vx * d
            y += vy * d
            val r2 = sqrt(x * x + y * y)
            if (r2 > 0.985) {
                x *= 0.985 / r2
                y *= 0.985 / r2
                vx *= -0.3
                vy *= -0.3
            }
            gx[i] = x.toFloat()
            gy[i] = y.toFloat()
            gvx[i] = vx.toFloat()
            gvy[i] = vy.toFloat()
            out[i * 3] = (x * scale).toFloat()
            out[i * 3 + 1] = (y * scale).toFloat()
            out[i * 3 + 2] = min(1.0, sqrt(vx * vx + vy * vy) * 1.6).toFloat()
        }
    }

    companion object {
        const val SAND_GRAINS = 24_000
        private const val TABLE = 700
        private const val MASK = 0xFFFFFFFFL
        private const val MAX_DT = 1.0 / 30
    }
}

/**
 * INK: the part of the fluid body that is not the solver — what is put into the fluid each frame. The solver itself
 * (advection, curl, vorticity, projection, the glass wall) is GPU work in the renderer. [step] fills a [InkFrame]
 * with splats: velocity (a push), dye (a puff of colour), and bursts (a radial push from a point: a pluck). Coordinates
 * are in the unit square of the fluid; colours are not here but roles, so that the renderer can pick them.
 */
class InkScript {
    private var time = 0.0
    private var held = 0.0
    private var spin = 0.0
    private var seed = 1.3
    private val bursts = ArrayList<DoubleArray>()

    var energy = 0.0
        private set

    var semitone = -1
        private set

    /** A pluck of [strength] from ([x], [y]) in unit-disc coordinates, y up. */
    fun pluck(strength: Float, x: Float, y: Float) {
        if (bursts.size >= MAX_BURSTS) bursts.removeAt(0)
        bursts.add(doubleArrayOf(0.5 + OrbPhysics.RADIUS * x, 0.5 + OrbPhysics.RADIUS * y, strength.toDouble()))
        energy = min(1.5, energy + strength)
    }

    fun answer(semitones: Int) {
        semitone = Math.floorMod(semitones, 12)
        held = 0.0
    }

    fun clear() {
        semitone = -1
    }

    /** What one step asks of the fluid. Reused: read it before the next [step]. */
    class InkFrame {
        var count = 0
        val kind = IntArray(CAPACITY)
        val role = IntArray(CAPACITY)
        val x = FloatArray(CAPACITY)
        val y = FloatArray(CAPACITY)

        /** Velocity splat: the push (x, y). Dye splat: the amount in [a]. Burst: the power in [a] and the seed in [b]. */
        val a = FloatArray(CAPACITY)
        val b = FloatArray(CAPACITY)
        val radius = FloatArray(CAPACITY)

        /** How fast the dye thins out this frame. */
        var dyeDissipation = 0f
        var energy = 0f
        var time = 0f
        var dt = 0f

        internal fun add(k: Int, r: Int, px: Double, py: Double, va: Double, vb: Double, rad: Double) {
            if (count >= CAPACITY) return
            kind[count] = k
            role[count] = r
            x[count] = px.toFloat()
            y[count] = py.toFloat()
            a[count] = va.toFloat()
            b[count] = vb.toFloat()
            radius[count] = rad.toFloat()
            count++
        }

        companion object {
            const val VELOCITY = 0
            const val DYE = 1
            const val BURST = 2
            const val CAPACITY = 128
        }
    }

    fun step(dt: Double, frame: InkFrame) {
        val d = min(dt, MAX_DT)
        time += d
        energy *= exp(-d * 1.6)
        frame.count = 0
        frame.dt = d.toFloat()
        frame.time = time.toFloat()
        frame.energy = energy.toFloat()
        val answered = semitone >= 0
        // the tonic keeps the ink moving: four slow sources circling the middle (while a degree stands in the ball, it only tints it)
        val calm = if (answered) 0.25 else 1.0
        for (i in 0 until 4) {
            val sgn = if (i % 2 == 1) -1.0 else 1.0
            val a = sgn * (0.17 + 0.05 * i) * time + i * 1.7
            val r = 0.30 + 0.11 * i + 0.07 * sin(0.37 * time + i * 2.0)
            val px = 0.5 + OrbPhysics.RADIUS * r * cos(a)
            val py = 0.5 + OrbPhysics.RADIUS * r * sin(a)
            frame.add(InkFrame.VELOCITY, 0, px, py, -sin(a) * 210 * sgn * calm * d, cos(a) * 210 * sgn * calm * d, 0.0030)
            frame.add(InkFrame.DYE, ROLE_TONIC + i, px, py, 2.4 * (if (answered) 0.55 else 1.0) * d, 0.0, 0.0042)
        }
        // a pluck is a push from one point outwards, and a puff of bright ink there
        for (bu in bursts) {
            seed += 2.1
            frame.add(InkFrame.BURST, 0, bu[0], bu[1], 430 * bu[2], seed, 0.0)
            frame.add(InkFrame.DYE, ROLE_BURST, bu[0], bu[1], 0.8 * bu[2], 0.0, 0.0060)
        }
        bursts.clear()
        var dissipation = 0.16
        if (answered) {
            held += d
            val (p, _) = Overtones.RATIO[semitone]
            val te = OrbPhysics.tension(semitone)
            spin += d * 0.45 * te
            val power = (0.75 + 1.3 * exp(-held / 0.6)) / sqrt(p.toDouble())
            val size = 0.0032 / (1 + 0.10 * p)
            val swirl = 0.04 + 0.6 * te * te
            // the answered degree: p jets round the middle; a simple ratio stands still, a rough one creeps and flickers
            for (k in 0 until p) {
                val a = spin + 1.5708 + 6.2832 * k / p
                val px = 0.5 + OrbPhysics.RADIUS * 0.17 * cos(a)
                val py = 0.5 + OrbPhysics.RADIUS * 0.17 * sin(a)
                val flick = 1 + te * 0.5 * sin(time * 9 + k * 2.4)
                val f = 2500 * power * flick * d
                val ca = cos(a)
                val sa = sin(a)
                frame.add(InkFrame.VELOCITY, 0, px, py, (ca - sa * swirl) * f, (sa + ca * swirl) * f, size)
                frame.add(InkFrame.DYE, ROLE_JET, px, py, 6.0 * power * flick * d, 0.0, size)
            }
            dissipation += 1.3 * exp(-held / 0.7)
        }
        frame.dyeDissipation = dissipation.toFloat()
    }

    companion object {
        private const val MAX_BURSTS = 6
        private const val MAX_DT = 1.0 / 30

        /** Roles of a dye splat: the four sources of the tonic (ROLE_TONIC + 0..3), a pluck, a jet of the answer. */
        const val ROLE_TONIC = 0
        const val ROLE_BURST = 4
        const val ROLE_JET = 5
    }
}
