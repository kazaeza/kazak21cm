package com.kazaeza.muzykalka.music

import kotlin.random.Random

/**
 * Modes, each described by its seven degrees in the app's major-relative convention.
 * [ruName] is nominative ("ми минор"), [ruPrepositional] goes after "в" ("в ми миноре").
 */
enum class Mode(val ruName: String, val ruPrepositional: String, degreeSpec: String) {
    MAJOR("мажор", "мажоре", "1 2 3 4 5 6 7"),
    MINOR("минор", "миноре", "1 2 b3 4 5 b6 b7"),
    DORIAN("дорийский", "дорийском", "1 2 b3 4 5 6 b7"),
    PHRYGIAN("фригийский", "фригийском", "1 b2 b3 4 5 b6 b7"),
    LYDIAN("лидийский", "лидийском", "1 2 3 #4 5 6 7"),
    MIXOLYDIAN("миксолидийский", "миксолидийском", "1 2 3 4 5 6 b7"),
    LOCRIAN("локрийский", "локрийском", "1 b2 b3 4 b5 b6 b7"),
    HARMONIC_MINOR("гармонический минор", "гармоническом миноре", "1 2 b3 4 5 b6 7"),
    MELODIC_MINOR("мелодический минор", "мелодическом миноре", "1 2 b3 4 5 6 7");

    /** Seven scale degrees, index 0 = tonic. */
    val degrees: List<Degree> = degreeSpec.split(' ').map(Degree::parse)

    /** True when the tonic triad is minor (♭3 in the scale). */
    val hasMinorThird: Boolean get() = degrees[2].semitones == 3

    fun contains(degree: Degree): Boolean = degrees.any { it sameSoundAs degree }

    /** The mode's own spelling of a chromatic step, or null if the step is not in the scale. */
    fun diatonicSpelling(semitones: Int): Degree? = degrees.firstOrNull { it.semitones == floorMod12(semitones) }

    /** Index (0..6) of the scale step with this sound, or -1. */
    fun indexOf(degree: Degree): Int = degrees.indexOfFirst { it sameSoundAs degree }
}

/**
 * A key: the ONLY absolute (lettered) name the user ever sees during practice (rule 1).
 * Holds a spelled tonic so that every degree has a well-defined absolute spelling.
 */
data class Key(val tonic: NoteName, val mode: Mode) {

    fun pitchClassOf(degree: Degree): PitchClass = tonic.pitchClass + degree.semitones

    /**
     * Absolute spelling of [degree] in this key: the letter is the tonic letter shifted by
     * (number − 1) steps, and the accidental makes up the difference. ♭3 in E is G, ♯4 in C is F♯.
     */
    fun spell(degree: Degree): NoteName {
        val letter = tonic.letter.shift(degree.number - 1)
        val target = pitchClassOf(degree).value
        val diff = signedMod12(target - letter.naturalPitchClass)
        return NoteName(letter, Accidental.ofSemitones(diff))
    }

    /**
     * Degree of an absolute pitch class in this key: the mode's own spelling when the sound is
     * in the scale (♯4 in lydian, ♭5 in locrian), otherwise the canonical chromatic spelling.
     */
    fun degreeOf(pitchClass: PitchClass): Degree {
        val st = pitchClass - tonic.pitchClass
        return mode.diatonicSpelling(st) ?: Degree.canonical(st)
    }

    fun degreeOf(note: MidiNote): Degree = degreeOf(note.pitchClass)

    /** Concrete MIDI note for a degree note, with the tonic of octave 0 at [tonicMidi]. */
    fun midiOf(note: DegreeNote, tonicMidi: MidiNote): MidiNote {
        require(tonicMidi.pitchClass == tonic.pitchClass) { "tonicMidi $tonicMidi is not the tonic of $this" }
        return tonicMidi + note.semitonesFromTonic
    }

    /** Lowest MIDI note with this key's tonic pitch class that is >= [atLeast]. */
    fun tonicAtOrAbove(atLeast: MidiNote): MidiNote = atLeast + (tonic.pitchClass - atLeast.pitchClass)

    /** Number of sharps/flats when the scale is spelled out (for choosing practical spellings). */
    val accidentalCount: Int get() = mode.degrees.count { spell(it).accidental != Accidental.NATURAL }

    val hasDoubleAccidentals: Boolean
        get() = mode.degrees.any { spell(it).accidental.semitones !in -1..1 }

    override fun toString(): String = "$tonic ${mode.name}"
}

/** Practical keys: one spelling per pitch class, chosen to minimise accidentals. */
object KeyCatalog {
    /** All 12 practical keys for [mode], ordered by tonic pitch class C..B. */
    fun practical(mode: Mode): List<Key> = PitchClass.ALL.map { practical(it, mode) }

    /** The practical spelling of the key on [tonic]: fewest accidentals, no double accidentals, sharps on ties. */
    fun practical(tonic: PitchClass, mode: Mode): Key {
        val candidates = Letter.entries.flatMap { letter ->
            listOf(Accidental.NATURAL, Accidental.SHARP, Accidental.FLAT).map { NoteName(letter, it) }
        }.filter { it.pitchClass == tonic }
        return candidates
            .map { Key(it, mode) }
            .filterNot { it.hasDoubleAccidentals }
            .sortedWith(
                compareBy<Key> { it.accidentalCount }
                    .thenBy { it.tonic.accidental != Accidental.NATURAL }
                    .thenBy { it.tonic.accidental == Accidental.FLAT },
            )
            .first()
    }
}

/**
 * Rule 2: the key is random every time, so the user memorises the formula, not the hand position.
 * Never repeats any of the last [avoidLast] tonics.
 */
class KeyPicker(private val random: Random, private val avoidLast: Int = 3) {
    private val recent = ArrayDeque<PitchClass>()

    fun next(mode: Mode): Key {
        val pool = PitchClass.ALL.filterNot { it in recent }
        val tonic = pool[random.nextInt(pool.size)]
        recent.addLast(tonic)
        while (recent.size > avoidLast) recent.removeFirst()
        return KeyCatalog.practical(tonic, mode)
    }
}
