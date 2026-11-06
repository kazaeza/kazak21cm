package com.kazaeza.muzykalka.srs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Review grade. Values match FSRS (1..4). */
enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

/** The 21 FSRS-6 weights. [DEFAULT] = py-fsrs 6.3.2 defaults. Optimising them per user is a later ticket. */
class FsrsParameters(val w: DoubleArray) {
    init {
        require(w.size == 21) { "FSRS-6 needs 21 parameters, got ${w.size}" }
    }

    companion object {
        val DEFAULT = FsrsParameters(
            doubleArrayOf(
                0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
                1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
            ),
        )
    }
}

/**
 * Memory state of one card (a song, riff or phrase).
 *
 * Time is measured in whole STUDY DAYS (an Int day number from [StudyDay]); the scheduler never
 * sees wall-clock time. A new card has null stability/difficulty and is due immediately.
 */
data class CardState(
    val stability: Double? = null,
    val difficulty: Double? = null,
    val lastReviewDay: Int? = null,
    /** Day the card is due; null for a new card (due now). */
    val dueDay: Int? = null,
    val reps: Int = 0,
    /** Number of AGAIN ratings after the first review. */
    val lapses: Int = 0,
) {
    val isNew: Boolean get() = stability == null

    fun isDue(today: Int): Boolean = dueDay == null || dueDay <= today
}

/**
 * FSRS-6 scheduler, exact port of py-fsrs 6.3.2 configured as the app uses it:
 * no learning/relearning steps (a forgotten song is simply ordered again the next day), no fuzz,
 * whole-day intervals. Verified against golden vectors in src/test/resources/fsrs6-golden.json
 * (regenerate with scripts/fsrs_golden.py).
 *
 * NOTE: Python's round() is round-half-to-even; we use [Math.rint] to match it exactly.
 */
class Fsrs(
    val parameters: FsrsParameters = FsrsParameters.DEFAULT,
    val desiredRetention: Double = 0.9,
    val maximumIntervalDays: Int = 36_500,
    /**
     * Product rule from the plan: «вылетела — завтра закажут снова». After AGAIN the card is due
     * no later than this many days, while S and D still follow FSRS exactly. null = pure FSRS
     * (used by the golden-vector test).
     */
    val maxIntervalAfterAgainDays: Int? = 1,
) {
    init {
        require(desiredRetention > 0.0 && desiredRetention < 1.0) { "desiredRetention must be in (0,1)" }
        require(maximumIntervalDays >= 1) { "maximumIntervalDays must be >= 1" }
    }

    private val w = parameters.w
    private val decay = -w[20]
    private val factor = 0.9.pow(1.0 / decay) - 1.0

    /** Probability of recall on [today]; 0.0 for a card never reviewed (py-fsrs convention). */
    fun retrievability(card: CardState, today: Int): Double {
        val s = card.stability ?: return 0.0
        val last = card.lastReviewDay ?: return 0.0
        val elapsed = max(0, today - last)
        return (1.0 + factor * elapsed / s).pow(decay)
    }

    fun review(card: CardState, rating: Rating, today: Int): CardState {
        val s: Double
        val d: Double
        if (card.stability == null || card.difficulty == null) {
            s = initialStability(rating)
            d = clampDifficulty(initialDifficulty(rating))
        } else {
            val elapsed = card.lastReviewDay?.let { today - it }
            s = if (elapsed != null && elapsed < 1) {
                shortTermStability(card.stability, rating)
            } else {
                nextStability(card.difficulty, card.stability, retrievability(card, today), rating)
            }
            d = nextDifficulty(card.difficulty, rating)
        }
        val fsrsInterval = nextIntervalDays(s)
        val interval = if (rating == Rating.AGAIN && maxIntervalAfterAgainDays != null) {
            min(fsrsInterval, maxIntervalAfterAgainDays)
        } else {
            fsrsInterval
        }
        return CardState(
            stability = s,
            difficulty = d,
            lastReviewDay = today,
            dueDay = today + interval,
            reps = card.reps + 1,
            lapses = card.lapses + if (!card.isNew && rating == Rating.AGAIN) 1 else 0,
        )
    }

    fun nextIntervalDays(stability: Double): Int {
        val raw = stability / factor * (desiredRetention.pow(1.0 / decay) - 1.0)
        return Math.rint(raw).toInt().coerceIn(1, maximumIntervalDays)
    }

    private fun clampStability(s: Double) = max(s, STABILITY_MIN)
    private fun clampDifficulty(d: Double) = min(max(d, 1.0), 10.0)

    private fun initialStability(r: Rating) = clampStability(w[r.value - 1])

    private fun initialDifficulty(r: Rating) = w[4] - exp(w[5] * (r.value - 1)) + 1.0

    private fun shortTermStability(s: Double, r: Rating): Double {
        var inc = exp(w[17] * (r.value - 3 + w[18])) * s.pow(-w[19])
        if (r != Rating.AGAIN) inc = max(inc, 1.0)
        return clampStability(s * inc)
    }

    private fun nextDifficulty(d: Double, r: Rating): Double {
        val delta = -(w[6] * (r.value - 3))
        val damped = d + (10.0 - d) * delta / 9.0
        val reverted = w[7] * initialDifficulty(Rating.EASY) + (1 - w[7]) * damped
        return clampDifficulty(reverted)
    }

    private fun nextStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val next = if (rating == Rating.AGAIN) {
            val longTerm = w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14])
            val shortTerm = s / exp(w[17] * w[18])
            min(longTerm, shortTerm)
        } else {
            val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
            val easyBonus = if (rating == Rating.EASY) w[16] else 1.0
            s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus)
        }
        return clampStability(next)
    }

    private companion object {
        const val STABILITY_MIN = 0.001
    }
}
