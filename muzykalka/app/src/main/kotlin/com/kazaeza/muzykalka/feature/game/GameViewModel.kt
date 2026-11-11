package com.kazaeza.muzykalka.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.data.repo.SessionKind
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.dsp.DEFAULT_MIN_HOLD_MS
import com.kazaeza.muzykalka.game.Answer
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.DegreeAnchor
import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.game.TirTask
import com.kazaeza.muzykalka.game.SingTask
import com.kazaeza.muzykalka.game.AnchorHints
import com.kazaeza.muzykalka.game.AttemptRecord
import com.kazaeza.muzykalka.game.HuntDriver
import com.kazaeza.muzykalka.game.LevelRules
import com.kazaeza.muzykalka.game.Levels
import com.kazaeza.muzykalka.game.ModeId
import com.kazaeza.muzykalka.game.RoundConfig
import com.kazaeza.muzykalka.game.RoundEngine
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.game.RoundState
import com.kazaeza.muzykalka.game.SpacedReview
import com.kazaeza.muzykalka.game.StepsDriver
import com.kazaeza.muzykalka.game.Task
import com.kazaeza.muzykalka.game.TirDriver
import com.kazaeza.muzykalka.game.DroneTask
import com.kazaeza.muzykalka.game.SingDriver
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Fretboard
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Overtones
import com.kazaeza.muzykalka.music.PulseKind
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.SoundPulse
import com.kazaeza.muzykalka.music.NoteNaming
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
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
    /** O-27: the newly opened level was taken — the next round is played on it. */
    val levelTaken: Boolean = false,
    /** «Пропевка» (O-14): on which try each found note was found (1 = first). */
    val foundOn: List<Int> = emptyList(),
) {
    val accuracy: Double get() = if (answered == 0) 0.0 else correct.toDouble() / answered

    /** «Пропевка»: the mean try on which a note was found; null if none was. */
    val averageTry: Double? get() = if (foundOn.isEmpty()) null else foundOn.average()

    val stars: Int get() = when {
        answered == 0 -> 0
        variant == Variant.KAMERTON_SEARCH -> averageTry.let { avg ->
            when {
                avg == null -> 0
                avg <= 1.5 -> 3
                avg <= 2.5 -> 2
                else -> 1
            }
        }
        accuracy >= 0.9 -> 3
        accuracy >= 0.7 -> 2
        accuracy >= 0.5 -> 1
        else -> 0
    }
}

data class FeedbackEvent(
    val serial: Int,
    val correct: Boolean,
    val points: Int,
    val expected: String,
    val given: String,
    /** Sung answers: signed distance to the nearest octave of the asked note (+ = too high); the ruler shows it (O-33). */
    val centsError: Double? = null,
)

/**
 * O-14 «Пропевка»: one miss of the search. [sung] — what the voice held (null after «подскажи»);
 * [centsError] — signed distance to the nearest octave of the asked degree (+ = sung too high).
 */
data class TryEvent(val serial: Int, val sung: Degree?, val centsError: Double?)

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
    /** ADR-019: the round clock is stopped, sound is off, answers are ignored. */
    val paused: Boolean = false,
    /** O-09: the user's own anchor songs, by semitones of the degree (a hint after a miss). */
    val anchors: Map<Int, DegreeAnchor> = emptyMap(),
    /** O-09: anchors of the degrees this round missed, offered in the summary. */
    val summaryAnchors: List<DegreeAnchor> = emptyList(),
    /** O-14: misses on the current note so far, and the latest one. */
    val tries: Int = 0,
    val lastTry: TryEvent? = null,
    /** The anchor that is sounding in the summary. */
    val playingAnchor: DegreeAnchor? = null,
)

fun Variant.needsMic(): Boolean = this == Variant.HUNT_FIND || this == Variant.HUNT_ALL_OCTAVES || isSung()

/** The voice answers: «Спой ступень» and «Пропевка». */
fun Variant.isSung(): Boolean = this == Variant.KAMERTON_SING || this == Variant.KAMERTON_SEARCH

/**
 * How long a sung note is listened to after its onset before it is judged (K-01, steady part).
 * O-14: longer than the 350 ms of O-07 — a voice needs time to settle; «Пропевка» waits longest.
 */
private fun sungWindowMs(v: Variant): Long = if (v == Variant.KAMERTON_SEARCH) 700L else 500L

