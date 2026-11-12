package com.kazaeza.muzykalka.dsp

import com.kazaeza.muzykalka.music.TapSound
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * O-36: the note timbres over the drone, the wooden taps and the small room.
 *
 * Everything here follows docs/design/lab/synth.js (the reference that was run, measured and listened to on the
 * lab page): the same tables and formulas, only laid out for real time — damped sines are two-pole resonators
 * (two multiplications a sample), noise bursts and envelopes are running products, and every buffer is
 * allocated up front, so starting a note on the audio thread allocates nothing.
 */

private const val TAU = 2 * PI

/** ln(1000): the factor in e^(−t·k) for a fall of 60 dB in a given time. */
private const val DECAY_60_DB = 6.9078

/** xorshift32 in −1…1: the generator the voices already use for noise (SynthVoice.nextNoise). */
internal class Noise(seed: Int = DEFAULT_SEED) {
    private var x = seed

    fun seed(seed: Int) {
        x = if (seed == 0) DEFAULT_SEED else seed
    }

    fun next(): Double {
        var v = x
        v = v xor (v shl 13)
        v = v xor (v ushr 17)
        v = v xor (v shl 5)
        x = v
        return v / 2147483648.0
    }

    private companion object {
        const val DEFAULT_SEED = 0x2545F491
    }
}

/** High notes are quieter: a small speaker and the ear are both keenest up there. 1 at and below A4, −3 dB per octave above. */
internal fun registerGain(hz: Double): Double = if (hz <= 440.0) 1.0 else sqrt(440.0 / hz)

/**
 * A bank of damped sines — a struck bar, a tine, a string, a stick — plus up to two bursts of noise.
 *
 * Every mode is y[n] = c1·y[n−1] − c2·y[n−2], which is a·rⁿ·sin(ωn) (r from its T60). Modes belong to a group;
 * a group has a gain, a rise (the contact time of the hammer: the sound swells in instead of clicking) and
 * may have a release (a damper) once the note is let go.
 */
internal class ResonatorBank(private val sr: Int, private val rng: Noise) {
    private val c1 = DoubleArray(MAX_MODES)
    private val c2 = DoubleArray(MAX_MODES)
    private val y1 = DoubleArray(MAX_MODES)
    private val y2 = DoubleArray(MAX_MODES)
    private val group = IntArray(MAX_MODES)
    private var count = 0
    private var groups = 0

    private val gGain = DoubleArray(GROUPS)
    private val gRiseQ = DoubleArray(GROUPS)
    private val gRiseN = DoubleArray(GROUPS)
    private val gRelT60 = DoubleArray(GROUPS)
    private val gRelMul = DoubleArray(GROUPS)
    private val gRel = DoubleArray(GROUPS)
    private val acc = DoubleArray(GROUPS)
    private var released = false
    private var steps = 0

    private val nLevel = DoubleArray(NOISES)
    private val nA = DoubleArray(NOISES)
    private val nLp = DoubleArray(NOISES)
    private val nPrev = DoubleArray(NOISES)
    private val nHp = BooleanArray(NOISES)
    private val nRiseQ = DoubleArray(NOISES)
    private val nRiseN = DoubleArray(NOISES)
    private val nDecQ = DoubleArray(NOISES)
    private val nDecN = DoubleArray(NOISES)
    private val nLeft = IntArray(NOISES)
    private var noises = 0

    fun clear() {
        count = 0
        groups = 0
        noises = 0
        released = false
        steps = 0
    }

    /** A group of modes with one [gain]; [riseSec] > 0 swells it in; [releaseT60] > 0 lets it be damped by [release]. */
    fun group(gain: Double, riseSec: Double, releaseT60: Double): Int {
        val g = groups++
        gGain[g] = gain
        gRiseQ[g] = if (riseSec > 0.0) exp(-1.0 / (riseSec * sr)) else 0.0
        gRiseN[g] = 1.0
        gRelT60[g] = releaseT60
        gRelMul[g] = 1.0
        gRel[g] = 1.0
        return g
    }

    /** One mode of [group]: frequency [f] Hz, amplitude [a], 60 dB down in [t60] seconds. */
    fun mode(group: Int, f: Double, a: Double, t60: Double) {
        if (f >= sr * 0.42 || abs(a) < 1e-9 || count >= MAX_MODES) return
        val w = TAU * f / sr
        val r = exp(-DECAY_60_DB / (t60 * sr))
        c1[count] = 2 * r * cos(w)
        c2[count] = r * r
        // y[0] = 0 is emitted first, then y[1] = a·r·sin(w): the state before it is y[−1] = −a·sin(w)/r
        y1[count] = 0.0
        y2[count] = -a * sin(w) / r
        this.group[count] = group
        count++
    }

