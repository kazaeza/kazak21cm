package com.kazaeza.muzykalka.game

import kotlin.math.roundToInt

/** Mastery condition for one variant/level inside a [TrackStage]. */
data class StageCriterion(
    val variant: Variant,
    val level: Int = 1,
    val minAttempts: Int = 20,
    val minAccuracy: Double = 0.80,
    val maxMedianMs: Long? = null,
)

/** One step of a structured training track. */
data class TrackStage(
    val id: String,
    val title: String,
    val goal: String,
    val drills: List<SetSegment>,
    val criteria: List<StageCriterion>,
    val minLevelByVariant: Map<Variant, Int> = emptyMap(),
)

/** A structured training program composed of ordered [stages]. */
data class Track(
    val id: String,
    val ru: String,
    val subtitle: String,
    val guitar: GuitarNeed,
    val requiresVoice: Boolean,
    val stages: List<TrackStage>,
)

/** Live evaluation of a single [StageCriterion] against attempt history. */
data class CriterionProgress(
    val criterion: StageCriterion,
    val attempts: Int,
    val accuracy: Double,
    val medianMs: Long?,
    val met: Boolean,
    val fraction: Double,
)

/** Live evaluation of a [TrackStage]. */
data class StageProgress(
    val stage: TrackStage,
    val fraction: Double,
    val done: Boolean,
    val summary: String,
    val details: List<CriterionProgress>,
)

/** Live evaluation of a [Track], identifying the current active stage. */
data class TrackProgress(
    val track: Track,
    val stages: List<StageProgress>,
    val currentStageIndex: Int,
    val overallFraction: Double,
    val completedStages: Int,
) {
    val currentStage: TrackStage get() = track.stages[currentStageIndex]
    val completed: Boolean get() = completedStages == track.stages.size
}

/**
 * Structured training tracks with live stage mastery computed from real [AttemptRecord] history.
 * Five non-guitar tracks form the core; physical-guitar practice lives in [GUITAR].
 */
object Tracks {
    const val EVAL_WINDOW = 50

    val STEPS_BASE = Track(
        id = "STEPS_BASE",
        ru = "Ступени до автоматизма",
        subtitle = "От опор 1·3·5 к семи ступеням, аккордам и хроматике",
        guitar = GuitarNeed.NONE,
        requiresVoice = false,
        stages = listOf(
            TrackStage(
                id = "steps_1",
                title = "Опоры 1·3·5",
                goal = "Уверенно слышать тонику, терцию и квинту",
                drills = listOf(
                    SetSegment(Variant.TIR_CLASSIC, 3.0),
                    SetSegment(Variant.TIR_DUEL, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_CLASSIC, level = 1, minAttempts = 25, minAccuracy = 0.85, maxMedianMs = 2_500L),
                ),
                minLevelByVariant = mapOf(Variant.TIR_CLASSIC to 1),
            ),
            TrackStage(
                id = "steps_2",
                title = "Все 7 ступеней",
                goal = "Узнавать любую ступень лада без подбора",
                drills = listOf(
                    SetSegment(Variant.TIR_CLASSIC, 3.0),
                    SetSegment(Variant.TIR_DUEL, 2.0),
                    SetSegment(Variant.TIR_SIMON, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_CLASSIC, level = 2, minAttempts = 30, minAccuracy = 0.85, maxMedianMs = 2_500L),
                ),
                minLevelByVariant = mapOf(Variant.TIR_CLASSIC to 2),
            ),
            TrackStage(
                id = "steps_3",
                title = "В аккордах и цепочках",
                goal = "Слышать ступень над движущимися аккордами и в мелодии",
                drills = listOf(
                    SetSegment(Variant.TIR_OVER_CHORDS, 3.0),
                    SetSegment(Variant.TIR_SIMON, 2.5),
                    SetSegment(Variant.TIR_CLASSIC, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_OVER_CHORDS, level = 1, minAttempts = 20, minAccuracy = 0.80),
                    StageCriterion(Variant.TIR_SIMON, level = 1, minAttempts = 15, minAccuracy = 0.75),
                ),
                minLevelByVariant = mapOf(Variant.TIR_OVER_CHORDS to 1, Variant.TIR_SIMON to 1),
            ),
            TrackStage(
                id = "steps_4",
                title = "Хроматика и созвучия",
                goal = "Различать альтерированные ступени и интервалы без дрона",
                drills = listOf(
                    SetSegment(Variant.TIR_CLASSIC, 3.0),
                    SetSegment(Variant.TIR_DYAD, 2.5),
                    SetSegment(Variant.TIR_OVER_CHORDS, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_CLASSIC, level = 3, minAttempts = 25, minAccuracy = 0.80),
                    StageCriterion(Variant.TIR_DYAD, level = 1, minAttempts = 20, minAccuracy = 0.80),
                ),
                minLevelByVariant = mapOf(Variant.TIR_CLASSIC to 3, Variant.TIR_DYAD to 1),
            ),
        ),
    )

