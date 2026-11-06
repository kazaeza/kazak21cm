package com.kazaeza.muzykalka.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.data.repo.SessionKind
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.game.Answer
import com.kazaeza.muzykalka.game.AttemptRecord
import com.kazaeza.muzykalka.game.HuntDriver
import com.kazaeza.muzykalka.game.LevelRules
import com.kazaeza.muzykalka.game.Levels
import com.kazaeza.muzykalka.game.ModeId
import com.kazaeza.muzykalka.game.RoundConfig
import com.kazaeza.muzykalka.game.RoundEngine
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.game.RoundState
import com.kazaeza.muzykalka.game.Task
import com.kazaeza.muzykalka.game.TirDriver
import com.kazaeza.muzykalka.game.TirTask
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Fretboard
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.NoteNaming
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** What to play. [level] null = the level selected in Modes. */
data class RoundSpec(
    val variant: Variant,
    val level: Int? = null,
    val exam: Boolean = false,
    val examTasks: Int = 20,
    /** Set when the round is part of a daily set or an exam. */
    val sessionId: Long? = null,
    val durationMs: Long = 75_000,
)

data class RoundSummary(
    val variant: Variant,
    val level: Int,
    val score: Int,
    val answered: Int,
    val correct: Int,
    val medianMs: Long?,
    val bestStreak: Int,
    val newRecord: Boolean,
    val unlockedLevel: Int?,
    val attempts: List<AttemptRecord>,
) {
    val accuracy: Double get() = if (answered == 0) 0.0 else correct.toDouble() / answered
    val stars: Int get() = when {
        answered == 0 -> 0
        accuracy >= 0.9 -> 3
        accuracy >= 0.7 -> 2
        accuracy >= 0.5 -> 1
        else -> 0
    }
}

data class FeedbackEvent(val serial: Int, val correct: Boolean, val points: Int, val expected: String, val given: String)

data class GameUi(
    val variant: Variant,
    val loading: Boolean = true,
    val level: Int = 1,
    val phase: RoundPhase = RoundPhase.PROMPTING,
    val task: Task? = null,
    val taskSerial: Int = 0,
    val score: Int = 0,
    val streak: Int = 0,
    val multiplier: Int = 1,
    val lives: Int? = null,
    val maxLives: Int = 3,
    val remainingMs: Long = 0,
    val totalMs: Long = 75_000,
    val answered: Int = 0,
    val taskLimit: Int? = null,
    val taskFraction: Float? = null,
    val feedback: FeedbackEvent? = null,
    val micLevelDb: Double = -90.0,
    val heard: MidiNote? = null,
    val micError: String? = null,
    val needsMic: Boolean = false,
    val notation: DegreeNotation = DegreeNotation.ARABIC,
    val naming: NoteNaming = NoteNaming.LATIN,
    val settings: Settings = Settings(),
    val summary: RoundSummary? = null,
    val exam: Boolean = false,
)

fun Variant.needsMic(): Boolean = this == Variant.HUNT_FIND || this == Variant.HUNT_ALL_OCTAVES

object Drills {
    /** Variants with a drill driver in this build (Taper has its own screen). */
    val PLAYABLE = setOf(Variant.TIR_CLASSIC, Variant.HUNT_FIND, Variant.HUNT_ALL_OCTAVES, Variant.HUNT_REVERSE)

    fun maxLevel(v: Variant): Int = when (v) {
        Variant.HUNT_FIND -> Levels.HUNT.size
        Variant.HUNT_REVERSE -> 3
        Variant.HUNT_ALL_OCTAVES -> 1
        Variant.TIR_CLASSIC -> Levels.TIR.size
        else -> 1
    }

    fun levelName(v: Variant, level: Int): String = when (v.mode) {
        ModeId.HUNT -> if (v == Variant.HUNT_ALL_OCTAVES) "весь гриф" else Levels.HUNT.getOrElse(level - 1) { "" }
        ModeId.TIR -> Levels.TIR.getOrElse(level - 1) { "" }
        else -> ""
    }

    fun promotionTargetMs(v: Variant, level: Int): Long = when (v.mode) {
        ModeId.TIR -> TirDriver.promotionTargetMs(level)
        else -> HuntDriver.promotionTargetMs(level)
    }

