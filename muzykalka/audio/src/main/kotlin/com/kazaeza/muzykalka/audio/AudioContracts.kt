package com.kazaeza.muzykalka.audio

import com.kazaeza.muzykalka.dsp.NoteEvent
import com.kazaeza.muzykalka.dsp.PitchFrame
import com.kazaeza.muzykalka.music.Score
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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
}

/**
 * Microphone / USB-interface side. AudioRecord (source UNPROCESSED when supported, else
 * VOICE_RECOGNITION), 48 kHz mono float, window 2048 / hop 512, McLeod pitch → StableNoteSegmenter.
 */
interface PitchInput {
    /** Every analysed hop (≈94/s). For the tuner, cents meters and the Recorder. */
    val frames: SharedFlow<PitchFrame>

    /** Stable notes only. Games consume these (first onset per task, rule 4 lives in the engine). */
    val notes: SharedFlow<NoteEvent>

    val isRunning: StateFlow<Boolean>

    /** Requires RECORD_AUDIO. [deviceId] = AudioDeviceInfo.id of a preferred input, null = default. */
    fun start(deviceId: Int?)

    fun stop()
}

/**
 * Half-duplex policy (risk «приложение слышит само себя»): while [AudioOutput.isSounding] and for
 * [tailMs] after, input notes are dropped. With headphones the gate is open. Ticket M-07.
 */
class DuplexGate(val tailMs: Long = 150)

/** Spoken orders («Autumn Leaves, в ми миноре!») via Android TextToSpeech, Locale("ru"). */
interface Announcer {
    suspend fun say(text: String)
}
