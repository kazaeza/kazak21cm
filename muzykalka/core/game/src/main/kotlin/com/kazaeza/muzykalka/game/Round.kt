package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.NoteName
import com.kazaeza.muzykalka.music.Score
import kotlin.random.Random

/** What the user sees and hears for one task. */
data class Prompt(
    /** Big card text: "F♯ · 5-я", "?", "Autumn Leaves". */
    val title: String,
    val subtitle: String? = null,
    /** Played BEFORE listening starts (cadence + question). Mic is gated while it plays. */
    val audio: Score? = null,
    /** Key shown as the single absolute address (rule 1), if the task has one. */
    val key: Key? = null,
)

/** One question. [itemKey] is a stable id for statistics ("tir:L2:♭3", "hunt:s5:pc6"). */
interface Task {
    val itemKey: String
    val prompt: Prompt
}

/** Everything the user can answer with. Input adapters (mic, buttons, voice) produce these. */
sealed interface Answer {
    data class DegreeChoice(val degree: Degree) : Answer
    data class ChordChoice(val chord: ChordDegree) : Answer
    data class NotePlayed(val note: MidiNote, val cents: Double) : Answer
    data class NoteNamed(val name: NoteName) : Answer
    data class Sung(val note: MidiNote, val cents: Double) : Answer
    data class Formula(val notes: List<DegreeNote>) : Answer
    data class ChordFormula(val chords: List<ChordDegree>) : Answer
    data class SelfReport(val grade: SelfGrade) : Answer
    data object TimedOut : Answer
}

/**
 * Result of one answer.
 * Rule 5 («ошибся — сразу слышишь правильный ответ»): a wrong answer MUST carry the correct
 * answer as text and, when the task had sound, as audio. Enforced in the constructor.
 */
data class Evaluation(
    val correct: Boolean,
    /** Human text of the expected answer in the user's notation, e.g. "♭3". */
    val expectedText: String,
    val givenText: String,
    /** Correct answer resolved to the tonic (Tir), or the right note (Hunt, Parrot). */
    val correction: Score? = null,
    val centsError: Double? = null,
    val taskHadSound: Boolean = false,
) {
    init {
        require(expectedText.isNotBlank()) { "Rule 5: expected answer text is mandatory" }
        require(correct || !taskHadSound || correction != null) { "Rule 5: wrong answer to a sounding task needs a correction score" }
    }
}

/**
 * A mode's brain: generates tasks and judges answers. Pure and deterministic given the context.
 * Drivers never touch time, audio or storage — the [RoundEngine] owns those concerns.
 */
interface ModeDriver<T : Task> {
    val variant: Variant

    /** Answers this driver understands; anything else is ignored by the engine. */
    val accepts: Set<Class<out Answer>>

    fun nextTask(context: DriverContext): T

    /** Must handle [Answer.TimedOut] as a wrong answer (with correction, rule 5). */
    fun evaluate(task: T, answer: Answer): Evaluation

    /** Per-task answer deadline (Hunt: shrinking timer). null = no deadline. */
    fun timeLimitMs(task: T, context: DriverContext): Long? = null
}

data class DriverContext(
    val random: Random,
    val level: Int,
    /** Recent attempts of this variant (newest last) — for adaptive item selection. */
    val history: List<AttemptRecord>,
)

/**
 * Rule 8 («каждый результат — число»): every judged answer becomes exactly one record, written by
 * the engine — a mode cannot skip it. Persisted by :data; all metrics are derived from these.
 */
data class AttemptRecord(
    val variant: Variant,
    val level: Int,
    val itemKey: String,
    val expected: String,
    val given: String,
    val correct: Boolean,
    /** From the end of the prompt audio (listening start) to the answer. */
    val latencyMs: Long,
    val centsError: Double?,
    /** Taper hint ladder step reached; 0 for drills. */
    val hintLevel: Int,
    val keyLabel: String?,
    val atEpochMs: Long,
)

/** Rule 6: drill rounds last 60–90 s. Lives null = unlimited. */
data class RoundConfig(
    val durationMs: Long = 75_000,
    val lives: Int? = 3,
    /** Every [comboStep] correct answers in a row the multiplier grows by one (max 4). */
    val comboStep: Int = 5,
    /** Pause after feedback before the next task. */
    val feedbackMs: Long = 900,
) {
    init {
        require(durationMs in 60_000..90_000) { "Rule 6: a round lasts 60–90 s, got $durationMs ms" }
        require(lives == null || lives > 0)
        require(comboStep > 0)
    }
}

enum class RoundPhase {
    /** Prompt audio is playing; answers are ignored and the mic is gated. */
    PROMPTING,

    /** Waiting for the FIRST answer (rule 4). */
    LISTENING,

    /** Showing/playing the evaluation; answers ignored. */
    FEEDBACK,
    FINISHED,
}

data class RoundState<T : Task>(
    val phase: RoundPhase,
    val task: T?,
    val level: Int,
    val score: Int,
    val streak: Int,
    val multiplier: Int,
    val livesLeft: Int?,
    val remainingMs: Long,
    val lastEvaluation: Evaluation?,
    val attempts: List<AttemptRecord>,
) {
    val correctCount: Int get() = attempts.count { it.correct }
    val accuracy: Double get() = if (attempts.isEmpty()) 0.0 else correctCount.toDouble() / attempts.size
}
