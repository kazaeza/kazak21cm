package com.kazaeza.muzykalka.feature.taper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.data.repo.SessionKind
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.game.BlindGrading
import com.kazaeza.muzykalka.game.BlindPhase
import com.kazaeza.muzykalka.game.BlindResult
import com.kazaeza.muzykalka.game.BlindSession
import com.kazaeza.muzykalka.game.SelfGrade
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.BookEntry
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

data class BlindUi(
    val loading: Boolean = true,
    val phase: BlindPhase = BlindPhase.FINISHED,
    /** The song of this order. The screen shows its name only from [BlindPhase.RESULT] on. */
    val entry: BookEntry? = null,
    val key: Key? = null,
    val keyText: String = "",
    val result: BlindResult? = null,
    val totalTips: Int = 0,
    val ordersDone: Int = 0,
    val playableCount: Int = 0,
    val queueLeft: Int = 0,
    val listening: Boolean = false,
    val settings: Settings = Settings(),
)

/**
 * Ticket O-10: drives [BlindSession]. No microphone (ADR-014): the user plays when ready and says
 * how it went. The announcer never says the name; the song and its attempt row go to the
 * statistics, nothing goes to the library (rule 7).
 */
class BlindViewModel(private val graph: AppGraph, private val budgetMinutes: Double = 10.0) : ViewModel() {
    private val state = MutableStateFlow(BlindUi())
    val ui: StateFlow<BlindUi> = state.asStateFlow()

    private var session: BlindSession? = null
    private var sessionId: Long? = null
    private var settings = Settings()
    private var today = 0
    private val random = Random(System.nanoTime())
    private val arranger = Arranger()
    private var announceJob: Job? = null
    private var listenJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private fun now() = System.nanoTime() / 1_000_000

    private suspend fun load() {
        settings = graph.settings.settings.first()
        today = graph.today(settings)
        val history = graph.practice.recentAttempts(Variant.TAPER_BLIND, 400)
        session = BlindSession(BlindSession.pool(), history, KeyPicker(random), random, budgetMs = (budgetMinutes * 60_000).toLong())
        sessionId = graph.practice.startSession(SessionKind.FREE, today)
        state.update { it.copy(loading = false, settings = settings) }
        nextOrder()
    }

    fun nextOrder() {
        stopListening()
        val s = session ?: return
        if (!s.next(now())) {
            publish()
            finishSession()
            return
        }
        val key = s.key ?: return
        publish()
        announceJob = viewModelScope.launch { graph.announcer.say("Песня вслепую, ${RuNames.inKey(key)}!") }
    }

    fun played() {
        announceJob?.cancel()
        session?.finishPlaying(now())
        publish()
    }

    fun grade(g: SelfGrade) {
        val s = session ?: return
        val result = s.grade(g)
        publish()
        val id = sessionId
        viewModelScope.launch {
            withContext(NonCancellable) { graph.practice.saveAttempts(listOf(BlindGrading.attempt(result, System.currentTimeMillis())), id) }
        }
    }

    /** After the reveal: the progression as the app plays it, to check by ear what was just played. */
    fun listen() {
        val s = session ?: return
        val entry = s.current ?: return
        val key = s.key ?: return
        if (s.phase != BlindPhase.RESULT) return
        if (listenJob?.isActive == true) {
            stopListening()
            return
        }
        state.update { it.copy(listening = true) }
        listenJob = viewModelScope.launch {
            try {
                graph.audioOutput.play(arranger.sketch(entry.song, key))
            } finally {
                state.update { it.copy(listening = false) }
            }
        }
    }

    private fun stopListening() {
        if (listenJob?.isActive == true) {
            listenJob?.cancel()
            graph.audioOutput.stop()
        }
        state.update { it.copy(listening = false) }
    }

    private fun publish() {
        val s = session ?: return
        state.update {
            it.copy(
                phase = s.phase,
                entry = s.current,
                key = s.key,
                keyText = s.key?.let { k -> RuNames.inKey(k) } ?: "",
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
        if (session?.results?.isNotEmpty() == true) graph.appScope.launch { graph.practice.finishSession(id) }
    }

    override fun onCleared() {
        announceJob?.cancel()
        if (listenJob?.isActive == true) graph.audioOutput.stop()
        if (session?.phase != BlindPhase.FINISHED) finishSession()
    }
}