    val CHORDS_FUNC = Track(
        id = "CHORDS_FUNC",
        ru = "Аккорды, бас и функции",
        subtitle = "Функция аккорда, бас, переходы, цвет и лады",
        guitar = GuitarNeed.NONE,
        requiresVoice = false,
        stages = listOf(
            TrackStage(
                id = "chords_1",
                title = "Дом · Уход · Напряжение",
                goal = "Чувствовать функцию аккорда и главные ступени над тоникой",
                drills = listOf(
                    SetSegment(Variant.TIR_LAYERS, 3.0),
                    SetSegment(Variant.TIR_ON_TONIC, 3.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_LAYERS, level = 1, minAttempts = 20, minAccuracy = 0.80),
                    StageCriterion(Variant.TIR_ON_TONIC, level = 1, minAttempts = 20, minAccuracy = 0.80),
                ),
                minLevelByVariant = mapOf(Variant.TIR_LAYERS to 1, Variant.TIR_ON_TONIC to 1),
            ),
            TrackStage(
                id = "chords_2",
                title = "Главные аккорды и бас",
                goal = "Узнавать I · IV · V · vi целиком и по одному басу",
                drills = listOf(
                    SetSegment(Variant.TIR_ON_TONIC, 3.0),
                    SetSegment(Variant.TIR_BASS, 3.0),
                    SetSegment(Variant.TIR_LAYERS, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_ON_TONIC, level = 2, minAttempts = 20, minAccuracy = 0.80),
                    StageCriterion(Variant.TIR_BASS, level = 1, minAttempts = 20, minAccuracy = 0.80),
                ),
                minLevelByVariant = mapOf(Variant.TIR_ON_TONIC to 2, Variant.TIR_BASS to 1),
            ),
            TrackStage(
                id = "chords_3",
                title = "Переходы и цвет",
                goal = "Слышать движение между аккордами и краску трезвучий",
                drills = listOf(
                    SetSegment(Variant.TIR_TWO_CHORDS, 3.0),
                    SetSegment(Variant.TIR_COLOR, 2.5),
                    SetSegment(Variant.TIR_BASS, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_TWO_CHORDS, level = 1, minAttempts = 20, minAccuracy = 0.80),
                    StageCriterion(Variant.TIR_COLOR, level = 1, minAttempts = 20, minAccuracy = 0.80),
                ),
                minLevelByVariant = mapOf(Variant.TIR_TWO_CHORDS to 1, Variant.TIR_COLOR to 1),
            ),
            TrackStage(
                id = "chords_4",
                title = "Лады и аккорд под нотой",
                goal = "Слышать лад целиком и гармонию под мелодической нотой",
                drills = listOf(
                    SetSegment(Variant.TIR_UNDER_NOTE, 3.0),
                    SetSegment(Variant.TIR_MODE, 2.5),
                    SetSegment(Variant.TIR_TWO_CHORDS, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_UNDER_NOTE, level = 1, minAttempts = 20, minAccuracy = 0.80),
                    StageCriterion(Variant.TIR_MODE, level = 1, minAttempts = 15, minAccuracy = 0.80),
                ),
                minLevelByVariant = mapOf(Variant.TIR_UNDER_NOTE to 1, Variant.TIR_MODE to 1),
            ),
        ),
    )

