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

/**
 * O-61: one finger of a [Grip] — the [place] it presses, the [degree] of the key it sounds, and whether it is the chord's root.
 */
data class GripDot(val place: FretPosition, val degree: Degree, val isRoot: Boolean)

/**
 * O-61: a chord as one shape of the hand, for the diagram: the [dots] pressed, the strings that ring [open] and those
 * that are not played ([muted]); [form] is the open chord the shape grows from.
 */
data class Grip(val form: ChordForm, val dots: List<GripDot>, val open: List<Int>, val muted: List<Int>)

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

    /**
     * O-61: the fret of each string, low to high, of a major or minor chord in [form], as the distance from the fret the
     * root is under; null — the string is not played. Written once, from the open chords the shapes are named after.
     */
    private fun template(form: ChordForm, minor: Boolean): List<Int?>? = when (form) {
        ChordForm.E -> if (minor) listOf(0, 2, 2, 0, 0, 0) else listOf(0, 2, 2, 1, 0, 0)
        ChordForm.A -> if (minor) listOf(null, 0, 2, 2, 1, 0) else listOf(null, 0, 2, 2, 2, 0)
        ChordForm.D -> if (minor) listOf(null, null, 0, 2, 3, 1) else listOf(null, null, 0, 2, 3, 2)
        ChordForm.G -> if (minor) null else listOf(0, -1, -3, -3, -3, 0)
        ChordForm.C -> if (minor) null else listOf(null, 0, -1, -3, -2, -3)
    }

    /**
     * O-61: the one shape of the hand for the chord whose root is at [root] — the fingers pressed, the open strings and
     * the muted ones. Only for a major or minor triad in a shape that has one, on a six-string neck in fourths with the
     * usual third between its two highest strings; null otherwise (the tones in the zone are all there is to show). The shape
     * is checked against the chord itself: every sound is a tone of it and all of its tones are there, or there is no grip.
     */
    fun grip(board: Fretboard, key: Key, root: RootPlace): Grip? {
        val form = root.form ?: return null
        val intervals = root.chord.quality.intervals.take(3)
        val minor = when (intervals) {
            listOf(0, 4, 7) -> false
            listOf(0, 3, 7) -> true
            else -> return null
        }
        val offsets = template(form, minor) ?: return null
        val lowToHigh = board.strings.sortedBy { board.tuning.open(it).value }
        if (lowToHigh.size != offsets.size) return null
        val rootPc = key.pitchClassOf(root.chord.root)
        val wanted = intervals.map { rootPc + it }.toSet()
        val dots = ArrayList<GripDot>()
        val open = ArrayList<Int>()
        val muted = ArrayList<Int>()
        val base = root.place.fret
        offsets.forEachIndexed { i, off ->
            val string = lowToHigh[i]
            if (off == null) {
                muted += string
                return@forEachIndexed
            }
            val fret = base + off
            if (fret < 0 || fret > board.fretCount) return null
            val pc = board.noteAt(FretPosition(string, fret)).pitchClass
            if (pc !in wanted) return null
            if (fret == 0) open += string
            val semis = Math.floorMod(pc.value - key.tonic.pitchClass.value, 12)
            dots += GripDot(FretPosition(string, fret), key.mode.diatonicSpelling(semis) ?: Degree.canonical(semis), pc == rootPc)
        }
        if (dots.map { board.noteAt(it.place).pitchClass }.toSet() != wanted) return null
        return Grip(form, dots.filter { it.place.fret > 0 }, open, muted)
    }

    /**
     * O-61: where the root at [place] stands against the root of the tonic chord at [home], in words — the rule that lets
     * the hand find any chord of the key from the first one: «на струну тоньше, на 2 лада выше». Read off the actual
     * places, so it is always what the map shows.
     */
    fun hop(home: FretPosition, place: FretPosition): String {
        val strings = home.string - place.string // positive: thinner
        val frets = place.fret - home.fret
        fun count(n: Int, one: String, few: String, many: String) = if (n == 1) one else if (n in 2..4) few else many
        val stringPart = when {
            strings == 0 -> "по той же струне"
            strings > 0 -> "на ${count(strings, "струну", "$strings струны", "$strings струн")} тоньше"
            else -> "на ${count(-strings, "струну", "${-strings} струны", "${-strings} струн")} толще"
        }
        val fretPart = when {
            frets == 0 -> "на том же ладу"
            frets > 0 -> "на ${count(frets, "1 лад", "$frets лада", "$frets ладов")} выше"
            else -> "на ${count(-frets, "1 лад", "${-frets} лада", "${-frets} ладов")} ниже"
        }
        return when {
            strings == 0 && frets == 0 -> "Это дом: отсюда всё считается."
            strings == 0 -> "Корень — $stringPart, $fretPart корня I."
            frets == 0 -> "Корень — $stringPart, $fretPart, что корень I."
            else -> "Корень — $stringPart и $fretPart, чем корень I."
        }
    }
}
