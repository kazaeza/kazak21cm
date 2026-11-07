package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.srs.Rating
import kotlin.math.max
import kotlin.math.min

/**
 * Rule 3 («сначала минимальная подсказка»): the hint ladder is climbed one step at a time and
 * every step costs tips. The ladder ends at [TOP] = [DEGREES] (O-13, ADR-027): chords in letters
 * are not offered any more. [FULL_SHEET] stays only so that orders logged before O-13 still read.
 */
enum class HintStep(val ru: String, val tipCost: Int) {
    NONE("без подсказок", 0),
    FORM("форма", 20),
    FIRST_CHORD("первый аккорд", 40),
    DEGREES("ступени", 70),
    FULL_SHEET("лист целиком", 100);

    /** The next step of the ladder; null at the top. Never [FULL_SHEET]. */
    fun next(): HintStep? = entries.getOrNull(ordinal + 1)?.takeIf { it <= TOP }

    /** One step down (vanishing cues); [NONE] stays [NONE]. */
    fun previous(): HintStep = entries[maxOf(0, ordinal - 1)]

    companion object {
        /** The highest step that is ever shown: the whole song in degrees. */
        val TOP: HintStep = DEGREES
    }
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
    /**
     * «Что это было?» score, 0..1 — see [TaperGrading.formulaAccuracy]; null = not asked. Since O-13
     * the Taper does not ask (ADR-027): orders logged before it carry a score.
     */
    val formulaAccuracy: Double?,
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
    fun rating(o: OrderOutcome): Rating {
        val formula = o.formulaAccuracy
        return when {
            o.self == SelfGrade.FAILED || o.maxHint >= HintStep.DEGREES || (formula != null && formula < FORMULA_FAIL) -> Rating.AGAIN
            o.maxHint > HintStep.NONE || o.self == SelfGrade.STUMBLED || (formula != null && formula < FORMULA_PASS) -> Rating.HARD
            (formula == null || formula >= 1.0) && o.startLatencyMs != null && o.startLatencyMs <= QUICK_START_MS -> Rating.EASY
            else -> Rating.GOOD
        }
    }

    /** «Играю с ходу»: no hints, remembered, and the name check passed if there was one. Main metric of the app. */
    fun playableNow(o: OrderOutcome): Boolean {
        val formula = o.formulaAccuracy
        return o.maxHint == HintStep.NONE && o.self != SelfGrade.FAILED && (formula == null || formula >= FORMULA_PASS)
    }

    /**
     * O-13 (ADR-027), vanishing cues: where the next order of a piece starts on the hint ladder,
     * from how its last order went. A piece never played opens with the whole song in degrees; a
     * clean play takes one step off next time, a stumble keeps the step, a failure goes back to
     * the degrees. Climbing up within an order is still one step at a time (rule 3).
     */
    fun startHint(lastHint: HintStep?, lastSelf: SelfGrade?): HintStep = when {
        lastHint == null || lastSelf == null -> HintStep.TOP
        lastSelf == SelfGrade.FAILED -> HintStep.TOP
        lastSelf == SelfGrade.CLEAN -> minOf(lastHint, HintStep.TOP).previous()
        else -> minOf(lastHint, HintStep.TOP)
    }

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
     * What «Что это было?» asked for before O-13: each distinct section once (in declaration order), with
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

    /** Same idea for a riff: plain edit distance over degrees (octave ignored). */
    fun melodyAccuracy(expected: List<com.kazaeza.muzykalka.music.Degree>, given: List<com.kazaeza.muzykalka.music.Degree>): Double {
        if (expected.isEmpty()) return if (given.isEmpty()) 1.0 else 0.0
        val n = expected.size
        val m = given.size
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val sub = if (expected[i - 1] sameSoundAs given[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + sub)
        }
        return max(0.0, 1.0 - d[n][m].toDouble() / max(n, m))
    }
}
