package com.kazaeza.muzykalka.music

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Standard concert pitch. Overridable per user in settings (A4 reference). */
const val DEFAULT_A4_HZ: Double = 440.0

internal fun floorMod12(value: Int): Int = Math.floorMod(value, 12)

/** Signed distance in semitones folded into -6..+5. */
internal fun signedMod12(value: Int): Int = floorMod12(value + 6) - 6

enum class Accidental(val semitones: Int, val symbol: String, val ruSuffix: String) {
    DOUBLE_FLAT(-2, "𝄫", "-дубль-бемоль"),
    FLAT(-1, "♭", "-бемоль"),
    NATURAL(0, "", ""),
    SHARP(1, "♯", "-диез"),
    DOUBLE_SHARP(2, "𝄪", "-дубль-диез");

    companion object {
        fun ofSemitones(semitones: Int): Accidental =
            entries.firstOrNull { it.semitones == semitones }
                ?: throw IllegalArgumentException("No accidental for $semitones semitones")
    }
}

/** Natural note letters. [solfegeRu] is the fixed-do Russian syllable used when naming notes "до-ре-ми". */
enum class Letter(val naturalPitchClass: Int, val solfegeRu: String) {
    C(0, "до"), D(2, "ре"), E(4, "ми"), F(5, "фа"), G(7, "соль"), A(9, "ля"), B(11, "си");

    /** Letter [steps] diatonic steps above this one (wraps around, may be negative). */
    fun shift(steps: Int): Letter = entries[Math.floorMod(ordinal + steps, 7)]
}

/** Absolute pitch class, C = 0 … B = 11. Used only where an absolute coordinate is unavoidable. */
@JvmInline
value class PitchClass private constructor(val value: Int) {
    operator fun plus(semitones: Int): PitchClass = of(value + semitones)

    /** Ascending interval in semitones (0..11) from [other] up to this pitch class. */
    operator fun minus(other: PitchClass): Int = floorMod12(value - other.value)

    override fun toString(): String = "PitchClass($value)"

    companion object {
        fun of(value: Int): PitchClass = PitchClass(floorMod12(value))
        val ALL: List<PitchClass> = (0..11).map(::of)
    }
}

/** A spelled note name without octave: F♯ and G♭ are different names of the same [pitchClass]. */
data class NoteName(val letter: Letter, val accidental: Accidental = Accidental.NATURAL) {
    val pitchClass: PitchClass get() = PitchClass.of(letter.naturalPitchClass + accidental.semitones)

    override fun toString(): String = letter.name + accidental.symbol

    companion object {
        /** Parses "C", "F#", "F♯", "Bb", "B♭", "Ebb", "C##". Case-insensitive letter. */
        fun parse(text: String): NoteName {
            val t = text.trim()
            require(t.isNotEmpty()) { "Empty note name" }
            val letter = Letter.valueOf(t.substring(0, 1).uppercase())
            val acc = when (t.substring(1)) {
                "" -> Accidental.NATURAL
                "#", "♯" -> Accidental.SHARP
                "b", "♭" -> Accidental.FLAT
                "##", "♯♯", "x", "𝄪" -> Accidental.DOUBLE_SHARP
                "bb", "♭♭", "𝄫" -> Accidental.DOUBLE_FLAT
                else -> throw IllegalArgumentException("Bad accidental in '$text'")
            }
            return NoteName(letter, acc)
        }
    }
}

/** MIDI note number; 60 = C4 (scientific pitch notation), 69 = A4. */
@JvmInline
value class MidiNote(val value: Int) : Comparable<MidiNote> {
    init {
        require(value in 0..127) { "MIDI note out of range: $value" }
    }

    val pitchClass: PitchClass get() = PitchClass.of(value)

    /** Scientific octave number: C4 = 60 → 4, B3 = 59 → 3. */
    val octave: Int get() = Math.floorDiv(value, 12) - 1

    fun frequency(a4Hz: Double = DEFAULT_A4_HZ): Double = a4Hz * 2.0.pow((value - 69) / 12.0)

    operator fun plus(semitones: Int): MidiNote = MidiNote(value + semitones)
    operator fun minus(semitones: Int): MidiNote = MidiNote(value - semitones)

    /** Signed interval in semitones from [other] to this. */
    infix fun semitonesFrom(other: MidiNote): Int = value - other.value

    override fun compareTo(other: MidiNote): Int = value.compareTo(other.value)

    override fun toString(): String = "MidiNote($value)"

    companion object {
        fun of(name: NoteName, octave: Int): MidiNote =
            // B♯3 is C4 and C♭4 is B3: octave belongs to the letter, accidentals may cross it.
            MidiNote((octave + 1) * 12 + name.letter.naturalPitchClass + name.accidental.semitones)

        /** Continuous MIDI number for a frequency (69.0 = A4). */
        fun fractional(hz: Double, a4Hz: Double = DEFAULT_A4_HZ): Double {
            require(hz > 0.0) { "Frequency must be positive: $hz" }
            return 69.0 + 12.0 * log2(hz / a4Hz)
        }

        /** Nearest note and the deviation from it in cents, in [-50, +50]. */
        fun nearest(hz: Double, a4Hz: Double = DEFAULT_A4_HZ): PitchReading {
            val f = fractional(hz, a4Hz)
            val n = f.roundToInt().coerceIn(0, 127)
            return PitchReading(MidiNote(n), (f - n) * 100.0)
        }
    }
}

/** A detected pitch snapped to the nearest note. [cents] > 0 means sharp. */
data class PitchReading(val note: MidiNote, val cents: Double)

/** Cents between two frequencies (positive when [hz] is above [referenceHz]). */
fun centsBetween(hz: Double, referenceHz: Double): Double = 1200.0 * log2(hz / referenceHz)
