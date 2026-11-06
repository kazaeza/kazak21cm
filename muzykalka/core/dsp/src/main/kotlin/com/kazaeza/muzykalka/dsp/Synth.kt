package com.kazaeza.muzykalka.dsp

import com.kazaeza.muzykalka.music.DEFAULT_A4_HZ
import com.kazaeza.muzykalka.music.Score

/**
 * Real-time synthesiser driven by the output thread. All voices are synthesised (no samples):
 * GUITAR/BASS = extended Karplus–Strong, PIANO = 2-operator FM e-piano, drums = noise/sine
 * models, CLICK = short sine burst. Ticket M-04.
 *
 * Threading contract: [schedule] and [stopAll] may be called from any thread; [render] only from
 * the audio thread; [render] must not allocate or lock (use a lock-free command queue).
 */
interface Synth {
    val sampleRate: Int

    /** Schedules [score] to start at absolute output frame [startFrame]; returns its end frame. */
    fun schedule(score: Score, startFrame: Long, a4Hz: Double = DEFAULT_A4_HZ): Long

    /** Renders the next [frames] mono samples into [out] starting at index 0. */
    fun render(out: FloatArray, frames: Int)

    /** Fades everything out within ~10 ms. */
    fun stopAll()

    /** Absolute index of the next frame [render] will produce. */
    val framePosition: Long
}

/** Offline rendering for tests and for measuring prompt length. */
fun Synth.renderOffline(score: Score, tailSeconds: Double = 0.5): FloatArray {
    val end = schedule(score, framePosition)
    val total = (end - framePosition + (tailSeconds * sampleRate).toLong()).toInt()
    val out = FloatArray(total)
    val block = FloatArray(256)
    var written = 0
    while (written < total) {
        val n = minOf(256, total - written)
        render(block, n)
        System.arraycopy(block, 0, out, written, n)
        written += n
    }
    return out
}

/** Default synth. Ticket M-04. */
class DefaultSynth(override val sampleRate: Int = SAMPLE_RATE) : Synth {
    override fun schedule(score: Score, startFrame: Long, a4Hz: Double): Long = TODO("M-04")
    override fun render(out: FloatArray, frames: Int): Unit = TODO("M-04")
    override fun stopAll(): Unit = TODO("M-04")
    override val framePosition: Long get() = TODO("M-04")
}