    /** A pair of strings: [mix] of the mode falls fast ([t60]); the rest hangs on ([t60b]) a fraction [det] higher, and beats against it. */
    fun mode2(group: Int, f: Double, a: Double, t60: Double, t60b: Double, mix: Double, det: Double) {
        mode(group, f, a * mix, t60)
        mode(group, f * (1.0 + det), a * (1.0 - mix), t60b)
    }

    /** A burst of noise through a one-pole low-pass ([fc]) and, if [hp], a one-pole high-pass: the thump of felt, the tick of a nail. */
    fun noise(seconds: Double, fc: Double, level: Double, hp: Boolean) {
        if (noises >= NOISES) return
        val k = noises++
        nLevel[k] = level
        nA[k] = 1.0 - exp(-TAU * fc / sr)
        nLp[k] = 0.0
        nPrev[k] = 0.0
        nHp[k] = hp
        nRiseQ[k] = exp(-1.0 / (seconds * 0.1 * sr))
        nRiseN[k] = 1.0
        nDecQ[k] = exp(-1.0 / (seconds * sr))
        nDecN[k] = 1.0
        nLeft[k] = Math.round(seconds * 6.0 * sr).toInt()
    }

    /** The note is let go: groups with a release fall at it; with [fast] (stopAll) everything falls in ~50 ms. */
    fun release(fast: Boolean) {
        if (released && !fast) return
        released = true
        for (g in 0 until groups) {
            val t60 = if (fast) FAST_RELEASE_T60 else gRelT60[g]
            gRelMul[g] = if (t60 > 0.0) exp(-DECAY_60_DB / (t60 * sr)) else 1.0
        }
    }

    fun next(): Double {
        for (g in 0 until groups) acc[g] = 0.0
        for (i in 0 until count) {
            val y = y1[i]
            y1[i] = c1[i] * y - c2[i] * y2[i]
            y2[i] = y
            acc[group[i]] += y
        }
        var out = 0.0
        for (g in 0 until groups) {
            val rise = if (gRiseQ[g] > 0.0) 1.0 - gRiseN[g] else 1.0
            out += gGain[g] * rise * gRel[g] * acc[g]
            if (gRiseQ[g] > 0.0) gRiseN[g] *= gRiseQ[g]
            if (released) gRel[g] *= gRelMul[g]
        }
        for (k in 0 until noises) {
            if (nLeft[k] <= 0) continue
            nLp[k] += nA[k] * (rng.next() - nLp[k])
            var y = nLp[k]
            if (nHp[k]) {
                y = nLp[k] - nPrev[k]
                nPrev[k] = nLp[k]
            }
            out += nLevel[k] * y * (1.0 - nRiseN[k]) * nDecN[k]
            nRiseN[k] *= nRiseQ[k]
            nDecN[k] *= nDecQ[k]
            nLeft[k]--
        }
        if ((++steps and 255) == 0) flushTiny()
        return out
    }

    /** Long-dead modes would sink into denormal numbers, which are slow on some CPUs: set them to zero. */
    private fun flushTiny() {
        for (i in 0 until count) {
            if (abs(y1[i]) < 1e-25 && abs(y2[i]) < 1e-25) {
                y1[i] = 0.0
                y2[i] = 0.0
            }
        }
    }

    private companion object {
        /** Felt: 14 partials, each of two stages. */
        const val MAX_MODES = 32
        const val GROUPS = 2
        const val NOISES = 2
        const val FAST_RELEASE_T60 = 0.05
    }
}

/**
 * A hummed voice: «oo–oh» with the mouth nearly closed. The source falls off with the number of the harmonic,
 * the mouth is three fixed resonances — fixed in hertz, so each note is coloured differently, as a voice is. It
 * scoops into the note, drifts a few cents, breathes, and only a long note gets a vibrato.
 */
