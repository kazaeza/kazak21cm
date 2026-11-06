package com.kazaeza.muzykalka.dsp

import com.kazaeza.muzykalka.music.DEFAULT_A4_HZ
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.Voice
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time synthesiser driven by the output thread. All voices are synthesised (no samples):
 * GUITAR/BASS = extended Karplus–Strong, PIANO = 2-operator FM e-piano, drums = noise/sine
 * models, CLICK = short sine burst.
 *
 * Threading contract: [schedule] and [stopAll] may be called from any thread; [render] only from
 * the audio thread; [render] does not lock and allocates nothing in steady state.
 */
interface Synth {
    val sampleRate: Int

    /** Schedules [score] to start at absolute output frame [startFrame]; returns its end frame. */
    fun schedule(score: Score, startFrame: Long, a4Hz: Double = DEFAULT_A4_HZ): Long

    /** Renders the next [frames] mono samples into [out] starting at index 0. */
    fun render(out: FloatArray, frames: Int)

    /** Fades everything out within ~10 ms and drops everything scheduled. */
    fun stopAll()

    /** Absolute index of the next frame [render] will produce. */
    val framePosition: Long

    /** True when nothing is sounding and nothing is scheduled (updated by [render]). */
    val isIdle: Boolean
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

class DefaultSynth(
    override val sampleRate: Int = SAMPLE_RATE,
    polyphony: Int = 24,
    private val masterGain: Float = 0.42f,
) : Synth {

    private class Event(val startFrame: Long, val endFrame: Long, val voice: Voice, val hz: Double, val velocity: Float)

    private object StopAll

    private val commands = ConcurrentLinkedQueue<Any>()
    private val pending = ArrayList<Event>(2048)
    private var pendingIndex = 0
    private val voices = Array(polyphony) { SynthVoice(sampleRate) }

    @Volatile
    private var position = 0L

    @Volatile
    private var idleFlag = true

    override val framePosition: Long get() = position
    override val isIdle: Boolean get() = idleFlag && commands.isEmpty()

    override fun schedule(score: Score, startFrame: Long, a4Hz: Double): Long {
        val framesPerBeat = sampleRate * 60.0 / score.tempoBpm
        val events = score.notes
            .map { n ->
                Event(
                    startFrame = startFrame + (n.startBeat * framesPerBeat).toLong(),
                    endFrame = startFrame + (n.endBeat * framesPerBeat).toLong(),
                    voice = n.voice,
                    hz = n.note?.frequency(a4Hz) ?: 0.0,
                    velocity = n.velocity,
                )
            }
            .sortedBy { it.startFrame }
        if (events.isNotEmpty()) {
            idleFlag = false
            commands.add(events)
        }
        return startFrame + (score.lengthBeats * framesPerBeat).toLong()
    }

    override fun stopAll() {
        commands.add(StopAll)
    }

    override fun render(out: FloatArray, frames: Int) {
        drainCommands()
        val base = position
        for (i in 0 until frames) {
            val now = base + i
            while (pendingIndex < pending.size && pending[pendingIndex].startFrame <= now) {
                startVoice(pending[pendingIndex], now)
                pendingIndex++
            }
            var sum = 0.0
            for (v in voices) if (v.active) sum += v.next(now)
            out[i] = tanh(sum * masterGain).toFloat()
        }
        if (pendingIndex > 256) {
            pending.subList(0, pendingIndex).clear()
            pendingIndex = 0
        }
        position = base + frames
        var anyActive = false
        for (v in voices) if (v.active) anyActive = true
        idleFlag = !anyActive && pendingIndex >= pending.size
    }

    @Suppress("UNCHECKED_CAST")
    private fun drainCommands() {
        while (true) {
            val cmd = commands.poll() ?: return
            if (cmd === StopAll) {
                pending.clear()
                pendingIndex = 0
                for (v in voices) if (v.active) v.fastRelease(position)
            } else {
                for (e in cmd as List<Event>) insertSorted(e)
            }
        }
    }

    private fun insertSorted(e: Event) {
        pending.add(e)
        var i = pending.size - 1
        while (i > pendingIndex && pending[i - 1].startFrame > e.startFrame) {
            pending[i] = pending[i - 1]
            i--
        }
        pending[i] = e
    }

    private fun startVoice(e: Event, now: Long) {
        var chosen: SynthVoice? = null
        for (v in voices) if (!v.active) { chosen = v; break }
        if (chosen == null) {
            // Steal the oldest voice.
            chosen = voices.minBy { it.startedAt }
        }
        chosen.start(e.voice, e.hz, e.velocity, now, e.endFrame)
    }
}

/**
 * One polyphonic slot that can play any [Voice]. All buffers are preallocated so starting a note
 * on the audio thread allocates nothing.
 */
private class SynthVoice(private val sr: Int) {
    var active = false
    var startedAt = 0L
    private var kind = Voice.PIANO
    private var releaseAt = 0L
    private var released = false
    private var releaseStart = 0L
    private var velocity = 1f

    // --- Karplus–Strong (GUITAR, BASS)
    private val delay = FloatArray(sr / 20 + 8)
    private var dLen = 1
    private var dIdx = 0
    private var prev = 0f
    private var apC = 0.0
    private var apX1 = 0.0
    private var apY1 = 0.0
    private var loopGain = 0.999
    private var releaseGain = 0.99
    private var lpState = 0.0
    private var lpCoef = 1.0

    // --- FM / oscillators
    private var hz = 440.0
    private var phase = 0.0
    private var modPhase = 0.0
    private var tinePhase = 0.0

    // --- noise
    private var noiseState = 0x2545F491

