package com.kazaeza.muzykalka.feature.taper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.data.repo.SessionKind
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.feature.common.hasMicPermission
import com.kazaeza.muzykalka.game.HintStep
import com.kazaeza.muzykalka.game.OrderResult
import com.kazaeza.muzykalka.game.SelfGrade
import com.kazaeza.muzykalka.game.TaperGrading
import com.kazaeza.muzykalka.game.TaperItem
import com.kazaeza.muzykalka.game.TaperPhase
import com.kazaeza.muzykalka.game.TaperSession
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.RuNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** How the Taper was opened. */
data class TaperSpec(
    val budgetMinutes: Double = 8.0,
    val sessionId: Long? = null,
    /** Order exactly this item (Library → «Заказать сейчас»). */
    val onlyItemId: String? = null,
    /** Exam memory stage: random items, no hints. */
    val exam: Boolean = false,
    val examItems: Int = 10,
)

data class TaperUi(
    val loading: Boolean = true,
    val phase: TaperPhase = TaperPhase.FINISHED,
    val emptyLibrary: Boolean = false,
    val nothingDue: Boolean = false,
    val item: TaperItem? = null,
    val keyText: String = "",
    val hint: HintStep = HintStep.NONE,
    val hintsAllowed: Boolean = true,
    val orderedAtMs: Long = 0,
    val startLatencyMs: Long? = null,
    val listening: Boolean = false,
    val heard: MidiNote? = null,
    val enteredChords: List<ChordDegree> = emptyList(),
    val enteredDegrees: List<Degree> = emptyList(),
    val expectedLength: Int = 0,
    val result: OrderResult? = null,
    val totalTips: Int = 0,
    val ordersDone: Int = 0,
    val playableCount: Int = 0,
    val queueLeft: Int = 0,
    val today: Int = 0,
    val settings: Settings = Settings(),
)

/** Ticket G-03: drives [TaperSession] — announce, listen for the start, hints, grade, formula, FSRS. */
class TaperViewModel(private val graph: AppGraph, private val spec: TaperSpec) : ViewModel() {
    private val state = MutableStateFlow(TaperUi(hintsAllowed = !spec.exam))
    val ui: StateFlow<TaperUi> = state.asStateFlow()

    private var session: TaperSession? = null
    private var allItems: List<TaperItem> = emptyList()
    private var sessionId: Long? = null
    private var ownSession = false
    private var settings = Settings()
    private var today = 0
    private val random = Random(System.nanoTime())
    private var listenJob: Job? = null
    private var announceJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private fun now() = System.nanoTime() / 1_000_000

    private suspend fun load() {
        settings = graph.settings.settings.first()
        today = graph.today(settings)
        allItems = graph.library.allOrderable().mapNotNull { it.toTaperItem() }
        val pool = when {
            spec.onlyItemId != null -> allItems.filter { it.id == spec.onlyItemId }
            spec.exam -> allItems.shuffled(random).take(spec.examItems)
            else -> TaperSession.due(allItems, today, random)
        }
        state.update { it.copy(loading = false, emptyLibrary = allItems.isEmpty(), nothingDue = allItems.isNotEmpty() && pool.isEmpty(), today = today, settings = settings) }
        if (pool.isNotEmpty()) start(pool)
    }

    /** All due items are holding: order a few anyway, weakest memory first. */
    fun orderExtra() {
        val extra = allItems.sortedBy { graph.fsrs.retrievability(it.card, today) }.take(3)
        if (extra.isNotEmpty()) viewModelScope.launch { start(extra) }
    }

    private suspend fun start(pool: List<TaperItem>) {
        session = TaperSession(
            pool, KeyPicker(random), graph.fsrs, today, random,
            budgetMs = TaperSession.budgetMs(spec.budgetMinutes), hintsAllowed = !spec.exam,
        )
        if (sessionId == null) {
            sessionId = spec.sessionId ?: graph.practice.startSession(if (spec.exam) SessionKind.ENTRANCE_EXAM else SessionKind.FREE, today).also { ownSession = true }
        }
        state.update { it.copy(nothingDue = false) }
        nextOrder()
    }