    fun engine(spec: RoundSpec, level: Int, settings: Settings, history: List<AttemptRecord>, epochOffsetMs: Long): RoundEngine<*> {
        val config = if (spec.exam) RoundConfig.exam(spec.examTasks) else RoundConfig(durationMs = spec.durationMs, lives = 3)
        val random = Random(System.nanoTime())
        return when (spec.variant) {
            Variant.TIR_CLASSIC, Variant.TIR_DUEL, Variant.TIR_RADIO ->
                RoundEngine(TirDriver(KeyPicker(random), settings.degreeNotation, spec.variant), config, random, level, history, epochOffsetMs)
            Variant.HUNT_FIND, Variant.HUNT_REVERSE, Variant.HUNT_ALL_OCTAVES ->
                RoundEngine(
                    HuntDriver(spec.variant, Fretboard(settings.tuning, settings.fretCount), settings.noteNaming, settings.degreeNotation, adaptiveTimer = !spec.exam),
                    config, random, level, history, epochOffsetMs,
                )
            else -> error("${spec.variant} is not a drill")
        }
    }
}

/**
 * Hosts one round (ticket U-02), following the RoundEngine protocol: play the prompt, then
 * listen; feed mic onsets and button taps as answers; play confirmation/correction on feedback;
 * save every attempt when the round ends (or is closed).
 */
class GameViewModel(private val graph: AppGraph, private val spec: RoundSpec) : ViewModel() {
    private val state = MutableStateFlow(GameUi(variant = spec.variant, exam = spec.exam))
    val ui: StateFlow<GameUi> = state.asStateFlow()

    private var engine: RoundEngine<*>? = null
    private var history: List<AttemptRecord> = emptyList()
    private var sessionId: Long? = null
    private var ownSession = false
    private var level = 1
    private var settings = Settings()
    private var feedbackAudio: Job? = null
    private var replayJob: Job? = null
    private val micJobs = mutableListOf<Job>()
    private val headphones = MutableStateFlow(false)
    private var feedbackSerial = 0
    private var lastFeedback: FeedbackEvent? = null
    private var finished = false
    private var micLevel = -90.0
    private var heard: MidiNote? = null

    init {
        viewModelScope.launch {
            setup()
            loop()
        }
    }

    private fun now() = System.nanoTime() / 1_000_000

    private suspend fun setup() {
        settings = graph.settings.settings.first()
        graph.audioOutput.a4Hz = settings.a4Hz
        val progress = graph.progress.current()
        level = (spec.level ?: progress.selectedLevel(spec.variant)).coerceIn(1, Drills.maxLevel(spec.variant))
        history = graph.practice.recentAttempts(spec.variant, 300)
        engine = Drills.engine(spec, level, settings, history, System.currentTimeMillis() - now())
        sessionId = spec.sessionId ?: graph.practice.startSession(SessionKind.FREE, graph.today(settings)).also { ownSession = true }
        val needsMic = spec.variant.needsMic()
        state.update {
            it.copy(
                loading = false, level = level, needsMic = needsMic, notation = settings.degreeNotation, naming = settings.noteNaming,
                settings = settings, totalMs = if (spec.exam) 0 else spec.durationMs, maxLives = if (spec.exam) 0 else 3,
                taskLimit = if (spec.exam) spec.examTasks else null,
            )
        }
        if (needsMic) startMic()
    }

    private var lastPhase: RoundPhase? = null
    private var lastSerial = -1

    /** Reacts to a state change at once (sound, feedback), not on the next tick. */
    private fun react(e: RoundEngine<*>) {
        val s = e.state
        if (s.phase != lastPhase || s.taskSerial != lastSerial) {
            lastPhase = s.phase
            lastSerial = s.taskSerial
            onEnter(e, s)
        }
        publish(e)
    }

    private suspend fun loop() {
        val e = engine ?: return
        e.start(now())
        while (true) {
            react(e)
            if (e.state.phase == RoundPhase.FINISHED) {
                finish(e)
                return
            }
            delay(33)
            if (feedbackAudio?.isActive != true) e.tick(now())
        }
    }

