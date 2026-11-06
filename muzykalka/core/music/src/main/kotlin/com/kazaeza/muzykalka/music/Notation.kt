package com.kazaeza.muzykalka.music

/** How degrees are written (Settings → «Запись ступеней»). */
enum class DegreeNotation { ARABIC, ROMAN, SOLFEGE }

/** How absolute note names are written (keys, Hunt prompts). */
enum class NoteNaming { LATIN, SOLFEGE_RU }

/**
 * The single place that turns domain values into user-visible text. UI never builds note or
 * degree strings by hand: that is how rule 1 stays enforceable (grep for spell()/absoluteSymbol()).
 */
object DegreeFormatter {
    private val SOLFEGE = listOf("до", "ре", "ми", "фа", "соль", "ля", "си")

    fun degree(degree: Degree, notation: DegreeNotation): String = when (notation) {
        DegreeNotation.ARABIC -> degree.accidental.symbol + degree.number
        DegreeNotation.ROMAN -> degree.accidental.symbol + listOf("I", "II", "III", "IV", "V", "VI", "VII")[degree.number - 1]
        DegreeNotation.SOLFEGE -> degree.accidental.symbol + SOLFEGE[degree.number - 1]
    }

    /**
     * Chords are written in Roman numerals in both 1–7 and I–VII modes: the plan names chords that
     * way («ii–V–I», «над V7») and Nashville superscripts like "♭7⁷" read badly on a phone.
     */
    fun chord(chord: ChordDegree, notation: DegreeNotation): String = when (notation) {
        DegreeNotation.ROMAN, DegreeNotation.ARABIC -> ChordParser.formatRoman(chord)
        DegreeNotation.SOLFEGE -> {
            // Movable-do root + Roman-style suffix, e.g. "ре m⁷", "соль⁷".
            val q = chord.quality
            val minor = if (q.lowerCaseRoman && q.degreeSuffix.none { it == '°' || it == 'ø' }) " m" else ""
            val base = degree(chord.root, notation) + minor + q.degreeSuffix
            if (chord.bass == null) base else base + "/" + degree(chord.bass, notation)
        }
    }

    fun line(notes: List<DegreeNote>, notation: DegreeNotation): String =
        notes.joinToString(" ") { degree(it.degree, notation) }
}

/** Russian-language names of absolute things: notes and keys. */
object RuNames {
    fun note(name: NoteName, naming: NoteNaming): String = when (naming) {
        NoteNaming.LATIN -> name.toString()
        NoteNaming.SOLFEGE_RU -> name.letter.solfegeRu + name.accidental.ruSuffix
    }

    /** "ми минор", "си-бемоль мажор", "E минор". */
    fun key(key: Key, naming: NoteNaming = NoteNaming.SOLFEGE_RU): String = note(key.tonic, naming) + " " + key.mode.ruName

    /** "в ми миноре", "в си-бемоль мажоре" — for the Taper's spoken order. */
    fun inKey(key: Key, naming: NoteNaming = NoteNaming.SOLFEGE_RU): String =
        "в " + note(key.tonic, naming) + " " + key.mode.ruPrepositional

    /** "5-я" — string ordinal as in the doc ("F♯ · 5-я"). */
    fun stringOrdinal(string: Int): String = "$string-я"
}
