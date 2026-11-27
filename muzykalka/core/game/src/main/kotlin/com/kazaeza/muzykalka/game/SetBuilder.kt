package com.kazaeza.muzykalka.game

/**
 * Duration-scaled, adaptive set builder.
 * Guarantees Rule 6 (every segment >= 1.0 min), unique variants, exact half-minute totals,
 * optional quiet-mode voice substitution, and a 100% non-guitar [buildDaily] core.
 */
object SetBuilder {
    val BUDGET_OPTIONS = listOf(5, 10, 15, 20, 30)

    private val SILENT_FALLBACKS = listOf(
        Variant.TIR_OVER_CHORDS,
        Variant.TIR_ON_TONIC,
        Variant.TIR_UNDER_NOTE,
        Variant.TIR_DYAD,
        Variant.TIR_LAYERS,
        Variant.TIR_COLOR,
    )

    private val DAILY_FILLERS = listOf(
        SetSegment(Variant.TIR_OVER_CHORDS, 2.5),
        SetSegment(Variant.TIR_LAYERS, 2.0),
        SetSegment(Variant.TIR_ON_TONIC, 2.0),
        SetSegment(Variant.TIR_LOOP3, 2.5),
        SetSegment(Variant.FORMULA_ALOUD, 2.5),
        SetSegment(Variant.TIR_NEXT, 2.0),
    )

    /**
     * Scale and select segments from [base] so the total equals [targetMinutes], every segment
     * is >= 1.0 min (Rule 6), variants are unique, and allocation is deterministic.
     */
    fun scale(base: List<SetSegment>, targetMinutes: Int): List<SetSegment> {
        val deduped = deduplicate(base)
        if (deduped.isEmpty()) return emptyList()
        val budget = targetMinutes.coerceAtLeast(1)
        val maxCount = when {
            budget <= 5 -> 3
            budget <= 10 -> 4
            else -> budget
        }.coerceAtMost(budget)
        val chosen = deduped.take(maxCount)
        if (budget == 5 && chosen.size == 3) {
            return listOf(
                chosen[0].copy(minutes = 2.0),
                chosen[1].copy(minutes = 1.5),
                chosen[2].copy(minutes = 1.5),
            )
        }
        val budgetHalf = budget * 2
        val totalWeight = chosen.sumOf { it.minutes.coerceAtLeast(0.5) }
        val exact = chosen.map { budgetHalf * (it.minutes.coerceAtLeast(0.5) / totalWeight) }
        val units = exact.map { maxOf(2, it.toInt()) }.toMutableList()
        var diff = budgetHalf - units.sum()
        while (diff > 0) {
            val best = units.indices.maxWithOrNull(
                compareBy<Int> { exact[it] - units[it] }.thenBy { -it }
            ) ?: break
            units[best] += 1
            diff -= 1
        }
        while (diff < 0) {
            val candidate = units.indices.filter { units[it] > 2 }
                .minWithOrNull(compareBy<Int> { exact[it] - units[it] }.thenBy { -it })
                ?: break
            units[candidate] -= 1
            diff += 1
        }
        return chosen.mapIndexed { idx, seg -> seg.copy(minutes = units[idx] * 0.5) }
    }

    /**
     * When [quiet] is true, replace any voice variant ([Variant.KAMERTON_SING], [Variant.KAMERTON_SEARCH])
     * with a non-duplicate silent ear variant while preserving each segment's minutes.
     */
    fun applyQuiet(plan: List<SetSegment>, quiet: Boolean): List<SetSegment> {
        if (!quiet) return plan
        val used = plan.filterNot { it.variant in Training.VOICE }.mapTo(mutableSetOf()) { it.variant }
        val result = mutableListOf<SetSegment>()
        for (segment in plan) {
            if (segment.variant !in Training.VOICE) {
                result += segment
            } else {
                val replacement = SILENT_FALLBACKS.firstOrNull { it !in used }
                if (replacement != null) {
                    used += replacement
                    result += segment.copy(variant = replacement)
                } else if (result.isNotEmpty()) {
                    val last = result.last()
                    result[result.lastIndex] = last.copy(minutes = last.minutes + segment.minutes)
                }
            }
        }
        return result
    }

    /**
     * Build the 100% non-guitar daily set: warmup (Tir), voice (via [SingUnlock], swapped if [quiet]),
     * active track's current stage drills, and transfer/songs, scaled to [targetMinutes] with [AdaptiveEar].
     */
    fun buildDaily(
        targetMinutes: Int,
        quiet: Boolean,
        activeTrack: Track?,
        singing: SingUnlock.Status,
        focus: EarFocus?,
        transferGapBig: Boolean,
        appVersion: String = "0.1",
        activeStageIndex: Int = 0,
    ): List<SetSegment> {
        val candidates = mutableListOf<SetSegment>()
        candidates += SetSegment(Variant.TIR_CLASSIC, 3.5)
        if (!quiet) {
            candidates += SetSegment(Variant.KAMERTON_SING, 3.0)
        }
        if (transferGapBig) {
            candidates += SetSegment(Variant.TIR_LOOP3, 3.0)
        }
        val stage = activeTrack
            ?.takeIf { it.guitar == GuitarNeed.NONE }
            ?.stages
            ?.takeIf { it.isNotEmpty() }
            ?.let { list -> list[activeStageIndex.coerceIn(0, list.lastIndex)] }
        if (stage != null) {
            candidates += stage.drills.filter { it.variant.guitar != GuitarNeed.REQUIRED }
        }
        if (!transferGapBig) {
            candidates += SetSegment(Variant.FORMULA_ALOUD, 2.5)
            candidates += SetSegment(Variant.TIR_LOOP3, 2.5)
            candidates += SetSegment(Variant.TIR_NEXT, 2.5)
        } else {
            candidates += SetSegment(Variant.FORMULA_ALOUD, 2.5)
            candidates += SetSegment(Variant.TIR_NEXT, 2.0)
        }
        candidates += DAILY_FILLERS

        val available = DailySet.available(candidates, appVersion)
            .filter { it.variant.guitar != GuitarNeed.REQUIRED }
        val maxBlocks = when {
            targetMinutes <= 15 -> 6
            targetMinutes <= 20 -> 7
            else -> 8
        }
        val withSing = deduplicate(SingUnlock.plan(available, singing)).take(maxBlocks)
        val afterQuiet = applyQuiet(withSing, quiet)
        val scaled = scale(afterQuiet, targetMinutes)
        return AdaptiveEar.apply(scaled, focus)
    }

    /** Build a duration-scaled session for any [TrackStage], applying [SingUnlock], [quiet], and [AdaptiveEar]. */
    fun buildStage(
        stage: TrackStage,
        targetMinutes: Int,
        quiet: Boolean,
        singing: SingUnlock.Status,
        focus: EarFocus?,
        appVersion: String = "0.1",
    ): List<SetSegment> {
        val available = DailySet.available(stage.drills, appVersion)
        val withSing = deduplicate(SingUnlock.plan(available, singing))
        val afterQuiet = applyQuiet(withSing, quiet)
        val scaled = scale(afterQuiet, targetMinutes)
        return AdaptiveEar.apply(scaled, focus)
    }

    private fun deduplicate(plan: List<SetSegment>): List<SetSegment> {
        val byVariant = LinkedHashMap<Variant, Double>()
        for (segment in plan) {
            if (segment.minutes <= 0.0) continue
            if (segment.variant !in byVariant) {
                byVariant[segment.variant] = segment.minutes
            }
        }
        return byVariant.map { (variant, minutes) -> SetSegment(variant, minutes) }
    }
}
