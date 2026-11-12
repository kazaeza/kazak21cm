package com.kazaeza.muzykalka.dsp

import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.ScoreNote
import com.kazaeza.muzykalka.music.ToneTimbre
import com.kazaeza.muzykalka.music.Voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * O-39: «Стекло». The reference numbers are those of docs/design/lab2/synth.js rendered with the user's choice
 * (warm 1, space 0.25, balance 1 for the note) and scaled by the master of the page over the master of this synth
 * (0.9 / 0.42) — if a test here is off by more than its margin, the port does not sound like the stand that was heard.
 */
class GlassTest {
    /** The master is turned almost off so that its tanh stays linear and the voice itself is measured. */
    private val scale = 1000.0
    private val out = 0.9 / 0.42

    private class Stereo(val l: DoubleArray, val r: DoubleArray)

    private fun synth(timbre: ToneTimbre = ToneTimbre.GLASS, tuning: GlassTuning = GlassTuning()) =
        DefaultSynth(masterGain = 1f / scale.toFloat()).also {
            it.toneTimbre = timbre
            it.glass = tuning
        }

    private fun render(synth: DefaultSynth, seconds: Double): Stereo {
        val n = (seconds * SAMPLE_RATE).toInt()
        val l = DoubleArray(n)
        val r = DoubleArray(n)
        val block = FloatArray(512)
        var done = 0
        while (done < n) {
            val m = min(256, n - done)
            synth.renderStereo(block, m)
            for (i in 0 until m) {
                l[done + i] = block[2 * i] * scale
                r[done + i] = block[2 * i + 1] * scale
            }
            done += m
        }
        return Stereo(l, r)
    }

    private fun rms(x: DoubleArray, from: Int, to: Int): Double {
        var e = 0.0
        for (i in from until to) e += x[i] * x[i]
        return sqrt(e / (to - from))
    }

    private fun db(x: Double) = 20 * log10(max(x, 1e-12))

    /** The largest jump between two neighbouring samples in [from, to): a click is a jump the music itself never makes. */
    private fun maxStep(x: DoubleArray, from: Int = 1, to: Int = x.size): Double {
        var step = 0.0
        for (i in max(from, 1) until to) step = max(step, abs(x[i] - x[i - 1]))
        return step
    }

    private fun corr(a: DoubleArray, b: DoubleArray, from: Int, to: Int): Double {
        var ab = 0.0
        var aa = 0.0
        var bb = 0.0
        for (i in from until to) {
            ab += a[i] * b[i]
            aa += a[i] * a[i]
            bb += b[i] * b[i]
        }
        return ab / sqrt(aa * bb)
    }

    private fun hz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun sec(s: Double) = (s * SAMPLE_RATE).toInt()

    // ------------------------------------------------------------------------------------------ tables

    @Test
    fun `a table has an RMS of one and its band sits on the pitch asked for`() {
        for (f in listOf(110.0, 220.0, 440.0)) {
            val t = Pad.table(SAMPLE_RATE, f, 16, 9.0, 3) { h, _ -> h.toDouble().pow(-1.6) }
            var e = 0.0
            for (v in t) e += v.toDouble() * v
            assertEquals(1.0, sqrt(e / t.size), 1e-3, "RMS of the table of $f Hz")
            // the strongest bin of the spectrum, found by a scan of the DFT around the pitch
            var best = 0.0
            var bestHz = 0.0
            var x = f * 0.97
            while (x < f * 1.03) {
                var re = 0.0
                var im = 0.0
                val step = 2 * PI * x / SAMPLE_RATE
                for (i in 0 until 16384) {
                    val w = 0.5 - 0.5 * cos(2 * PI * i / 16384)
                    re += w * t[i] * cos(step * i)
                    im += w * t[i] * sin(step * i)
                }
                val m = re * re + im * im
                if (m > best) {
                    best = m
                    bestHz = x
                }
                x += f * 0.0005
            }
            assertEquals(f, bestHz, f * 0.01, "pitch of the table of $f Hz")
        }
    }

