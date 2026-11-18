package com.kazaeza.muzykalka.game

/**
 * The 12-week program: entrance exam (week 0), credits every two weeks, graduation.
 * Thresholds are the plan's proposals («мои предложения, а не нормы»): revisit after the entrance exam.
 */
enum class Skill(val ru: String) {
    NECK_LOW_STRINGS("Гриф, 6-я и 5-я струны"),
    NECK_FULL("Гриф целиком"),
    EAR_DEGREES("Звук → имя: 7 ступеней"),
    EAR_PROGRESSIONS("Звук → имя: ход из 4 аккордов"),
    VOICE("Имя → звук: спеть ступень"),
    ECHO("Звук → место: фраза из 4 нот"),
    MEMORY("Память: 10 вещей с ходу"),
}

enum class Comparison { LESS_THAN, AT_LEAST, AT_MOST }

data class Threshold(val skill: Skill, val check: String, val comparison: Comparison, val value: Double, val unit: String, val since: String)

object Graduation {
    val THRESHOLDS = listOf(
        Threshold(Skill.NECK_LOW_STRINGS, "любая нота, среднее время", Comparison.LESS_THAN, 1.0, "с", "0.1"),
        Threshold(Skill.NECK_FULL, "любая нота, среднее время", Comparison.LESS_THAN, 2.5, "с", "0.1"),
        Threshold(Skill.EAR_DEGREES, "7 ступеней в тональности", Comparison.AT_LEAST, 0.9, "верно", "0.1"),
        Threshold(Skill.EAR_PROGRESSIONS, "ход из 4 аккордов", Comparison.AT_LEAST, 0.8, "верно", "0.1"),
        Threshold(Skill.VOICE, "медиана отклонения", Comparison.AT_MOST, 30.0, "центов", "0.2"),
        Threshold(Skill.ECHO, "с первой попытки", Comparison.AT_LEAST, 0.8, "верно", "0.2"),
        Threshold(Skill.MEMORY, "10 случайных из 20, случайная тональность, без подсказок", Comparison.AT_LEAST, 8.0, "вещей", "0.1"),
    )

    fun passed(t: Threshold, measured: Double): Boolean = when (t.comparison) {
        Comparison.LESS_THAN -> measured < t.value
        Comparison.AT_LEAST -> measured >= t.value
        Comparison.AT_MOST -> measured <= t.value
    }
}

/** One block of the daily set. */
data class SetSegment(val variant: Variant, val minutes: Double)

/**
 * O-55 (ADR-054): the sets to choose from. «Сет дня» mixes everything; the others each lean one way — the guitar in
 * hands, or none at all («гитара далеко не всегда есть с собой»): the ear with headphones, songs recalled in the head,
 * and the road, where nothing is sung aloud and a round is short. [guitar] is what the set needs as a whole (the most
 * of its parts); [goal] says in a line what it trains. Minutes are a budget: the set screen turns them into rounds.
 */
