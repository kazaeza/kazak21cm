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

    /** Share of the energy of the first [seconds] above [hz]. */
    private fun highShare(audio: FloatArray, hz: Double, seconds: Double = 0.4): Double {
        val n = 16_384
        val frames = (SAMPLE_RATE * seconds).toInt().coerceAtMost(n)
        val re = DoubleArray(n) { if (it < frames) audio[it].toDouble() else 0.0 }
        val im = DoubleArray(n)
        Fft(n).forward(re, im)
        val cut = (hz * n / SAMPLE_RATE).toInt()
        var high = 0.0
        var all = 0.0
        for (k in 1 until n / 2) {
            val e = hypot(re[k], im[k]).let { it * it }
            all += e
            if (k >= cut) high += e
        }
        return high / all
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
}
