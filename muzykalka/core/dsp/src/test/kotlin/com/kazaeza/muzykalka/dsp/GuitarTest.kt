package com.kazaeza.muzykalka.dsp

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.NoteName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.hypot

/** O-70 (ADR-068): a strummed chord sounds like wood and never drives the master into saturation. */
class GuitarTest {
    private val key = Key(NoteName.parse("E"), Mode.MAJOR)

    private fun strum(chord: String): FloatArray = DefaultSynth().renderOffline(Arranger().strum(ChordDegree.parse(chord), key))

    /** Share of the energy of the first [seconds] from [from] up to [to] Hz. */
    private fun share(audio: FloatArray, from: Double, to: Double = SAMPLE_RATE / 2.0, seconds: Double = 0.5): Double {
        val n = 32_768
        val frames = (SAMPLE_RATE * seconds).toInt().coerceAtMost(n)
        val re = DoubleArray(n) { if (it < frames) audio[it].toDouble() * (0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * it / frames)) else 0.0 }
        val im = DoubleArray(n)
        Fft(n).forward(re, im)
        var band = 0.0
        var all = 0.0
        for (k in 1 until n / 2) {
            val e = hypot(re[k], im[k]).let { it * it }
            val hz = k.toDouble() * SAMPLE_RATE / n
            all += e
            if (hz in from..to) band += e
        }
        return band / all
    }

    private fun highShare(audio: FloatArray, hz: Double) = share(audio, hz)

    private fun rms(audio: FloatArray, fromSeconds: Double, toSeconds: Double): Double {
        val a = (fromSeconds * SAMPLE_RATE).toInt()
        val b = minOf(audio.size, (toSeconds * SAMPLE_RATE).toInt())
        var sum = 0.0
        for (i in a until b) sum += audio[i].toDouble() * audio[i]
        return kotlin.math.sqrt(sum / (b - a))
    }

    @Test
    fun `a full strum stays in the clean range of the master`() {
        for (c in listOf("I", "IV", "V7", "vi")) {
            val audio = strum(c)
            val peak = audio.maxOf { abs(it) }
            assertTrue(peak < 0.8f, "$c peaks at $peak: the master would bend it")
        }
    }

    @Test
    fun `the strum is warm - little energy above 4 kHz (it was a third before O-70)`() {
        for (c in listOf("I", "IV", "V7", "vi")) {
            val share = highShare(strum(c), 4_000.0)
            assertTrue(share < 0.06, "$c: ${"%.4f".format(share)} of the energy is fizz")
        }
    }

    @Test
    fun `O-73 - the body is in it - most of the energy low, no wall of nasal mid-range (it was half before)`() {
        for (c in listOf("I", "IV", "V7", "vi", "ii")) {
            val audio = strum(c)
            val mid = share(audio, 600.0, 4_800.0)
            val low = share(audio, 0.0, 600.0)
            assertTrue(mid < 0.30, "$c: ${"%.2f".format(mid)} of the energy sits in the nasal 0.6-4.8 kHz band")
            assertTrue(low > 0.65, "$c: only ${"%.2f".format(low)} of the energy is below 600 Hz: thin")
        }
    }

    @Test
    fun `O-73 - a chord rings for seconds, and a stopped one is silent at once (O-66 stops sounds from layering)`() {
        val audio = strum("I")
        assertTrue(rms(audio, 1.0, 1.25) > 0.15 * rms(audio, 0.0, 0.25), "a chord does not die in a second")

        val synth = DefaultSynth()
        synth.schedule(Arranger().strum(ChordDegree.parse("I"), key), synth.framePosition)
        val out = FloatArray(SAMPLE_RATE)
        synth.render(out, SAMPLE_RATE / 3)
        val before = rms(out, 0.2, 0.33)
        synth.stopAll()
        val after = FloatArray(SAMPLE_RATE / 3)
        synth.render(after, after.size)
        assertTrue(before > 0.02, "it was sounding: $before")
        assertTrue(rms(after, 0.15, 0.33) < 0.02 * before, "and then it was not")
    }
}
