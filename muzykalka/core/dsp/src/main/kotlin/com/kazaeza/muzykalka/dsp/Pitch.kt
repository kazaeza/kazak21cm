package com.kazaeza.muzykalka.dsp

import com.kazaeza.muzykalka.music.DEFAULT_A4_HZ
import com.kazaeza.muzykalka.music.MidiNote
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Sample rate of every audio path in the app (input, output, offline render). */
const val SAMPLE_RATE: Int = 48_000

/** Result of analysing one window. [clarity] is the NSDF peak height, 0..1 (1 = perfectly periodic). */
data class PitchEstimate(val hz: Double, val clarity: Double)

/**
 * Monophonic pitch detector for guitar (down to drop-C, ~65 Hz) and voice (up to ~1100 Hz).
 * Implementations must be allocation-free in [detect] (called ~100×/s on the audio thread).
 */
interface PitchDetector {
    val sampleRate: Int
    val windowSize: Int

    /** Analyses [windowSize] samples starting at [offset]; null when no confident pitch. */
    fun detect(samples: FloatArray, offset: Int = 0): PitchEstimate?
}

/**
 * McLeod Pitch Method: normalised square difference function (NSDF) computed through an FFT
 * autocorrelation, key-maxima peak picking with threshold [k] × highest key maximum, and parabolic
 * interpolation of the chosen peak.
 *
 * Not thread-safe: one instance per analysis thread (buffers are reused between calls).
 */
class McLeodPitchDetector(
    override val sampleRate: Int = SAMPLE_RATE,
    override val windowSize: Int = 2048,
    minHz: Double = 60.0,
    maxHz: Double = 1400.0,
    private val k: Double = 0.9,
    private val minClarity: Double = 0.5,
    /** RMS below this (linear, 1e-3 = −60 dBFS) is silence. */
    private val minRms: Double = 1e-3,
) : PitchDetector {
    init {
        require(Integer.bitCount(windowSize) == 1) { "windowSize must be a power of two" }
        require(sampleRate / minHz * 2 <= windowSize) { "window too short for $minHz Hz" }
        require(maxHz > minHz)
    }

    private val fftSize = windowSize * 2
    private val fft = Fft(fftSize)
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val x = DoubleArray(windowSize)
    private val minLag = max(2, (sampleRate / maxHz).toInt())
    private val maxLag = min(windowSize - 2, (sampleRate / minHz).toInt() + 1)
    private val nsdf = DoubleArray(maxLag + 2)
    private val keyMaxima = IntArray(maxLag)

    override fun detect(samples: FloatArray, offset: Int): PitchEstimate? {
        require(offset >= 0 && offset + windowSize <= samples.size) { "window out of bounds" }
        val n = windowSize

        var mean = 0.0
        for (i in 0 until n) mean += samples[offset + i]
        mean /= n
        var energy = 0.0
        for (i in 0 until n) {
            val v = samples[offset + i] - mean
            x[i] = v
            energy += v * v
        }
        if (energy / n < minRms * minRms) return null

        // Linear autocorrelation r(τ) via zero-padded FFT.
        for (i in 0 until n) {
            re[i] = x[i]
            im[i] = 0.0
        }
        for (i in n until fftSize) {
            re[i] = 0.0
            im[i] = 0.0
        }
        fft.forward(re, im)
        for (i in 0 until fftSize) {
            re[i] = re[i] * re[i] + im[i] * im[i]
            im[i] = 0.0
        }
        fft.inverse(re, im)

        // NSDF n'(τ) = 2 r(τ) / m(τ), m(τ) = Σ x_j² + x_{j+τ}², updated incrementally.
        var m = 2.0 * energy
        nsdf[0] = 1.0
        for (tau in 1..maxLag + 1) {
            m -= x[tau - 1] * x[tau - 1] + x[n - tau] * x[n - tau]
            nsdf[tau] = if (m > 1e-12) 2.0 * re[tau] / m else 0.0
        }

        // Key maxima: the highest local maximum of every positive lobe after the first zero crossing.
        var count = 0
        var tau = 1
        while (tau <= maxLag && nsdf[tau] > 0.0) tau++
        var best = -1
        var inLobe = false
        while (tau <= maxLag) {
            val v = nsdf[tau]
            if (v > 0.0) {
                if (!inLobe) {
                    inLobe = true
                    best = -1
                }
                val isPeak = v >= nsdf[tau - 1] && v >= nsdf[tau + 1]
                if (isPeak && (best < 0 || v > nsdf[best])) best = tau
            } else if (inLobe) {
                if (best > 0) keyMaxima[count++] = best
                inLobe = false
            }
            tau++
        }
        if (inLobe && best > 0 && best < maxLag) keyMaxima[count++] = best
        if (count == 0) return null

        var highest = 0.0
        for (i in 0 until count) if (keyMaxima[i] >= minLag) highest = max(highest, nsdf[keyMaxima[i]])
        if (highest <= 0.0) return null
        val threshold = k * highest
        var chosen = -1
        for (i in 0 until count) {
            val t = keyMaxima[i]
            if (t >= minLag && nsdf[t] >= threshold) {
                chosen = t
                break
            }
        }
        if (chosen < 0) return null

        // Parabolic interpolation around the chosen lag.
        val a = nsdf[chosen - 1]
        val b = nsdf[chosen]
        val c = nsdf[chosen + 1]
        val denominator = a - 2.0 * b + c
        val delta = if (abs(denominator) > 1e-12) 0.5 * (a - c) / denominator else 0.0
        val period = chosen + delta
        val clarity = min(1.0, b - 0.25 * (a - c) * delta)
        if (clarity < minClarity || period <= 0.0) return null
        return PitchEstimate(sampleRate / period, clarity)
    }
}

