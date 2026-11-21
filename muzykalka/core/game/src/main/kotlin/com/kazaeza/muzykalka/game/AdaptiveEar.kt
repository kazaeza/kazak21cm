package com.kazaeza.muzykalka.game

/** S-01 pilot. Only ordinary single-note Tir answers are evidence about the full keypad. */
data class EarFocus(val variant: Variant, val pair: List<String>, val level: Int) {
    val reason: String get() = when (variant) {
        Variant.TIR_DUEL -> "В разные дни путались ступени ${pair.joinToString(" и ")}. Один раунд на эту пару."
        Variant.TIR_CLASSIC -> "После дуэли проверим ступени ${pair.joinToString(" и ")} на полном круге."
        else -> if (pair.isEmpty()) "Одни и те же ступени над аккордами даются труднее. Сегодня один раунд с аккордами."
            else "На полном круге пара узнаётся. Попробуй услышать ступени над аккордами."
    }
}

object AdaptiveEar {
    fun choose(history: List<AttemptRecord>, today: Int, dayOf: (Long) -> Int): EarFocus? {
        val recent = history.filter { dayOf(it.atEpochMs) in today - 13..today }.sortedBy { it.atEpochMs }
        val notes = recent.filter { it.variant == Variant.TIR_CLASSIC && it.level in 1..3 &&
            it.itemKey.startsWith("tir:L") && it.expected in Metrics.CHROMATIC_LABELS }
        val lastDuel = recent.lastOrNull { it.variant == Variant.TIR_DUEL && it.itemKey.startsWith("tir:duel:") }
        if (lastDuel != null) {
            val pair = lastDuel.itemKey.removePrefix("tir:duel:").substringBefore(':').split('~')
            val duelDay = dayOf(lastDuel.atEpochMs)
            val level = notes.lastOrNull { it.atEpochMs < lastDuel.atEpochMs && it.expected in pair }?.level
            if (pair.size == 2 && pair.all { it in Metrics.CHROMATIC_LABELS } && level != null && today - duelDay <= 7) {
                if (today == duelDay) return null
                val probe = notes.filter { dayOf(it.atEpochMs) > duelDay && it.expected in pair && it.level == level }.takeLast(24)
                if (pair.any { degree -> probe.count { it.expected == degree } < 3 }) {
                    return EarFocus(Variant.TIR_CLASSIC, pair, level)
                }
                if (pair.all { degree -> probe.filter { it.expected == degree }.let { xs -> xs.count { it.correct } >= 0.8 * xs.size } }) {
                    return if (recent.any { it.variant == Variant.TIR_OVER_CHORDS && dayOf(it.atEpochMs) > duelDay }) null
                        else EarFocus(Variant.TIR_OVER_CHORDS, pair, 1)
                }
            }
        }
        val level = notes.lastOrNull()?.level ?: return null
        val sameLevel = notes.filter { it.level == level }.takeLast(100)
        for (p in Metrics.confusedPairs(Metrics.confusion(sameLevel))) {
            val pair = listOf(p.first, p.second)
            if (pair.any { degree -> sameLevel.count { it.expected == degree } < 4 }) continue
            val misses = sameLevel.filter { !it.correct && it.expected in pair && it.given in pair }
            if (misses.map { dayOf(it.atEpochMs) }.distinct().size >= 2 &&
                pair.any { degree -> sameLevel.filter { it.expected == degree }.takeLast(8).count { !it.correct } >= 2 }) {
                return EarFocus(Variant.TIR_DUEL, pair, level)
            }
        }
        return contextFocus(recent, today, dayOf)
    }

    private fun contextFocus(rows: List<AttemptRecord>, today: Int, dayOf: (Long) -> Int): EarFocus? {
        val chords = rows.filter { it.variant == Variant.TIR_OVER_CHORDS && it.level == 1 }.takeLast(80)
        if (chords.isEmpty() || dayOf(chords.last().atEpochMs) >= today - 1) return null
        val classic = rows.filter { it.variant == Variant.TIR_CLASSIC && it.level == 2 }.takeLast(100)
        // Compare the same degrees in the same mode. Each category gets equal weight.
        fun groups(xs: List<AttemptRecord>) = xs.filter { it.expected in Metrics.CHROMATIC_LABELS && it.keyLabel != null }
            .groupBy { it.expected to com.kazaeza.muzykalka.music.Key.modeOfLabel(it.keyLabel) }
            .filter { (key, trials) -> key.second != null && trials.size >= 3 && trials.map { dayOf(it.atEpochMs) }.distinct().size >= 2 }
        val base = groups(classic)
        val other = groups(chords)
        val shared = base.keys.intersect(other.keys)
        if (shared.size < 3) return null
        fun accuracy(xs: List<AttemptRecord>) = xs.count { it.correct }.toDouble() / xs.size
        val gap = shared.map { accuracy(base.getValue(it)) - accuracy(other.getValue(it)) }.average()
        return if (gap >= 0.2) EarFocus(Variant.TIR_OVER_CHORDS, emptyList(), 1) else null
    }

    /** Keep 2.5 of the four Tir minutes in the regular round and reserve 90 seconds for the focus. */
    fun apply(plan: List<SetSegment>, focus: EarFocus?): List<SetSegment> {
        if (focus == null) return plan
        return plan.flatMap { segment ->
            if (segment.variant == Variant.TIR_CLASSIC && segment.minutes >= 3.0) {
                listOf(SetSegment(focus.variant, 1.5), segment.copy(minutes = segment.minutes - 1.5))
            } else listOf(segment)
        }
    }
}
