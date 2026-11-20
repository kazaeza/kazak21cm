package com.kazaeza.muzykalka.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * O-73 (ADR-070): the guitar, made again. A struck string is not one loop but two: a steel string vibrates in two planes at
 * once — the one it was plucked in, which hands its energy to the bridge and dies sooner, and the one across it, which
 * is barely excited, hardly couples to the bridge and rings on a little detuned. Their sum is the guitar's own double decay
 * (a knock, then a long fading shimmer) and the slow beating that a single loop never has. The pluck is a triangle — the
 * string pulled aside at a point and let go, whose spectrum falls as 1/n² with the comb of its position — with a little
 * of the pick's noise on it, and the pick's click comes apart from the string, short and mid-range.
 * Everything that leaves the strings goes to ONE [GuitarBody] — the box that colours them all alike.
 */
internal class GuitarString(private val sr: Int) {
    private val plane = StringLoop(sr)
    private val across = StringLoop(sr)
    private var envelope = 1.0
    private var relDecay = 1.0
    private var released = false

    // the pick: a short band of noise outside the loops
    private var pickLeft = 0
    private var pickLevel = 0.0
    private var pickLow = 0.0
    private var pickHigh = 0.0
    private var noise = 0x2545F491
    private val pickDecay = exp(-1.0 / (sr * PICK_SECONDS))
    private var pickEnv = 0.0

    /** Strikes the string at [hz]; [velocity] 0…1; [seed] makes the pluck its own. */
    fun pluck(hz: Double, velocity: Float, seed: Int) {
        noise = seed or 1
        envelope = 1.0
        relDecay = 1.0
        released = false
        // the higher the string, the less the loop may darken the sound, or a high note would be a dull thud
        val damp = 0.5 - 0.35 * ((hz - 82.0) / 400.0).coerceIn(0.0, 1.0)
        val t60 = 5.2 - 3.6 * min(1.0, hz / 900.0)
        val position = 0.13 + 0.05 * unit()
        plane.tune(hz, t60, damp)
        across.tune(hz * (1.0 + DETUNE), t60 * ACROSS_T60, damp)
        val bright = 0.5 + 0.4 * velocity
        pluckShape(plane, position, bright, velocity * PLANE_LEVEL)
        pluckShape(across, min(0.45, position * 2.1), bright, velocity * ACROSS_LEVEL)
        pickLeft = (sr * PICK_SECONDS * 8).toInt()
        pickLevel = PICK_LEVEL * velocity
        pickEnv = 1.0
        pickLow = 0.0
        pickHigh = 0.0
    }

    /** Lets the string go: its sound dies in [seconds] (a finger laid on it; a hundredth of a second is a stop). */
    fun release(seconds: Double) {
        released = true
        relDecay = exp(-1.0 / (sr * seconds / 5.0))
    }

    fun next(): Double {
        var y = plane.next() + across.next()
        if (pickLeft > 0) {
            val n = nextNoise()
            pickLow += 0.34 * (n - pickLow)
            pickHigh += 0.07 * (n - pickHigh)
            y += (pickLow - pickHigh) * pickLevel * pickEnv
            pickEnv *= pickDecay
            pickLeft--
        }
        if (released) envelope *= relDecay
        return y * envelope
    }

    private fun unit(): Double = (nextNoise() + 1.0) * 0.5

    private fun nextNoise(): Double {
        var x = noise
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noise = x
        return x / 2147483648.0
    }

    /**
     * The loop's first lap. What the bridge feels is the SLOPE of the string, not its displacement, and a string pulled aside
     * at [position] and let go has a slope that is a square wave with a step at the pluck: its spectrum falls as 1/n (6 dB per
     * octave, as a guitar's does) with a comb at the pluck's place, where a triangle's would fall as 1/n² and sound like a
     * flute. The corners are rounded by the width of the pick or the finger ([bright]) and some of the pick's noise is on it.
     */
    private fun pluckShape(loop: StringLoop, position: Double, bright: Double, level: Double) {
        val buf = loop.buffer
        val len = loop.length
        val peakAt = max(1, (len * position).toInt())
        var lp = 0.0
        var lp2 = 0.0
        var soft = 0.0
        var soft2 = 0.0
        var mean = 0.0
        for (i in 0 until len) {
            val step = if (i < peakAt) 1.0 / position else -1.0 / (1.0 - position)
            soft += bright * (step - soft)
            soft2 += bright * (soft - soft2)
            lp += bright * (nextNoise() - lp)
            lp2 += bright * (lp - lp2)
            val x = (1.0 - NOISE_SHARE) * soft2 + NOISE_SHARE * lp2 * 3.0
            buf[i] = x.toFloat()
            mean += x
        }
        mean /= len
        var peak = 1e-9
        for (i in 0 until len) {
            buf[i] = (buf[i] - mean).toFloat()
            peak = max(peak, abs(buf[i].toDouble()))
        }
        val scale = (level / peak).toFloat()
        for (i in 0 until len) buf[i] *= scale
    }