enum class Training(val ru: String, val goal: String, val guitar: GuitarNeed, val plan: List<SetSegment>) {
    DAILY(
        "Сет дня",
        "по кусочку каждого режима",
        GuitarNeed.REQUIRED,
        listOf(
            SetSegment(Variant.HUNT_FIND, 2.0),
            SetSegment(Variant.TIR_CLASSIC, 4.0),
            SetSegment(Variant.KAMERTON_SING, 3.0),
            SetSegment(Variant.PARROT_ECHO, 3.0),
            SetSegment(Variant.TAPER_STEPS, 3.0),
            SetSegment(Variant.TAPER_ORDER, 5.0),
        ),
    ),
    GUITAR(
        "Гитара в руках",
        "гриф, звук в пальцы и песни в разных тональностях",
        GuitarNeed.REQUIRED,
        listOf(
            SetSegment(Variant.HUNT_FIND, 3.0),
            SetSegment(Variant.PARROT_ECHO, 3.0),
            SetSegment(Variant.TAPER_STEPS, 4.0),
            SetSegment(Variant.TAPER_ORDER, 6.0),
        ),
    ),
    EAR(
        "Слух",
        "ступени, созвучия, аккорды и лады на слух — в наушниках",
        GuitarNeed.NONE,
        listOf(
            SetSegment(Variant.TIR_CLASSIC, 3.0),
            SetSegment(Variant.TIR_DYAD, 2.0),
            SetSegment(Variant.TIR_OVER_CHORDS, 3.0),
            SetSegment(Variant.TIR_COLOR, 3.0),
            SetSegment(Variant.TIR_BASS, 2.0),
            SetSegment(Variant.TIR_MODE, 2.0),
            SetSegment(Variant.TIR_SIMON, 3.0),
        ),
    ),
    SONGS(
        "Песни в голове",
        "аккорд над тоникой, два аккорда, узнать ход, угадать следующий, вспомнить формулу",
        GuitarNeed.NONE,
        listOf(
            SetSegment(Variant.TIR_ON_TONIC, 2.0),
            SetSegment(Variant.TIR_TWO_CHORDS, 2.0),
            SetSegment(Variant.TIR_SONG, 2.0),
            SetSegment(Variant.TIR_NEXT, 3.0),
            SetSegment(Variant.FORMULA_ALOUD, 3.0),
        ),
    ),
    ROAD(
        "В дороге",
        "коротко и молча: петь не надо, хватит одной руки",
        GuitarNeed.NONE,
        listOf(
            SetSegment(Variant.TIR_CLASSIC, 2.0),
            SetSegment(Variant.TIR_UNDER_NOTE, 2.0),
            SetSegment(Variant.HUNT_REVERSE, 2.0),
        ),
    ),
    ;

    /** No part of it listens to the guitar or to the voice: it can be done where nothing can be played or sung. */
    val silent: Boolean get() = plan.none { it.variant.guitar == GuitarNeed.REQUIRED || it.variant in VOICE }

    companion object {
        /** Parts that listen to the voice. */
        val VOICE = setOf(Variant.KAMERTON_SING, Variant.KAMERTON_SEARCH)

        /** What «Сегодня» offers besides the daily set: the guitar first, then the ones without it. */
        val OTHERS = listOf(GUITAR, EAR, SONGS, ROAD)

        /** The name a route carries; an unknown one (an older build's) is the daily set. */
        fun of(name: String?): Training = entries.firstOrNull { it.name == name } ?: DAILY
    }
}

/**
 * The daily set (20 min) and the short day (5 min). Defaults from the plan, except that the Taper
 * block starts with «Ходы» — banal progressions as the base — before the user's own songs
 * (ADR-014). The adaptive planner (ticket S-01) redistributes time within ±50% of these.
 */
object DailySet {
    val DEFAULT = Training.DAILY.plan

    /** «Нет времени? 5 минут»: still counts toward the streak. */
    val MINIMAL = listOf(
        SetSegment(Variant.TIR_CLASSIC, 1.5),
        SetSegment(Variant.TAPER_STEPS, 1.5),
        SetSegment(Variant.TAPER_ORDER, 2.0),
    )

    /**
     * Nothing from the library is due (or the library is empty): the songs' minutes go to «Ходы»
     * instead of an empty bar, so the set never ends on «nothing to play».
     */
    fun withoutSongs(plan: List<SetSegment>): List<SetSegment> {
        val songMinutes = plan.filter { it.variant == Variant.TAPER_ORDER }.sumOf { it.minutes }
        if (songMinutes == 0.0) return plan
        val rest = plan.filter { it.variant != Variant.TAPER_ORDER }
        return if (rest.any { it.variant == Variant.TAPER_STEPS }) {
            rest.map { if (it.variant == Variant.TAPER_STEPS) it.copy(minutes = it.minutes + songMinutes) else it }
        } else {
            rest + SetSegment(Variant.TAPER_STEPS, songMinutes)
        }
    }

    /** Only segments whose variant has shipped in [appVersion] ("0.1" ≤ "0.2" ≤ "0.3"). */
    fun available(plan: List<SetSegment>, appVersion: String): List<SetSegment> =
        plan.filter { it.variant.since <= appVersion }
}