    @Test
    fun `the generator is the one of the reference`() {
        // the first values of xorshift32 from seed 3, as the lab page computes them
        val g = XorShift(3)
        val first = DoubleArray(3) { g.next() }
        assertEquals(3 xor (3 shl 13), 3 xor (3 shl 13)) // keeps the Int maths visible below
        var v = 3
        v = v xor (v shl 13)
        v = v xor (v ushr 17)
        v = v xor (v shl 5)
        assertEquals((v.toLong() and 0xFFFFFFFFL) / 4294967296.0, first[0], 0.0)
        assertTrue(first.all { it in 0.0..1.0 })
    }

    // ------------------------------------------------------------------------------------------ the drone

    /** k of the reference (docs/design/lab2/synth.js) for the tonics C2, D2, E2, F2, G2, A2, B2 at warm 1, space 0.25. */
    private val referenceK = mapOf(36 to 0.0650, 38 to 0.0553, 40 to 0.0639, 41 to 0.0618, 43 to 0.0609, 45 to 0.0685, 47 to 0.0630)

    @Test
    fun `the glass drone is as loud as the reference in every key, and wide`() {
        for ((midi, k) in referenceK) {
            val s = synth()
            s.setDrone(listOf(hz(midi), 2 * hz(midi)))
            val x = render(s, 6.0 + 10.92)
            val from = sec(6.0)
            val to = x.l.size
            val rmsAll = sqrt((rms(x.l, from, to).pow(2) + rms(x.r, from, to).pow(2)) / 2)
            // the reference brought every key to 0.09; the port uses one number for all of them (0.0625), so it is off by what k is off
            val expected = 0.09 * out * (0.0625 / k)
            assertEquals(db(expected), db(rmsAll), 0.9, "level of the drone on MIDI $midi")
            val c = corr(x.l, x.r, from, to)
            assertTrue(c in 0.2..0.75, "the two ears are different, not unrelated: $c on MIDI $midi")
        }
    }

    @Test
    fun `the drone comes in and goes out without a click, and the hall rings after it`() {
        val s = synth()
        s.setDrone(listOf(hz(45), 2 * hz(45)))
        val inn = render(s, 3.0)
        val steady = maxStep(inn.l, sec(2.0), sec(3.0))
        s.setDrone(emptyList())
        val away = render(s, 12.0)
        // a click is a jump of the size of the signal; the music itself never steps by more than it does when it sits still
        assertTrue(maxStep(inn.l) < 1.5 * steady, "coming in: step ${maxStep(inn.l)}, steady $steady")
        assertTrue(maxStep(away.l) < 1.5 * steady, "going out: step ${maxStep(away.l)}, steady $steady")
        assertTrue(steady > 0.0)
        // the drone fades out in a second or two and the hall rings a little longer: still audible 1 s after, gone in 10 s
        val rmsBefore = rms(inn.l, sec(2.0), sec(3.0))
        assertTrue(rms(away.l, sec(1.0), sec(1.5)) > 0.02 * rmsBefore, "the hall still rings after the drone has gone")
        assertTrue(rms(away.l, sec(10.0), sec(12.0)) < 0.001 * rmsBefore, "and has died by 10 s")
        assertTrue(s.isIdle, "and the synth is idle again, so the output may sleep")
    }

    @Test
    fun `a new key is a crossfade`() {
        val s = synth()
        s.setDrone(listOf(hz(45), 2 * hz(45)))
        val before = render(s, 4.0)
        val steady = maxStep(before.l, sec(2.0), sec(4.0))
        s.setDrone(listOf(hz(40), 2 * hz(40)))
        val x = render(s, 4.0)
        // two keys together may be a little more than one, a click is far more than that
        assertTrue(maxStep(x.l) < 2.0 * steady, "largest step ${maxStep(x.l)}, steady $steady")
        assertTrue(rms(x.l, sec(3.0), sec(4.0)) > 0.1, "the new key is there")
    }

