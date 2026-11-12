package com.kazaeza.muzykalka.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * O-39: «Стекло» — the instrument the user chose on the lab page (docs/design/lab2/synth.js, `glass`). One voice for
 * the drone and for the note over it, both in the same stereo hall:
 *
 *  - the drone is a choir of glass: PADsynth tables (every harmonic is a narrow band of noise, wider for the higher
 *    ones, so one note is already an ensemble), each note read at two places (left, right), the two notes breathing
 *    apart; a sine an octave under the tonic holds the pitch;
 *  - the note is a small FM bell (so the pitch is there at once), a bar that sustains, and a breath of the same choir;
 *  - the hall is eight delay lines that feed each other, with an allpass diffusion in front.
 *
 * The reference rendered loops and notes into buffers; here everything is laid out for real time (recursive
 * envelopes, running phases, a reverb that streams) and every buffer is allocated up front, so the audio thread
 * allocates nothing. The only heavy work — the inverse FFT that makes a table — is done by [GlassTables] off the
 * audio thread, once for each pitch.
 */

private const val TAU = 2 * PI

/**
 * What the user turned on the lab page and chose: the three sliders. The defaults ARE the choice
 * (warm 1, space 0.25, note to drone 0.6).
 */
data class GlassTuning(
    /** −1 (dark) … 1 (bright): where the choir's spectrum rolls off, and how steeply it falls. */
    val warm: Double = 1.0,
    /** 0 … 1: how much hall — the send into the reverb, and how long it rings. */
    val space: Double = 0.25,
    /** 0.3 … 2: the note against the drone (the drone stays as it is). */
    val balance: Double = 0.6,
) {
    init {
        require(warm in -1.0..1.0) { "warm out of -1..1" }
        require(space in 0.0..1.0) { "space out of 0..1" }
        require(balance in 0.3..2.0) { "balance out of 0.3..2" }
    }

    internal val droneSend: Double get() = 0.45 * (0.35 + 1.3 * space)
    internal val noteSend: Double get() = 0.40 * (0.35 + 1.3 * space)

    /** Seconds the hall takes to fall by 60 dB. */
    internal val t60: Double get() = 4.5 * (0.65 + 0.7 * space)
}

/** The reference was heard through the page's master of 0.9; this synth's master is 0.42 — the same loudness out. */
private const val OUT = 0.9 / 0.42

/** The reference scaled its drone to an RMS of 0.09 (stereo); this is that scale for the raw mix, averaged over the twelve tonics (they differ by ±1 dB). */
internal const val GLASS_DRONE_GAIN = 0.0625 * OUT

/** The reference scaled its A4 to 0.16 over the first 0.4 s: this is that scale for the raw note (balance 1, strength 0.8). */
internal const val GLASS_NOTE_GAIN = 0.19668 * OUT

/** xorshift32 in 0…1, bit for bit the generator of the reference, so a table is the same table. */
internal class XorShift(seed: Int) {
    private var s = if (seed == 0) 1 else seed