    private companion object {
        const val DETUNE = 0.0014
        const val ACROSS_T60 = 1.7
        const val PLANE_LEVEL = 0.44
        const val ACROSS_LEVEL = 0.16
        const val NOISE_SHARE = 0.3
        const val PICK_SECONDS = 0.0035
        const val PICK_LEVEL = 0.15
    }
}

/**
 * One loop of a waveguide string: the delay line, a one-pole loss filter (its [damp] is how much the highs are taken away on
 * every lap; ½ is the average of two samples), an all-pass for the fraction of a sample the length does not hold, and the gain
 * that makes the fundamental fall by 60 dB in [tune]'s t60. The line is preallocated: starting a note allocates nothing.
 */
internal class StringLoop(sr: Int) {
    val buffer = FloatArray(sr / 20 + 8)
    var length = 2
        private set
    private var index = 0
    private var previous = 0f
    private var damp = 0.5
    private var apC = 0.0
    private var apX1 = 0.0
    private var apY1 = 0.0
    private var gain = 0.999
    private val rate = sr

    fun tune(hz: Double, t60: Double, damp: Double) {
        this.damp = damp
        val loop = rate / hz - damp
        var n = floor(loop).toInt()
        var frac = loop - n
        if (frac < 0.1) {
            n -= 1
            frac += 1.0
        }
        length = n.coerceIn(2, buffer.size - 1)
        apC = (1.0 - frac) / (1.0 + frac)
        apX1 = 0.0
        apY1 = 0.0
        previous = 0f
        index = 0
        gain = 0.001.pow(1.0 / (hz * t60))
    }

    fun next(): Double {
        val out = buffer[index]
        val lossy = ((1.0 - damp) * out + damp * previous) * gain
        previous = out
        val ap = apC * lossy + apX1 - apC * apY1
        apX1 = lossy
        apY1 = ap
        buffer[index] = ap.toFloat()
        index++
        if (index >= length) index = 0
        return out.toDouble()
    }
}

/**
 * O-73: the box of the guitar — the resonances of the air in it and of the top and the back, as one bank of narrow
 * peaks that every string passes through, on one bus. A string alone is thin; it is these that give it the weight at
 * 100–200 Hz and the wooden mid-range that say «acoustic guitar». The frequencies and Q are those measured on an
 * ordinary steel-string guitar (the air ~100 Hz, the top ~200 Hz, the back ~230 Hz, then the higher plate modes); the gains
 * are chosen by ear and by the spectrum test, not measured.
 */
internal class GuitarBody(sr: Int) {
    private val modes = Array(MODE_HZ.size) { Resonance().also { r -> r.set(sr, MODE_HZ[it], MODE_Q[it]) } }

    fun process(x: Double): Double {
        var sum = DRY * x
        for (i in modes.indices) sum += MODE_GAIN[i] * modes[i].next(x)
        return sum
    }

    fun reset(sr: Int) {
        for (i in modes.indices) modes[i].set(sr, MODE_HZ[i], MODE_Q[i])
    }

    companion object {
        /** Ring time of the lowest mode is ~20 ms, so a tenth of a second is long enough for the box to be silent. */
        const val TAIL_SECONDS = 0.25
        private const val DRY = 1.0
        private val MODE_HZ = doubleArrayOf(98.0, 192.0, 232.0, 285.0, 410.0, 555.0, 730.0, 1050.0, 1500.0, 2150.0)
        private val MODE_Q = doubleArrayOf(6.0, 9.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 20.0, 20.0)
        private val MODE_GAIN = doubleArrayOf(0.5, 0.5, 0.4, 0.4, 0.6, 0.6, 0.6, 0.75, 0.75, 0.6)
    }
}

/** One resonance of a guitar body — a band-pass biquad (0 dB at its peak), preallocated, no allocation per sample. */
internal class Resonance {
    private var b0 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun set(sr: Int, hz: Double, q: Double) {
        val w = 2 * PI * hz / sr
        val alpha = sin(w) / (2 * q)
        val a0 = 1 + alpha
        b0 = alpha / a0
        a1 = -2 * cos(w) / a0
        a2 = (1 - alpha) / a0
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    fun next(x: Double): Double {
        val y = b0 * x - b0 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }
}