internal class HumVoice(private val sr: Int, private val rng: Noise) {
    private val parts = DoubleArray(MAX_PARTS)
    private var nParts = 0
    private var norm = 1.0
    private var gain = 0.0
    private var hz = 220.0
    private var drift = 0.0
    private var shimmer = 0.0
    private var vibRate = 5.3
    private var phase = 0.0
    private var br1 = 0.0
    private var br2 = 0.0
    private var releaseTau = RELEASE_TAU
    private val lpD = 1.0 - exp(-TAU * 2.2 / sr)
    private val lpS = 1.0 - exp(-TAU * 3.0 / sr)

    fun start(hz: Double, velocity: Float) {
        this.hz = hz
        nParts = 0
        norm = 0.0
        var k = 1
        while (k * hz < 5200.0 && k <= MAX_PARTS) {
            val a = k.toDouble().pow(-1.6) * mouth(k * hz)
            parts[nParts++] = a
            norm += a
            k++
        }
        gain = if (norm > 0.0) 0.85 * velocity * registerGain(hz) / norm else 0.0
        drift = 0.0
        shimmer = 0.0
        vibRate = 5.3
        phase = 0.0
        br1 = 0.0
        br2 = 0.0
        releaseTau = RELEASE_TAU
    }

    /** stopAll: the voice closes in ~30 ms instead of its own 110 ms. */
    fun fastRelease() {
        releaseTau = FAST_RELEASE_TAU
    }

    /** One sample, [t] seconds after the start; [sinceRelease] seconds after the note was let go (ignored unless [released]). */
    fun next(t: Double, released: Boolean, sinceRelease: Double): Double {
        // slow wander of pitch and loudness: low-passed noise, a few hertz wide
        drift += lpD * (rng.next() * 360.0 - drift) // cents; about ±2.5 on average
        shimmer += lpS * (rng.next() * 120.0 - shimmer) // about ±1 on average
        val scoop = -18.0 * exp(-t / 0.045) // cents: it comes up into the note
        val vibDepth = 14.0 * ((t - 0.45) / 0.6).coerceIn(0.0, 1.0)
        vibRate += 0.0008 * rng.next()
        val cents = scoop + drift + vibDepth * sin(TAU * vibRate * t)
        phase += hz * 2.0.pow(cents / 1200.0) / sr
        if (phase >= 1.0) phase -= 1.0
        var env = 1.0 / (1.0 + exp(-(t - 0.045) / 0.011)) // opens in ~70 ms
        if (released) env *= exp(-sinceRelease / releaseTau)
        val open = 1.0 / (1.0 + exp(-(t - 0.07) / 0.02)) // the upper harmonics open a little later
        // sin(k·p) by the Chebyshev recurrence: one sin and one cos a sample, however many harmonics
        val p = TAU * phase
        val twoCos = 2.0 * cos(p)
        var prev = 0.0
        var cur = sin(p)
        var y = if (nParts > 0) parts[0] * cur else 0.0
        for (k in 1 until nParts) {
            val next = twoCos * cur - prev
            y += parts[k] * open * next
            prev = cur
            cur = next
        }
        // breath: noise through the mouth's two resonances
        val x = rng.next()
        br1 += 0.05 * (x - br1)
        br2 += 0.11 * (x - br2)
        val breath = (br2 - br1) * (0.2 + 0.3 * exp(-t / 0.08))
        return (y * (1.0 + 0.04 * shimmer) + breath * norm) * env * gain
    }

    private fun mouth(f: Double): Double {
        var s = 0.06
        for (i in FORMANT_HZ.indices) {
            val d = (f - FORMANT_HZ[i]) / FORMANT_BW[i]
            s += FORMANT_GAIN[i] / (1.0 + d * d)
        }
        return s
    }

    private companion object {
        const val MAX_PARTS = 24
        const val RELEASE_TAU = 0.11
        const val FAST_RELEASE_TAU = 0.03
        val FORMANT_HZ = doubleArrayOf(400.0, 860.0, 2600.0)
        val FORMANT_BW = doubleArrayOf(110.0, 130.0, 220.0)
        val FORMANT_GAIN = doubleArrayOf(1.0, 0.55, 0.10)
    }
}

/** What a note of each timbre is made of: the tables of docs/design/lab/synth.js, loaded into a [ResonatorBank]. */
internal object Timbres {
    private fun vary(rng: Noise): Double = 1.0 + 0.08 * rng.next()

