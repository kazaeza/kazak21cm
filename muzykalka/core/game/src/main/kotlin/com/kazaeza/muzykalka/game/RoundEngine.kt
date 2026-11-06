package com.kazaeza.muzykalka.game

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Drives one drill round. Pure state machine: time comes in as `nowMs` arguments, side effects
 * (playing audio, gating the mic, saving records) are performed by the caller in reaction to
 * state changes. This is where the plan's rules are enforced for EVERY mode:
 *
 *  - Rule 4 (first note counts): only the first accepted answer in [RoundPhase.LISTENING] is
 *    judged; everything during PROMPTING/FEEDBACK and after the first answer is dropped.
 *  - Rule 5 (wrong → hear the right answer): [Evaluation] cannot be built without a correction;
 *    the caller plays `lastEvaluation.correction` on entering FEEDBACK.
 *  - Rule 6 (60–90 s rounds): [RoundConfig] refuses other durations.
 *  - Rule 8 (every result is a number): each judged answer appends one [AttemptRecord].
 *
 * Caller protocol (ViewModel):
 *  1. `start(now)`; if phase == PROMPTING play `task.prompt.audio`, then `promptFinished(now)`.
 *  2. Forward answers with `answer(a, now)`; call `tick(now)` every ~50 ms.
 *  3. On entering FEEDBACK play `lastEvaluation.correction` (wrong) or the driver's confirmation.
 *  4. On FINISHED persist `attempts` (if not streamed already) and show the summary.
 */
class RoundEngine<T : Task>(
    private val driver: ModeDriver<T>,
    private val config: RoundConfig,
    private val random: Random,
    private val level: Int,
    history: List<AttemptRecord> = emptyList(),
) {
    private val history = history.toMutableList()
    private var startedAt = 0L
    private var listeningSince = 0L
    private var feedbackUntil = 0L

    var state: RoundState<T> = RoundState(
        phase = RoundPhase.FINISHED, task = null, level = level, score = 0, streak = 0, multiplier = 1,
        livesLeft = config.lives, remainingMs = config.durationMs, lastEvaluation = null, attempts = emptyList(),
    )
        private set

    private fun context() = DriverContext(random, level, history.takeLast(200))

    fun start(nowMs: Long): RoundState<T> {
        startedAt = nowMs
        state = state.copy(phase = RoundPhase.FINISHED) // reset guard
        return nextTask(nowMs)
    }

    private fun nextTask(nowMs: Long): RoundState<T> {
        val task = driver.nextTask(context())
        val phase = if (task.prompt.audio != null) RoundPhase.PROMPTING else RoundPhase.LISTENING
        if (phase == RoundPhase.LISTENING) listeningSince = nowMs
        state = state.copy(phase = phase, task = task, lastEvaluation = null, remainingMs = remaining(nowMs))
        return state
    }

    fun promptFinished(nowMs: Long): RoundState<T> {
        if (state.phase != RoundPhase.PROMPTING) return state
        listeningSince = nowMs
        state = state.copy(phase = RoundPhase.LISTENING, remainingMs = remaining(nowMs))
        return state
    }

    fun answer(answer: Answer, nowMs: Long): RoundState<T> {
        val task = state.task
        if (state.phase != RoundPhase.LISTENING || task == null) return state // rule 4
        if (answer !is Answer.TimedOut && driver.accepts.none { it.isInstance(answer) }) return state
        val evaluation = driver.evaluate(task, answer)
        val latency = max(0L, nowMs - listeningSince)
        val record = AttemptRecord(
            variant = driver.variant, level = level, itemKey = task.itemKey,
            expected = evaluation.expectedText, given = evaluation.givenText, correct = evaluation.correct,
            latencyMs = latency, centsError = evaluation.centsError, hintLevel = 0,
            keyLabel = task.prompt.key?.toString(), atEpochMs = nowMs,
        )
        history += record
        val streak = if (evaluation.correct) state.streak + 1 else 0
        val multiplier = min(4, 1 + streak / config.comboStep)
        val points = if (evaluation.correct) Scoring.points(latency, multiplier) else 0
        val lives = state.livesLeft?.let { if (evaluation.correct) it else it - 1 }
        feedbackUntil = nowMs + config.feedbackMs
        state = state.copy(
            phase = RoundPhase.FEEDBACK, score = state.score + points, streak = streak, multiplier = multiplier,
            livesLeft = lives, lastEvaluation = evaluation, attempts = state.attempts + record, remainingMs = remaining(nowMs),
        )
        return state
    }

    fun tick(nowMs: Long): RoundState<T> {
        if (state.phase == RoundPhase.FINISHED) return state
        val remaining = remaining(nowMs)
        val outOfLives = state.livesLeft == 0
        when {
            state.phase == RoundPhase.FEEDBACK && nowMs >= feedbackUntil ->
                return if (remaining <= 0 || outOfLives) finish(nowMs) else nextTask(nowMs)
            state.phase != RoundPhase.FEEDBACK && remaining <= 0 -> return finish(nowMs)
            state.phase == RoundPhase.LISTENING -> {
                val task = state.task
                val limit = task?.let { driver.timeLimitMs(it, context()) }
                if (limit != null && nowMs - listeningSince >= limit) return answer(Answer.TimedOut, nowMs)
            }
            else -> Unit
        }
        state = state.copy(remainingMs = remaining)
        return state
    }

    private fun finish(nowMs: Long): RoundState<T> {
        state = state.copy(phase = RoundPhase.FINISHED, remainingMs = remaining(nowMs))
        return state
    }

    private fun remaining(nowMs: Long) = max(0L, config.durationMs - (nowMs - startedAt))
}

/** Points: 100 base + up to 50 for speed (full bonus under 0.5 s, none after 2.5 s), × combo. */
object Scoring {
    fun points(latencyMs: Long, multiplier: Int): Int {
        val speed = ((2_500 - latencyMs).coerceIn(0, 2_000) / 2_000.0 * 50).toInt()
        return (100 + speed) * multiplier
    }
}
