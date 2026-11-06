package com.kazaeza.muzykalka.music

/**
 * Chord qualities. [intervals] are semitones above the root (may exceed 12 for extensions).
 * [lowerCaseRoman] decides the numeral case (ii, vii°); [degreeSuffix] is appended to a degree
 * name (V⁷, iiø⁷, Imaj⁷); [absoluteSuffix] is used ONLY in the full-sheet hint (Em7, F♯m7♭5).
 */
enum class ChordQuality(
    val intervals: List<Int>,
    val lowerCaseRoman: Boolean,
    val degreeSuffix: String,
    val absoluteSuffix: String,
) {
    MAJOR(listOf(0, 4, 7), false, "", ""),
    MINOR(listOf(0, 3, 7), true, "", "m"),
    DIMINISHED(listOf(0, 3, 6), true, "°", "dim"),
    AUGMENTED(listOf(0, 4, 8), false, "+", "aug"),
    SUS2(listOf(0, 2, 7), false, "sus²", "sus2"),
    SUS4(listOf(0, 5, 7), false, "sus⁴", "sus4"),
    POWER(listOf(0, 7), false, "⁵", "5"),
    MAJOR6(listOf(0, 4, 7, 9), false, "⁶", "6"),
    MINOR6(listOf(0, 3, 7, 9), true, "⁶", "m6"),
    DOMINANT7(listOf(0, 4, 7, 10), false, "⁷", "7"),
    MAJOR7(listOf(0, 4, 7, 11), false, "maj⁷", "maj7"),
    MINOR7(listOf(0, 3, 7, 10), true, "⁷", "m7"),
    MINOR_MAJOR7(listOf(0, 3, 7, 11), true, "maj⁷", "m(maj7)"),
    HALF_DIMINISHED7(listOf(0, 3, 6, 10), true, "ø⁷", "m7♭5"),
    DIMINISHED7(listOf(0, 3, 6, 9), true, "°⁷", "dim7"),
    DOMINANT7_SUS4(listOf(0, 5, 7, 10), false, "⁷sus⁴", "7sus4"),
    ADD9(listOf(0, 4, 7, 14), false, "add⁹", "add9"),
    DOMINANT9(listOf(0, 4, 7, 10, 14), false, "⁹", "9"),
    MAJOR9(listOf(0, 4, 7, 11, 14), false, "maj⁹", "maj9"),
    MINOR9(listOf(0, 3, 7, 10, 14), true, "⁹", "m9");

    /** Pitch-class set relative to the root (0..11), for recognising a stacked chord. */
    val pitchClassSet: Set<Int> get() = intervals.map(::floorMod12).toSet()

    companion object {
        /** Quality whose pitch-class set equals [set], or null. Prefers simpler qualities (declaration order). */
        fun fromPitchClassSet(set: Set<Int>): ChordQuality? = entries.firstOrNull { it.pitchClassSet == set }
    }
}

/**
 * A chord named by its root DEGREE: ii⁷, V⁷, ♭VII, i. This is how songs are stored and shown.
 * [bass] is an optional slash bass, also a degree (I/3 = first chord over the 3rd).
 */
data class ChordDegree(val root: Degree, val quality: ChordQuality, val bass: Degree? = null) {

    /** Chord tones as semitones above the TONIC (not the root), ascending from the root. */
    fun semitonesFromTonic(): List<Int> = quality.intervals.map { root.semitones + it }

    /** Absolute chord symbol in [key] — allowed only on the full-sheet hint step (rule 1). */
    fun absoluteSymbol(key: Key): String {
        val base = key.spell(root).toString() + quality.absoluteSuffix
        return if (bass == null) base else base + "/" + key.spell(bass)
    }

    override fun toString(): String = ChordParser.formatRoman(this)

    companion object {
        fun parse(text: String): ChordDegree = ChordParser.parse(text)
    }
}

/**
 * Parser/formatter for the two text notations used in seeds, import and debug:
 *  - Roman:  I  ii  iii  IV  V7  vi  vii°  ♭VII  bIII  iiø7  ii7  Imaj7  V7sus4  I/3
 *  - Arabic (Nashville-like): 1  2m  2m7  4  57  ♭7  1maj7  — root is exactly ONE digit, the rest
 *    is the absolute-style suffix, so "57" = 5 with suffix "7" (dominant seventh on the 5th).
 * Superscript digits (⁷ ⁹ ⁶ ⁵ ² ⁴) are accepted everywhere and normalised to ASCII.
 */
