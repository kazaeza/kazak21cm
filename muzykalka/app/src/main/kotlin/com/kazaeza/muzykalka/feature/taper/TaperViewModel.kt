package com.kazaeza.muzykalka.feature.taper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.data.repo.SessionKind
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.game.HintStep
import com.kazaeza.muzykalka.game.OrderResult
import com.kazaeza.muzykalka.game.SelfGrade
import com.kazaeza.muzykalka.game.TaperGrading
import com.kazaeza.muzykalka.game.TaperItem
import com.kazaeza.muzykalka.game.TaperPhase
import com.kazaeza.muzykalka.game.TaperSession
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.RuNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
    /** Play exactly this item (Library → «Сыграть сейчас»). */
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
    /** The key of this order — chords tapped on the sheet sound in it (O-17). */
    val key: Key? = null,
    val keyText: String = "",
    val hint: HintStep = HintStep.NONE,
    val hintsAllowed: Boolean = true,
    val orderedAtMs: Long = 0,
    val startLatencyMs: Long? = null,
    val result: OrderResult? = null,
    val totalTips: Int = 0,
    val ordersDone: Int = 0,
    val playableCount: Int = 0,
    val queueLeft: Int = 0,
    val today: Int = 0,
    val settings: Settings = Settings(),
)

/**
 * Ticket G-03: drives [TaperSession] — announce, hints, self-grade, FSRS. No microphone (ADR-014):
 * the user plays when ready and says how it went; start speed is not measured. O-13: each piece
 * starts where its last order leaves it on the hint ladder (vanishing cues, ADR-027).
 */
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
    private var announceJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private fun now() = System.nanoTime() / 1_000_000

    private suspend fun load() {
        settings = graph.settings.settings.first()
        today = graph.today(settings)
        val last = graph.practice.lastOrders()
        allItems = graph.library.allOrderable().mapNotNull { it.toTaperItem() }.map { item ->
            val (hint, self) = last[item.id] ?: (null to null)
            item.copy(startHint = TaperGrading.startHint(hint, self))
        }
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
        if (!s.next(now())) {
            publish()
            finishSession()
            return
        }
        val item = s.current ?: return
        val key = s.key ?: return
        publish()
        announceJob = viewModelScope.launch { graph.announcer.say("«${item.title}», ${RuNames.inKey(key)}!") }
    }

    fun hint() {
        session?.requestHint()
        publish()
    }

    fun played() {
        announceJob?.cancel()
        session?.finishPlaying()
        publish()
    }

    /** «Как прошло?» closes the order: it is graded and saved at once. */
    fun grade(g: SelfGrade) {
        val result = session?.grade(g) ?: return
        publish()
        viewModelScope.launch {
            withContext(NonCancellable) { graph.practice.recordOrder(result, sessionId, today) }
        }
    }

    private fun publish() {
        val s = session ?: return
        val cur = s.current
        state.update {
            it.copy(
                phase = s.phase,
                item = cur,
                key = s.key,
                keyText = s.key?.let { k -> RuNames.inKey(k) } ?: "",
                hint = s.hint,
                orderedAtMs = s.orderedAtMs,
                startLatencyMs = s.startLatencyMs,
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

    override fun onCleared() {
        announceJob?.cancel()
        if (session?.phase != TaperPhase.FINISHED) finishSession()
    }
}
