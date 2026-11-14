package com.kazaeza.muzykalka.music

import kotlin.random.Random

/**
 * O-41: which songs of the library open on a given chord. A song «starts with» the chord on a degree when its first
 * chord is rooted there (a slash bass does not matter). «Вайб ступеней» offers these songs behind the anchor slot of a
 * degree: the ones in the same mode, and with the chord that stands on that degree in the mode's own scale, come
 * first — so the card of V offers songs that open on a V of the major scale before one that happens to open on a ♭VII.
 * Pure: the library is passed in, the chance is [Random].
 */
object SongOpenings {
    /** The song's first chord stands on [degree] (enharmonic spellings are the same degree). */
    fun opens(song: Song, degree: Degree): Boolean = song.chordsInOrder.firstOrNull()?.root?.semitones == degree.semitones

    /**
     * How well [song] fits the card of [degree] in [mode]: 2 for the same mode, 1 more when its first chord has the third
     * of the chord that stands there in [mode]'s scale (a degree outside the scale has no such chord).
     */
    fun fit(song: Song, degree: Degree, mode: Mode): Int {
        val first = song.chordsInOrder.firstOrNull() ?: return 0
        val step = mode.degrees.indexOfFirst { it.semitones == degree.semitones }
        val ownThird = if (step >= 0) Harmony.diatonicChord(mode, step).quality.intervals.getOrNull(1) else null
        val sameThird = ownThird != null && first.quality.intervals.getOrNull(1) == ownThird
        return (if (song.mode == mode) 2 else 0) + (if (sameThird) 1 else 0)
    }

    /** Two chords the ear takes for one: the same root and quality (a slash bass does not count). */
    fun sameChord(a: ChordDegree, b: ChordDegree): Boolean = a.root.semitones == b.root.semitones && a.quality == b.quality

    /**
     * O-43: the first [count] chords of [song] as the ear hears them change — a chord held over several bars is one
     * change, so a twelve-bar blues opens «I → IV → I → V», not «I I I I». Fewer when the song has fewer changes.
     */
    fun changes(song: Song, count: Int): List<ChordDegree> {
        val out = ArrayList<ChordDegree>(count)
        for (c in song.chordsInOrder) {
            if (out.size == count) break
            if (out.isEmpty() || !sameChord(out.last(), c)) out += ChordDegree(c.root, c.quality)
        }
        return out
    }

    /** [chords] as one text that is equal for chords the ear takes for the same: «I-V-vi-IV» (roots spelled one way). */
    fun signature(chords: List<ChordDegree>): String =
        chords.joinToString("-") { ChordParser.formatRoman(ChordDegree(Degree.canonical(Math.floorMod(it.root.semitones, 12)), it.quality)) }

    /**
     * [chords] as the ear hears them without a key: the steps between the roots, counted from the first chord, and the
     * qualities. «vi–IV–I–V» of a major song and «i–♭VI–♭III–♭VII» of a minor one are the same loop, hence one shape.
     */
    fun shape(chords: List<ChordDegree>): String {
        val first = chords.firstOrNull()?.root?.semitones ?: 0
        return chords.joinToString("-") { "${Math.floorMod(it.root.semitones - first, 12)}${it.quality.name}" }
    }

    /** How many chords from the start two openings share. */
    fun commonPrefix(a: List<ChordDegree>, b: List<ChordDegree>): Int = a.zip(b).takeWhile { (x, y) -> sameChord(x, y) }.size

    /**
     * The items of [items] whose song opens on [degree], best fit first; among equals the order is [random]'s, so
     * a fresh look at the card offers another song first. [songOf] says which song an item holds (none: skipped).
     */
    fun <T> candidates(items: List<T>, songOf: (T) -> Song?, degree: Degree, mode: Mode, random: Random): List<T> =
        items
            .mapNotNull { item -> songOf(item)?.takeIf { opens(it, degree) }?.let { song -> item to fit(song, degree, mode) } }
            .shuffled(random)
            .sortedByDescending { it.second }
            .map { it.first }
}
