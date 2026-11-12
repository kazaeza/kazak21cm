package com.kazaeza.muzykalka.dsp

import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.ScoreNote
import com.kazaeza.muzykalka.music.TapSound
import com.kazaeza.muzykalka.music.ToneTimbre
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.music.centsBetween
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * O-36: the note timbres over the drone, the taps and the small room. The reference numbers are those of
 * docs/design/lab/analysis.txt (a note of 1 s, strength 0.8, rendered by docs/design/lab/synth.js) — if a test
 * here is off by more than its margin, the port differs from the reference that was listened to.
 */
class TimbreTest {
    /** Notes of the table: A3, A4, A5, E6 (220, 440, 880, 1318.5 Hz). */
    private val midis = listOf(57, 69, 81, 88)

    private class Ref(val peak: Double, val rmsDb: Double)

    /** peak and RMS of the first 0.3 s (dB) of analysis.txt, by timbre and note. */
    private val table = mapOf(
        ToneTimbre.MARIMBA to listOf(Ref(0.882, -9.8), Ref(0.809, -10.8), Ref(0.509, -14.9), Ref(0.391, -17.4)),
        ToneTimbre.KALIMBA to listOf(Ref(0.800, -9.2), Ref(0.776, -10.1), Ref(0.567, -14.1), Ref(0.456, -16.4)),
        ToneTimbre.FELT to listOf(Ref(1.028, -12.4), Ref(0.885, -14.5), Ref(0.543, -18.7), Ref(0.386, -21.4)),
        ToneTimbre.HUM to listOf(Ref(0.536, -12.4), Ref(0.632, -9.3), Ref(0.458, -11.2), Ref(0.352, -15.0)),
    )
    private val newTimbres = table.keys

    /** The master is turned almost off so that its tanh stays linear and the voice itself is measured. */
    private val scale = 1000.0

    private fun synth(timbre: ToneTimbre, room: Boolean = false) =
        DefaultSynth(masterGain = 1f / scale.toFloat()).also {
            it.toneTimbre = timbre
            it.room = room
        }

    /** One note of [midi], held 1 s at strength 0.8, then 0.6 s of tail. */
    private fun note(timbre: ToneTimbre, midi: Int, room: Boolean = false): FloatArray {
        val score = Score(60.0, listOf(ScoreNote(0.0, 1.0, Voice.TONE, MidiNote(midi), 0.8f)))
        val out = synth(timbre, room).renderOffline(score, tailSeconds = 0.6)
        return FloatArray(out.size) { (out[it] * scale).toFloat() }
    }

    private fun rms(x: FloatArray, from: Int, to: Int): Double {
        var s = 0.0
        for (i in from until to) s += x[i].toDouble() * x[i]
        return sqrt(s / (to - from))
    }

    private fun db(v: Double) = 20 * log10(v + 1e-12)

