package com.kazaeza.muzykalka.game

import kotlin.random.Random

/**
 * Adaptive item choice shared by the drills: slow and error-prone items come up more often,
 * unseen items get a fixed bonus so everything is visited.
 */
object ItemWeights {
    fun weight(itemKey: String, history: List<AttemptRecord>, unseen: Double = 2.0): Double {
        var n = 0
        var errors = 0
        val latencies = LongArray(20)
        for (i in history.indices.reversed()) {
            val a = history[i]
            if (a.itemKey != itemKey) continue
            latencies[n] = a.latencyMs
            if (!a.correct) errors++
            n++
            if (n == latencies.size) break
        }
        if (n == 0) return unseen
        val sorted = latencies.copyOf(n).also { it.sort() }
        val median = sorted[n / 2]
        return 1.0 + median / 1000.0 + 2.0 * errors / n
    }

    /** Weighted random choice; [avoid] (e.g. the previous item) is skipped when possible. */
    fun <T> pick(random: Random, items: List<T>, avoid: T? = null, weight: (T) -> Double): T {
        require(items.isNotEmpty()) { "Nothing to pick from" }
        val pool = if (items.size > 1 && avoid != null) items.filter { it != avoid }.ifEmpty { items } else items
        val weights = pool.map { maxOf(0.01, weight(it)) }
        var r = random.nextDouble() * weights.sum()
        for (i in pool.indices) {
            r -= weights[i]
            if (r <= 0) return pool[i]
        }
        return pool.last()
    }
}

/**
 * O-44 «Песня дня»: one item of [ids] for a study [day] — the same all day, another tomorrow, and over the days
 * every item gets its turn before one comes round again. Pure: the day comes as a parameter.
 */
object PickOfDay {
    fun pick(ids: Collection<String>, day: Int): String? {
        if (ids.isEmpty()) return null
        val sorted = ids.sorted()
        // a walk through the list with a step coprime to its size: every item once per round, in a mixed-up order
        val n = sorted.size
        val step = generateSequence(n / 2 + 1) { it + 1 }.first { gcd(it, n) == 1 }
        return sorted[Math.floorMod(day.toLong() * step, n.toLong()).toInt()]
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