    @Test
    fun `the classic drone stays mono and the glass one does not`() {
        val classic = synth(ToneTimbre.HUM)
        classic.setDrone(listOf(hz(45), 2 * hz(45)))
        val c = render(classic, 4.0)
        assertTrue(corr(c.l, c.r, sec(2.0), sec(4.0)) > 0.9999, "the old drone is one channel in two")
        val glass = synth(ToneTimbre.GLASS)
        glass.setDrone(listOf(hz(45), 2 * hz(45)))
        val g = render(glass, 4.0)
        assertTrue(corr(g.l, g.r, sec(2.0), sec(4.0)) < 0.9, "the glass drone has two ears")
    }

    @Test
    fun `switching the instrument under a drone that sounds hands over, it does not add up`() {
        val s = synth(ToneTimbre.HUM)
        s.setDrone(listOf(hz(45), 2 * hz(45)))
        render(s, 2.0)
        s.toneTimbre = ToneTimbre.GLASS
        s.setDrone(listOf(hz(45), 2 * hz(45)))
        val x = render(s, 8.0)
        // after the hand-over only the choir is left: the old drone was mono, so a left that differs from the right means it is gone
        assertTrue(corr(x.l, x.r, sec(6.0), sec(8.0)) < 0.9)
    }

    @Test
    fun `mono render is the two ears averaged`() {
        val a = synth()
        val b = synth()
        a.setDrone(listOf(hz(45), 2 * hz(45)))
        b.setDrone(listOf(hz(45), 2 * hz(45)))
        val st = FloatArray(512)
        val mono = FloatArray(256)
        repeat(40) {
            a.renderStereo(st, 256)
            b.render(mono, 256)
            for (i in 0 until 256) assertEquals(0.5f * (st[2 * i] + st[2 * i + 1]), mono[i], 1e-9f)
        }
    }

    // ------------------------------------------------------------------------------------------ the note

    private fun note(midi: Int, seconds: Double = 1.0, tuning: GlassTuning = GlassTuning(balance = 1.0), velocity: Float = 0.8f): Stereo {
        val s = synth(tuning = tuning)
        s.schedule(Score(60.0, listOf(ScoreNote(0.0, seconds, Voice.TONE, MidiNote(midi), velocity))), s.framePosition)
        return render(s, seconds + 5.0)
    }

    private class Ref(val peak: Double, val rmsDb: Double)

    /** peak (max of both ears) and RMS of the first 0.4 s of the left ear (dB) of the reference for A3, A4, A5, E6 (220, 440, 880, 1318.5 Hz), balance 1. */
    private val table = listOf(
        57 to Ref(0.3230, -15.16),
        69 to Ref(0.3023, -16.17),
        81 to Ref(0.2811, -17.55),
        88 to Ref(0.1971, -20.94),
    )

    @Test
    fun `a glass note is as loud as the reference, and quieter the higher it is`() {
        val rmsDbs = ArrayList<Double>()
        for ((midi, ref) in table) {
            val x = note(midi)
            val peak = max(x.l.maxOf { abs(it) }, x.r.maxOf { abs(it) })
            val rmsDb = db(rms(x.l, 0, sec(0.4)))
            rmsDbs += rmsDb
            assertEquals(db(ref.peak * out), db(peak), 1.5, "peak of MIDI $midi")
            assertEquals(ref.rmsDb + db(out), rmsDb, 1.5, "RMS of the first 0.4 s of MIDI $midi")
        }
        // the register: E6 is quieter than A3 by what the reference measured (5.8 dB)
        assertEquals(5.8, rmsDbs.first() - rmsDbs.last(), 1.5)
    }

    @Test
    fun `the balance is the note against the drone and nothing else`() {
        val a = note(69, tuning = GlassTuning(balance = 1.0))
        val b = note(69, tuning = GlassTuning(balance = 0.6))
        assertEquals(db(0.6), db(rms(b.l, 0, sec(1.0))) - db(rms(a.l, 0, sec(1.0))), 0.05)
    }