object ChordParser {
    private val SUPERSCRIPTS = mapOf(
        '⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4',
        '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9',
    )
    private val ROMAN = listOf("vii", "iii", "vi", "iv", "ii", "v", "i")
    private val ROMAN_VALUE = mapOf("i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5, "vi" to 6, "vii" to 7)

    fun parse(text: String): ChordDegree {
        val src = text.trim().map { SUPERSCRIPTS[it] ?: it }.joinToString("")
        require(src.isNotEmpty()) { "Empty chord" }
        val slash = src.indexOf('/')
        val main = if (slash >= 0) src.substring(0, slash) else src
        val bass = if (slash >= 0) Degree.parse(src.substring(slash + 1)) else null

        var i = 0
        val acc = when (main.getOrNull(0)) {
            'b', '♭' -> { i = 1; Accidental.FLAT }
            '#', '♯' -> { i = 1; Accidental.SHARP }
            else -> Accidental.NATURAL
        }
        val rest = main.substring(i)
        return if (rest.firstOrNull()?.isDigit() == true) {
            // Arabic: "<n>[suffix]" where n is 1..7 and suffix uses the absolute vocabulary (m, m7, 7, maj7, dim, ...)
            // To keep "57" unambiguous the root is exactly one digit.
            val number = rest.first().digitToInt()
            val quality = parseAbsoluteSuffix(rest.substring(1), text)
            ChordDegree(Degree(number, acc), quality, bass)
        } else {
            val numeral = ROMAN.firstOrNull { rest.lowercase().startsWith(it) }
                ?: throw IllegalArgumentException("Bad chord '$text'")
            val numeralText = rest.substring(0, numeral.length)
            val lower = numeralText == numeralText.lowercase()
            require(lower || numeralText == numeralText.uppercase()) { "Mixed-case numeral in '$text'" }
            val quality = parseRomanSuffix(rest.substring(numeral.length), lower, text)
            ChordDegree(Degree(ROMAN_VALUE.getValue(numeral), acc), quality, bass)
        }
    }

    private fun parseRomanSuffix(suffix: String, lower: Boolean, original: String): ChordQuality = when (suffix) {
        "" -> if (lower) ChordQuality.MINOR else ChordQuality.MAJOR
        "7" -> if (lower) ChordQuality.MINOR7 else ChordQuality.DOMINANT7
        "maj7", "M7", "Δ", "Δ7" -> if (lower) ChordQuality.MINOR_MAJOR7 else ChordQuality.MAJOR7
        "6" -> if (lower) ChordQuality.MINOR6 else ChordQuality.MAJOR6
        "9" -> if (lower) ChordQuality.MINOR9 else ChordQuality.DOMINANT9
        "maj9" -> ChordQuality.MAJOR9
        "°", "o", "dim" -> ChordQuality.DIMINISHED.also { require(lower) { "Diminished must be lower-case: '$original'" } }
        "°7", "o7", "dim7" -> ChordQuality.DIMINISHED7
        "ø", "ø7", "m7b5", "m7♭5" -> ChordQuality.HALF_DIMINISHED7
        "+", "aug" -> ChordQuality.AUGMENTED
        "sus2" -> ChordQuality.SUS2
        "sus4", "sus" -> ChordQuality.SUS4
        "7sus4", "7sus" -> ChordQuality.DOMINANT7_SUS4
        "5" -> ChordQuality.POWER
        "add9" -> ChordQuality.ADD9
        else -> throw IllegalArgumentException("Unknown chord suffix '$suffix' in '$original'")
    }

    private fun parseAbsoluteSuffix(suffix: String, original: String): ChordQuality = when (suffix) {
        "" -> ChordQuality.MAJOR
        "m", "-" -> ChordQuality.MINOR
        "7" -> ChordQuality.DOMINANT7
        "m7", "-7" -> ChordQuality.MINOR7
        "maj7", "M7", "Δ", "Δ7" -> ChordQuality.MAJOR7
        "m(maj7)", "mmaj7", "mM7" -> ChordQuality.MINOR_MAJOR7
        "6" -> ChordQuality.MAJOR6
        "m6" -> ChordQuality.MINOR6
        "9" -> ChordQuality.DOMINANT9
        "maj9" -> ChordQuality.MAJOR9
        "m9" -> ChordQuality.MINOR9
        "dim", "°", "o" -> ChordQuality.DIMINISHED
        "dim7", "°7", "o7" -> ChordQuality.DIMINISHED7
        "m7b5", "m7♭5", "ø", "ø7" -> ChordQuality.HALF_DIMINISHED7
        "aug", "+" -> ChordQuality.AUGMENTED
        "sus2" -> ChordQuality.SUS2
        "sus4", "sus" -> ChordQuality.SUS4
        "7sus4", "7sus" -> ChordQuality.DOMINANT7_SUS4
        "5" -> ChordQuality.POWER
        "add9" -> ChordQuality.ADD9
        else -> throw IllegalArgumentException("Unknown chord suffix '$suffix' in '$original'")
    }

    private val ROMAN_UPPER = listOf("I", "II", "III", "IV", "V", "VI", "VII")

    /** Roman numeral with superscript suffix: "V⁷", "iiø⁷", "♭VII", "Imaj⁷", "I/3". */
    fun formatRoman(chord: ChordDegree): String {
        val numeral = ROMAN_UPPER[chord.root.number - 1].let { if (chord.quality.lowerCaseRoman) it.lowercase() else it }
        val base = chord.root.accidental.symbol + numeral + chord.quality.degreeSuffix
        return if (chord.bass == null) base else "$base/${chord.bass}"
    }

    /** Arabic with superscript suffix and explicit "m" for minor family: "2m⁷", "5⁷", "♭7", "1maj⁷". */
    fun formatArabic(chord: ChordDegree): String {
        val q = chord.quality
        val minorMark = if (q.lowerCaseRoman && q !in setOf(ChordQuality.DIMINISHED, ChordQuality.HALF_DIMINISHED7, ChordQuality.DIMINISHED7)) "m" else ""
        val base = chord.root.accidental.symbol + chord.root.number + minorMark + q.degreeSuffix
        return if (chord.bass == null) base else "$base/${chord.bass}"
    }
}
