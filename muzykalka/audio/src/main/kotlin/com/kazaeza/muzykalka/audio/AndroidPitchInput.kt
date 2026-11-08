package com.kazaeza.muzykalka.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.kazaeza.muzykalka.dsp.McLeodPitchDetector
import com.kazaeza.muzykalka.dsp.NoteEvent
import com.kazaeza.muzykalka.dsp.NoteSegmenterConfig
import com.kazaeza.muzykalka.dsp.PitchFrame
import com.kazaeza.muzykalka.dsp.SAMPLE_RATE
import com.kazaeza.muzykalka.dsp.StableNoteSegmenter
import com.kazaeza.muzykalka.dsp.rmsDb
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Ticket M-06. AudioRecord → 2048-sample window, hop 512 → MPM → frames → segmenter → notes.
 * Frames are timestamped at the window centre on the System.nanoTime() clock.
 */
class AndroidPitchInput(private val context: Context) : PitchInput {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    private val framesFlow = MutableSharedFlow<PitchFrame>(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val notesFlow = MutableSharedFlow<NoteEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val running = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    override val frames: SharedFlow<PitchFrame> = framesFlow.asSharedFlow()
    override val notes: SharedFlow<NoteEvent> = notesFlow.asSharedFlow()
    override val isRunning: StateFlow<Boolean> = running.asStateFlow()
    override val error: StateFlow<String?> = errorFlow.asStateFlow()

    @Volatile
    private var active = false
    private var thread: Thread? = null
    private var currentDevice: Int? = null
    private var currentA4 = 0.0
    private var currentHold = 0

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun start(deviceId: Int?, a4Hz: Double, minHoldMs: Int) {
        if (active && deviceId == currentDevice && a4Hz == currentA4 && minHoldMs == currentHold) return
        if (active) stopInternal()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            errorFlow.value = "Нет доступа к микрофону"
            return
        }
        val record = try {
            createRecord(deviceId)
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord failed", e)
            null
        }
        if (record == null) {
            errorFlow.value = "Микрофон занят или недоступен"
            return
        }
        errorFlow.value = null
        currentDevice = deviceId
        currentA4 = a4Hz
        currentHold = minHoldMs
        active = true
        running.value = true
        thread = Thread({ loop(record, a4Hz, minHoldMs) }, "muzykalka-audio-in").apply {
            isDaemon = true
            start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun createRecord(deviceId: Int?): AudioRecord? {
        val unprocessed = audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val record = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBuffer, WINDOW * 4 * 2))
            .build()
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        if (deviceId != null) {
            audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)?.firstOrNull { it.id == deviceId }?.let(record::setPreferredDevice)
        }
        return record
    }

    private fun loop(record: AudioRecord, a4Hz: Double, minHoldMs: Int) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Exception) {
        }
        val detector = McLeodPitchDetector(SAMPLE_RATE, WINDOW)
        val segmenter = StableNoteSegmenter(NoteSegmenterConfig(minHoldMs = minHoldMs, a4Hz = a4Hz))
        val window = FloatArray(WINDOW)
        val hop = FloatArray(HOP)
        var filled = 0
        try {
            record.startRecording()
            while (active) {
                var got = 0
                while (got < HOP && active) {
                    val n = record.read(hop, got, HOP - got, AudioRecord.READ_BLOCKING)
                    if (n < 0) {
                        active = false
                        break
                    }
                    got += n
                }
                if (!active) break
                System.arraycopy(window, HOP, window, 0, WINDOW - HOP)
                System.arraycopy(hop, 0, window, WINDOW - HOP, HOP)
                filled = minOf(WINDOW, filled + HOP)
                if (filled < WINDOW) continue
                val frame = PitchFrame(
                    timeNanos = System.nanoTime() - HALF_WINDOW_NS,
                    estimate = detector.detect(window, 0),
                    rmsDb = rmsDb(window, 0, WINDOW),
                )
                framesFlow.tryEmit(frame)
                for (event in segmenter.accept(frame)) notesFlow.tryEmit(event)
            }
        } catch (e: Exception) {
            Log.w(TAG, "input loop ended", e)
            errorFlow.value = "Микрофон остановлен"
        } finally {
            runCatching { record.stop() }
            record.release()
            running.value = false
        }
    }

    private fun stopInternal() {
        active = false
        thread?.join(500)
        thread = null
        running.value = false
    }

    @Synchronized
    override fun stop() = stopInternal()

    private companion object {
        const val TAG = "PitchInput"
        const val WINDOW = 2048
        const val HOP = 512
        const val HALF_WINDOW_NS = WINDOW / 2 * 1_000_000_000L / SAMPLE_RATE
    }
}