    /** Marimba: a rosewood bar over a tube, overtones at 4× and 9.9× that die in a blink, and the knock of the wood. */
    fun marimba(bank: ResonatorBank, rng: Noise, hz: Double, vel: Float) {
        bank.clear()
        val reg = registerGain(hz)
        val t1 = (2.6 * (220.0 / hz).pow(0.45)).coerceIn(0.7, 3.0)
        val contact = 1400.0 + 1600.0 * vel // a yarn mallet: the higher the overtone the less of it
        val soft0 = soft(hz, contact)
        val bar = bank.group(0.80 * vel * reg, 0.0012, 0.35)
        bank.mode(bar, hz, 1.0, t1)
        bank.mode(bar, hz * 4.0, 0.42 * soft(hz * 4.0, contact) / soft0 * vary(rng), t1 * 0.22)
        bank.mode(bar, hz * 9.9, 0.20 * soft(hz * 9.9, contact) / soft0 * vary(rng), t1 * 0.08)
        // the wood itself answers every stroke with the same short knock, whatever the note
        val knock = bank.group(vel * reg, 0.0004, 0.0)
        bank.mode(knock, 1130.0 * (1.0 + 0.012 * rng.next()), 0.10 * vary(rng), 0.045)
        bank.mode(knock, 2710.0 * (1.0 + 0.012 * rng.next()), 0.05 * vary(rng), 0.022)
        bank.noise(0.004, 1800.0, 0.10 * vel * reg, false)
    }

    private fun soft(f: Double, contact: Double): Double = 1.0 / (1.0 + (f / contact).pow(2))

    /** Kalimba: a steel tine on a box. Overtones far from the note (6.27× and 17.55×) and gone at once, then nearly one pure tone. */
    fun kalimba(bank: ResonatorBank, rng: Noise, hz: Double, vel: Float) {
        bank.clear()
        val reg = registerGain(hz)
        val t1 = (2.3 * (220.0 / hz).pow(0.4)).coerceIn(0.6, 2.8)
        val tine = bank.group(0.9 * vel * reg, 0.0008, 0.45)
        bank.mode(tine, hz, 1.0, t1)
        bank.mode(tine, hz * 2.0, 0.10, t1 * 0.55)
        bank.mode(tine, hz * 6.27, 0.16 * vary(rng) * min(1.0, 1500.0 / hz), 0.11)
        bank.mode(tine, hz * 17.55, 0.06 * vary(rng) * min(1.0, 700.0 / hz), 0.035)
        bank.noise(0.002, 6000.0, 0.07 * vel * reg, true)
    }

    /**
     * Felt piano: partials a little sharp of whole multiples (stiff wire), the hammer an eighth along the string,
     * the felt eating the top. Every partial is a pair of strings; under it the thump of the hammer.
     */
    fun felt(bank: ResonatorBank, rng: Noise, hz: Double, vel: Float) {
        bank.clear()
        val tBase = (5.5 * (110.0 / hz).pow(0.6)).coerceIn(0.9, 6.0)
        val stiff = 0.0004 * (hz / 261.6).pow(1.1)
        val fc = 800.0 + 1700.0 * vel // how much top the felt lets through
        // the felt already takes the top off: less cut up high
        val fg = vel * (if (hz <= 440.0) 1.0 else (440.0 / hz).pow(0.25))
        val string = bank.group(0.42 * fg, 0.004, 0.14)
        var norm = 1.0
        for (k in 1..14) {
            val f = k * hz * sqrt(1.0 + stiff * k * k)
            if (f > 6500.0) break
            val a = abs(sin(k * PI / 8.0)) / k / (1.0 + (f / fc).pow(2)).pow(0.75) * (1.0 + 0.06 * rng.next())
            if (k == 1) norm = a
            val t = tBase / (1.0 + 0.35 * (k - 1))
            bank.mode2(string, f, a / norm, t * 0.38, t * 1.5, 0.62, 0.0004)
        }
        bank.noise(0.022, 420.0, 0.30 * fg, false) // felt on string
        bank.noise(0.003, 2600.0, 0.025 * fg, true) // the action
    }

    private class TapSpec(
        val level: Double,
        val noiseSeconds: Double,
        val noiseFc: Double,
        val noiseLevel: Double,
        /** f, a, t60 for each mode. */
        val modes: DoubleArray,
    )

