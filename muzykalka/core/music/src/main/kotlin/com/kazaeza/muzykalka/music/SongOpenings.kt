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
