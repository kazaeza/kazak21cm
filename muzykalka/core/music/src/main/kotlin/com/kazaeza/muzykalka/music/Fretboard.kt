package com.kazaeza.muzykalka.music

/**
 * Guitar tuning. String numbering follows the guitarist convention everywhere in the app:
 * string 1 = highest (thinnest), string 6 = lowest. [openNotes] index 0 is string 1.
 */
data class Tuning(val id: String, val ruName: String, val openNotes: List<MidiNote>) {
    init {
        require(openNotes.size in 4..8) { "Unsupported string count ${openNotes.size}" }
    }

    val stringCount: Int get() = openNotes.size

    fun open(string: Int): MidiNote {
        require(string in 1..stringCount) { "String $string out of 1..$stringCount" }
        return openNotes[string - 1]
    }

    companion object {
        private fun of(id: String, ru: String, vararg lowToHigh: Int) =
            Tuning(id, ru, lowToHigh.reversed().map(::MidiNote))

        val STANDARD = of("standard", "Стандарт (E A D G B E)", 40, 45, 50, 55, 59, 64)
        val DROP_D = of("drop_d", "Drop D (D A D G B E)", 38, 45, 50, 55, 59, 64)
        val HALF_STEP_DOWN = of("eb_standard", "Пониженный на полтона (E♭)", 39, 44, 49, 54, 58, 63)
        val D_STANDARD = of("d_standard", "Пониженный на тон (D)", 38, 43, 48, 53, 57, 62)
        val DROP_C_SHARP = of("drop_c_sharp", "Drop C♯", 37, 44, 49, 54, 58, 63)
        val DROP_C = of("drop_c", "Drop C (C G C F A D)", 36, 43, 48, 53, 57, 62)
        val C_STANDARD = of("c_standard", "Пониженный на два тона (C)", 36, 41, 46, 51, 55, 60)

        val PRESETS: List<Tuning> = listOf(STANDARD, DROP_D, HALF_STEP_DOWN, D_STANDARD, DROP_C_SHARP, DROP_C, C_STANDARD)

        fun byId(id: String): Tuning? = PRESETS.firstOrNull { it.id == id }
    }
}

/** A place on the neck. Fret 0 = open string. */
data class FretPosition(val string: Int, val fret: Int) {
    init {
        require(string >= 1) { "String must be >= 1" }
        require(fret >= 0) { "Fret must be >= 0" }
    }
}

/** The neck: maps places to sounds and back. Pure geometry, no notation. */
class Fretboard(val tuning: Tuning, val fretCount: Int = 22) {
    init {
        require(fretCount in 12..27) { "Unsupported fret count $fretCount" }
    }

    val strings: IntRange get() = 1..tuning.stringCount

    fun noteAt(position: FretPosition): MidiNote {
        require(position.fret <= fretCount) { "Fret ${position.fret} > $fretCount" }
        return tuning.open(position.string) + position.fret
    }

    /** Every place where exactly this note (pitch AND octave) can be played. */
    fun positionsOf(note: MidiNote): List<FretPosition> = strings.mapNotNull { s ->
        val fret = note.value - tuning.open(s).value
        if (fret in 0..fretCount) FretPosition(s, fret) else null
    }

    /** Frets on [string] where this pitch class sounds, ascending. */
    fun fretsOf(pitchClass: PitchClass, string: Int): List<Int> {
        val first = pitchClass - tuning.open(string).pitchClass
        return generateSequence(first) { it + 12 }.takeWhile { it <= fretCount }.toList()
    }

    /** Concrete notes of [pitchClass] available on [string] (one per octave). */
    fun notesOf(pitchClass: PitchClass, string: Int): List<MidiNote> =
        fretsOf(pitchClass, string).map { tuning.open(string) + it }

    val lowest: MidiNote get() = tuning.openNotes.min()
    val highest: MidiNote get() = tuning.openNotes.max() + fretCount
}