    private fun onEnter(e: RoundEngine<*>, s: RoundState<*>) {
        when (s.phase) {
            RoundPhase.PROMPTING -> {
                val audio = s.task?.prompt?.audio
                viewModelScope.launch {
                    if (audio != null) graph.audioOutput.play(audio)
                    e.promptFinished(now())
                    react(e)
                }
            }
            RoundPhase.FEEDBACK -> {
                val ev = s.lastEvaluation ?: return
                lastFeedback = FeedbackEvent(++feedbackSerial, ev.correct, s.lastPoints, ev.expectedText, ev.givenText)
                val sound = if (ev.correct) ev.confirmation else ev.correction
                if (sound != null) {
                    feedbackAudio = viewModelScope.launch {
                        graph.audioOutput.play(sound)
                        delay(120)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun publish(e: RoundEngine<*>) {
        val s = e.state
        val limit = if (s.phase == RoundPhase.LISTENING) e.currentTimeLimitMs() else null
        val fraction = limit?.let { (1f - (now() - e.listeningSinceMs).toFloat() / it).coerceIn(0f, 1f) }
        state.update {
            it.copy(
                phase = s.phase, task = s.task, taskSerial = s.taskSerial, score = s.score, streak = s.streak,
                multiplier = s.multiplier, lives = s.livesLeft, remainingMs = s.remainingMs, answered = s.attempts.size,
                taskFraction = fraction, feedback = lastFeedback, micLevelDb = micLevel, heard = heard,
            )
        }
    }

    /** Button / pad answers. */
    fun answer(answer: Answer) {
        val e = engine ?: return
        e.answer(answer, now())
        react(e)
    }

    /** Tir: hear the question again (cadence not repeated). */
    fun replay() {
        val e = engine ?: return
        val task = e.state.task as? TirTask ?: return
        if (e.state.phase != RoundPhase.LISTENING || replayJob?.isActive == true) return
        replayJob = viewModelScope.launch { graph.audioOutput.play(task.question) }
    }

    /** User closed the round: keep what was answered. */
    fun quit() {
        val e = engine ?: return
        if (finished) return
        e.abort(now())
        graph.appScope.launch { finish(e) }
    }

    private fun startMic() {
        graph.pitchInput.start(settings.inputDeviceId, settings.a4Hz)
        micJobs += viewModelScope.launch {
            combine(graph.headphones.connected, flowOf(settings.headphones)) { a, b -> a || b }.collect { headphones.value = it }
        }
        micJobs += viewModelScope.launch {
            graph.gate.onsets(graph.pitchInput, graph.audioOutput, null, headphones).collect { onset ->
                val e = engine ?: return@collect
                e.answer(Answer.NotePlayed(onset.note, onset.cents), onset.timeNanos / 1_000_000)
                react(e)
            }
        }
        micJobs += viewModelScope.launch {
            var last = 0L
            graph.pitchInput.frames.collect { f ->
                micLevel = f.rmsDb
                val est = f.estimate
                heard = if (est != null && est.clarity > 0.85 && f.rmsDb > -50) MidiNote.nearest(est.hz, settings.a4Hz).note else null
                val t = now()
                if (t - last > 70) {
                    last = t
                    state.update { it.copy(micLevelDb = micLevel, heard = heard) }
                }
            }
        }
        micJobs += viewModelScope.launch { graph.pitchInput.error.collect { err -> state.update { it.copy(micError = err) } } }
    }

    private fun stopMic() {
        micJobs.forEach { it.cancel() }
        micJobs.clear()
        if (spec.variant.needsMic()) graph.pitchInput.stop()
    }

    private suspend fun finish(e: RoundEngine<*>) {
        if (finished) return
        finished = true
        stopMic()
        val s = e.state
        val summary = withContext(NonCancellable) {
            graph.practice.saveAttempts(s.attempts, sessionId)
            if (ownSession && s.attempts.isNotEmpty()) sessionId?.let { graph.practice.finishSession(it) }
            val newRecord = !spec.exam && s.score > 0 && graph.progress.submitScore(spec.variant, s.score)
            val levelAttempts = (history + s.attempts).filter { it.level == level }
            val unlocked = if (!spec.exam && level < Drills.maxLevel(spec.variant) &&
                LevelRules.shouldPromote(levelAttempts, Drills.promotionTargetMs(spec.variant, level)) &&
                graph.progress.unlock(spec.variant, level + 1)
            ) level + 1 else null
            RoundSummary(
                variant = spec.variant, level = level, score = s.score, answered = s.attempts.size,
                correct = s.attempts.count { it.correct },
                medianMs = s.attempts.filter { it.correct }.map { it.latencyMs }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] },
                bestStreak = s.bestStreak, newRecord = newRecord, unlockedLevel = unlocked, attempts = s.attempts,
            )
        }
        state.update { it.copy(summary = summary, phase = RoundPhase.FINISHED) }
    }

    override fun onCleared() {
        stopMic()
        graph.audioOutput.stop()
        val e = engine
        if (e != null && !finished) {
            e.abort(now())
            graph.appScope.launch { finish(e) }
        }
    }
}
