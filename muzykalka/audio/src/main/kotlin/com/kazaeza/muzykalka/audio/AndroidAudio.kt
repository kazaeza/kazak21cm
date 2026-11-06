package com.kazaeza.muzykalka.audio

import android.content.Context
import com.kazaeza.muzykalka.dsp.NoteEvent
import com.kazaeza.muzykalka.dsp.PitchFrame
import com.kazaeza.muzykalka.music.Score
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Ticket M-05. */
class AndroidAudioOutput(@Suppress("unused") private val context: Context) : AudioOutput {
    private val sounding = MutableStateFlow(false)
    override val isSounding: StateFlow<Boolean> = sounding
    override suspend fun play(score: Score): Unit = TODO("M-05: AudioTrack + Synth render thread")
    override fun stop(): Unit = TODO("M-05")
}

/** Ticket M-06. */
class AndroidPitchInput(@Suppress("unused") private val context: Context) : PitchInput {
    override val frames: SharedFlow<PitchFrame> = MutableSharedFlow(extraBufferCapacity = 64)
    override val notes: SharedFlow<NoteEvent> = MutableSharedFlow(extraBufferCapacity = 16)
    private val running = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = running
    override fun start(deviceId: Int?): Unit = TODO("M-06: AudioRecord read thread + MPM + segmenter")
    override fun stop(): Unit = TODO("M-06")
}

/** Ticket M-09. */
class TtsAnnouncer(@Suppress("unused") private val context: Context) : Announcer {
    override suspend fun say(text: String): Unit = TODO("M-09: TextToSpeech ru")
}