    fun next(): Double {
        var v = s
        v = v xor (v shl 13)
        v = v xor (v ushr 17)
        v = v xor (v shl 5)
        s = v
        return (v.toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}

/** PADsynth: a tone made in the spectrum, then one inverse FFT. */
internal object Pad {
    private val twiddles = HashMap<Int, Pair<DoubleArray, DoubleArray>>()

    private fun twiddle(n: Int): Pair<DoubleArray, DoubleArray> = synchronized(twiddles) {
        twiddles.getOrPut(n) { DoubleArray(n / 2) { cos(TAU * it / n) } to DoubleArray(n / 2) { sin(TAU * it / n) } }
    }

    /** In-place inverse transform without the 1/n (the table is scaled to an RMS of 1 afterwards anyway). */
    private fun ifft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        val (cosT, sinT) = twiddle(n)
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val step = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until half) {
                    val cr = cosT[k * step]
                    val ci = sinT[k * step]
                    val a = i + k
                    val b = a + half
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * A table of `2^bits` samples that plays [hz] when read one sample per sample. Every harmonic is a Gaussian band
     * of noise, [bwCents] wide at the first harmonic and wider (h^0.92) higher up, with random phases; [amp] gives the
     * weight of harmonic `h` at `f` hertz. The table is scaled to an RMS of 1.
     */
    fun table(sr: Int, hz: Double, bits: Int, bwCents: Double, seed: Int, amp: (h: Int, f: Double) -> Double): FloatArray {
        val n = 1 shl bits
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val rnd = XorShift(seed)
        val top = min(9000.0, sr * 0.4)
        val bwScale = 2.0.pow(bwCents / 1200.0) - 1.0
        var h = 1
        while (hz * h < top) {
            val f = hz * h
            val a = amp(h, f)
            if (a >= 1e-4) {
                val sigma = bwScale * hz * h.toDouble().pow(0.92) / sr * n
                val c = f / sr * n
                var i = max(1, floor(c - 4 * sigma).toInt())
                while (i <= c + 4 * sigma && i < n / 2) {
                    val x = (i - c) / sigma
                    re[i] += a * exp(-x * x) / sigma
                    i++
                }
            }
            h++
        }
        for (i in 1 until n / 2) {
            val m = re[i]
            val p = TAU * rnd.next()
            re[i] = m * cos(p)
            im[i] = m * sin(p)
            re[n - i] = re[i]
            im[n - i] = -im[i]
        }
        ifft(re, im)
        var e = 0.0
        for (i in 0 until n) e += re[i] * re[i]
        val k = 1.0 / sqrt(e / n)
        return FloatArray(n) { (re[it] * k).toFloat() }
    }
}

/**
 * The tables of the choir, made once for each pitch and kept (a few dozen of them: the twelve tonics of the drone and the notes of the game). Making one takes tens of
 * milliseconds, so it must not be asked for on the audio thread: [DefaultSynth] asks when a note is scheduled or a
 * drone is set, and the audio thread only ever finds them ready.
 */
internal object GlassTables {
    private const val DRONE_BITS = 17
    private const val NOTE_BITS = 16
    private const val DRONE_LIMIT = 48
    private const val NOTE_LIMIT = 64

    private data class Key(val sr: Int, val milliHz: Long, val warm: Int, val seed: Int)

    private fun lru(limit: Int) = object : LinkedHashMap<Key, FloatArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, FloatArray>?): Boolean = size > limit
    }

    private val drones = lru(DRONE_LIMIT)
    private val notes = lru(NOTE_LIMIT)

    private fun cached(map: MutableMap<Key, FloatArray>, key: Key, make: () -> FloatArray): FloatArray {
        synchronized(map) { map[key] }?.let { return it }
        val table = make()
        synchronized(map) { map[key] = table }
        return table
    }

    /** The choir of one drone note: slot 0 is the tonic, the others the notes above it. */
    fun drone(sr: Int, hz: Double, warm: Double, slot: Int): FloatArray {
        val seed = when (slot) {
            0 -> 11
            1 -> 23
            else -> 31 + slot
        }
        val key = Key(sr, (hz * 1000).roundToLong(), (warm * 100).roundToInt(), seed)
        return cached(drones, key) { Pad.table(sr, hz, DRONE_BITS, 26.0, seed) { h, f -> droneAmp(warm, h, f) } }
    }

    /** The breath of the choir under one bell. */
    fun note(sr: Int, hz: Double, warm: Double): FloatArray {
        val key = Key(sr, (hz * 1000).roundToLong(), (warm * 100).roundToInt(), 3)
        return cached(notes, key) { Pad.table(sr, hz, NOTE_BITS, 9.0, 3) { h, f -> noteAmp(warm, h, f) } }
    }

    private fun droneAmp(warm: Double, h: Int, f: Double): Double {
        val tilt = h.toDouble().pow(-1.2 + 0.3 * warm)
        val formants = 1 +
            1.5 * exp(-((f - 650) / 380).pow(2)) +
            0.8 * exp(-((f - 1300) / 520).pow(2)) +
            0.4 * exp(-((f - 2700) / 700).pow(2))
        return tilt * formants / sqrt(1 + (f / (3000 * 2.0.pow(warm))).pow(4))
    }

    private fun noteAmp(warm: Double, h: Int, f: Double): Double =
        h.toDouble().pow(-1.9 + 0.3 * warm) / sqrt(1 + (f / (3600 * 2.0.pow(warm))).pow(4))
}