/** One analysed hop from the live input, timestamped on the shared monotonic clock (System.nanoTime). */
data class PitchFrame(
    val timeNanos: Long,
    val estimate: PitchEstimate?,
    /** Window RMS in dBFS (0 = full scale). */
    val rmsDb: Double,
)

/** Note-level events derived from frames. */
sealed interface NoteEvent {
    val timeNanos: Long

    /**
     * A note became stable: same pitch (±[NoteSegmenterConfig.maxWobbleCents]) for ≥ minHoldMs.
     * [timeNanos] is when the note STARTED (first frame of the stable run), not when it was confirmed.
     */
    data class Onset(override val timeNanos: Long, val note: MidiNote, val cents: Double, val hz: Double) : NoteEvent

    /** The sounding note ended (silence, re-pluck, or a different stable pitch follows). */
    data class Release(override val timeNanos: Long) : NoteEvent
}

/**
 * Turns frames into notes. This is where "a note counts only if it holds a fraction of a
 * second" (risk table: room noise, false notes) is implemented. Rule 4 (first note only) is NOT
 * here — the game engine accepts the first Onset per task and ignores the rest.
 */
data class NoteSegmenterConfig(
    val minHoldMs: Int = 90,
    val maxWobbleCents: Double = 40.0,
    val minClarity: Double = 0.85,
    val gateDb: Double = -50.0,
    /** Invalid frames for this long end a sounding note. */
    val releaseMs: Int = 60,
    /** RMS jump (dB) over the last few frames that counts as a re-pluck of the same note. */
    val repluckDb: Double = 6.0,
    val a4Hz: Double = DEFAULT_A4_HZ,
)

interface NoteSegmenter {
    /** Feed frames in time order; returns the events that happen on this frame (usually none). */
    fun accept(frame: PitchFrame): List<NoteEvent>

    fun reset()
}

/**
 * IDLE → CANDIDATE → SOUNDING state machine.
 *  - A frame is valid when clarity ≥ minClarity and RMS ≥ gateDb.
 *  - A candidate survives while frames stay within ±maxWobble of its running mean; after minHold
 *    it becomes an Onset (timestamped at the candidate start).
 *  - While SOUNDING: invalid frames for releaseMs → Release; a different stable pitch for minHold
 *    → Release + Onset; a sudden RMS jump on the same pitch (re-pluck) → Release, then the new
 *    candidate must hold minHold again before its Onset.
 */
class StableNoteSegmenter(private val config: NoteSegmenterConfig = NoteSegmenterConfig()) : NoteSegmenter {
    private enum class State { IDLE, CANDIDATE, SOUNDING }

    private var state = State.IDLE
    private val minHoldNs = config.minHoldMs * 1_000_000L
    private val releaseNs = config.releaseMs * 1_000_000L

