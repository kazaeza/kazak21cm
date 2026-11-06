package com.kazaeza.muzykalka.audio

import com.kazaeza.muzykalka.dsp.NoteEvent
import com.kazaeza.muzykalka.dsp.PitchFrame
import com.kazaeza.muzykalka.music.Score
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Speaker side. One instance per process (AppGraph). Backed by a single low-latency AudioTrack
 * (48 kHz, float, PERFORMANCE_MODE_LOW_LATENCY) whose render thread pulls from core:dsp Synth.
 */
interface AudioOutput {
    /** Plays [score] and suspends until it has finished SOUNDING (including release tail). */
    suspend fun play(score: Score)

    /** Stops everything immediately (fade ≤ 10 ms). */
    fun stop()

    /** True while anything is audible — the [DuplexGate] reads this. */
    val isSounding: StateFlow<Boolean>

    /** System.nanoTime() when the last sound finished (0 if never). */
    val lastSoundEndNanos: Long

    /** A4 reference used to render notes (Settings). */
    var a4Hz: Double
}

/**
 * Microphone / USB-interface side. AudioRecord (source UNPROCESSED when supported, else
 * VOICE_RECOGNITION), 48 kHz mono float, window 2048 / hop 512, McLeod pitch → StableNoteSegmenter.
 */
interface PitchInput {
    /** Every analysed hop (≈94/s). For the tuner, level meters and the Recorder. */
    val frames: SharedFlow<PitchFrame>

    /** Stable notes only. Games consume these (first onset per task, rule 4 lives in the engine). */
    val notes: SharedFlow<NoteEvent>

    val isRunning: StateFlow<Boolean>

    /** Human-readable reason the input could not start (no permission, device busy), or null. */
    val error: StateFlow<String?>

    /** Requires RECORD_AUDIO. [deviceId] = AudioDeviceInfo.id of a preferred input, null = default. */
    fun start(deviceId: Int?, a4Hz: Double)

    fun stop()
}

/** Spoken orders («Autumn Leaves, в ми миноре!») via Android TextToSpeech. */
interface Announcer {
    /** Speaks and suspends until done. Silently returns when no Russian voice is available. */
    suspend fun say(text: String)

    val isSpeaking: StateFlow<Boolean>

    val lastSpeechEndNanos: Long

    /** null = not initialised yet, false = no Russian voice installed. */
    val available: StateFlow<Boolean?>
}

/**
 * Half-duplex policy (risk «приложение слышит само себя»): notes that START while the app is
 * sounding or speaking, or within [tailMs] after, are dropped. With headphones the gate is open.
 */
class DuplexGate(private val tailMs: Long = 150) {
    fun onsets(
        input: PitchInput,
        output: AudioOutput,
        announcer: Announcer?,
        headphones: StateFlow<Boolean>,
    ): Flow<NoteEvent.Onset> = input.notes.filterIsInstance<NoteEvent.Onset>().filter { allows(it.timeNanos, output, announcer, headphones.value) }

    fun allows(noteStartNanos: Long, output: AudioOutput, announcer: Announcer?, headphones: Boolean): Boolean {
        if (headphones) return true
        val tail = tailMs * 1_000_000
        if (output.isSounding.value) return false
        if (noteStartNanos <= output.lastSoundEndNanos + tail) return false
        if (announcer != null) {
            if (announcer.isSpeaking.value) return false
            if (noteStartNanos <= announcer.lastSpeechEndNanos + tail) return false
        }
        return true
    }
}