/**
 * The hall: eight delay lines that feed each other through a Householder mix, an allpass diffusion in front of
 * them (different for the two ears), an 18 ms pre-delay, and two lines that breathe (their length moves a few
 * samples), which keeps the tail from ringing on one pitch. Wet signal only: [wetL] and [wetR] after [process].
 */
internal class GlassSpace(private val sr: Int) {
    private val lens = IntArray(8) { scaled(LENS[it]) }
    private val sizes = IntArray(8) { lens[it] + PAD }
    private val bufs = Array(8) { FloatArray(sizes[it]) }
    private val w = IntArray(8)
    private val lp = DoubleArray(8)
    private val s = DoubleArray(8)
    private val g = DoubleArray(8)
    private val damp = exp(-TAU * DAMP_HZ / sr)
    private val apL = Array(4) { FloatArray(scaled(AP_L[it])) }
    private val apR = Array(4) { FloatArray(scaled(AP_R[it])) }
    private val apLi = IntArray(4)
    private val apRi = IntArray(4)
    private val pre = (0.018 * sr).roundToInt()
    private val preL = FloatArray(pre)
    private val preR = FloatArray(pre)
    private var pi = 0
    private val inc1 = TAU * 0.31 / sr
    private val inc2 = TAU * 0.47 / sr
    private var ph1 = 0.0
    private var ph2 = 1.7

    var wetL = 0.0
        private set
    var wetR = 0.0
        private set

    init {
        setT60(3.0)
    }

    private fun scaled(n: Int): Int = (n * sr / 48_000.0).roundToInt()

    /** How long the tail takes to fall by 60 dB. Takes effect at once; the lines keep what they hold. */
    fun setT60(t60: Double) {
        for (k in 0 until 8) g[k] = 10.0.pow(-3.0 * lens[k] / (t60 * sr))
    }

    fun reset() {
        for (b in bufs) b.fill(0f)
        for (b in apL) b.fill(0f)
        for (b in apR) b.fill(0f)
        preL.fill(0f)
        preR.fill(0f)
        w.fill(0)
        apLi.fill(0)
        apRi.fill(0)
        lp.fill(0.0)
        s.fill(0.0)
        pi = 0
        wetL = 0.0
        wetR = 0.0
    }

    fun process(inL: Double, inR: Double) {
        var l = preL[pi].toDouble()
        var r = preR[pi].toDouble()
        preL[pi] = inL.toFloat()
        preR[pi] = inR.toFloat()
        if (++pi == pre) pi = 0
        for (j in 0 until 4) {
            val bl = apL[j]
            var i = apLi[j]
            val yl = bl[i] - AP_G * l
            bl[i] = (l + AP_G * yl).toFloat()
            if (++i == bl.size) i = 0
            apLi[j] = i
            l = yl
            val br = apR[j]
            i = apRi[j]
            val yr = br[i] - AP_G * r
            br[i] = (r + AP_G * yr).toFloat()
            if (++i == br.size) i = 0
            apRi[j] = i
            r = yr
        }
        ph1 += inc1
        if (ph1 >= TAU) ph1 -= TAU
        ph2 += inc2
        if (ph2 >= TAU) ph2 -= TAU
        var sum = 0.0
        for (k in 0 until 8) {
            val buf = bufs[k]
            val size = sizes[k]
            val o: Double
            if (k == 2 || k == 5) {
                var pos = w[k] - lens[k] - (12.0 + 9.0 * sin(if (k == 2) ph1 else ph2))
                if (pos < 0.0) pos += size
                val i0 = pos.toInt()
                val fr = pos - i0
                var i1 = i0 + 1
                if (i1 >= size) i1 -= size
                o = buf[i0] * (1.0 - fr) + buf[i1] * fr
            } else {
                var rd = w[k] - lens[k]
                if (rd < 0) rd += size
                o = buf[rd].toDouble()
            }
            lp[k] = o + damp * (lp[k] - o)
            s[k] = lp[k] * g[k]
            sum += s[k]
        }
        sum *= 0.25
        for (k in 0 until 8) {
            val inp = if (k % 2 == 1) r else l
            val sign = if (k % 4 < 2) 1.0 else -1.0
            bufs[k][w[k]] = (s[k] - sum + inp * sign).toFloat()
            if (++w[k] == sizes[k]) w[k] = 0
        }
        wetL = (s[0] - s[2] + s[4] - s[6]) * 0.5
        wetR = (s[1] - s[3] + s[5] - s[7]) * 0.5
    }