/** After the drone starts or changes key, before the first sound over it. */
private const val DRONE_SETTLE_MS = 1_500L

/**
 * O-35: the picture of a note lags the sound by about this much (the speaker's and the screen's delay). A guess:
 * set by eye on a phone.
 */
private const val PULSE_LATENCY_MS = 60L

/** Pitch classes only: any octave of the tonic will do. */
private val PULSE_TONIC_FLOOR = MidiNote(48)

/** The drone holds a moment after an anchor's last note, so it lands home. */
private const val ANCHOR_TAIL_MS = 600L

object Drills {
    /** Variants with a drill driver in this build (library songs have their own Taper screen). */
    val PLAYABLE = setOf(
        Variant.TIR_CLASSIC, Variant.TIR_OVER_CHORDS, Variant.TIR_UNDER_NOTE, Variant.HUNT_FIND, Variant.HUNT_ALL_OCTAVES, Variant.HUNT_REVERSE,
        Variant.TAPER_STEPS, Variant.KAMERTON_SEARCH, Variant.KAMERTON_SING,
    )

    fun maxLevel(v: Variant): Int = when (v) {
        Variant.HUNT_FIND -> Levels.HUNT.size
        Variant.HUNT_REVERSE -> 3
        Variant.HUNT_ALL_OCTAVES -> 1
        Variant.TIR_CLASSIC -> Levels.TIR.size
        Variant.TIR_OVER_CHORDS -> Levels.TIR_CHORDS.size
        Variant.TIR_UNDER_NOTE -> Levels.TIR_UNDER.size
        Variant.TAPER_STEPS -> Levels.STEPS.size
        // Only «ступени от тоники» so far; intervals and melodies are later levels of K-01.
        Variant.KAMERTON_SING -> 1
        Variant.KAMERTON_SEARCH -> Levels.KAMERTON_SEARCH.size
        else -> 1
    }

    /** «Ходы» are self-graded: every level is open, start wherever it feels right. */
    fun allLevelsOpen(v: Variant): Boolean = v == Variant.TAPER_STEPS

    /** Guitar-paced drills: the longest round rule 6 allows, and no lives for honest self-grades. */
    fun guitarPaced(v: Variant): Boolean = v == Variant.TAPER_STEPS

    /** No lives and the longest round: guitar-paced drills and the calm search of «Пропевка» (O-14). */
    fun relaxed(v: Variant): Boolean = guitarPaced(v) || v.isSung()

    fun levelName(v: Variant, level: Int): String = when (v.mode) {
        ModeId.HUNT -> if (v == Variant.HUNT_ALL_OCTAVES) "весь гриф" else Levels.HUNT.getOrElse(level - 1) { "" }
        ModeId.TIR -> when (v) {
            Variant.TIR_OVER_CHORDS -> Levels.TIR_CHORDS
            Variant.TIR_UNDER_NOTE -> Levels.TIR_UNDER
            else -> Levels.TIR
        }.getOrElse(level - 1) { "" }
        ModeId.TAPER -> Levels.STEPS.getOrElse(level - 1) { "" }
        ModeId.KAMERTON -> (if (v == Variant.KAMERTON_SEARCH) Levels.KAMERTON_SEARCH else Levels.KAMERTON).getOrElse(level - 1) { "" }
        else -> ""
    }

    /** O-27 (ADR-037): the Tir's ladders climb slowly — 60 answers over three days; the rest as before. */
    fun pace(v: Variant): LevelRules.Pace = if (v.mode == ModeId.TIR) LevelRules.SLOW else LevelRules.QUICK

    fun promotionTargetMs(v: Variant, level: Int): Long = when (v.mode) {
        ModeId.TIR -> when (v) {
            Variant.TIR_OVER_CHORDS -> TirDriver.OVER_CHORDS_TARGET_MS
            Variant.TIR_UNDER_NOTE -> TirDriver.UNDER_NOTE_TARGET_MS
            else -> TirDriver.promotionTargetMs(level)
        }
        ModeId.TAPER -> StepsDriver.PROMOTION_TARGET_MS
        ModeId.KAMERTON -> if (v == Variant.KAMERTON_SEARCH) SingDriver.SEARCH_PROMOTION_TARGET_MS else SingDriver.PROMOTION_TARGET_MS
        else -> HuntDriver.promotionTargetMs(level)
    }

