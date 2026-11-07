package com.kazaeza.muzykalka.game

/**
 * Spaced review across days (ticket O-06, RESEARCH §4 F): what was missed on an earlier day comes
 * back first today, until it is answered right once today. Within a round the adaptive weights
 * ([ItemWeights]) already repeat errors; this brings them back on the next days too, because
 * spaced practice holds better than massed practice (Katz & Wiseheart 2025).
 *
 * An item is due when, on the LAST earlier day it was asked (at most [LOOKBACK_DAYS] ago), it was
 * missed at least once, and it has no right answer today yet. Pure: study days come from [dayOf].
 */
object SpacedReview {
    const val LOOKBACK_DAYS = 7
    const val MAX_ITEMS = 8

    data class Item(
        /** Normalised key (see `normalize` of [queue]), e.g. "note:♭3". */
        val key: String,
        /** Misses on the day it was last missed. */
        val errors: Int,
        /** 1 = yesterday. */
        val daysAgo: Int,
        /** Canonical codes given instead on that day ("5" for a 3 heard as 5), without «не знаю». */
        val confusedWith: List<String>,
    )

    /**
     * [history] — attempts of one variant, any order. [normalize] maps an attempt's item key to
     * the review key (null = not reviewable): Tir merges levels so a degree missed on level 1 comes
     * back on level 2 too. Most recent misses first, then the most missed.
     */
    fun queue(
        history: List<AttemptRecord>,
        today: Int,
        dayOf: (epochMs: Long) -> Int,
        normalize: (itemKey: String) -> String? = { it },
        lookbackDays: Int = LOOKBACK_DAYS,
        max: Int = MAX_ITEMS,
    ): List<Item> {
        val byKey = HashMap<String, MutableList<Pair<Int, AttemptRecord>>>()
        for (a in history) {
            val key = normalize(a.itemKey) ?: continue
            byKey.getOrPut(key) { mutableListOf() } += dayOf(a.atEpochMs) to a
        }
        return byKey.mapNotNull { (key, dated) ->
            if (dated.any { (day, a) -> day == today && a.correct }) return@mapNotNull null
            val lastDay = dated.map { it.first }.filter { it < today && it >= today - lookbackDays }.maxOrNull() ?: return@mapNotNull null
            val misses = dated.filter { (day, a) -> day == lastDay && !a.correct }.map { it.second }
            if (misses.isEmpty()) return@mapNotNull null
            Item(
                key = key,
                errors = misses.size,
                daysAgo = today - lastDay,
                confusedWith = misses.map { it.given }.filter { it.isNotBlank() && it != "?" && it != "—" && ' ' !in it }.distinct(),
            )
        }
            .sortedWith(compareBy<Item> { it.daysAgo }.thenByDescending { it.errors }.thenBy { it.key })
            .take(max)
    }
}
