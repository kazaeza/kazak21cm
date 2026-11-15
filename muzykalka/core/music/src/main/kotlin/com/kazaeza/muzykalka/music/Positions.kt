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