    private fun spectrum(x: FloatArray, from: Int, n: Int): DoubleArray {
        val re = DoubleArray(n) { (x.getOrElse(from + it) { 0f }) * (0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * it / n)) }
        val im = DoubleArray(n)
        Fft(n).forward(re, im)
        return DoubleArray(n / 2) { kotlin.math.hypot(re[it], im[it]) }
    }

    /** A-weighted loudness (dB, arbitrary zero) of the first 0.34 s: the ear's view of how loud a note is. */
    private fun loudness(x: FloatArray): Double {
        val n = 16384
        val m = spectrum(x, 0, n)
        var a = 0.0
        for (i in 1 until m.size) {
            val f = i * SAMPLE_RATE.toDouble() / n
            val f2 = f * f
            val w = 12194.0 * 12194.0 * f2 * f2 /
                ((f2 + 20.6 * 20.6) * sqrt((f2 + 107.7 * 107.7) * (f2 + 737.9 * 737.9)) * (f2 + 12194.0 * 12194.0)) * 1.2589
            a += (m[i] * w) * (m[i] * w)
        }
        return 10 * log10(a + 1e-20)
    }

    /** The strongest spectral peak within a semitone of [hz], refined by a parabola through its neighbours (Hz). */
    private fun fundamental(x: FloatArray, from: Int, hz: Double): Double {
        val n = 16384
        val m = spectrum(x, from, n)
        val bin = hz * n / SAMPLE_RATE
        val lo = (bin * 0.94).toInt()
        val hi = (bin * 1.06).toInt() + 1
        var best = lo
        for (i in lo..hi) if (m[i] > m[best]) best = i
        val a = kotlin.math.ln(m[best - 1])
        val b = kotlin.math.ln(m[best])
        val c = kotlin.math.ln(m[best + 1])
        val shift = 0.5 * (a - c) / (a - 2 * b + c)
        return (best + shift) * SAMPLE_RATE / n
    }

    @Test
    fun `the note sounds on pitch`() {
        for (timbre in newTimbres) for (midi in midis) {
            val hz = MidiNote(midi).frequency()
            val x = note(timbre, midi)
            // from 0.1 s: the knock of the wood is over, the scoop of a hum has come up, its vibrato has not begun
            val est = fundamental(x, (0.1 * SAMPLE_RATE).toInt(), hz)
            assertTrue(abs(centsBetween(est, hz)) < 3.0, "$timbre midi $midi: $est vs $hz")
        }
    }

    @Test
    fun `peak and level of the first 0,3 s match the reference within 1 dB`() {
        for ((timbre, refs) in table) midis.forEachIndexed { i, midi ->
            val x = note(timbre, midi)
            val peak = x.maxOf { abs(it) }.toDouble()
            val r = db(rms(x, 0, (0.3 * SAMPLE_RATE).toInt()))
            assertEquals(db(refs[i].peak), db(peak), 1.0, "$timbre midi $midi: peak $peak vs ${refs[i].peak}")
            assertEquals(refs[i].rmsDb, r, 1.0, "$timbre midi $midi: rms $r dB")
        }
    }

    @Test
    fun `nothing above 9 kHz and no click at the start`() {
        for (timbre in newTimbres) for (midi in midis) {
            val x = note(timbre, midi)
            val m = spectrum(x, 0, 16384)
            var all = 0.0
            var high = 0.0
            for (i in 1 until m.size) {
                val p = m[i] * m[i]
                all += p
                if (i * SAMPLE_RATE.toDouble() / 16384 > 9000.0) high += p
            }
            assertTrue(high / all < 0.001, "$timbre midi $midi: ${100 * high / all} % above 9 kHz")
            var jump = 0.0
            for (i in 1 until 480) jump = max(jump, abs((x[i] - x[i - 1]).toDouble()))
            assertTrue(jump < 0.3, "$timbre midi $midi: first step $jump")
        }
    }

    @Test
    fun `high notes are no longer louder than low ones - the point of the ticket`() {
        val spread = ToneTimbre.entries.associateWith { t ->
            val l = midis.map { loudness(note(t, it)) }
            l.max() - l.min()
        }
        assertTrue(spread.getValue(ToneTimbre.CLASSIC) > 9.0, "the old sound is ~10 dB louder up high: $spread")
        for (t in newTimbres) assertTrue(spread.getValue(t) < 7.0, "$t: $spread")
    }

    @Test
    fun `a marimba and a kalimba note dies away by itself`() {
        for (timbre in listOf(ToneTimbre.MARIMBA, ToneTimbre.KALIMBA)) for (midi in midis) {
            val x = note(timbre, midi)
            val start = db(rms(x, 0, (0.3 * SAMPLE_RATE).toInt()))
            val later = db(rms(x, (0.6 * SAMPLE_RATE).toInt(), SAMPLE_RATE))
            assertTrue(start - later > 12.0, "$timbre midi $midi: only ${start - later} dB down at 0.6-1.0 s")
        }
    }

    @Test
    fun `every note of a timbre differs a hair from the last, a hum most`() {
        for (timbre in newTimbres) {
            val s = synth(timbre)
            val score = Score(60.0, listOf(ScoreNote(0.0, 1.0, Voice.TONE, MidiNote(69), 0.8f)))
            val a = s.renderOffline(score, 0.2)
            val b = s.renderOffline(score, 0.2) // starts at a later frame, so another grain of chance
            var same = 0
            for (i in 0 until minOf(a.size, b.size, 9600)) if (a[i] == b[i]) same++
            assertTrue(same < 9000, "$timbre: two notes are the same sample for sample")
        }
    }

    @Test
    fun `a note that ends is silent soon after, whatever the timbre`() {
        for (timbre in ToneTimbre.entries) {
            val s = synth(timbre)
            val score = Score(60.0, listOf(ScoreNote(0.0, 0.5, Voice.TONE, MidiNote(64), 0.8f)))
            val out = s.renderOffline(score, tailSeconds = 1.5)
            val end = out.size - 1
            val tail = db(rms(FloatArray(out.size) { (out[it] * scale).toFloat() }, end - 4800, end))
            // O-39: the glass rings in its own hall on purpose (the user chose its room); the others die with the note
            val limit = if (timbre == ToneTimbre.GLASS) -45.0 else -60.0
            assertTrue(tail < limit, "$timbre: still $tail dB 1.4 s after the end")
            // the voice is given back: a synth with nothing else going on becomes idle
            val rest = if (timbre == ToneTimbre.GLASS) 8 * SAMPLE_RATE else 4800
            s.render(FloatArray(rest), rest)
            assertTrue(s.isIdle, "$timbre: not idle after the note")
        }
    }

    @Test
    fun `taps are short, soft and each one differs`() {
        for (sound in TapSound.entries) for (low in listOf(false, true)) {
            val s = synth(ToneTimbre.CLASSIC)
            s.tap(sound, low, 1f)
            val x = FloatArray(SAMPLE_RATE / 4)
            s.render(x, x.size)
            val y = FloatArray(x.size) { (x[it] * scale).toFloat() }
            val peak = y.maxOf { abs(it) }.toDouble()
            assertTrue(peak <= 0.35, "$sound low=$low: peak $peak")
            assertTrue(peak > 0.1, "$sound low=$low: peak $peak is too faint")
            var end = y.size - 1
            while (end > 0 && abs(y[end]) < peak * 0.01) end--
            assertTrue(end < 0.06 * SAMPLE_RATE, "$sound low=$low: ${1000.0 * end / SAMPLE_RATE} ms to -40 dB")
            var jump = 0.0
            for (i in 1 until y.size) jump = max(jump, abs((y[i] - y[i - 1]).toDouble()))
            assertTrue(jump < 0.3, "$sound low=$low: step $jump")
        }
        val s = synth(ToneTimbre.CLASSIC)
        val a = FloatArray(2400)
        val b = FloatArray(2400)
        s.tap(TapSound.TICK, false, 1f)
        s.render(a, a.size)
        s.render(FloatArray(4800), 4800)
        s.tap(TapSound.TICK, false, 1f)
        s.render(b, b.size)
        assertFalse(a.contentEquals(b), "two taps are the same sample for sample")
    }

    @Test
    fun `the back tap is lower and softer, the volume scales it`() {
        fun render(low: Boolean, gain: Float): FloatArray {
            val s = synth(ToneTimbre.CLASSIC)
            s.tap(TapSound.STICKS, low, gain)
            val x = FloatArray(2400)
            s.render(x, x.size)
            return FloatArray(x.size) { (x[it] * scale).toFloat() }
        }
        val normal = render(false, 1f)
        val back = render(true, 1f)
        val quiet = render(false, 0.5f)
        val none = render(false, 0f)
        assertTrue(back.maxOf { abs(it) } < normal.maxOf { abs(it) })
        assertEquals(0.5, quiet.maxOf { abs(it) } / normal.maxOf { abs(it) }.toDouble(), 0.12, "half the volume")
        assertTrue(none.all { it == 0f }, "volume 0 is silence")
    }

    @Test
    fun `a tap neither delays nor replaces a note, and stopAll takes both away`() {
        val s = synth(ToneTimbre.MARIMBA)
        val score = Score(60.0, listOf(ScoreNote(0.0, 2.0, Voice.TONE, MidiNote(69), 0.8f)))
        s.schedule(score, 0)
        s.tap(TapSound.TICK, false, 1f)
        s.render(FloatArray(4800), 4800)
        s.stopAll()
        s.render(FloatArray(9600), 9600) // ~200 ms to fade
        val after = FloatArray(4800)
        s.render(after, after.size)
        assertTrue(after.all { abs(it * scale) < 0.01 }, "still sounding after stopAll")
        s.render(FloatArray(33600), 33600) // the voices are given back 0.6 s after they were released
        assertTrue(s.isIdle)
    }

    @Test
    fun `the room's answer to one click has the energy and peak of the reference`() {
        val room = SmallRoom(SAMPLE_RATE)
        var energy = 0.0
        var peak = 0.0
        for (i in 0 until SAMPLE_RATE) {
            val y = room.process(if (i == 0) 1.0 else 0.0)
            energy += y * y
            peak = max(peak, abs(y))
        }
        assertEquals(1.11, energy, 0.05)
        assertEquals(0.5, peak, 0.02)
    }

    @Test
    fun `the room rings after the note and only for notes, not the drone`() {
        val dry = synth(ToneTimbre.MARIMBA, room = false)
        val wet = synth(ToneTimbre.MARIMBA, room = true)
        val score = Score(60.0, listOf(ScoreNote(0.0, 0.3, Voice.TONE, MidiNote(69), 0.8f)))
        val a = dry.renderOffline(score, 1.0)
        val b = wet.renderOffline(score, 1.0)
        val from = (0.9 * SAMPLE_RATE).toInt()
        val to = (1.2 * SAMPLE_RATE).toInt()
        assertTrue(rms(FloatArray(b.size) { b[it] * scale.toFloat() }, from, to) > 3 * rms(FloatArray(a.size) { a[it] * scale.toFloat() }, from, to),
            "the room should keep ringing after the note has stopped")
        // the drone is dry: the same drone with and without the room is the same
        val d1 = synth(ToneTimbre.HUM, room = false).also { it.setDrone(listOf(110.0)) }
        val d2 = synth(ToneTimbre.HUM, room = true).also { it.setDrone(listOf(110.0)) }
        val x = FloatArray(4800)
        val y = FloatArray(4800)
        d1.render(x, x.size)
        d2.render(y, y.size)
        assertTrue(x.contentEquals(y), "the drone must stay dry")
        // and the room costs nothing, and holds nothing, once it has died away
        wet.render(FloatArray(48000), 48000)
        assertTrue(wet.isIdle)
    }
}
