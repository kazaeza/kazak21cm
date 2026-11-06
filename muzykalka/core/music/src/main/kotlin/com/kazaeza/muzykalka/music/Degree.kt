package com.kazaeza.muzykalka.music

/**
 * Scale degree relative to the tonic — THE name of a sound in this app (rule 1: degrees, not letters).
 *
 * Convention (fixed, never mode-dependent): a degree is measured against the MAJOR scale of the
 * tonic. In A minor, C is ♭3; in D dorian, B is 6 and C is ♭7; in E phrygian, F is ♭2.
 * One coordinate system for every key and every mode, so a formula learned once works everywhere.
 *
 * Two spellings of the same sound (♯4 / ♭5) are different [Degree]s with equal [semitones];
 * answer checking compares with [sameSoundAs].
 */
data class Degree(val number: Int, val accidental: Accidental = Accidental.NATURAL) {
    init {
        require(number in 1..7) { "Degree number must be 1..7, was $number" }
        require(accidental.semitones in -1..1) { "Only ♭, ♮, ♯ are allowed on degrees, was $accidental" }
    }

    /** Semitones above the tonic, 0..11. */
    val semitones: Int get() = floorMod12(MAJOR_STEPS[number - 1] + accidental.semitones)

    val isChromaticInMajor: Boolean get() = accidental != Accidental.NATURAL

    infix fun sameSoundAs(other: Degree): Boolean = semitones == other.semitones

    /** Short ASCII-free label: "1", "♭3", "♯4". UI formatting with user notation lives in [DegreeFormatter]. */
    override fun toString(): String = accidental.symbol + number

    companion object {
        internal val MAJOR_STEPS = intArrayOf(0, 2, 4, 5, 7, 9, 11)

        val ONE = Degree(1)
        val FLAT_TWO = Degree(2, Accidental.FLAT)
        val TWO = Degree(2)
        val FLAT_THREE = Degree(3, Accidental.FLAT)
        val THREE = Degree(3)
        val FOUR = Degree(4)
        val SHARP_FOUR = Degree(4, Accidental.SHARP)
        val FLAT_FIVE = Degree(5, Accidental.FLAT)
        val FIVE = Degree(5)
        val FLAT_SIX = Degree(6, Accidental.FLAT)
        val SIX = Degree(6)
        val FLAT_SEVEN = Degree(7, Accidental.FLAT)
        val SEVEN = Degree(7)

        /** The seven natural degrees 1..7. */
        val NATURALS: List<Degree> = (1..7).map { Degree(it) }

        /**
         * Canonical spelling of each of the 12 chromatic steps, the one the app shows by default:
         * 1 ♭2 2 ♭3 3 4 ♯4 5 ♭6 6 ♭7 7 (matches the doc: chromatic level = ♭2, ♭3, ♯4, ♭6, ♭7).
         */
        val CHROMATIC: List<Degree> = listOf(
            ONE, FLAT_TWO, TWO, FLAT_THREE, THREE, FOUR, SHARP_FOUR, FIVE, FLAT_SIX, SIX, FLAT_SEVEN, SEVEN,
        )

        fun canonical(semitones: Int): Degree = CHROMATIC[floorMod12(semitones)]

        /** Parses "1", "b3", "♭3", "#4", "♯4". */
        fun parse(text: String): Degree {
            val t = text.trim()
            require(t.isNotEmpty()) { "Empty degree" }
            val (acc, rest) = when (t.first()) {
                'b', '♭' -> Accidental.FLAT to t.substring(1)
                '#', '♯' -> Accidental.SHARP to t.substring(1)
                else -> Accidental.NATURAL to t
            }
            val n = rest.toIntOrNull() ?: throw IllegalArgumentException("Bad degree '$text'")
            return Degree(n, acc)
        }
    }
}

/**
 * A degree placed in a register: [octave] 0 is the octave from the tonic up to the 7th,
 * -1 is the octave below the tonic, +1 the next one. Melodies, riffs and phrases are lists of these.
 */
data class DegreeNote(val degree: Degree, val octave: Int = 0) {
    /** Signed semitones from the reference tonic (tonic of octave 0). */
    val semitonesFromTonic: Int get() = degree.semitones + 12 * octave

    override fun toString(): String = when {
        octave > 0 -> "$degree" + "'".repeat(octave)
        octave < 0 -> "$degree" + ",".repeat(-octave)
        else -> "$degree"
    }

    companion object {
        /** Parses "5", "♭7,", "1'", "3''" ("'" = octave up, "," = octave down). */
        fun parse(text: String): DegreeNote {
            val t = text.trim()
            val ups = t.count { it == '\'' }
            val downs = t.count { it == ',' }
            return DegreeNote(Degree.parse(t.trimEnd('\'', ',')), ups - downs)
        }

        /** Parses a space-separated line: "3 3 4 5 5 4 3 2 1". */
        fun parseLine(text: String): List<DegreeNote> =
            text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::parse)
    }
}