    val SONGS_HEAD = Track(
        id = "SONGS_HEAD",
        ru = "Песни в голове и перенос",
        subtitle = "От узнавания гармонии к переносу в живую музыку",
        guitar = GuitarNeed.NONE,
        requiresVoice = false,
        stages = listOf(
            TrackStage(
                id = "songs_1",
                title = "Узнать гармонию",
                goal = "Слышать знакомые начала песен и простые связки аккордов",
                drills = listOf(
                    SetSegment(Variant.TIR_SONG, 3.0),
                    SetSegment(Variant.TIR_TWO_CHORDS, 3.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_SONG, level = 1, minAttempts = 15, minAccuracy = 0.80),
                    StageCriterion(Variant.TIR_TWO_CHORDS, level = 1, minAttempts = 15, minAccuracy = 0.80),
                ),
                minLevelByVariant = mapOf(Variant.TIR_SONG to 1, Variant.TIR_TWO_CHORDS to 1),
            ),
            TrackStage(
                id = "songs_2",
                title = "Петля в трёх тональностях",
                goal = "Удерживать ступень аккорда при смене тональности",
                drills = listOf(
                    SetSegment(Variant.TIR_LOOP3, 3.5),
                    SetSegment(Variant.TIR_ON_TONIC, 2.5),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_LOOP3, level = 1, minAttempts = 20, minAccuracy = 0.80),
                ),
                minLevelByVariant = mapOf(Variant.TIR_LOOP3 to 1),
            ),
            TrackStage(
                id = "songs_3",
                title = "Предсказать ход",
                goal = "Угадывать следующий аккорд и помнить формулы без подсказок",
                drills = listOf(
                    SetSegment(Variant.TIR_NEXT, 3.0),
                    SetSegment(Variant.FORMULA_ALOUD, 3.0),
                    SetSegment(Variant.TIR_SONG, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_NEXT, level = 1, minAttempts = 15, minAccuracy = 0.75),
                    StageCriterion(Variant.FORMULA_ALOUD, level = 1, minAttempts = 15, minAccuracy = 0.80),
                ),
                minLevelByVariant = mapOf(Variant.TIR_NEXT to 1),
            ),
            TrackStage(
                id = "songs_4",
                title = "Перенос в живую фактуру",
                goal = "Узнавать ступени в живом миксе так же уверенно, как над дроном",
                drills = listOf(
                    SetSegment(Variant.TIR_TRANSFER, 3.0),
                    SetSegment(Variant.TIR_LOOP3, 2.5),
                    SetSegment(Variant.FORMULA_ALOUD, 2.5),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_TRANSFER, level = 1, minAttempts = 20, minAccuracy = 0.75),
                    StageCriterion(Variant.TIR_LOOP3, level = 2, minAttempts = 20, minAccuracy = 0.80),
                ),
                minLevelByVariant = mapOf(Variant.TIR_TRANSFER to 1, Variant.TIR_LOOP3 to 2),
            ),
        ),
    )

    val VOICE_EAR = Track(
        id = "VOICE_EAR",
        ru = "Голос и внутренний слух",
        subtitle = "Представлять ноту до звучания и попадать голосом",
        guitar = GuitarNeed.NONE,
        requiresVoice = true,
        stages = listOf(
            TrackStage(
                id = "voice_1",
                title = "Опоры голосом",
                goal = "Находить 1, 3 и 5 голосом над дроном",
                drills = listOf(
                    SetSegment(Variant.KAMERTON_SEARCH, 3.0),
                    SetSegment(Variant.TIR_CLASSIC, 2.5),
                ),
                criteria = listOf(
                    StageCriterion(Variant.KAMERTON_SEARCH, level = 1, minAttempts = 20, minAccuracy = 0.60),
                ),
                minLevelByVariant = mapOf(Variant.KAMERTON_SEARCH to 1, Variant.TIR_CLASSIC to 1),
            ),
            TrackStage(
                id = "voice_2",
                title = "Весь лад в Пропевке",
                goal = "Находить любую из семи ступеней голосом с первой попытки",
                drills = listOf(
                    SetSegment(Variant.KAMERTON_SEARCH, 3.5),
                    SetSegment(Variant.TIR_CLASSIC, 2.5),
                ),
                criteria = listOf(
                    StageCriterion(Variant.KAMERTON_SEARCH, level = 2, minAttempts = 20, minAccuracy = 0.65),
                ),
                minLevelByVariant = mapOf(Variant.KAMERTON_SEARCH to 2),
            ),
            TrackStage(
                id = "voice_3",
                title = "Спой ступень с первой ноты",
                goal = "Петь заданную ступень сразу и удерживать короткие мелодии",
                drills = listOf(
                    SetSegment(Variant.KAMERTON_SING, 3.0),
                    SetSegment(Variant.TIR_SIMON, 2.5),
                    SetSegment(Variant.KAMERTON_SEARCH, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.KAMERTON_SING, level = 1, minAttempts = 20, minAccuracy = 0.75),
                    StageCriterion(Variant.TIR_SIMON, level = 1, minAttempts = 15, minAccuracy = 0.75),
                ),
                minLevelByVariant = mapOf(Variant.KAMERTON_SING to 1, Variant.TIR_SIMON to 1),
            ),
        ),
    )

    val NECK_HEAD = Track(
        id = "NECK_HEAD",
        ru = "Гриф в голове",
        subtitle = "Знать ноты на грифе тапами — без инструмента в руках",
        guitar = GuitarNeed.NONE,
        requiresVoice = false,
        stages = listOf(
            TrackStage(
                id = "neck_1",
                title = "6-я и 5-я струны",
                goal = "Мгновенно называть опорные ноты на басовых струнах",
                drills = listOf(
                    SetSegment(Variant.HUNT_REVERSE, 3.5),
                    SetSegment(Variant.TIR_CLASSIC, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.HUNT_REVERSE, level = 1, minAttempts = 20, minAccuracy = 0.85, maxMedianMs = 2_000L),
                ),
                minLevelByVariant = mapOf(Variant.HUNT_REVERSE to 1),
            ),
            TrackStage(
                id = "neck_2",
                title = "Весь гриф тапами",
                goal = "Свободно ориентироваться на средних струнах и по всему грифу",
                drills = listOf(
                    SetSegment(Variant.HUNT_REVERSE, 4.0),
                    SetSegment(Variant.TIR_UNDER_NOTE, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.HUNT_REVERSE, level = 2, minAttempts = 20, minAccuracy = 0.85, maxMedianMs = 2_200L),
                ),
                minLevelByVariant = mapOf(Variant.HUNT_REVERSE to 2),
            ),
            TrackStage(
                id = "neck_3",
                title = "Ориентация и слух",
                goal = "Быстро видеть любую ноту на всём грифе и связывать со слухом",
                drills = listOf(
                    SetSegment(Variant.HUNT_REVERSE, 3.5),
                    SetSegment(Variant.TIR_CLASSIC, 2.5),
                ),
                criteria = listOf(
                    StageCriterion(Variant.HUNT_REVERSE, level = 3, minAttempts = 25, minAccuracy = 0.85, maxMedianMs = 2_200L),
                    StageCriterion(Variant.TIR_CLASSIC, level = 2, minAttempts = 20, minAccuracy = 0.85),
                ),
                minLevelByVariant = mapOf(Variant.HUNT_REVERSE to 3, Variant.TIR_CLASSIC to 2),
            ),
        ),
    )

    val GUITAR_HANDS = Track(
        id = "GUITAR_HANDS",
        ru = "Гитара в руках",
        subtitle = "Гриф по микрофону, бас-линии, ходы и песни в любой тональности",
        guitar = GuitarNeed.REQUIRED,
        requiresVoice = false,
        stages = listOf(
            TrackStage(
                id = "guitar_1",
                title = "Гриф по микрофону",
                goal = "Находить любую ноту и все её октавы на живой гитаре",
                drills = listOf(
                    SetSegment(Variant.HUNT_FIND, 3.0),
                    SetSegment(Variant.HUNT_ALL_OCTAVES, 3.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.HUNT_FIND, level = 1, minAttempts = 20, minAccuracy = 0.85, maxMedianMs = 2_000L),
                    StageCriterion(Variant.HUNT_ALL_OCTAVES, level = 1, minAttempts = 15, minAccuracy = 0.80),
                ),
                minLevelByVariant = mapOf(Variant.HUNT_FIND to 1, Variant.HUNT_ALL_OCTAVES to 1),
            ),
            TrackStage(
                id = "guitar_2",
                title = "Бас-линия и ходы",
                goal = "Снимать бас по слуху и играть базовые аккордовые связки",
                drills = listOf(
                    SetSegment(Variant.TIR_BASSLINE, 3.0),
                    SetSegment(Variant.TAPER_STEPS, 3.5),
                    SetSegment(Variant.HUNT_FIND, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TIR_BASSLINE, level = 1, minAttempts = 15, minAccuracy = 0.75),
                    StageCriterion(Variant.TAPER_STEPS, level = 1, minAttempts = 15, minAccuracy = 0.80),
                ),
                minLevelByVariant = mapOf(Variant.TIR_BASSLINE to 1, Variant.TAPER_STEPS to 1),
            ),
            TrackStage(
                id = "guitar_3",
                title = "Песни в любой тональности",
                goal = "Играть песни из библиотеки по ступеням без подсказок",
                drills = listOf(
                    SetSegment(Variant.TAPER_ORDER, 4.0),
                    SetSegment(Variant.TAPER_STEPS, 3.0),
                    SetSegment(Variant.TIR_BASSLINE, 2.0),
                ),
                criteria = listOf(
                    StageCriterion(Variant.TAPER_ORDER, level = 1, minAttempts = 10, minAccuracy = 0.80),
                    StageCriterion(Variant.TAPER_STEPS, level = 2, minAttempts = 15, minAccuracy = 0.80),
                ),
                minLevelByVariant = mapOf(Variant.TAPER_STEPS to 2),
            ),
        ),
    )

    val NON_GUITAR: List<Track> = listOf(STEPS_BASE, CHORDS_FUNC, SONGS_HEAD, VOICE_EAR, NECK_HEAD)
    val GUITAR: List<Track> = listOf(GUITAR_HANDS)
    val ALL: List<Track> = NON_GUITAR + GUITAR

    fun byId(id: String?): Track = ALL.firstOrNull { it.id == id } ?: STEPS_BASE

    fun evaluate(
        track: Track,
        attempts: List<AttemptRecord>,
        unlockedLevels: Map<Variant, Int> = emptyMap(),
    ): TrackProgress {
        val byVariant = attempts.groupBy { it.variant }
        val stages = track.stages.map { stage ->
            val details = stage.criteria.map { criterion ->
                evaluateCriterion(criterion, byVariant[criterion.variant].orEmpty(), unlockedLevels)
            }
            val done = details.isNotEmpty() && details.all { it.met }
            val fraction = when {
                done -> 1.0
                details.isEmpty() -> 0.0
                else -> details.map { it.fraction }.average().coerceIn(0.0, 0.99)
            }
            StageProgress(
                stage = stage,
                fraction = fraction,
                done = done,
                summary = stageSummary(done, details),
                details = details,
            )
        }
        val completed = stages.count { it.done }
        val currentIdx = stages.indexOfFirst { !it.done }.let { idx ->
            if (idx >= 0) idx else (stages.size - 1).coerceAtLeast(0)
        }
        val overall = if (stages.isEmpty()) 0.0 else stages.map { it.fraction }.average().coerceIn(0.0, 1.0)
        return TrackProgress(
            track = track,
            stages = stages,
            currentStageIndex = currentIdx,
            overallFraction = overall,
            completedStages = completed,
        )
    }

    private fun evaluateCriterion(
        criterion: StageCriterion,
        variantAttempts: List<AttemptRecord>,
        unlockedLevels: Map<Variant, Int>,
    ): CriterionProgress {
        val unlockedPast = (unlockedLevels[criterion.variant] ?: 0) > criterion.level
        val exact = variantAttempts.filter { it.level == criterion.level }.sortedBy { it.atEpochMs }.takeLast(EVAL_WINDOW)
        val atOrAbove = variantAttempts.filter { it.level >= criterion.level }.sortedBy { it.atEpochMs }.takeLast(EVAL_WINDOW)
        val exactProgress = scoreSample(criterion, exact, unlockedPast)
        if (atOrAbove.size == exact.size) return exactProgress
        val aboveProgress = scoreSample(criterion, atOrAbove, unlockedPast)
        val exactRank = if (exactProgress.met) 2.0 else exactProgress.fraction
        val aboveRank = if (aboveProgress.met) 2.0 else aboveProgress.fraction
        return if (aboveRank > exactRank || (exact.isEmpty() && atOrAbove.isNotEmpty())) aboveProgress else exactProgress
    }

    private fun scoreSample(
        criterion: StageCriterion,
        sample: List<AttemptRecord>,
        unlockedPast: Boolean,
    ): CriterionProgress {
        val count = sample.size
        val acc = if (count == 0) 0.0 else sample.count { it.correct }.toDouble() / count
        val med = Metrics.median(sample.filter { it.correct }.map { it.latencyMs })
        val speedOk = criterion.maxMedianMs == null || (med != null && med <= criterion.maxMedianMs)
        val met = unlockedPast || (count >= criterion.minAttempts && acc >= criterion.minAccuracy && speedOk)
        val fraction = if (met) {
            1.0
        } else if (count == 0) {
            0.0
        } else {
            val vol = (count.toDouble() / criterion.minAttempts.coerceAtLeast(1)).coerceIn(0.0, 1.0)
            val accPart = (acc / criterion.minAccuracy.coerceAtLeast(0.01)).coerceIn(0.0, 1.0)
            val speedPart = when {
                criterion.maxMedianMs == null -> 1.0
                med == null || med <= 0L -> 0.0
                else -> (criterion.maxMedianMs.toDouble() / med).coerceIn(0.0, 1.0)
            }
            (vol * accPart * speedPart).coerceIn(0.0, 0.99)
        }
        return CriterionProgress(
            criterion = criterion,
            attempts = count,
            accuracy = acc,
            medianMs = med,
            met = met,
            fraction = fraction,
        )
    }

    private fun stageSummary(done: Boolean, details: List<CriterionProgress>): String {
        if (done) return "Этап освоен"
        val next = details.firstOrNull { !it.met } ?: return "Этап освоен"
        if (next.attempts == 0) return "Ещё не начат · нужно ${next.criterion.minAttempts} ответов"
        val pct = (next.accuracy * 100.0).roundToInt()
        val targetPct = (next.criterion.minAccuracy * 100.0).roundToInt()
        val maxMs = next.criterion.maxMedianMs
        if (next.attempts >= next.criterion.minAttempts && next.accuracy >= next.criterion.minAccuracy &&
            maxMs != null && (next.medianMs == null || next.medianMs > maxMs)
        ) {
            return "Точность есть · ускорить до ${formatSeconds(maxMs)}"
        }
        return "${next.attempts}/${next.criterion.minAttempts} ответов · $pct% (цель $targetPct%)"
    }

    private fun formatSeconds(ms: Long): String {
        val tenths = ((ms + 50L) / 100L).toInt()
        return "${tenths / 10},${tenths % 10} с"
    }
}
