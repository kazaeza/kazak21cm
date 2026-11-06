package com.kazaeza.muzykalka.dsp

import com.kazaeza.muzykalka.music.DEFAULT_A4_HZ
import com.kazaeza.muzykalka.music.MidiNote

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
 * McLeod Pitch Method (NSDF via FFT autocorrelation, key-maxima peak picking with k = 0.9,
 * parabolic interpolation). Spec and acceptance tests: ticket M-02 in docs/ROADMAP.md.
 */
class McLeodPitchDetector(
    override val sampleRate: Int = SAMPLE_RATE,
    override val windowSize: Int = 2048,
    private val minHz: Double = 60.0,
    private val maxHz: Double = 1400.0,
) : PitchDetector {
    init {
        require(Integer.bitCount(windowSize) == 1) { "windowSize must be a power of two" }
        require(sampleRate / minHz * 2 <= windowSize) { "window too short for $minHz Hz" }
    }

    override fun detect(samples: FloatArray, offset: Int): PitchEstimate? = TODO("M-02: implement MPM")
}

/** One analysed hop from the live input, timestamped on the shared monotonic clock. */
data class PitchFrame(
    val timeNanos: Long,
    val estimate: PitchEstimate?,
    /** Window RMS in dBFS (0 = full scale). */
    val rmsDb: Double,
)

/** Note-level events derived from frames. */
sealed interface NoteEvent {
    val timeNanos: Long

    /** A note became stable: same pitch (±[NoteSegmenterConfig.maxWobbleCents]) for ≥ minHoldMs. */
    data class Onset(override val timeNanos: Long, val note: MidiNote, val cents: Double, val hz: Double) : NoteEvent

    /** The sounding note ended (silence, or a different stable pitch follows). */
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
    val a4Hz: Double = DEFAULT_A4_HZ,
)

interface NoteSegmenter {
    /** Feed frames in time order; returns an event when one happens on this frame. */
    fun accept(frame: PitchFrame): NoteEvent?

    fun reset()
}

/** Default segmenter. Spec and tests: ticket M-03. */
class StableNoteSegmenter(private val config: NoteSegmenterConfig = NoteSegmenterConfig()) : NoteSegmenter {
    override fun accept(frame: PitchFrame): NoteEvent? = TODO("M-03: implement note segmentation (config=$config)")

    override fun reset(): Unit = TODO("M-03")
}