    private companion object {
        val LENS = intArrayOf(1423, 1597, 1789, 1993, 2251, 2503, 2797, 3089)
        val AP_L = intArrayOf(241, 373, 491, 617)
        val AP_R = intArrayOf(263, 359, 521, 601)
        const val PAD = 64
        const val AP_G = 0.62
        const val DAMP_HZ = 5200.0
    }
}

/**
 * The drone: up to [SLOTS] notes of the choir (four asked for, the rest still fading out), each a table read at two
 * places — the left ear hears the table and a third of its other half, the right ear the other way round — and
 * breathing on its own slow wave. Gains glide, so a new key is a crossfade (0.35 s in, 0.25 s out), never a click.
 * Audio thread only.
 */
internal class GlassDrone(private val sr: Int) {
    private class Slot {
        var table: FloatArray = EMPTY
        var gain = 0.0
        var target = 0.0
        var pos = 0
        var lfo = 0.0
        var lfoStep = 0.0
        var depth = 0.0
        var lead = false
        var sub = 0.0
        var subStep = 0.0
    }

    private val slots = Array(SLOTS) { Slot() }
    private val up = 1.0 - exp(-1.0 / (sr * 0.35))
    private val down = 1.0 - exp(-1.0 / (sr * 0.25))

    var left = 0.0
        private set
    var right = 0.0
        private set

    val silent: Boolean
        get() {
            for (sl in slots) if (sl.gain > 1e-5 || sl.target > 0.0) return false
            return true
        }

    /** Fades out what is playing and fades in [tables] (one per frequency, the tonic first). An empty list fades the drone out. */
    fun set(frequencies: List<Double>, tables: List<FloatArray>) {
        for (sl in slots) sl.target = 0.0
        val n = min(min(frequencies.size, tables.size), SLOTS / 2)
        // the same power however many notes are held: the two of tonic and octave are the measured case
        val norm = if (n == 0) 0.0 else sqrt((1.0 + LVL * LVL) / (1.0 + LVL * LVL * (n - 1)))
        for (i in 0 until n) {
            val table = tables[i]
            val lead = i == 0
            var slot = slots.firstOrNull { it.table === table && it.gain > 1e-4 }
            if (slot == null) {
                slot = slots.filter { it.target == 0.0 }.minByOrNull { it.gain } ?: continue
                slot.table = table
                slot.gain = 0.0
                slot.pos = 0
                slot.lead = lead
                slot.depth = if (lead) 0.14 else 0.3
                slot.lfo = if (lead) 0.0 else 2.2 + 1.3 * (i - 1)
                slot.lfoStep = TAU * (if (lead) 1.0 else 2.0) / (TABLE_SECONDS * sr)
                slot.sub = 0.0
                slot.subStep = TAU * frequencies[i] / sr
            }
            slot.target = GLASS_DRONE_GAIN * norm * (if (lead) 1.0 else LVL)
        }
    }

    fun next() {
        var l = 0.0
        var r = 0.0
        for (sl in slots) {
            val target = sl.target
            if (sl.gain < 1e-6 && target == 0.0) continue
            sl.gain += (target - sl.gain) * (if (target > sl.gain) up else down)
            val t = sl.table
            val mask = t.size - 1
            val a = t[sl.pos].toDouble()
            val b = t[(sl.pos + (t.size shr 1)) and mask].toDouble()
            sl.pos = (sl.pos + 1) and mask
            val breath = 1.0 + sl.depth * sin(sl.lfo)
            sl.lfo += sl.lfoStep
            if (sl.lfo >= TAU) sl.lfo -= TAU
            var sub = 0.0
            if (sl.lead) {
                sub = SUB * sin(sl.sub)
                sl.sub += sl.subStep
                if (sl.sub >= TAU) sl.sub -= TAU
            }
            val gb = sl.gain * breath
            l += gb * (a + OTHER * b + sub)
            r += gb * (b + OTHER * a + sub)
        }
        left = l
        right = r
    }

