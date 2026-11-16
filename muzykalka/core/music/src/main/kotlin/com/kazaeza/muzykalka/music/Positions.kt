package com.kazaeza.muzykalka.music

/**
 * O-21: the five movable shapes of the CAGED system — the user's own choice of system for the neck. These are names
 * of SHAPES (the open chords they come from), not of the notes that sound: the chord's root is never spelled (rule 1).
 */
enum class ChordForm(val ru: String) {
    E("форма E — баррэ от басовой струны"),
    A("форма A — баррэ от второй басовой"),
    D("форма D — от третьей басовой"),
    G("форма G — корень под мизинцем на басовой"),
    C("форма C — корень под мизинцем на второй басовой"),
}

/**
 * Where the root of one chord of the key lies in a zone: [step] of the scale (0 = I), the [chord], the [place] on one
 * of the three bass strings, and the [form] that grows from that root (null when no handy shape does).
 */
data class RootPlace(val step: Int, val chord: ChordDegree, val place: FretPosition, val form: ChordForm?)

/** O-53: one of the places of a chord along the neck — the [form] it is taken in and the fret its zone starts at. */
data class FormPlace(val form: ChordForm, val start: Int)

/** A tone of a chord on the neck: where it is and which degree of the KEY it is (the colour and the label). */
data class TonePlace(val place: FretPosition, val degree: Degree, val isRoot: Boolean)

/**
 * O-21 «Позиция» (ADR-050): the chords of a key in one zone of the neck. The three bass strings are a fourth apart, so
 * five frets of them hold fifteen semitones in a row — every root of the key is always there: the question is never
 * «is there one», only «which», and that is what the map answers. Pure geometry; no sound, no notation.
 */
object Positions {
    /** Frets in a zone: what the hand covers without moving. */
    const val SPAN = 5

    fun zone(start: Int): IntRange = start until start + SPAN

    /** The three lowest strings, the lowest first (string numbers; the thickest string has the highest number). */
    fun bassStrings(board: Fretboard): List<Int> = board.strings.sortedBy { board.tuning.open(it).value }.take(3)

    /** The latest fret a zone can start at on this neck. */
    fun lastStart(board: Fretboard): Int = minOf(board.fretCount - SPAN + 1, 12)

    /**
     * The zone to start from: the tonic on the lowest string under the first finger, with one fret of room below it
     * for the chords that reach down — the zone begins a fret before the tonic.
     */
    fun homeZone(board: Fretboard, key: Key): Int {
        val lowest = bassStrings(board).first()
        val tonicFret = board.fretsOf(key.tonic.pitchClass, lowest).first()
        return (tonicFret - 1).coerceIn(0, lastStart(board))
    }

    /**
     * The root of each of the seven chords of the key inside the zone that starts at [start]: on the lowest string that
     * has it — one of the three bass strings in any tuning in fourths.
     */
    fun roots(board: Fretboard, key: Key, start: Int): List<RootPlace> {
        val bass = bassStrings(board)
        val fourths = bass.zipWithNext().all { (low, high) -> board.tuning.open(high).value - board.tuning.open(low).value == 5 }
        // in fourths the three bass strings always have the root; a drop tuning leaves a gap, and the next strings fill it
        val lowToHigh = board.strings.sortedBy { board.tuning.open(it).value }
        return (0..6).map { step ->
            val chord = Harmony.diatonicChord(key.mode, step)
            val pc = key.pitchClassOf(chord.root)
            val rank = lowToHigh.indexOfFirst { s -> board.fretsOf(pc, s).any { it in zone(start) } }
            check(rank >= 0) { "no string has the root of step $step in frets ${zone(start)}" }
            val string = lowToHigh[rank]
            val fret = board.fretsOf(pc, string).first { it in zone(start) }
            RootPlace(step, chord, FretPosition(string, fret), if (fourths && rank < bass.size) form(rank, fret - start, chord.quality) else null)
        }
    }

    /**
     * The shape that grows from a root on bass string number [rank] (0 = the lowest) at [offset] frets into the zone.
     * A root in the lower half is under the first finger: E, A, D. In the upper half it is under the little finger:
     * G and C — handy for major chords only, so a minor or diminished chord there has no form (the zone wants moving).
     */
    private fun form(rank: Int, offset: Int, quality: ChordQuality): ChordForm? = when {
        offset <= 2 -> listOf(ChordForm.E, ChordForm.A, ChordForm.D)[rank]
        quality.intervals.getOrNull(1) != 4 -> null
        rank == 0 -> ChordForm.G
        rank == 1 -> ChordForm.C
        else -> null
    }

    /**
     * O-53: the same chord along the whole neck — the zones in which each shape of the system holds it, from the nut
     * up. This is what CAGED is: one chord, five places, each a few frets after the last. A major chord has all five;
     * a minor or diminished one only the three with the root under the first finger. Empty when the bass strings are
     * not a fourth apart (a drop tuning): the shapes do not hold there.
     */
    fun forms(board: Fretboard, key: Key, chord: ChordDegree): List<FormPlace> {
        val bass = bassStrings(board)
        if (bass.size < 3 || !bass.zipWithNext().all { (low, high) -> board.tuning.open(high).value - board.tuning.open(low).value == 5 }) return emptyList()
        val pc = key.pitchClassOf(chord.root)
        val last = lastStart(board)
        // where the root is on each bass string within the first octave, and how far into the zone each shape wants it
        fun start(rank: Int, offset: Int): Int? {
            val fret = board.fretsOf(pc, bass[rank]).first()
            return listOf(fret - offset, fret - offset + 12).firstOrNull { it in 0..last } ?: (fret - offset).takeIf { fret == 0 && offset == 1 }?.let { 0 }
        }
        val major = chord.quality.intervals.getOrNull(1) == 4
        val wanted = listOf(ChordForm.E to (0 to 1), ChordForm.A to (1 to 1), ChordForm.D to (2 to 1)) +
            if (major) listOf(ChordForm.G to (0 to 3), ChordForm.C to (1 to 3)) else emptyList()
        return wanted.mapNotNull { (form, at) -> start(at.first, at.second)?.let { FormPlace(form, it) } }
            // only the places where the map itself would name this shape: the two must never disagree
            .filter { place -> roots(board, key, place.start).any { SongOpenings.sameChord(it.chord, chord) && it.form == place.form } }
            .sortedBy { it.start }
    }

    /** Every place in the zone, on every string, where a tone of [chord] sounds — the shape appears by itself. */
    fun tones(board: Fretboard, key: Key, chord: ChordDegree, start: Int): List<TonePlace> {
        val rootPc = key.pitchClassOf(chord.root)
        val byPc = chord.quality.intervals.take(4).associate { interval ->
            val semitones = Math.floorMod(chord.root.semitones + interval, 12)
            (rootPc + interval) to (key.mode.diatonicSpelling(semitones) ?: Degree.canonical(semitones))
        }
        return board.strings.flatMap { s ->
            zone(start).filter { it <= board.fretCount }.mapNotNull { fret ->
                val pc = board.noteAt(FretPosition(s, fret)).pitchClass
                byPc[pc]?.let { TonePlace(FretPosition(s, fret), it, pc == rootPc) }
            }
        }
    }
}
