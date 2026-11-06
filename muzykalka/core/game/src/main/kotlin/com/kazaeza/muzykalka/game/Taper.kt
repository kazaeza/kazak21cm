package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.srs.Rating
import kotlin.math.max
import kotlin.math.min

/**
 * Rule 3 («сначала минимальная подсказка»): the hint ladder is climbed one step at a time and
 * every step costs tips. Absolute chord names appear ONLY at [FULL_SHEET] (rule 1).
 */
enum class HintStep(val ru: String, val tipCost: Int) {
    NONE("без подсказок", 0),
    FORM("форма", 20),
    FIRST_CHORD("первый аккорд", 40),
    DEGREES("ступени", 70),
    FULL_SHEET("лист целиком", 100);

    fun next(): HintStep? = entries.getOrNull(ordinal + 1)
}

/** Self-assessment after an order (v0.1 has no chord recognition). */
enum class SelfGrade(val ru: String) {
    CLEAN("сыграл с ходу"),
    STUMBLED("с запинками"),
    FAILED("не вспомнил"),
}

/** Outcome of one Taper order, the input to SRS and to «Играю с ходу». */
data class OrderOutcome(
    val songId: String,
    val maxHint: HintStep,
    val self: SelfGrade,
    /** «Что это было?» score, 0..1 — see [TaperGrading.formulaAccuracy]. */
    val formulaAccuracy: Double,
    /** From the order to the first note played; null if not measured (no mic). */
    val startLatencyMs: Long?,
)

object TaperGrading {
    const val BASE_TIP = 100
    const val START_WINDOW_MS = 10_000L
    const val QUICK_START_MS = 3_000L
    const val FORMULA_PASS = 0.9
    const val FORMULA_FAIL = 0.6

    /** Maps an order to an FSRS rating. The single source of truth for «держится / вылетела». */
    fun rating(o: OrderOutcome): Rating = when {
        o.self == SelfGrade.FAILED || o.maxHint >= HintStep.DEGREES || o.formulaAccuracy < FORMULA_FAIL -> Rating.AGAIN
        o.maxHint > HintStep.NONE || o.self == SelfGrade.STUMBLED || o.formulaAccuracy < FORMULA_PASS -> Rating.HARD
        o.formulaAccuracy >= 1.0 && o.startLatencyMs != null && o.startLatencyMs <= QUICK_START_MS -> Rating.EASY
        else -> Rating.GOOD
    }

    /** «Играю с ходу»: no hints, remembered, and the name check passed. Main metric of the app. */
    fun playableNow(o: OrderOutcome): Boolean =
        o.maxHint == HintStep.NONE && o.self != SelfGrade.FAILED && o.formulaAccuracy >= FORMULA_PASS

    fun tips(o: OrderOutcome): Int {
        val base = max(0, BASE_TIP - o.maxHint.tipCost)
        val factor = when (o.self) {
            SelfGrade.CLEAN -> 1.0
            SelfGrade.STUMBLED -> 0.5
            SelfGrade.FAILED -> 0.0
        }
        val quick = if (o.startLatencyMs != null && o.startLatencyMs <= QUICK_START_MS && factor > 0) 20 else 0
        return (base * factor).toInt() + quick
    }

    /**
     * What «Что это было?» asks for: each distinct section once (in declaration order), with
     * immediate repeats inside a section collapsed (i i → i). Keeps entry to ~20 taps per song.
     */
    fun expectedFormula(song: Song): List<ChordDegree> = song.sections.flatMap { section ->
        val chords = section.bars.flatMap { it.chords }
        chords.filterIndexed { i, c -> i == 0 || chords[i - 1] != c }
    }

    /**
     * Weighted edit distance score in 0..1: substitution with the right root but wrong quality
     * costs 0.5, anything else 1. 1.0 = perfect.
     */
    fun formulaAccuracy(expected: List<ChordDegree>, given: List<ChordDegree>): Double {
        if (expected.isEmpty()) return if (given.isEmpty()) 1.0 else 0.0
        val n = expected.size
        val m = given.size
        val d = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 0..n) d[i][0] = i.toDouble()
        for (j in 0..m) d[0][j] = j.toDouble()
        for (i in 1..n) for (j in 1..m) {
            val e = expected[i - 1]
            val g = given[j - 1]
            val sub = when {
                e.root sameSoundAs g.root && e.quality == g.quality -> 0.0
                e.root sameSoundAs g.root -> 0.5
                else -> 1.0
            }
            d[i][j] = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + sub)
        }
        return max(0.0, 1.0 - d[n][m] / max(n, m))
    }
}
