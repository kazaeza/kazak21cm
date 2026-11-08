package com.kazaeza.muzykalka.game

/**
 * «Спой ступень» (first note, speed, three lives) is closed until «Пропевка» shows the voice can find a degree
 * at all: at least half of the last [WINDOW] notes found on the first try, out of at least [MIN_TRIES].
 * «Пропевка» records only the first try of each note (ADR-028), so its history is exactly that measure.
 */
object SingUnlock {
    const val WINDOW = 30
    const val MIN_TRIES = 20

    /** [tries] — notes counted (the last [WINDOW]), [correct] — found on the first try. */
    data class Status(val open: Boolean, val tries: Int, val correct: Int)

    fun status(attempts: List<AttemptRecord>): Status {
        val last = attempts.filter { it.variant == Variant.KAMERTON_SEARCH }.sortedBy { it.atEpochMs }.takeLast(WINDOW)
        val correct = last.count { it.correct }
        return Status(open = last.size >= MIN_TRIES && correct * 2 >= last.size, tries = last.size, correct = correct)
    }

    /** While «Спой ступень» is closed, its block of the daily set is sung as «Пропевка» instead. */
    fun plan(plan: List<SetSegment>, status: Status): List<SetSegment> {
        if (status.open) return plan
        val swapped = plan.map { if (it.variant == Variant.KAMERTON_SING) it.copy(variant = Variant.KAMERTON_SEARCH) else it }
        val search = swapped.filter { it.variant == Variant.KAMERTON_SEARCH }
        if (search.size < 2) return swapped
        val merged = search.first().copy(minutes = search.sumOf { it.minutes })
        return swapped.filter { it.variant != Variant.KAMERTON_SEARCH }.let { rest ->
            val at = swapped.indexOfFirst { it.variant == Variant.KAMERTON_SEARCH }.coerceAtMost(rest.size)
            rest.take(at) + merged + rest.drop(at)
        }
    }
}