    // Candidate (also reused for the "pending different pitch" while SOUNDING).
    private var candStart = 0L
    private var candSumCents = 0.0
    private var candCount = 0
    private var candSumHz = 0.0
    private var candInvalidRun = 0

    // Sounding note.
    private var noteCents = 0.0
    private var lastValid = 0L
    private var pendingActive = false

    private val recentRms = DoubleArray(4) { Double.NEGATIVE_INFINITY }
    private var rmsIdx = 0

    override fun reset() {
        state = State.IDLE
        pendingActive = false
        candCount = 0
        recentRms.fill(Double.NEGATIVE_INFINITY)
    }

    override fun accept(frame: PitchFrame): List<NoteEvent> {
        val est = frame.estimate
        val valid = est != null && est.clarity >= config.minClarity && frame.rmsDb >= config.gateDb
        var minRecent = Double.POSITIVE_INFINITY
        for (v in recentRms) minRecent = min(minRecent, v)
        val jump = frame.rmsDb - minRecent
        recentRms[rmsIdx] = frame.rmsDb
        rmsIdx = (rmsIdx + 1) % recentRms.size
        val hz = est?.hz ?: 0.0
        val cents = if (valid) MidiNote.fractional(hz, config.a4Hz) * 100.0 else Double.NaN
        val t = frame.timeNanos

        return when (state) {
            State.IDLE -> {
                if (valid) startCandidate(cents, hz, t)
                emptyList()
            }

            State.CANDIDATE -> {
                if (!valid) {
                    candInvalidRun++
                    if (candInvalidRun >= 2) state = State.IDLE
                    return emptyList()
                }
                candInvalidRun = 0
                if (abs(cents - candMean()) <= config.maxWobbleCents) {
                    addToCandidate(cents, hz)
                    if (t - candStart >= minHoldNs) return listOf(confirmCandidate(t))
                } else {
                    startCandidate(cents, hz, t)
                }
                emptyList()
            }

            State.SOUNDING -> {
                if (!valid) {
                    if (t - lastValid >= releaseNs) {
                        state = State.IDLE
                        pendingActive = false
                        return listOf(NoteEvent.Release(t))
                    }
                    return emptyList()
                }
                lastValid = t
                val samePitch = abs(cents - noteCents) <= config.maxWobbleCents
                if (samePitch && jump >= config.repluckDb) {
                    state = State.CANDIDATE
                    pendingActive = false
                    startCandidate(cents, hz, t)
                    return listOf(NoteEvent.Release(t))
                }
                if (samePitch) {
                    pendingActive = false
                    return emptyList()
                }
                if (!pendingActive || abs(cents - candMean()) > config.maxWobbleCents) {
                    pendingActive = true
                    startCandidate(cents, hz, t)
                    return emptyList()
                }
                addToCandidate(cents, hz)
                if (t - candStart >= minHoldNs) {
                    pendingActive = false
                    val release = NoteEvent.Release(candStart)
                    return listOf(release, confirmCandidate(t))
                }
                emptyList()
            }
        }
    }

    private fun candMean() = if (candCount == 0) Double.NaN else candSumCents / candCount

    private fun startCandidate(cents: Double, hz: Double, t: Long) {
        if (state == State.IDLE) state = State.CANDIDATE
        candStart = t
        candSumCents = cents
        candSumHz = hz
        candCount = 1
        candInvalidRun = 0
    }

    private fun addToCandidate(cents: Double, hz: Double) {
        candSumCents += cents
        candSumHz += hz
        candCount++
    }

    private fun confirmCandidate(t: Long): NoteEvent.Onset {
        val mean = candMean()
        val midi = (mean / 100.0).roundToInt().coerceIn(0, 127)
        state = State.SOUNDING
        noteCents = mean
        lastValid = t
        return NoteEvent.Onset(candStart, MidiNote(midi), mean - midi * 100.0, candSumHz / candCount)
    }
}

/** RMS of a window in dBFS. */
fun rmsDb(samples: FloatArray, offset: Int, length: Int): Double {
    var sum = 0.0
    for (i in offset until offset + length) sum += samples[i].toDouble() * samples[i]
    val rms = sqrt(sum / length)
    return if (rms <= 1e-12) -240.0 else 20.0 * kotlin.math.log10(rms)
}
