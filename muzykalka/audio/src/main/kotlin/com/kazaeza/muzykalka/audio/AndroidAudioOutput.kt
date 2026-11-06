package com.kazaeza.muzykalka.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.kazaeza.muzykalka.dsp.DefaultSynth
import com.kazaeza.muzykalka.dsp.SAMPLE_RATE
import com.kazaeza.muzykalka.dsp.Synth
import com.kazaeza.muzykalka.music.DEFAULT_A4_HZ
import com.kazaeza.muzykalka.music.Score
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Ticket M-05. One AudioTrack, created on first use, stopped after [IDLE_STOP_NS] of silence to
 * save battery. The render thread runs at URGENT_AUDIO priority and pulls [Synth.render].
 */
class AndroidAudioOutput(
    context: Context,
    private val synth: Synth = DefaultSynth(SAMPLE_RATE),
) : AudioOutput {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val lock = Any()

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var running = false

    @Volatile
    private var trackStartFrame = 0L

    @Volatile
    private var lastActivityNanos = System.nanoTime()

    @Volatile
    private var soundingUntilFrame = 0L

    private val sounding = MutableStateFlow(false)
    override val isSounding: StateFlow<Boolean> = sounding.asStateFlow()

    @Volatile
    override var lastSoundEndNanos: Long = 0L
        private set

    @Volatile
    override var a4Hz: Double = DEFAULT_A4_HZ

    private fun ensureRunning(): Boolean = synchronized(lock) {
        if (running) return true
        try {
            val burst = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
            val block = burst.coerceIn(64, 1024)
            val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minBuffer, block * 4 * 4))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            trackStartFrame = synth.framePosition
            t.play()
            track = t
            running = true
            lastActivityNanos = System.nanoTime()
            Thread({ renderLoop(t, block) }, "muzykalka-audio-out").apply {
                priority = Thread.MAX_PRIORITY
                isDaemon = true
                start()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack unavailable", e)
            running = false
            false
        }
    }

    private fun renderLoop(t: AudioTrack, block: Int) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Exception) {
        }
        val buffer = FloatArray(block)
        try {
            while (running) {
                synth.render(buffer, block)
                val written = t.write(buffer, 0, block, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
                if (synth.isIdle && System.nanoTime() - lastActivityNanos > IDLE_STOP_NS) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "render loop ended", e)
        } finally {
            synchronized(lock) {
                running = false
                track = null
            }
            runCatching { t.stop() }
            runCatching { t.release() }
        }
    }

    /** Synth frame that has reached the speaker. */
    private fun playedFrame(): Long {
        val t = track ?: return Long.MAX_VALUE
        val head = t.playbackHeadPosition.toLong() and 0xffffffffL
        return trackStartFrame + head
    }

    override suspend fun play(score: Score) {
        if (score.notes.isEmpty()) return
        if (!ensureRunning()) return
        lastActivityNanos = System.nanoTime()
        val start = synth.framePosition + LEAD_FRAMES
        val end = synth.schedule(score, start, a4Hz) + TAIL_FRAMES
        soundingUntilFrame = max(soundingUntilFrame, end)
        sounding.value = true
        try {
            val deadline = System.nanoTime() + ((end - start) * 1_000_000_000L / SAMPLE_RATE) + 2_000_000_000L
            while (playedFrame() < end && running && System.nanoTime() < deadline) delay(12)
        } catch (e: CancellationException) {
            synth.stopAll()
            soundingUntilFrame = 0
            markSilent()
            throw e
        }
        lastActivityNanos = System.nanoTime()
        if (playedFrame() >= soundingUntilFrame || !running) markSilent()
    }

    private fun markSilent() {
        sounding.value = false
        lastSoundEndNanos = System.nanoTime()
    }

    override fun stop() {
        synth.stopAll()
        soundingUntilFrame = 0
        if (sounding.value) markSilent()
    }

    private companion object {
        const val TAG = "AudioOutput"
        const val LEAD_FRAMES = 960L // 20 ms of scheduling headroom
        const val TAIL_FRAMES = 4_800L // 100 ms release tail
        const val IDLE_STOP_NS = 10_000_000_000L
    }
}