    companion object {
        const val SLOTS = 8

        /** The octave and the rest sit under the tonic by this much. */
        private const val LVL = 0.6
        private const val OTHER = 0.3
        private const val SUB = 0.22

        /** The breath of the reference was a whole number of waves in a table of 4 × 2^17 samples at 48 kHz. */
        private const val TABLE_SECONDS = 4.0 * (1 shl 17) / 48_000.0
        private val EMPTY = FloatArray(2)
    }
}

/**
 * The note: a small FM bell (a tine that is gone in a moment, so the pitch is there at once), a bar that sustains,
 * and a breath of the same choir that comes in after them. Both ears hear the bell alike; the breath differs.
 * Envelopes are running products. Audio thread only; [start] allocates nothing.
 */
internal class GlassVoice(private val sr: Int) {
    var right = 0.0
        private set

    private var pad: FloatArray = EMPTY
    private var w = 0.0
    private var ratio = 2.0
    private var index = 1.0
    private var gain = 0.0
    private var ph = 0.0
    private var n = 0
    private var onE = 1.0
    private var tineE = 1.0
    private var fmE = 1.0
    private var barE = 1.0
    private var bodyE = 1.0
    private var padE = 1.0
    private var relBell = 1.0
    private var relPad = 1.0
    private var relBellD = d(0.14)
    private var relPadD = d(0.28)
    private var releasing = false

    private fun d(tau: Double): Double = exp(-1.0 / (tau * sr))

    private val onD = d(0.002)
    private val tineD = d(0.7)
    private val fmD = d(0.045)
    private val barD = d(0.35)
    private val bodyD = d(1.1)
    private val padD = d(0.06)

    /** [table] is the breath of the choir for [hz] ([GlassTables.note]); [velocity] 0.8 is the strength the reference was measured at. */
    fun start(hz: Double, velocity: Float, table: FloatArray?, tuning: GlassTuning) {
        pad = table ?: EMPTY
        w = TAU * hz / sr
        ratio = max(2.0, min(14.0, (6500.0 / hz).roundToInt().toDouble()))
        index = 1.1 * sqrt(min(1.0, 600.0 / hz))
        gain = GLASS_NOTE_GAIN * tuning.balance * registerGain(hz) * velocity / 0.8
        ph = 0.0
        n = 0
        onE = 1.0
        tineE = 1.0
        fmE = 1.0
        barE = 1.0
        bodyE = 1.0
        padE = 1.0
        relBell = 1.0
        relPad = 1.0
        relBellD = d(0.14)
        relPadD = d(0.28)
        releasing = false
    }

    /** stopAll: the voice closes in ~12 ms instead of its own 140 / 280. */
    fun fastRelease() {
        relBellD = d(0.012)
        relPadD = d(0.012)
    }

    /** One stereo sample; the left is returned, the right is in [right]. [released]: the note has been let go. */
    fun next(released: Boolean): Double {
        val on = 1.0 - onE
        val tine = sin(ph + index * fmE * sin(ratio * ph)) * tineE * 0.5
        val bar = sin(ph + 0.6 * barE * sin(ph)) * (0.4 + 0.6 * bodyE) * 0.8
        var m = (tine + bar) * on
        var breath = (1.0 - padE) * 0.22
        if (released) {
            if (!releasing) releasing = true else {
                relBell *= relBellD
                relPad *= relPadD
            }
            m *= relBell
            breath *= relPad
        }
        val mask = pad.size - 1
        val a = pad[n and mask].toDouble()
        val b = pad[(n + (pad.size shr 1)) and mask].toDouble()
        val l = m + breath * (a + OTHER * b)
        val r = m + breath * (b + OTHER * a)
        ph += w
        if (ph >= TAU) ph -= TAU
        n++
        onE *= onD
        tineE *= tineD
        fmE *= fmD
        barE *= barD
        bodyE *= bodyD
        padE *= padD
        right = r * gain
        return l * gain
    }

    private companion object {
        const val OTHER = 0.3
        val EMPTY = FloatArray(1)
    }
}
