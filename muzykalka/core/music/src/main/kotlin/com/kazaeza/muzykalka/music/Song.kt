package com.kazaeza.muzykalka.music

/**
 * A bar: 1..4 chords splitting the bar evenly. Rhythm is deliberately not modelled: the app trains
 * recall of the FORMULA (which degrees, in which order, in which form), not reading.
 */
data class Bar(val chords: List<ChordDegree>) {
    init {
        require(chords.size in 1..4) { "A bar holds 1..4 chords, got ${chords.size}" }
    }
}

/** A named section of a song ("A", "B", "Куплет"). */
data class Section(val label: String, val bars: List<Bar>) {
    init {
        require(label.isNotBlank()) { "Section label is blank" }
        require(bars.isNotEmpty()) { "Section $label has no bars" }
    }
}

/**
 * A piece in the user's library (rule 7: only the user's own music). Stored ONLY in degrees;
 * the key is chosen at random every time it is ordered (rule 2).
 *
 * @property mode tonal context used when picking a random key (MAJOR, MINOR, DORIAN …).
 * @property form order of sections, e.g. ["A", "A", "B", "C"] for AABC.
 * @property startFret the fret where the user's hand usually starts — a hint, never shown first.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String? = null,
    val mode: Mode,
    val sections: List<Section>,
    val form: List<String>,
    val startFret: Int? = null,
    val notes: String = "",
) {
    init {
        require(title.isNotBlank()) { "Song title is blank" }
        require(sections.isNotEmpty()) { "Song '$title' has no sections" }
        val labels = sections.map { it.label }
        require(labels.size == labels.toSet().size) { "Duplicate section labels in '$title': $labels" }
        require(form.isNotEmpty()) { "Song '$title' has an empty form" }
        require(form.all { it in labels }) { "Form $form of '$title' references unknown sections (have $labels)" }
    }

    fun section(label: String): Section = sections.first { it.label == label }

    /** Sections in performance order (expanded form). */
    val performanceOrder: List<Section> get() = form.map(::section)

    /** Every chord as performed, in order. */
    val chordsInOrder: List<ChordDegree> get() = performanceOrder.flatMap { s -> s.bars.flatMap { it.chords } }

    val firstChord: ChordDegree get() = chordsInOrder.first()

    /** "AABC". Multi-letter labels are joined with spaces. */
    val formString: String
        get() = if (form.all { it.length == 1 }) form.joinToString("") else form.joinToString(" ")

    val barCount: Int get() = performanceOrder.sumOf { it.bars.size }
}

/** What a melody is for. */
enum class MelodyKind {
    /** The user's own riff or a riff from a favourite piece — ordered in Taper like a song. */
    RIFF,

    /** A tune for «Угадай по цифрам» (Kamerton): recognised from degree digits alone. */
    TUNE,

    /** A Lexicon phrase, labelled with the harmony it fits over. */
    PHRASE,
}

/** A single-voice line in degrees: riff, tune or lexicon phrase. */
data class Melody(
    val id: String,
    val title: String,
    val mode: Mode,
    val kind: MelodyKind,
    val notes: List<DegreeNote>,
    /** For PHRASE: the chord it is played over, e.g. V⁷ («над V7»). */
    val over: ChordDegree? = null,
) {
    init {
        require(title.isNotBlank()) { "Melody title is blank" }
        require(notes.isNotEmpty()) { "Melody '$title' has no notes" }
    }
}

/**
 * Plain-text progression format, used for seeds, import and tests:
 * ```
 * A: | iv7 | bVII7 | bIIImaj7 | bVImaj7 |
 * B: | iiø7 | V7 | i | i |
 * ```
 * One section per line, "Label:" then bars separated by "|", chords in a bar separated by spaces.
 */
object ProgressionText {
    fun parseSections(text: String): List<Section> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { line ->
            val colon = line.indexOf(':')
            require(colon > 0) { "Section line needs 'Label:' prefix: '$line'" }
            val label = line.substring(0, colon).trim()
            val bars = line.substring(colon + 1).split('|').map { it.trim() }.filter { it.isNotEmpty() }
                .map { bar -> Bar(bar.split(Regex("\\s+")).map(ChordDegree::parse)) }
            Section(label, bars)
        }

    fun format(sections: List<Section>): String = sections.joinToString("\n") { s ->
        s.label + ": | " + s.bars.joinToString(" | ") { b -> b.chords.joinToString(" ") { ChordParser.formatRoman(it) } } + " |"
    }

    /** "AABC" → [A, A, B, C]; "Verse Chorus Verse" → [Verse, Chorus, Verse]. */
    fun parseForm(text: String): List<String> {
        val t = text.trim()
        return if (t.contains(' ')) t.split(Regex("\\s+")) else t.map { it.toString() }
    }
}
