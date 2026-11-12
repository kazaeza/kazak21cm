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
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.ScoreNote
import com.kazaeza.muzykalka.music.TapSound
import com.kazaeza.muzykalka.music.ToneTimbre
import com.kazaeza.muzykalka.music.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Ticket M-05. One AudioTrack, created on first use, stopped after [IDLE_STOP_NS] of silence to
 * save battery. The render thread runs at URGENT_AUDIO priority and pulls [Synth.renderStereo] (O-39: two channels,
 * the glass instrument has two ears). Making the tables of the glass is tens of milliseconds a table, so it is done
 * here off the callers' threads: [drone] on its own thread, [play] on a pool, [prewarm] in the background.
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

    override var toneTimbre: ToneTimbre
        get() = synth.toneTimbre
        set(value) {
            val ownedDrone = synth.toneTimbre.ownsDrone
            synth.toneTimbre = value
            // the glass has a drone of its own: one that sounds now changes its instrument with the timbre
            if (value.ownsDrone != ownedDrone) droneNotes?.let { drone(it) }
        }

    override var room: Boolean
        get() = synth.room
        set(value) {
            synth.room = value
        }

    @Volatile
    private var keepOpen = false

    /** The drone as asked for, so that a change of instrument (the glass has a drone of its own) can hand it over. */
    @Volatile
    private var droneNotes: List<MidiNote>? = null

    /** Every change of the drone goes through this one thread, in order: [drone] and [stop] must not overtake each other. */
    private val droneWorker: ExecutorService = worker("muzykalka-drone", background = false)

    /** [prewarm] makes the tables of the glass behind everything else. */
    private val warmer: ExecutorService = worker("muzykalka-warm", background = true)

    private fun ensureRunning(): Boolean = synchronized(lock) {
        if (running) return true
        try {
            val burst = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
            val block = burst.coerceIn(64, 1024)
            val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
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
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minBuffer, block * CHANNELS * 4 * 4))
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
        val buffer = FloatArray(block * CHANNELS)
        try {
            while (running) {
                synth.renderStereo(buffer, block)
                val written = t.write(buffer, 0, block * CHANNELS, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
                if (synth.isIdle && !keepOpen && System.nanoTime() - lastActivityNanos > IDLE_STOP_NS) break
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
        // the glass reads a table of its own for each note, and the first time making one takes tens of milliseconds
        withContext(Dispatchers.Default) { synth.prepare(score, a4Hz) }
        val start = synth.framePosition + LEAD_FRAMES
        // a bar, a string or the glass in its hall rings on after its note: the microphone waits for the ring too
        val ring = if (score.notes.any { it.voice == Voice.TONE }) synth.toneTimbre.ringMs * SAMPLE_RATE / 1000L else 0L
        val end = synth.schedule(score, start, a4Hz) + max(TAIL_FRAMES, ring)
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
        droneNotes = null
        droneWorker.execute { synth.setDrone(emptyList()) }
        soundingUntilFrame = 0
        if (sounding.value) markSilent()
    }

    override fun tap(sound: TapSound, low: Boolean, gain: Float) {
        if (gain <= 0f || !ensureRunning()) return
        lastActivityNanos = System.nanoTime()
        synth.tap(sound, low, gain)
    }

    override fun keepOpen(on: Boolean) {
        keepOpen = on
        if (on) ensureRunning()
        lastActivityNanos = System.nanoTime()
    }

    override fun drone(notes: List<MidiNote>?) {
        val hz = notes.orEmpty().map { it.frequency(a4Hz) }
        if (hz.isNotEmpty() && !ensureRunning()) return
        lastActivityNanos = System.nanoTime()
        droneNotes = notes?.takeIf { it.isNotEmpty() }
        // the tables of the glass drone are made in setDrone, and the caller is the UI thread
        droneWorker.execute { synth.setDrone(hz) }
    }

    override fun prewarm(drones: List<List<MidiNote>>, notes: List<MidiNote>) {
        if (!synth.toneTimbre.ownsDrone) return
        val a4 = a4Hz
        warmer.execute {
            try {
                for (d in drones) synth.prepareDrone(d.map { it.frequency(a4) })
                val score = Score(60.0, notes.mapIndexed { i, n -> ScoreNote(i.toDouble(), i + 1.0, Voice.TONE, n, 0.8f) })
                synth.prepare(score, a4)
            } catch (e: Exception) {
                Log.w(TAG, "prewarm ended", e)
            }
        }
    }

    private fun worker(name: String, background: Boolean): ExecutorService = Executors.newSingleThreadExecutor { job ->
        Thread(
            {
                if (background) runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
                job.run()
            },
            name,
        ).apply { isDaemon = true }
    }

    private companion object {
        const val TAG = "AudioOutput"
        const val LEAD_FRAMES = 960L // 20 ms of scheduling headroom
        const val TAIL_FRAMES = 4_800L // 100 ms release tail
        const val CHANNELS = 2 // O-39: the glass has two ears; everything else is in the middle
        const val IDLE_STOP_NS = 10_000_000_000L
    }
}
