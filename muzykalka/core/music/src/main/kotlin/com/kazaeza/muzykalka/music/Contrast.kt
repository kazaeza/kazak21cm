package com.kazaeza.muzykalka.music

/**
 * O-74 (ADR-071): after a miss, what the user CHOSE, in the same words as the right answer — one short tag each, so that
 * the two sounds heard one after another are told apart by what they do, not by what they are called: «6 — тяжесть:
 * тянет вниз к 5». The right answer's own tag is on the screen already (ADR-065); this is the other one.
 */
object Contrasts {
    /** The degree [given] chosen over the drone: its feel word and where it leans. */
    fun degree(given: Degree, mode: Mode, notation: DegreeNotation): String {
        val name = DegreeFormatter.degree(given, notation)
        val vibe = DegreeVibes.of(given).word
        val path = Harmony.resolution(DegreeNote(given), mode)
        val pull = path.firstOrNull()?.let { to ->
            val up = to.semitonesFromTonic > given.semitones
            "тянет ${if (up) "вверх" else "вниз"} к ${DegreeFormatter.degree(to.degree, notation)}"
        } ?: "уже дома, никуда не тянет"
        return "$name — $vibe: $pull"
    }

    /** The degree [given] chosen over [chord]: what it does against that chord. */
    fun degreeOver(given: Degree, chord: ChordDegree, mode: Mode, notation: DegreeNotation): String =
        "${DegreeFormatter.degree(given, notation)} — ${DegreeVibes.of(given).word}, ${DegreeVibes.overChord(given, chord, mode, notation)}"

    /** The chord [given] chosen: its job in the key and where it pulls. */
    fun chord(given: ChordDegree, mode: Mode, notation: DegreeNotation): String {
        val job = ChordFunction.of(given, mode)
        val to = ChordFunction.pullsTo(given, mode)
        val pull = if (to.isEmpty()) "покой, никуда не тянет" else "тянется в " + to.joinToString(" или ") { DegreeFormatter.chord(it, notation) }
        return "${DegreeFormatter.chord(given, notation)} — ${job.ru}: $pull"
    }
}
