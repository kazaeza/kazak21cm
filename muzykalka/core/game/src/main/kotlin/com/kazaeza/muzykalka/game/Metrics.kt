package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.srs.CardState
import com.kazaeza.muzykalka.srs.Fsrs
import kotlin.math.min

/** A Taper order as the metrics see it. */
data class OrderFact(val itemId: String, val studyDay: Int, val playableNow: Boolean, val atEpochMs: Long)

/** Numbers behind «Прогресс» (ticket P-01). Pure functions over attempts, orders and cards. */
object Metrics {

    fun median(values: List<Long>): Long? = if (values.isEmpty()) null else values.sorted()[values.size / 2]

    /**
     * Streak of consecutive practice days ending today — or yesterday, so the streak is not shown
     * as lost before today's practice. [days] are study-day numbers in any order.
     */
    fun streak(days: Collection<Int>, today: Int): Int {
        val set = days.toSet()
        var d = if (today in set) today else today - 1
        var n = 0
        while (d in set) {
            n++
            d--
        }
        return n
    }

    /** Neck heat cell for one (string, pitch class), from Hunt FIND attempts. */
    data class HeatCell(val string: Int, val pitchClass: Int, val medianMs: Long, val accuracy: Double, val count: Int)

    private val HUNT_KEY = Regex("""^hunt:s(\d+):pc(\d+)$""")

    fun fretHeat(attempts: List<AttemptRecord>): List<HeatCell> =
        attempts.mapNotNull { a -> HUNT_KEY.find(a.itemKey)?.let { m -> (m.groupValues[1].toInt() to m.groupValues[2].toInt()) to a } }
            .groupBy({ it.first }, { it.second })
            .map { (k, list) ->
                val recent = list.takeLast(20)
                HeatCell(
                    string = k.first, pitchClass = k.second,
                    medianMs = median(recent.filter { it.correct }.map { it.latencyMs }) ?: HuntDriver.START_LIMIT_MS,
                    accuracy = recent.count { it.correct }.toDouble() / recent.size,
                    count = list.size,
                )
            }

    /** Tir single-note confusion: rows = expected degree, columns = answered degree (canonical codes). */
    data class Confusion(val labels: List<String>, val counts: List<IntArray>) {
        fun total(row: Int) = counts[row].sum()
    }

    val CHROMATIC_LABELS = com.kazaeza.muzykalka.music.Degree.CHROMATIC.map { it.toString() }

    fun confusion(attempts: List<AttemptRecord>): Confusion {
        val labels = CHROMATIC_LABELS
        val counts = List(labels.size) { IntArray(labels.size) }
        attempts.filter { it.variant.mode == ModeId.TIR && it.itemKey.startsWith("tir:L") && it.expected in labels && it.given in labels }
            .forEach { counts[labels.indexOf(it.expected)][labels.indexOf(it.given)]++ }
        return Confusion(labels, counts)
    }

    /** The two degrees confused with each other most often (for «Дуэль» and the Progress hint). */
    fun worstPair(c: Confusion): Pair<String, String>? {
        var best: Pair<String, String>? = null
        var bestCount = 0
        for (i in c.labels.indices) for (j in c.labels.indices) {
            if (i == j) continue
            val n = c.counts[i][j] + c.counts[j][i]
            if (n > bestCount && i < j) {
                bestCount = n
                best = c.labels[i] to c.labels[j]
            }
        }
        return best
    }