    private val STICKS = TapSpec(
        0.20, 0.0015, 7000.0, 0.35,
        doubleArrayOf(1850.0, 1.0, 0.045, 2720.0, 0.45, 0.030, 4350.0, 0.22, 0.018, 6100.0, 0.10, 0.010),
    )
    private val BLOCK = TapSpec(
        0.22, 0.002, 4000.0, 0.25,
        doubleArrayOf(920.0, 1.0, 0.070, 1380.0, 0.50, 0.050, 2490.0, 0.25, 0.028, 3900.0, 0.10, 0.015),
    )
    private val TICK = TapSpec(
        0.20, 0.001, 8000.0, 0.45,
        doubleArrayOf(2400.0, 1.0, 0.016, 3900.0, 0.40, 0.010),
    )

    /**
     * A wooden tap. Not tuned to the key: its modes are far from harmonic and it is short. Every tap differs a
     * hair in pitch (±1.5 %) and level (±1 dB), so a run of them is not a machine gun. [low] is the second voice
     * (back, cancel): the same stick, 20 % lower and 30 % softer. [gain] is the volume setting.
     */
    fun tap(bank: ResonatorBank, rng: Noise, sound: TapSound, low: Boolean, gain: Double) {
        bank.clear()
        val spec = when (sound) {
            TapSound.STICKS -> STICKS
            TapSound.BLOCK -> BLOCK
            TapSound.TICK -> TICK
        }
        val pitch = (if (low) 0.8 else 1.0) * (1.0 + 0.015 * rng.next())
        val level = spec.level * (if (low) 0.7 else 1.0) * 10.0.pow(0.05 * rng.next()) * gain
        val wood = bank.group(level, 0.0003, 0.0)
        for (i in 0 until spec.modes.size / 3) {
            bank.mode(wood, spec.modes[3 * i] * pitch, spec.modes[3 * i + 1] * (1.0 + 0.1 * rng.next()), spec.modes[3 * i + 2])
        }
        bank.noise(spec.noiseSeconds, spec.noiseFc, level * spec.noiseLevel, true)
    }

    /** A tap is over after this long (its longest mode is 44 ms to −40 dB). */
    const val TAP_SECONDS = 0.16
}

/**
 * Air: a small room. Four delay lines fed back through a Householder mix, the top damped in the loop;
 * 0.55 s to −60 dB. [process] takes one dry sample and returns the room's answer to it (the caller scales it).
 * The delays are those of the reference at 48 kHz, scaled to [sr].
 */
internal class SmallRoom(sr: Int) {
    private val len = IntArray(4) { maxOf(2, Math.round(BASE_DELAYS[it] * sr / 48_000.0).toInt()) }
    private val buf = Array(4) { FloatArray(len[it]) }
    private val idx = IntArray(4)
    private val lp = DoubleArray(4)
    private val out = DoubleArray(4)
    private val gain = DoubleArray(4) { 10.0.pow(-3.0 * len[it] / (T60 * sr)) }
    private val damp = 1.0 - exp(-TAU * DAMP_HZ / sr)
    private val pre = FloatArray(maxOf(1, Math.round(PRE_DELAY * sr / 48_000.0).toInt()))
    private var preIdx = 0

    fun reset() {
        for (k in 0 until 4) {
            buf[k].fill(0f)
            idx[k] = 0
            lp[k] = 0.0
        }
        pre.fill(0f)
        preIdx = 0
    }

    fun process(x: Double): Double {
        val xd = pre[preIdx].toDouble()
        pre[preIdx] = x.toFloat()
        if (++preIdx == pre.size) preIdx = 0
        var sum = 0.0
        for (k in 0 until 4) {
            out[k] = buf[k][idx[k]].toDouble()
            sum += out[k]
        }
        sum *= 0.5 // Householder: o − (2/N)·Σo, N = 4
        for (k in 0 until 4) {
            lp[k] += damp * ((out[k] - sum) * gain[k] - lp[k])
            buf[k][idx[k]] = (xd + lp[k]).toFloat()
            if (++idx[k] == len[k]) idx[k] = 0
        }
        return 0.5 * (out[0] - out[1] + out[2] - out[3])
    }

    companion object {
        private val BASE_DELAYS = intArrayOf(1031, 1327, 1523, 1871)
        private const val PRE_DELAY = 480
        private const val T60 = 0.55
        private const val DAMP_HZ = 3800.0

        /** How loud the room is against the dry sound. */
        const val WET = 0.16

        /** The room keeps ringing this long after the last voice stops (its T60 and a little more). */
        const val TAIL_SECONDS = 0.7
    }
}
