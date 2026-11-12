package com.kazaeza.muzykalka.dsp

import com.kazaeza.muzykalka.music.DEFAULT_A4_HZ
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.TapSound
import com.kazaeza.muzykalka.music.ToneTimbre
import com.kazaeza.muzykalka.music.Voice
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time synthesiser driven by the output thread. All voices are synthesised (no samples):
 * GUITAR/BASS = extended Karplus–Strong, PIANO = 2-operator FM e-piano, TONE = the note over the drone
 * (its timbre is [toneTimbre]: the soft additive CLASSIC voice, or marimba / kalimba / felt piano / hum, O-36),
 * drums = noise/sine models, CLICK = short sine burst. Beside the scored voices runs a [setDrone] bank: a
 * sustained wavetable drone that outlives [stopAll]; and [tap], a tiny wooden sound for buttons.
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

    /** Fades everything scheduled out within ~10 ms and drops it. The drone keeps sounding. */
    fun stopAll()

    /**
     * Sustained drone on [frequencies] (Hz). A new set crossfades from the old one (~0.4 s);
     * an empty list fades the drone out. Callable from any thread.
     */
    fun setDrone(frequencies: List<Double>)

    /** O-36: how a [Voice.TONE] note sounds. Read when the note starts, so a change touches the next note. */
    var toneTimbre: ToneTimbre

    /**
     * O-36: a small room around notes and taps (the drone stays dry). Turning it on adds a tail of ~0.7 s to
     * everything that sounds; off, it costs nothing.
     */
    var room: Boolean

    /**
     * O-36: one wooden tap, from the first frame [render] produces next. [low] is the second voice (back, cancel);
     * [gain] 0…2 is the volume setting. Callable from any thread; not a note — the caller must not count it as
     * the app «sounding» (a tap must not shut the microphone).
     */
    fun tap(sound: TapSound, low: Boolean = false, gain: Float = 1f)

    /** Absolute index of the next frame [render] will produce. */
    val framePosition: Long

    /** True when nothing is sounding, nothing is scheduled and the drone is silent (updated by [render]). */
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

    private class DroneCommand(val frequencies: List<Double>)

    private class TapCommand(val sound: TapSound, val low: Boolean, val gain: Float)

    private val drone = DroneBank(sampleRate)

    private val commands = ConcurrentLinkedQueue<Any>()
    private val pending = ArrayList<Event>(2048)
    private var pendingIndex = 0
    private val voices = Array(polyphony) { SynthVoice(sampleRate) }

    @Volatile
    private var position = 0L

    @Volatile
    private var idleFlag = true

    @Volatile
    override var toneTimbre: ToneTimbre = ToneTimbre.CLASSIC

    @Volatile
    override var room: Boolean = false

    private val smallRoom = SmallRoom(sampleRate)
    private val roomTailFrames = (SmallRoom.TAIL_SECONDS * sampleRate).toInt()
    private var roomLeft = 0
    private var roomWasOn = false

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

    override fun tap(sound: TapSound, low: Boolean, gain: Float) {
        idleFlag = false
        commands.add(TapCommand(sound, low, gain.coerceIn(0f, 2f)))
    }

    override fun setDrone(frequencies: List<Double>) {
        if (frequencies.isNotEmpty()) idleFlag = false
        commands.add(DroneCommand(frequencies))
    }

    override fun render(out: FloatArray, frames: Int) {
        drainCommands()
        val base = position
        val roomOn = room
        if (roomOn && !roomWasOn) {
            smallRoom.reset()
            roomLeft = 0
        }
        roomWasOn = roomOn
        for (i in 0 until frames) {
            val now = base + i
            while (pendingIndex < pending.size && pending[pendingIndex].startFrame <= now) {
                startVoice(pending[pendingIndex], now)
                pendingIndex++
            }
            var played = 0.0
            var anyPlaying = false
            for (v in voices) {
                if (v.active) {
                    played += v.next(now)
                    anyPlaying = true
                }
            }
            var sum = drone.next() + played
            if (roomOn) {
                // the room hears the notes and taps, not the drone, and rings on after the last of them
                if (anyPlaying) roomLeft = roomTailFrames
                if (roomLeft > 0) {
                    sum += SmallRoom.WET * smallRoom.process(played)
                    if (!anyPlaying && --roomLeft == 0) smallRoom.reset()
                }
            }
            out[i] = tanh(sum * masterGain).toFloat()
        }
        if (pendingIndex > 256) {
            pending.subList(0, pendingIndex).clear()
            pendingIndex = 0
        }
        position = base + frames
        var anyActive = false
        for (v in voices) if (v.active) anyActive = true
        idleFlag = !anyActive && pendingIndex >= pending.size && drone.silent && roomLeft == 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun drainCommands() {
        while (true) {
            val cmd = commands.poll() ?: return
            if (cmd === StopAll) {
                pending.clear()
                pendingIndex = 0
                for (v in voices) if (v.active) v.fastRelease(position)
            } else if (cmd is DroneCommand) {
                drone.set(cmd.frequencies)
            } else if (cmd is TapCommand) {
                startTap(cmd, position)
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
        chosen.start(e.voice, e.hz, e.velocity, now, e.endFrame, toneTimbre)
    }

    private fun startTap(c: TapCommand, now: Long) {
        var chosen: SynthVoice? = null
        for (v in voices) if (!v.active) { chosen = v; break }
        if (chosen == null) chosen = voices.minBy { it.startedAt }
        chosen.startTap(c.sound, c.low, c.gain, now)
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

    // --- O-36: timbres of the note over the drone, and the wooden taps
    private val rng = Noise()
    private val bank = ResonatorBank(sr, rng)
    private val hum = HumVoice(sr, rng)
    private var timbre = ToneTimbre.CLASSIC
    private var isTap = false

    fun start(voice: Voice, frequency: Double, vel: Float, now: Long, end: Long, toneTimbre: ToneTimbre = ToneTimbre.CLASSIC) {
        active = true
        isTap = false
        timbre = if (voice == Voice.TONE) toneTimbre else ToneTimbre.CLASSIC
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
            Voice.TONE -> startTone(frequency, vel)
            else -> Unit
        }
    }

    private fun startTone(frequency: Double, vel: Float) {
        rng.seed(noiseState)
        when (timbre) {
            ToneTimbre.CLASSIC -> Unit
            ToneTimbre.MARIMBA -> Timbres.marimba(bank, rng, frequency, vel)
            ToneTimbre.KALIMBA -> Timbres.kalimba(bank, rng, frequency, vel)
            ToneTimbre.FELT -> Timbres.felt(bank, rng, frequency, vel)
            ToneTimbre.HUM -> hum.start(frequency, vel)
        }
    }

    /** O-36: a wooden tap; it lives in a voice of the same pool, and is never released by time, only by dying away. */
    fun startTap(sound: TapSound, low: Boolean, gain: Float, now: Long) {
        active = true
        isTap = true
        timbre = ToneTimbre.CLASSIC
        kind = Voice.CLICK
        velocity = 1f
        startedAt = now
        releaseAt = Long.MAX_VALUE
        released = false
        noiseState = (now.toInt() * 1103515245 + 12345) or 1
        rng.seed(noiseState)
        Timbres.tap(bank, rng, sound, low, gain.toDouble())
    }

    fun fastRelease(now: Long) {
        if (!released) {
            released = true
            releaseStart = now
        }
        releaseAt = min(releaseAt, now)
        releaseGain = 0.995
        bank.release(fast = true)
        hum.fastRelease()
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
            bank.release(fast = false)
        }
        val t = (now - startedAt).toDouble() / sr
        val y = if (isTap) {
            bank.next()
        } else {
            when (kind) {
                Voice.GUITAR, Voice.BASS -> string()
                Voice.PIANO -> ePiano(t, now)
                Voice.TONE -> when (timbre) {
                    ToneTimbre.CLASSIC -> tone(t, now)
                    ToneTimbre.HUM -> hum.next(t, released, (now - releaseStart).toDouble() / sr)
                    else -> bank.next()
                }
                Voice.KICK -> kick(t)
                Voice.SNARE -> snare(t)
                Voice.HAT -> hat(t)
                Voice.CLICK -> click(t)
            }
        }
        val over = if (isTap) t > Timbres.TAP_SECONDS else kind.isDrum() && t > 0.7
        // a hummed note closes more slowly (110 ms), so its voice is kept a little longer: no click at the cut
        val keep = if (kind == Voice.TONE && timbre == ToneTimbre.HUM) 0.9 else 0.6
        if (t > 8.0 || (released && (now - releaseStart) > sr * keep) || over) active = false
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

    /**
     * Soft, singable voice for the drone (ADR-017): fundamental + a little 2nd/3rd harmonic,
     * 30 ms attack, slow sag, vibrato that sets in after ~0.35 s like a held sung note.
     */
    private fun tone(t: Double, now: Long): Double {
        val attack = min(1.0, t / 0.03)
        var env = attack * (0.75 + 0.25 * exp(-t / 0.4)) * exp(-t / 6.0)
        if (released) env *= exp(-(now - releaseStart).toDouble() / sr / 0.09)
        val vibDepth = 0.0035 * min(1.0, max(0.0, (t - 0.35) / 0.4))
        val f = hz * (1.0 + vibDepth * sin(2 * PI * 5.2 * t))
        phase += f / sr
        if (phase >= 1.0) phase -= 1.0
        val p = 2 * PI * phase
        val y = sin(p) + 0.22 * sin(2 * p) + 0.08 * sin(3 * p)
        return y * env * velocity * 0.75
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

/**
 * The tonic drone (ADR-017): up to [SLOTS] sustained notes read from one band-limited wavetable
 * (a warm organ/tanpura-like spectrum), each as two oscillators detuned by ±0.12 % so the drone
 * slowly «breathes». Gains glide, so a key change is a crossfade, never a click.
 * Audio-thread only, except [set] which the synth calls from [DefaultSynth.render].
 */
internal class DroneBank(private val sr: Int) {
    private val hz = DoubleArray(SLOTS)
    private val gain = DoubleArray(SLOTS)
    private val target = DoubleArray(SLOTS)
    private val phaseA = DoubleArray(SLOTS)
    private val phaseB = DoubleArray(SLOTS)
    private val glide = 1.0 - exp(-1.0 / (sr * 0.16))

    val silent: Boolean
        get() {
            for (i in 0 until SLOTS) if (gain[i] > 1e-4 || target[i] > 0.0) return false
            return true
        }

    fun set(frequencies: List<Double>) {
        for (i in 0 until SLOTS) target[i] = 0.0
        val level = if (frequencies.isEmpty()) 0.0 else LEVEL / frequencies.size
        for (f in frequencies.take(SLOTS / 2)) {
            // Reuse a slot already on this pitch (no restart), else the quietest free one.
            var slot = (0 until SLOTS).firstOrNull { abs(hz[it] - f) < 0.01 && gain[it] > 1e-4 }
            if (slot == null) slot = (0 until SLOTS).filter { target[it] == 0.0 }.minByOrNull { gain[it] } ?: continue
            if (abs(hz[slot] - f) >= 0.01) {
                hz[slot] = f
                gain[slot] = 0.0
                phaseA[slot] = 0.0
                phaseB[slot] = 0.37
            }
            target[slot] = level
        }
    }

    fun next(): Double {
        var sum = 0.0
        for (i in 0 until SLOTS) {
            val g = gain[i] + (target[i] - gain[i]) * glide
            gain[i] = g
            if (g < 1e-5 && target[i] == 0.0) continue
            phaseA[i] += hz[i] * (1.0 + DETUNE) / sr
            phaseB[i] += hz[i] * (1.0 - DETUNE) / sr
            if (phaseA[i] >= 1.0) phaseA[i] -= 1.0
            if (phaseB[i] >= 1.0) phaseB[i] -= 1.0
            sum += g * 0.5 * (read(phaseA[i]) + read(phaseB[i]))
        }
        return sum
    }

    private fun read(phase: Double): Double {
        val x = phase * TABLE
        val i = x.toInt()
        val frac = x - i
        return WAVE[i] + (WAVE[i + 1] - WAVE[i]) * frac
    }

    companion object {
        const val SLOTS = 8
        private const val TABLE = 2048
        private const val DETUNE = 0.0012

        /** Peak drone level before the master gain: present, but under the questions. */
        private const val LEVEL = 0.55

        /** Harmonic amplitudes 1..10: dark and round, a touch of nasal 3rd/5th. */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.36, 0.2, 0.16, 0.08, 0.06, 0.035, 0.025, 0.015)

        private val WAVE: DoubleArray = run {
            val w = DoubleArray(TABLE + 1)
            for (i in 0..TABLE) {
                val p = 2 * PI * i / TABLE
                var y = 0.0
                for (h in HARMONICS.indices) y += HARMONICS[h] * sin((h + 1) * p)
                w[i] = y
            }
            val peak = w.maxOf { kotlin.math.abs(it) }
            for (i in w.indices) w[i] /= peak
            w
        }
    }
}
