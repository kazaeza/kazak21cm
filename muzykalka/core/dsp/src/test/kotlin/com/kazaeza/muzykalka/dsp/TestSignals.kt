package com.kazaeza.muzykalka.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Deterministic synthetic signals for DSP acceptance tests. */
object TestSignals {
    fun sine(hz: Double, seconds: Double, amp: Double = 0.5, sr: Int = SAMPLE_RATE): FloatArray =
        FloatArray((seconds * sr).toInt()) { i -> (amp * sin(2 * PI * hz * i / sr)).toFloat() }

    /** Band-limited sawtooth (harmonics up to Nyquist) — rich spectrum like a distorted guitar. */
    fun saw(hz: Double, seconds: Double, amp: Double = 0.3, sr: Int = SAMPLE_RATE): FloatArray {
        val n = (seconds * sr).toInt()
        val harmonics = (sr / 2 / hz).toInt()
        return FloatArray(n) { i ->
            var v = 0.0
            for (k in 1..harmonics) v += sin(2 * PI * k * hz * i / sr) / k
            (amp * v * 2 / PI).toFloat()
        }
    }

    /**
     * Plucked string (basic Karplus–Strong) with a strong 2nd harmonic at the attack — the classic
     * octave-error trap for autocorrelation detectors.
     */
    fun pluck(hz: Double, seconds: Double, seed: Int = 7, sr: Int = SAMPLE_RATE): FloatArray {
        val period = (sr / hz).toInt()
        val rnd = Random(seed)
        val buf = DoubleArray(period) { rnd.nextDouble(-1.0, 1.0) }
        val n = (seconds * sr).toInt()
        val out = FloatArray(n)
        var idx = 0
        for (i in 0 until n) {
            val next = (idx + 1) % period
            val v = 0.5 * (buf[idx] + buf[next]) * 0.996
            out[i] = (buf[idx] * 0.5).toFloat()
            buf[idx] = v
            idx = next
        }
        return out
    }

    /** Human-like voice: fundamental + formant-ish harmonics + 5 Hz vibrato of ±[vibratoCents]. */
    fun voice(hz: Double, seconds: Double, vibratoCents: Double = 15.0, sr: Int = SAMPLE_RATE): FloatArray {
        val n = (seconds * sr).toInt()
        var phase = 0.0
        return FloatArray(n) { i ->
            val t = i.toDouble() / sr
            val f = hz * Math.pow(2.0, vibratoCents * sin(2 * PI * 5 * t) / 1200)
            phase += 2 * PI * f / sr
            (0.4 * sin(phase) + 0.25 * sin(2 * phase) + 0.15 * sin(3 * phase) + 0.05 * sin(5 * phase)).toFloat()
        }
    }

    fun whiteNoise(seconds: Double, amp: Double = 0.1, seed: Int = 3, sr: Int = SAMPLE_RATE): FloatArray {
        val rnd = Random(seed)
        return FloatArray((seconds * sr).toInt()) { (amp * rnd.nextDouble(-1.0, 1.0)).toFloat() }
    }

    fun mix(a: FloatArray, b: FloatArray): FloatArray = FloatArray(maxOf(a.size, b.size)) { i ->
        (if (i < a.size) a[i] else 0f) + (if (i < b.size) b[i] else 0f)
    }

    fun decay(signal: FloatArray, timeConstantSeconds: Double, sr: Int = SAMPLE_RATE): FloatArray =
        FloatArray(signal.size) { i -> (signal[i] * exp(-i / (timeConstantSeconds * sr))).toFloat() }
}