    fun nextOrder() {
        val s = session ?: return
        stopListening()
        if (!s.next(now())) {
            publish()
            finishSession()
            return
        }
        val item = s.current ?: return
        val key = s.key ?: return
        publish()
        announceJob = viewModelScope.launch {
            graph.announcer.say("«${item.title}», ${RuNames.inKey(key)}!")
            startListening()
        }
    }

    private fun startListening() {
        if (!hasMicPermission(graph.app)) return
        val s = session ?: return
        graph.pitchInput.start(settings.inputDeviceId, settings.a4Hz)
        state.update { it.copy(listening = true) }
        listenJob = viewModelScope.launch {
            val headphones = MutableStateFlow(settings.headphones || graph.headphones.connected.value)
            launch {
                graph.pitchInput.frames.collect { f ->
                    val est = f.estimate
                    val heard = if (est != null && est.clarity > 0.85 && f.rmsDb > -50) MidiNote.nearest(est.hz, settings.a4Hz).note else null
                    state.update { it.copy(heard = heard) }
                }
            }
            launch {
                delay(com.kazaeza.muzykalka.game.TaperGrading.START_WINDOW_MS + 500)
                stopListening()
            }
            graph.gate.onsets(graph.pitchInput, graph.audioOutput, graph.announcer, headphones).collect { onset ->
                s.onFirstNote(onset.timeNanos / 1_000_000)
                publish()
                if (s.startLatencyMs != null) stopListening()
            }
        }
    }

    private fun stopListening() {
        if (listenJob != null) {
            listenJob?.cancel()
            listenJob = null
            graph.pitchInput.stop()
        }
        state.update { it.copy(listening = false, heard = null) }
    }

    fun hint() {
        session?.requestHint()
        publish()
    }

    fun played() {
        announceJob?.cancel()
        stopListening()
        session?.finishPlaying()
        publish()
    }

    fun grade(g: SelfGrade) {
        session?.grade(g)
        publish()
    }

    fun addChord(c: ChordDegree) = state.update { it.copy(enteredChords = it.enteredChords + c) }
    fun addDegree(d: Degree) = state.update { it.copy(enteredDegrees = it.enteredDegrees + d) }
    fun undo() = state.update { it.copy(enteredChords = it.enteredChords.dropLast(1), enteredDegrees = it.enteredDegrees.dropLast(1)) }

    fun submitFormula() {
        val s = session ?: return
        val cur = s.current ?: return
        val result = if (cur.song != null) s.submitChords(state.value.enteredChords) else s.submitDegrees(state.value.enteredDegrees)
        publish()
        viewModelScope.launch {
            withContext(NonCancellable) { graph.practice.recordOrder(result, sessionId, today) }
        }
    }

    private fun publish() {
        val s = session ?: return
        val cur = s.current
        val expected = when {
            cur?.song != null -> TaperGrading.expectedFormula(cur.song!!).size
            cur?.melody != null -> cur.melody!!.notes.size
            else -> 0
        }
        state.update {
            it.copy(
                phase = s.phase,
                item = cur,
                keyText = s.key?.let { k -> RuNames.inKey(k) } ?: "",
                hint = s.hint,
                orderedAtMs = s.orderedAtMs,
                startLatencyMs = s.startLatencyMs,
                expectedLength = expected,
                enteredChords = if (s.phase == TaperPhase.PLAYING) emptyList() else it.enteredChords,
                enteredDegrees = if (s.phase == TaperPhase.PLAYING) emptyList() else it.enteredDegrees,
                result = s.lastResult,
                totalTips = s.totalTips,
                ordersDone = s.results.size,
                playableCount = s.results.count { r -> r.playableNow },
                queueLeft = s.remainingInQueue,
            )
        }
    }

    private fun finishSession() {
        val id = sessionId ?: return
        val done = session?.results?.isNotEmpty() == true
        if (ownSession && done) graph.appScope.launch { graph.practice.finishSession(id) }
    }

    /** Key of the current order (for the full-sheet hint). */
    fun currentKey() = session?.key

    override fun onCleared() {
        stopListening()
        announceJob?.cancel()
        if (session?.phase != TaperPhase.FINISHED) finishSession()
    }
}