    @Test
    fun `the pitch of the note is right at once`() {
        val errors = ArrayList<String>()
        var worst = 0.0
        for (midi in listOf(45, 57, 69, 81, 88)) {
            val f = hz(midi)
            val x = note(midi, seconds = 1.5)
            // a Hann-windowed DFT of 0.68 s from 0.12 s on, scanned in 0.25-cent steps round the pitch
            val n = 32768
            val from = sec(0.12)
            var best = 0.0
            var bestCents = 0.0
            var c = -20.0
            while (c <= 20.0) {
                val step = 2 * PI * f * 2.0.pow(c / 1200) / SAMPLE_RATE
                var re = 0.0
                var im = 0.0
                for (i in 0 until n) {
                    val w = 0.5 - 0.5 * cos(2 * PI * i / n)
                    re += w * x.l[from + i] * cos(step * i)
                    im += w * x.l[from + i] * sin(step * i)
                }
                if (re * re + im * im > best) {
                    best = re * re + im * im
                    bestCents = c
                }
                c += 0.25
            }
            errors += "MIDI $midi: $bestCents"
            worst = max(worst, abs(bestCents))
        }
        // the bell is exact; what the measure sees is a decay under the window and a band of 9 cents of the breath
        assertTrue(worst <= 4.0, "pitch error in cents: $errors")
    }

    @Test
    fun `the bell has a front and the breath comes after it`() {
        val x = note(69)
        // the pitch is there at once: within 5 ms the note is already at half of its first 0.1 s
        val first = rms(x.l, 0, sec(0.1))
        assertTrue(rms(x.l, sec(0.005), sec(0.02)) > 0.5 * first)
        // the two ears of the note are close but not the same (the breath is different in each)
        val c = corr(x.l, x.r, sec(0.5), sec(1.0))
        assertTrue(c in 0.6..0.999, "ears $c")
        // after it ends the tail rings in the hall and dies
        assertTrue(rms(x.l, sec(1.5), sec(2.0)) > 0.0, "tail")
        assertTrue(rms(x.l, sec(5.5), sec(6.0)) < 0.01 * first, "gone by 5.5 s")
    }

    @Test
    fun `stopAll cuts the bells and their hall quickly, and leaves the drone alone`() {
        val s = synth()
        s.setDrone(listOf(hz(45), 2 * hz(45)))
        render(s, 6.0)
        val drone = render(s, 1.0)
        s.schedule(Score(60.0, listOf(ScoreNote(0.0, 3.0, Voice.TONE, MidiNote(69), 0.8f))), s.framePosition)
        render(s, 1.0)
        s.stopAll()
        val after = render(s, 1.0)
        // the drone alone had this RMS; 0.3 s after stopAll the bells and the tail of their hall are gone and it is what is left
        val alone = rms(drone.l, 0, drone.l.size)
        val late = rms(after.l, sec(0.4), sec(1.0))
        assertTrue(abs(db(late) - db(alone)) < 3.0, "late ${db(late)} dB, drone alone ${db(alone)} dB")
    }

    @Test
    fun `chords of notes add up and no more, and the voices are let go`() {
        val midis = (0..5).map { 60 + 2 * it }
        val singles = midis.sumOf { m -> note(m, tuning = GlassTuning()).let { x -> max(x.l.maxOf { abs(it) }, x.r.maxOf { abs(it) }) } }
        val s = synth()
        val notes = midis.mapIndexed { i, m -> ScoreNote(0.2 * i, 0.2 * i + 1.0, Voice.TONE, MidiNote(m), 0.8f) }
        s.schedule(Score(60.0, notes), s.framePosition)
        val x = render(s, 12.0)
        val peak = max(x.l.maxOf { abs(it) }, x.r.maxOf { abs(it) })
        assertTrue(peak < singles * 1.02, "peak $peak, all six one by one $singles")
        assertTrue(s.isIdle, "everything has died and the synth sleeps")
        assertFalse(x.l.any { it.isNaN() })
    }

    @Test
    fun `a note of another timbre is untouched by the glass machinery`() {
        val s = synth(ToneTimbre.HUM)
        s.schedule(Score(60.0, listOf(ScoreNote(0.0, 1.0, Voice.TONE, MidiNote(69), 0.8f))), s.framePosition)
        val x = render(s, 2.0)
        assertTrue(corr(x.l, x.r, 0, sec(1.0)) > 0.9999, "the hum is in the middle")
    }
}