    fun engine(
        spec: RoundSpec,
        level: Int,
        settings: Settings,
        history: List<AttemptRecord>,
        epochOffsetMs: Long,
        review: List<SpacedReview.Item> = emptyList(),
        headphones: Boolean = false,
    ): RoundEngine<*> {
        val config = when {
            spec.exam -> RoundConfig.exam(spec.examTasks)
            relaxed(spec.variant) -> RoundConfig(durationMs = 90_000, lives = null)
            else -> RoundConfig(durationMs = spec.durationMs, lives = 3)
        }
        val random = Random(System.nanoTime())
        return when (spec.variant) {
            Variant.TAPER_STEPS -> RoundEngine(StepsDriver(KeyPicker(random), settings.degreeNotation), config, random, level, history, epochOffsetMs)
            Variant.TIR_CLASSIC, Variant.TIR_OVER_CHORDS, Variant.TIR_UNDER_NOTE, Variant.TIR_DUEL, Variant.TIR_RADIO ->
                RoundEngine(TirDriver(KeyPicker(random), settings.degreeNotation, spec.variant, review = review), config, random, level, history, epochOffsetMs)
            // Without headphones the drone would leak into the microphone (ADR-021).
            Variant.KAMERTON_SING, Variant.KAMERTON_SEARCH ->
                RoundEngine(SingDriver(KeyPicker(random), settings.degreeNotation, withDrone = headphones, variant = spec.variant), config, random, level, history, epochOffsetMs)
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

    private val pulseFlow = MutableSharedFlow<SoundPulse>(extraBufferCapacity = 32)

    /**
     * O-35: the plucks of whatever sounds, as they sound, for the strings and the sphere. A question's pluck carries
     * no pitch (ADR-015), so the picture of it says nothing about the note.
     */
    val pulses: SharedFlow<SoundPulse> = pulseFlow.asSharedFlow()

    /** Plays [score] and, in step with it, sends its plucks to [pulses]. They stop with the sound. */
    private suspend fun playPulsing(score: Score, kind: PulseKind, tonic: MidiNote?) = coroutineScope {
        val emitter = launch {
            var elapsed = 0L
            for (p in Overtones.pulses(score, kind, tonic)) {
                val due = p.atMs + PULSE_LATENCY_MS
                if (due > elapsed) delay(due - elapsed)
                elapsed = maxOf(elapsed, due)
                pulseFlow.tryEmit(p)
            }
        }
        try {
            graph.audioOutput.play(score)
        } finally {
            emitter.cancel()
        }
    }

    /** The tonic a task is in (any octave: a pluck only needs the pitch class); null where there is no key. */
    private fun tonicOf(task: Task?): MidiNote? = (task as? TirTask)?.key?.tonicAtOrAbove(PULSE_TONIC_FLOOR)

    private var engine: RoundEngine<*>? = null
    private var history: List<AttemptRecord> = emptyList()
    private var sessionId: Long? = null
    private var ownSession = false
    private var level = 1
    private var settings = Settings()
    private var feedbackAudio: Job? = null
    private var replayJob: Job? = null
    private val micJobs = mutableListOf<Job>()
    private val arranger = Arranger()
    private val headphones = MutableStateFlow(false)
    private var feedbackSerial = 0
    private var lastFeedback: FeedbackEvent? = null
    private var finished = false
    private var currentDrone: List<MidiNote>? = null
    private var micLevel = -90.0
    private var heard: MidiNote? = null

    init {
        viewModelScope.launch {
            setup()
            loop()
        }
    }

    private var pausedAtMs: Long? = null
    private var pausedTotalMs = 0L

    private fun realNow() = System.nanoTime() / 1_000_000

    /** Round clock: stands still while paused, so the timer, task limits and latencies skip the pause. */
    private fun now() = (pausedAtMs ?: realNow()) - pausedTotalMs

    private val paused: Boolean get() = pausedAtMs != null
    private var promptPlaying = false

    private suspend fun awaitUnpaused() {
        while (paused) delay(50)
    }

    /** New key (or no drone): crossfade, and let the ear settle into «home». */
    private suspend fun ensureDrone(drone: List<MidiNote>?) {
        if (drone == currentDrone) return
        graph.audioOutput.drone(drone)
        currentDrone = drone
        if (drone != null) delay(DRONE_SETTLE_MS)
    }

    private suspend fun setup() {
        settings = graph.settings.settings.first()
        graph.audioOutput.a4Hz = settings.a4Hz
        val progress = graph.progress.current()
        level = (spec.level ?: progress.selectedLevel(spec.variant)).coerceIn(1, Drills.maxLevel(spec.variant))
        history = graph.practice.recentAttempts(spec.variant, 300)
        val wearsHeadphones = graph.headphones.connected.value || settings.headphones
        engine = Drills.engine(spec, level, settings, history, System.currentTimeMillis() - now(), review = review(), headphones = wearsHeadphones)
        sessionId = spec.sessionId ?: graph.practice.startSession(SessionKind.FREE, graph.today(settings)).also { ownSession = true }
        val needsMic = spec.variant.needsMic()
        val anchors = graph.anchors.current()
        state.update {
            it.copy(
                loading = false, level = level, needsMic = needsMic, notation = settings.degreeNotation, naming = settings.noteNaming,
                settings = settings, anchors = anchors,
                totalMs = if (spec.exam) 0 else if (Drills.relaxed(spec.variant)) 90_000 else spec.durationMs,
                maxLives = if (spec.exam || Drills.relaxed(spec.variant)) 0 else 3,
                taskLimit = if (spec.exam) spec.examTasks else null,
            )
        }
        if (needsMic) startMic()
    }

    /** O-06: earlier days' Tir misses come back first (not in exams — they measure, not train). */
    private suspend fun review(): List<SpacedReview.Item> {
        if (spec.exam || spec.variant != Variant.TIR_CLASSIC) return emptyList()
        val wallNow = System.currentTimeMillis()
        val since = wallNow - (SpacedReview.LOOKBACK_DAYS + 1) * 86_400_000L
        val studyDay = graph.studyDay(settings)
        return SpacedReview.queue(
            history = graph.practice.attemptsSince(spec.variant, since),
            today = studyDay.of(Instant.ofEpochMilli(wallNow)),
            dayOf = { studyDay.of(Instant.ofEpochMilli(it)) },
            normalize = TirDriver::reviewKey,
        )
    }

    private var lastPhase: RoundPhase? = null
    private var lastSerial = -1
    private var lastTries = 0
    private var trySerial = 0
    private var lastTry: TryEvent? = null
    private var hintJob: Job? = null

    /** Reacts to a state change at once (sound, feedback), not on the next tick. */
    private fun react(e: RoundEngine<*>) {
        val s = e.state
        if (s.phase != lastPhase || s.taskSerial != lastSerial) {
            if (s.taskSerial != lastSerial) lastTry = null
            lastPhase = s.phase
            lastSerial = s.taskSerial
            onEnter(e, s)
        }
        if (s.tries != lastTries) {
            if (s.tries > lastTries && s.phase == RoundPhase.LISTENING) onTry(s)
            lastTries = s.tries
        }
        publish(e)
    }

    /**
     * O-14, a miss of the search (ADR-028): show what was sung and where to look. After «подскажи»
     * the right note walks home (rule 5, at the moment it was asked for) and the search goes on.
     */
    private fun onTry(s: RoundState<*>) {
        val ev = s.lastEvaluation ?: return
        val sung = if (ev.centsError != null) runCatching { Degree.parse(ev.givenCode) }.getOrNull() else null
        lastTry = TryEvent(++trySerial, sung, ev.centsError)
        val correction = ev.correction
        if (ev.centsError == null && correction != null) {
            hintJob?.cancel()
            hintJob = viewModelScope.launch { playPulsing(correction, PulseKind.REVEAL, tonicOf(s.task)) }
        }
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
                val droneTask = s.task as? DroneTask
                val drone = droneTask?.drone
                val intro = droneTask?.intro
                viewModelScope.launch {
                    awaitUnpaused()
                    // O-15 (ADR-029): a new key over its tonic first, then the drone moves to the task's chord.
                    if (intro != null) {
                        ensureDrone(droneTask.introDrone ?: drone)
                        awaitUnpaused()
                        promptPlaying = true
                        playPulsing(intro, PulseKind.HOME, tonicOf(s.task))
                        promptPlaying = false
                        awaitUnpaused()
                    }
                    ensureDrone(drone)
                    awaitUnpaused()
                    ensureDrone(drone)
                    if (audio != null) {
                        promptPlaying = true
                        playPulsing(audio, PulseKind.QUESTION, tonicOf(s.task))
                        promptPlaying = false
                    }
                    e.promptFinished(now())
                    react(e)
                }
            }
            RoundPhase.FEEDBACK -> {
                val ev = s.lastEvaluation ?: return
                lastFeedback = FeedbackEvent(++feedbackSerial, ev.correct, s.lastPoints, ev.expectedText, ev.givenText, ev.centsError)
                val sound = if (ev.correct) ev.confirmation else ev.correction
                if (sound != null) {
                    feedbackAudio = viewModelScope.launch {
                        playPulsing(sound, PulseKind.REVEAL, tonicOf(s.task))
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
                tries = s.tries, lastTry = lastTry,
            )
        }
    }

    /** Button / pad answers. */
    fun answer(answer: Answer) {
        val e = engine ?: return
        if (paused) return
        e.answer(answer, now())
        react(e)
    }

    /** 🔁: Tir — the question again, «Спой ступень» — the tonic (scale not repeated; the drone keeps sounding). */
    fun replay() {
        val e = engine ?: return
        val sound = (e.state.task as? DroneTask)?.replay ?: return
        if (paused || e.state.phase != RoundPhase.LISTENING || replayJob?.isActive == true) return
        replayJob = viewModelScope.launch { playPulsing(sound, PulseKind.QUESTION, tonicOf(e.state.task)) }
    }

    /** ⏸ or the back gesture: stop the clock and the sound (drone included). */
    fun pause() {
        if (paused || finished || engine == null) return
        pausedAtMs = realNow()
        replayJob?.cancel()
        graph.audioOutput.stop()
        currentDrone = null
        state.update { it.copy(paused = true) }
    }

    /** Back to the same question: the drone returns and the question sounds again. */
    fun resume() {
        val at = pausedAtMs ?: return
        pausedTotalMs += realNow() - at
        pausedAtMs = null
        state.update { it.copy(paused = false) }
        val e = engine ?: return
        val task = e.state.task ?: return
        // A prompt that has not started yet resumes by itself; a cut one or a pending answer gets the question again.
        val cutPrompt = e.state.phase == RoundPhase.PROMPTING && promptPlaying
        if (e.state.phase != RoundPhase.LISTENING && !cutPrompt) return
        val drone = (task as? DroneTask)?.drone
        val sound = (task as? DroneTask)?.replay ?: task.prompt.audio
        replayJob = viewModelScope.launch {
            ensureDrone(drone)
            while (e.state.phase == RoundPhase.PROMPTING && e.state.task === task) delay(30)
            if (sound != null && e.state.phase == RoundPhase.LISTENING && e.state.task === task) playPulsing(sound, PulseKind.QUESTION, tonicOf(task))
        }
    }

    /** User closed the round: keep what was answered. */
    fun quit() {
        val e = engine ?: return
        if (finished) return
        e.abort(now())
        graph.appScope.launch { finish(e) }
    }

    private fun startMic() {
        graph.pitchInput.start(
            settings.inputDeviceId, settings.a4Hz,
            if (spec.variant.isSung()) SingDriver.VOICE_MIN_HOLD_MS else DEFAULT_MIN_HOLD_MS,
        )
        micJobs += viewModelScope.launch {
            combine(graph.headphones.connected, flowOf(settings.headphones)) { a, b -> a || b }.collect { headphones.value = it }
        }
        micJobs += viewModelScope.launch {
            graph.gate.onsets(graph.pitchInput, graph.audioOutput, null, headphones).collect { onset ->
                val e = engine ?: return@collect
                if (paused) return@collect
                val at = onset.timeNanos / 1_000_000 - pausedTotalMs
                if (spec.variant.isSung()) {
                    if (e.state.phase != RoundPhase.LISTENING || sungJob?.isActive == true) return@collect
                    sungJob = viewModelScope.launch { answerSung(e, onset.note, onset.cents, at) }
                } else {
                    e.answer(Answer.NotePlayed(onset.note, onset.cents), at)
                    react(e)
                }
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

    private var sungJob: Job? = null

    /** K-01: listen to the steady part of the sung note, then answer with its median pitch (time = onset). */
    private suspend fun answerSung(e: RoundEngine<*>, note: MidiNote, cents: Double, atMs: Long) {
        val hz = mutableListOf<Double>()
        withTimeoutOrNull(sungWindowMs(spec.variant)) {
            graph.pitchInput.frames.collect { f ->
                val est = f.estimate
                if (est != null && est.clarity > 0.85 && f.rmsDb > -50) hz += est.hz
            }
        }
        if (paused) return
        val steady = SingDriver.steadyPitch(hz, settings.a4Hz)
        e.answer(Answer.Sung(steady?.note ?: note, steady?.cents ?: cents), atMs)
        react(e)
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
        graph.audioOutput.drone(null)
        currentDrone = null
        val s = e.state
        val summary = withContext(NonCancellable) {
            graph.practice.saveAttempts(s.attempts, sessionId)
            if (ownSession && s.attempts.isNotEmpty()) sessionId?.let { graph.practice.finishSession(it) }
            val newRecord = !spec.exam && s.score > 0 && graph.progress.submitScore(spec.variant, s.score)
            val levelAttempts = (history + s.attempts).filter { it.level == level }
            val studyDay = graph.studyDay(settings)
            val unlocked = if (!spec.exam && level < Drills.maxLevel(spec.variant) &&
                LevelRules.shouldPromote(levelAttempts, Drills.promotionTargetMs(spec.variant, level), Drills.pace(spec.variant)) { ms ->
                    studyDay.of(Instant.ofEpochMilli(ms)).toLong()
                } &&
                (spec.variant != Variant.TIR_CLASSIC || TirDriver.halvesAllow(level, levelAttempts)) &&
                graph.progress.unlock(spec.variant, level + 1)
            ) level + 1 else null
            RoundSummary(
                variant = spec.variant, level = level, score = s.score, answered = s.attempts.size,
                correct = s.attempts.count { it.correct },
                medianMs = s.attempts.filter { it.correct }.map { it.latencyMs }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] },
                bestStreak = s.bestStreak, newRecord = newRecord, unlockedLevel = unlocked, attempts = s.attempts,
                foundOn = s.foundOn,
            )
        }
        val anchors = state.value.anchors
        val offered = AnchorHints.missed(s.attempts).mapNotNull { anchors[it.semitones] }
        anchorKey = (s.task as? TirTask)?.key ?: (s.task as? SingTask)?.key
        state.update { it.copy(summary = summary, phase = RoundPhase.FINISHED, summaryAnchors = offered) }
    }

    /** The key of the round's last question: the anchor is heard where the ear just was. */
    private var anchorKey: Key? = null
    private var anchorJob: Job? = null

    /** O-09, summary ▶: the tonic drone, a breath, the opening; tapped again — silence. */
    fun playAnchor(anchor: DegreeAnchor) {
        anchorJob?.cancel()
        graph.audioOutput.stop()
        if (state.value.playingAnchor == anchor) {
            state.update { it.copy(playingAnchor = null) }
            return
        }
        val key = anchorKey ?: KeyPicker(Random.Default).next(Mode.MAJOR).also { anchorKey = it }
        state.update { it.copy(playingAnchor = anchor) }
        // stop() above already silenced the drone; a cancelled job leaves the sound to whoever cancelled it.
        anchorJob = viewModelScope.launch {
            try {
                graph.audioOutput.drone(arranger.drone(key))
                delay(DRONE_SETTLE_MS)
                playPulsing(anchor.score(key, arranger), PulseKind.REVEAL, key.tonicAtOrAbove(PULSE_TONIC_FLOOR))
                delay(ANCHOR_TAIL_MS)
                graph.audioOutput.drone(null)
            } finally {
                state.update { if (it.playingAnchor == anchor) it.copy(playingAnchor = null) else it }
            }
        }
    }

    /** New round or leaving the summary: the anchor stops. */
    /** O-27: a new level is an offer; this is the «yes». */
    fun takeLevel(level: Int) {
        viewModelScope.launch { graph.progress.select(spec.variant, level) }
        state.update { it.copy(summary = it.summary?.copy(levelTaken = true)) }
    }

    fun stopAnchor() {
        if (anchorJob?.isActive != true) return
        anchorJob?.cancel()
        graph.audioOutput.stop()
        state.update { it.copy(playingAnchor = null) }
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
