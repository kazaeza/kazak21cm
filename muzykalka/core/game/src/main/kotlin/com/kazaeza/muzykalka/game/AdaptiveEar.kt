package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.Mode

/** S-01 pilot. Only ordinary single-note Tir answers are evidence about the full keypad. */
data class FocusSession(val id: Long, val day: Int, val focus: EarFocus, val status: String)

data class EarFocus(val variant: Variant, val pair: List<String>, val level: Int, val parentId: Long? = null, val chord: String? = null, val retention: Boolean = false) {
    val reason: String get() = when (variant) {
        Variant.TIR_DUEL -> if (parentId != null) "На полном круге пара ${pair.joinToString(" и ")} ещё путается. Сравним её снова."
            else "В разные дни путались ступени ${pair.joinToString(" и ")}. Один раунд на эту пару."
        Variant.TIR_CLASSIC -> if (retention) "Две недели спустя. Послушай ступени на полном круге."
            else "После дуэли проверим ступени ${pair.joinToString(" и ")} на полном круге."
        else -> if (chord != null) "Над аккордом $chord знакомые ступени узнаются реже. Послушай их в этом контексте."
            else if (pair.isEmpty()) "Одни и те же ступени над аккордами даются труднее. Сегодня один раунд с аккордами."
            else "На полном круге пара узнаётся. Попробуй услышать ступени над аккордами."
    }
}

object AdaptiveEar {
    fun choose(history: List<AttemptRecord>, today: Int, lessons: List<FocusSession> = emptyList(), dayOf: (Long) -> Int): EarFocus? {
        val recent = history.filter { dayOf(it.atEpochMs) in today - 13..today }.sortedBy { it.atEpochMs }
        val notes = recent.filter { it.variant == Variant.TIR_CLASSIC && it.level in 1..3 &&
            it.itemKey.startsWith("tir:L") && it.expected in Metrics.CHROMATIC_LABELS }
        val active = lessons.filter { it.day in today - 20..today }
        if (active.any { it.day == today }) return null
        val duel = active.lastOrNull { it.focus.variant == Variant.TIR_DUEL && it.status == "COMPLETED" }
        if (duel != null) {
            val pair = duel.focus.pair
            if (today - duel.day < 14 && pair.any { degree -> recent.none { it.focusSessionId == duel.id && it.expected == degree } }) {
                return duel.focus
            }
            val children = active.filter { it.focus.parentId == duel.id && it.status == "COMPLETED" && it.day > duel.day }
            val sample = firstProbeAnswers(history, duel, children)
            if (today - duel.day >= 14) {
                val delayed = children.filter { it.focus.retention }
                val check = firstProbeAnswers(history, duel, delayed, retention = true)
                if (pair.all { d -> sample.count { it.expected == d } >= 3 && passed(sample, d) }) {
                    if (pair.any { d -> check.count { it.expected == d } < 2 }) {
                        return EarFocus(Variant.TIR_CLASSIC, pair, duel.focus.level, duel.id, retention = true)
                    }
                    if (pair.any { d -> check.count { it.expected == d && !it.correct } >= 2 } && delayed.maxOf { it.day } >= today - 2) {
                        return EarFocus(Variant.TIR_DUEL, pair, duel.focus.level, duel.id)
                    }
                }
            } else {
                if (pair.any { d -> sample.count { it.expected == d } < 3 }) {
                    return EarFocus(Variant.TIR_CLASSIC, pair, duel.focus.level, duel.id)
                }
                val uncertain = pair.filterNot { passed(sample, it) }
                if (uncertain.isNotEmpty()) {
                    val repeatedMiss = uncertain.any { d -> sample.count { it.expected == d && !it.correct } >= 2 }
                    return EarFocus(if (repeatedMiss) Variant.TIR_DUEL else Variant.TIR_CLASSIC, pair, duel.focus.level, duel.id)
                }
                if (children.none { it.focus.variant == Variant.TIR_OVER_CHORDS } &&
                    pair.all { d -> listOf(Mode.MAJOR, Mode.MINOR).any { mode -> mode.degrees.any { it.toString() == d } } }) {
                    return EarFocus(Variant.TIR_OVER_CHORDS, pair, 1, duel.id)
                }
            }
        }
        val baseline = notes.filter { it.focusSessionId == null && (duel == null || dayOf(it.atEpochMs) > duel.day) }
        val level = baseline.lastOrNull()?.level ?: return contextFocus(recent, today, dayOf)
        val sameLevel = baseline.filter { it.level == level }.takeLast(100)
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

    private fun passed(sample: List<AttemptRecord>, degree: String): Boolean {
        val rows = sample.filter { it.expected == degree }
        return rows.isNotEmpty() && rows.count { it.correct } >= 0.8 * rows.size
    }

    fun firstProbeAnswers(history: List<AttemptRecord>, duel: FocusSession, sessions: List<FocusSession>,
                          retention: Boolean = false): List<AttemptRecord> {
        val checks = sessions.filter { it.status == "COMPLETED" && it.focus.parentId == duel.id &&
            it.day > duel.day && it.focus.variant == Variant.TIR_CLASSIC && it.focus.retention == retention }.associateBy { it.id }
        return history.filter { it.focusSessionId in checks && it.variant == Variant.TIR_CLASSIC &&
            it.level == duel.focus.level && it.expected in duel.focus.pair }.sortedBy { it.atEpochMs }
            .distinctBy { checks[it.focusSessionId]?.day to it.expected }
            .filter { it.replayCount == 0 && it.hintLevel == 0 }
    }

    private fun contextFocus(rows: List<AttemptRecord>, today: Int, dayOf: (Long) -> Int): EarFocus? {
        val chords = rows.filter { it.variant == Variant.TIR_OVER_CHORDS && it.level == 1 }.takeLast(80)
        if (chords.isEmpty() || dayOf(chords.last().atEpochMs) >= today - 1) return null
        val classic = rows.filter { it.variant == Variant.TIR_CLASSIC && it.level == 2 }.takeLast(100)
        // Compare the same degrees in the same mode. Each category gets equal weight.
        fun groups(xs: List<AttemptRecord>) = xs.filter { it.expected in Metrics.CHROMATIC_LABELS && it.keyLabel != null }
            .groupBy { it.expected to Key.modeOfLabel(it.keyLabel) }
            .filter { (key, trials) -> key.second != null && trials.size >= 3 && trials.map { dayOf(it.atEpochMs) }.distinct().size >= 2 }
        val base = groups(classic.filter { it.focusSessionId == null })
        val ordinaryChords = chords.filter { it.focusSessionId == null }
        val other = groups(ordinaryChords)
        val shared = base.keys.intersect(other.keys)
        if (shared.size < 3) return null
        fun accuracy(xs: List<AttemptRecord>) = xs.count { it.correct }.toDouble() / xs.size
        // Attribute the gap to a particular chord only when its own matched sample clears the same bar.
        val specific = ordinaryChords.groupBy { it.itemKey.split(':').getOrNull(3) }.mapNotNull { (chord, trials) ->
            if (chord.isNullOrBlank()) return@mapNotNull null
            val onChord = groups(trials)
            val matching = base.keys.intersect(onChord.keys)
            if (matching.size < 3) return@mapNotNull null
            val delta = matching.map { accuracy(base.getValue(it)) - accuracy(onChord.getValue(it)) }.average()
            if (delta >= 0.2) chord to delta else null
        }.maxByOrNull { it.second }
        if (specific != null) return EarFocus(Variant.TIR_OVER_CHORDS, emptyList(), 1, chord = specific.first)
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
