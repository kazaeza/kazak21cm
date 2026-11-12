package com.kazaeza.muzykalka.music

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** What a pluck stands for on screen (O-35). Only [QUESTION] is forbidden to carry a pitch. */
enum class PulseKind {
    /** The tonic, the scale of a new key, an intro: home. */
    HOME,

    /** A question: no pitch at all, or the picture would give the answer away (ADR-015). */
    QUESTION,

    /** After the answer: a confirmation, a correction, a hint, an anchor. */
    REVEAL,

    /** A finger on the screen. */
    TOUCH,
}

/**
 * One pluck to draw. [pitch] is semitones above the tonic (0..11) and is null for a question. [x] is where
 * along the string it was plucked, 0..1. [atMs] counts from the start of the sound it belongs to.
 */
data class SoundPulse(
    val kind: PulseKind,
    val atMs: Long,
    val strength: Float,
    val pitch: Int? = null,
    val x: Float = 0.5f,
) {
    init {
        require(kind != PulseKind.QUESTION || pitch == null) { "a question pulse must not carry a pitch (ADR-015)" }
        require(pitch == null || pitch in 0..11) { "pitch out of 0..11" }
    }
}

/**
 * O-35: the arithmetic behind the live sphere and the strings. Pure functions; no pictures here, only
 * the numbers the pictures are drawn from.
 */
object Overtones {
    /** Where a question is plucked: always the same, so its look says nothing about the note. */
    const val QUESTION_X = 0.31f

    /** Just ratio p:q of a degree to the tonic, by semitones 0..11. */
    val RATIO: List<Pair<Int, Int>> =
        listOf(1 to 1, 16 to 15, 9 to 8, 6 to 5, 5 to 4, 4 to 3, 7 to 5, 3 to 2, 8 to 5, 5 to 3, 9 to 5, 15 to 8)

    /** The ratio of [semitones] above the tonic, folded into one octave. */
    fun ratio(semitones: Int): Pair<Int, Int> = RATIO[Math.floorMod(semitones, 12)]

    /**
     * 0 = rest (the tonic itself), 1 = the most restless (the minor second). The simpler the ratio, the sooner
     * the two waves meet again: ((p + q) − 2) / 29, clamped to 0..1.
     */
    fun tension(semitones: Int): Float {
        val (p, q) = ratio(semitones)
        return (((p + q) - 2) / 29f).coerceIn(0f, 1f)
    }

    /** Plucked string: the weight of the k-th harmonic when plucked at [x] (0..1) is sin(k·π·x) / k. */
    fun harmonicWeight(k: Int, x: Float): Float = (sin(k * PI * x) / k).toFloat()

    /**
     * Loudness of the k-th harmonic [tSec] seconds after the pluck: a quick rise, a soft fall that is the
     * faster the higher the harmonic (as on a real string), and a window that closes the tail at 2.5 s so a
     * pluck can be dropped there without a jump.
     */
    fun envelope(k: Int, tSec: Float): Float {
        if (tSec <= 0f) return 0f
        val rise = 1f - exp(-tSec / 0.012f)
        val fall = exp(-tSec * k / 1.4f)
        return rise * fall * tail(tSec)
    }

    /** A pluck is dead after this many seconds. */
    const val LIFE = 2.5f
    private const val TAIL_FROM = 1.2f

    /** The window that closes a pluck's tail: 1 until 1.2 s, then a smooth fall to 0 at [LIFE]. */
    fun tail(tSec: Float): Float {
        val s = ((tSec - TAIL_FROM) / (LIFE - TAIL_FROM)).coerceIn(0f, 1f)
        return 1f - s * s * (3f - 2f * s)
    }

    /** O-37: how much of a pluck goes into each slow mode of the drop, l = 2..5. They add up to 1. */
    val DROP_WEIGHTS: List<Float> = listOf(0.50f, 0.28f, 0.15f, 0.07f)

    /** Hz of the slowest mode (l = 2); the others follow a drop: sqrt(l(l−1)(l+2)/8) times this. */
    const val DROP_F2 = 0.9f

    /** Extra seconds of inner flow per second while a pluck pushes (times [kick]). */
    const val DROP_SURGE = 3.0f

    /** The l of mode [i]: 2..5. */
    private fun dropL(i: Int): Int = i + 2

    /** Frequency of mode [i] (l = i + 2) in Hz. */
    fun dropFrequency(i: Int): Float {
        val l = dropL(i)
        return DROP_F2 * sqrt(l * (l - 1) * (l + 2) / 8f)
    }

    /**
     * O-37: amplitude of mode [i] (l = i + 2) [tSec] after a pluck of strength 1. The higher the mode the faster
     * it dies, as on a string. 0 before the pluck and from [LIFE] on, so a pluck can be dropped there.
     */
    fun dropMode(i: Int, tSec: Float): Float {
        if (tSec <= 0f || tSec >= LIFE) return 0f
        val l = dropL(i)
        val rise = 1f - exp(-tSec / 0.012f)
        val fall = exp(-tSec * (0.9f + 0.5f * (l - 2)))
        return DROP_WEIGHTS[i] * rise * fall * tail(tSec) * cos(2.0 * PI * dropFrequency(i) * tSec).toFloat()
    }

    /** O-37: how hard a pluck of strength 1 pushes the drop [tSec] after it: a soft rise (50 ms), a slow fall. */
    fun kick(tSec: Float): Float {
        if (tSec <= 0f || tSec >= LIFE) return 0f
        return (1f - exp(-tSec / 0.05f)) * exp(-tSec * 2f) * tail(tSec)
    }

    /**
     * The notes of [score] as plucks: drums are skipped, notes that start together (a chord) are one pluck,
     * the stronger the more notes. A [PulseKind.QUESTION] carries no pitch; the others carry the semitones
     * above [tonic] (null if the tonic is unknown).
     */
    fun pulses(score: Score, kind: PulseKind, tonic: MidiNote?): List<SoundPulse> {
        val x = if (kind == PulseKind.HOME) 0.5f else QUESTION_X
        return score.notes
            .filter { it.note != null }
            .groupBy { Math.round(it.startBeat * 1000.0) }
            .toSortedMap()
            .values
            .map { group ->
                val first = group.first()
                val at = (first.startBeat * 60_000.0 / score.tempoBpm).toLong()
                val pitch = if (kind == PulseKind.QUESTION || tonic == null) null else Math.floorMod(group.minOf { it.note!!.value } - tonic.value, 12)
                SoundPulse(kind, at, minOf(1f, 0.55f + 0.15f * group.size), pitch, x)
            }
    }
}