    /**
     * «Играю с ходу» by day: for each day with orders, how many items had their latest order (up to
     * and including that day) played without hints.
     */
    fun playableNowSeries(orders: List<OrderFact>): List<Pair<Int, Int>> {
        if (orders.isEmpty()) return emptyList()
        val sorted = orders.sortedBy { it.atEpochMs }
        val latest = mutableMapOf<String, Boolean>()
        val result = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < sorted.size) {
            val day = sorted[i].studyDay
            while (i < sorted.size && sorted[i].studyDay == day) {
                latest[sorted[i].itemId] = sorted[i].playableNow
                i++
            }
            result += day to latest.values.count { it }
        }
        return result
    }

    /** Mean predicted recall of reviewed cards in [daysAhead] days — «удержание через 7 и 30 дней». */
    fun retention(cards: List<CardState>, today: Int, daysAhead: Int, fsrs: Fsrs = Fsrs()): Double? {
        val reviewed = cards.filter { !it.isNew }
        if (reviewed.isEmpty()) return null
        return reviewed.map { fsrs.retrievability(it, today + daysAhead) }.average()
    }

    /** Which variants feed which link of the triangle. */
    fun variantsOf(link: Link): Set<Variant> = when (link) {
        Link.NAME_TO_PLACE -> setOf(Variant.HUNT_FIND, Variant.HUNT_ALL_OCTAVES)
        Link.PLACE_TO_NAME -> setOf(Variant.HUNT_REVERSE)
        Link.SOUND_TO_NAME -> setOf(Variant.TIR_CLASSIC, Variant.TIR_DUEL, Variant.TIR_RADIO)
        Link.NAME_TO_SOUND -> setOf(Variant.KAMERTON_SING, Variant.KAMERTON_DIGITS)
        Link.PLACE_TO_SOUND -> setOf(Variant.KAMERTON_PREDICT)
        Link.SOUND_TO_PLACE -> setOf(Variant.PARROT_ECHO, Variant.PARROT_CALL_RESPONSE)
    }

    private fun targetMs(link: Link): Long = when (link) {
        Link.NAME_TO_PLACE, Link.PLACE_TO_NAME -> 1_500
        Link.SOUND_TO_NAME -> 2_500
        else -> 3_000
    }

    /**
     * Strength of each link, 0..1, from the last 60 attempts: accuracy × (½ + ½ speed), where
     * speed = target / median latency (capped at 1). null = never trained.
     */
    fun linkScores(attempts: List<AttemptRecord>): Map<Link, Double?> = Link.entries.associateWith { link ->
        val variants = variantsOf(link)
        val recent = attempts.filter { it.variant in variants }.takeLast(60)
        if (recent.isEmpty()) {
            null
        } else {
            val acc = recent.count { it.correct }.toDouble() / recent.size
            val med = median(recent.filter { it.correct }.map { it.latencyMs })
            val speed = if (med == null || med == 0L) 0.0 else min(1.0, targetMs(link).toDouble() / med)
            acc * (0.5 + 0.5 * speed)
        }
    }

    /** One line per variant for the Progress screen. */
    data class VariantStats(val variant: Variant, val attempts: Int, val accuracy: Double, val medianMs: Long?)

    fun variantStats(attempts: List<AttemptRecord>): List<VariantStats> =
        attempts.groupBy { it.variant }.map { (v, list) ->
            val recent = list.takeLast(100)
            VariantStats(v, list.size, recent.count { it.correct }.toDouble() / recent.size, median(recent.filter { it.correct }.map { it.latencyMs }))
        }.sortedBy { it.variant.ordinal }
}

/**
 * Entrance exam and credits (ticket G-04): fixed task counts, no lives, no clock. Each stage
 * measures one [Skill]; [measure] turns its attempts into the number compared with [Graduation].
 */
data class ExamStage(val skill: Skill, val variant: Variant, val level: Int, val tasks: Int)

object Exam {
    /** 0.1 battery. Memory (Taper without hints) is run separately after these stages. */
    val STAGES_V01 = listOf(
        ExamStage(Skill.NECK_LOW_STRINGS, Variant.HUNT_FIND, level = 1, tasks = 20),
        ExamStage(Skill.NECK_FULL, Variant.HUNT_FIND, level = 3, tasks = 20),
        ExamStage(Skill.EAR_DEGREES, Variant.TIR_CLASSIC, level = 2, tasks = 20),
        ExamStage(Skill.EAR_PROGRESSIONS, Variant.TIR_CLASSIC, level = 6, tasks = 10),
    )

    const val MEMORY_ITEMS = 10

    /** Hunt: mean seconds over all answers (timeouts count as the full deadline). Tir: accuracy. */
    fun measure(skill: Skill, attempts: List<AttemptRecord>): Double = when (skill) {
        Skill.NECK_LOW_STRINGS, Skill.NECK_FULL ->
            if (attempts.isEmpty()) HuntDriver.EXAM_LIMIT_MS / 1000.0
            else attempts.map { if (it.correct) it.latencyMs else HuntDriver.EXAM_LIMIT_MS }.average() / 1000.0
        else -> if (attempts.isEmpty()) 0.0 else attempts.count { it.correct }.toDouble() / attempts.size
    }
}
