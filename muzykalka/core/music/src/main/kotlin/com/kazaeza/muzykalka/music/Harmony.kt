package com.kazaeza.muzykalka.music

/** Diatonic harmony, cadences and resolutions — the musical content behind Tir and backings. */
object Harmony {

    /**
     * Chord built by stacking thirds inside [mode] on scale step [step] (0 = tonic).
     * Triads by default, sevenths when [sevenths] is true. Root is the mode's own spelling.
     */
    fun diatonicChord(mode: Mode, step: Int, sevenths: Boolean = false): ChordDegree {
        require(step in 0..6) { "Step must be 0..6" }
        val root = mode.degrees[step]
        val tones = (if (sevenths) listOf(0, 2, 4, 6) else listOf(0, 2, 4)).map { mode.degrees[(step + it) % 7] }
        val set = tones.map { floorMod12(it.semitones - root.semitones) }.toSet()
        val quality = ChordQuality.fromPitchClassSet(set)
            ?: error("No quality for $set on step $step of $mode")
        return ChordDegree(root, quality)
    }

    /**
     * Cadence that establishes the key before a Tir question (FET-style). Natural minor borrows
     * the major V⁷ from harmonic minor: the leading tone makes the tonic unambiguous.
     */
    fun cadence(mode: Mode): List<ChordDegree> = when (mode) {
        Mode.MAJOR -> chords("I IV V7 I")
        Mode.MINOR, Mode.HARMONIC_MINOR -> chords("i iv V7 i")
        Mode.MELODIC_MINOR -> chords("i IV V7 i")
        Mode.DORIAN -> chords("i IV bVII i")
        Mode.PHRYGIAN -> chords("i bII bvii i")
        Mode.LYDIAN -> chords("I II vii I")
        Mode.MIXOLYDIAN -> chords("I bVII IV I")
        Mode.LOCRIAN -> chords("i° bII bvii i°")
    }

    private fun chords(spec: String): List<ChordDegree> = spec.split(' ').map(ChordDegree::parse)

    /**
     * Where a degree goes to land on the tonic, played after every Tir answer (rule 5: the right
     * answer is always heard, resolved). Deterministic rule:
     *  1. Target the nearer tonic by semitones: 1..5 semitones → down to 1; 7..11 → up to 8 (1');
     *     the tritone goes up when spelled ♯ (♯4) and down when spelled ♭ (♭5).
     *  2. A note outside the mode first moves by a semitone toward the target to the adjacent scale tone.
     *  3. Then walk the scale stepwise to the target tonic.
     * Examples in major: 5 → 6 7 1'; 4 → 3 2 1; ♭7 → 7 1'; ♯4 → 5 6 7 1'; 1 → (nothing).
     */
    fun resolution(start: DegreeNote, mode: Mode): List<DegreeNote> {
        val st = start.degree.semitones
        if (st == 0) return emptyList()
        val up = when {
            st < 6 -> false
            st > 6 -> true
            else -> start.degree.accidental == Accidental.SHARP
        }
        val targetAbs = if (up) 12 * (start.octave + 1) else 12 * start.octave
        val scale = scaleTonesAround(mode, start.octave)
        val startAbs = start.semitonesFromTonic
        val path = mutableListOf<Int>()
        var cur = startAbs
        // Step 2: chromatic note → adjacent scale tone toward the target.
        if (scale.none { it == cur }) {
            cur = if (up) scale.first { it > cur } else scale.last { it < cur }
            path += cur
        }
        // Step 3: stepwise along the scale.
        while (cur != targetAbs) {
            cur = if (up) scale.first { it > cur } else scale.last { it < cur }
            path += cur
        }
        return path.map { abs -> toDegreeNote(abs, mode) }
    }

    /** Scale tones (semitones from tonic of octave 0) covering octaves [octave-1, octave+1]. */
    private fun scaleTonesAround(mode: Mode, octave: Int): List<Int> =
        (octave - 1..octave + 1).flatMap { o -> mode.degrees.map { it.semitones + 12 * o } }.sorted() + (12 * (octave + 2))

    private fun toDegreeNote(abs: Int, mode: Mode): DegreeNote {
        val oct = Math.floorDiv(abs, 12)
        val st = floorMod12(abs)
        return DegreeNote(mode.diatonicSpelling(st) ?: Degree.canonical(st), oct)
    }
}