    fun start(voice: Voice, frequency: Double, vel: Float, now: Long, end: Long) {
        active = true
        kind = voice
        hz = frequency
        velocity = vel
        startedAt = now
        releaseAt = max(end, now + 1)
        released = false
        phase = 0.0
        modPhase = 0.0
        tinePhase = 0.0
        lpState = 0.0
        noiseState = (now.toInt() * 1103515245 + 12345) or 1
        when (voice) {
            Voice.GUITAR -> startString(t60 = (4.5 - 3.0 * min(1.0, hz / 700.0)), brightness = 0.85, outputLp = 1.0)
            Voice.BASS -> startString(t60 = 3.5, brightness = 0.35, outputLp = 0.12)
            else -> Unit
        }
    }

    fun fastRelease(now: Long) {
        if (!released) {
            released = true
            releaseStart = now
        }
        releaseAt = min(releaseAt, now)
        releaseGain = 0.995
    }

    private fun startString(t60: Double, brightness: Double, outputLp: Double) {
        val loop = sr / hz - 0.5 // the 2-point average adds half a sample
        var n = floor(loop).toInt()
        var frac = loop - n
        if (frac < 0.1) {
            n -= 1
            frac += 1.0
        }
        dLen = n.coerceIn(2, delay.size - 1)
        apC = (1.0 - frac) / (1.0 + frac)
        apX1 = 0.0
        apY1 = 0.0
        prev = 0f
        dIdx = 0
        loopGain = 0.001.pow(1.0 / (hz * t60))
        releaseGain = 0.001.pow(1.0 / (hz * 0.12))
        lpCoef = outputLp
        // Excitation: noise, low-passed by [brightness], DC removed, plucked ~1/7 along the string.
        var lp = 0.0
        var mean = 0.0
        for (i in 0 until dLen) {
            lp += brightness * (nextNoise() - lp)
            delay[i] = lp.toFloat()
            mean += lp
        }
        mean /= dLen
        val pick = max(1, dLen / 7)
        var peak = 1e-9
        for (i in dLen - 1 downTo pick) {
            delay[i] = (delay[i] - mean - (delay[i - pick] - mean)).toFloat()
        }
        for (i in 0 until dLen) peak = max(peak, kotlin.math.abs(delay[i].toDouble()))
        val scale = (0.9 * velocity / peak).toFloat()
        for (i in 0 until dLen) delay[i] *= scale
    }

    private fun nextNoise(): Double {
        var x = noiseState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noiseState = x
        return x / 2147483648.0
    }

    fun next(now: Long): Double {
        if (!released && now >= releaseAt) {
            released = true
            releaseStart = now
        }
        val t = (now - startedAt).toDouble() / sr
        val y = when (kind) {
            Voice.GUITAR, Voice.BASS -> string()
            Voice.PIANO -> ePiano(t, now)
            Voice.KICK -> kick(t)
            Voice.SNARE -> snare(t)
            Voice.HAT -> hat(t)
            Voice.CLICK -> click(t)
        }
        if (t > 8.0 || (released && (now - releaseStart) > sr * 0.6) || (kind.isDrum() && t > 0.7)) active = false
        return y
    }

    private fun Voice.isDrum() = this == Voice.KICK || this == Voice.SNARE || this == Voice.HAT || this == Voice.CLICK

    private fun string(): Double {
        val out = delay[dIdx]
        val avg = 0.5 * (out + prev)
        prev = out
        val g = if (released) releaseGain else loopGain
        val lossy = avg * g
        val ap = apC * lossy + apX1 - apC * apY1
        apX1 = lossy
        apY1 = ap
        delay[dIdx] = ap.toFloat()
        dIdx++
        if (dIdx >= dLen) dIdx = 0
        lpState += lpCoef * (out - lpState)
        return lpState * (if (lpCoef < 1.0) 1.8 else 1.0)
    }

    private fun ePiano(t: Double, now: Long): Double {
        val attack = min(1.0, t / 0.003)
        var env = attack * exp(-t / 1.6)
        if (released) env *= exp(-(now - releaseStart).toDouble() / sr / 0.12)
        val index = 1.8 * velocity * exp(-t / 0.5) + 0.25
        modPhase += hz / sr
        phase += hz / sr
        if (modPhase >= 1.0) modPhase -= 1.0
        if (phase >= 1.0) phase -= 1.0
        var y = sin(2 * PI * phase + index * sin(2 * PI * modPhase))
        val tineHz = hz * 7.0
        if (tineHz < sr * 0.45) {
            tinePhase += tineHz / sr
            if (tinePhase >= 1.0) tinePhase -= 1.0
            y += 0.12 * exp(-t / 0.05) * sin(2 * PI * tinePhase)
        }
        return y * env * velocity * 0.8
    }

    private fun kick(t: Double): Double {
        val f = 48.0 + 110.0 * exp(-t / 0.035)
        phase += f / sr
        val body = sin(2 * PI * phase) * exp(-t / 0.28)
        val click = if (t < 0.003) nextNoise() * 0.4 else 0.0
        return (body + click) * velocity * 1.2
    }

    private fun snare(t: Double): Double {
        phase += 185.0 / sr
        val tone = sin(2 * PI * phase) * exp(-t / 0.07) * 0.5
        val n = nextNoise()
        val hp = n - lpState
        lpState = n
        val noise = hp * exp(-t / 0.13) * 0.45
        return (tone + noise) * velocity
    }

    private fun hat(t: Double): Double {
        val n = nextNoise()
        val hp = n - lpState
        lpState = n
        return hp * exp(-t / 0.035) * 0.3 * velocity
    }

    private fun click(t: Double): Double {
        if (t > 0.03) return 0.0
        phase += 1600.0 / sr
        return sin(2 * PI * phase) * exp(-t / 0.008) * 0.7 * velocity
    }
}
