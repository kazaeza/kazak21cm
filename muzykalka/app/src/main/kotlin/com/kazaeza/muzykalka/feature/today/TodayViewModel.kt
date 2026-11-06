package com.kazaeza.muzykalka.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.data.repo.MemoryStatus
import com.kazaeza.muzykalka.game.Link
import com.kazaeza.muzykalka.game.Metrics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class TodayUi(
    val loading: Boolean = true,
    val playableNow: Int = 0,
    val libraryCount: Int = 0,
    val holding: Int = 0,
    val dueCount: Int = 0,
    val streak: Int = 0,
    val practicedToday: Boolean = false,
    val links: List<Double?> = List(6) { null },
    val examDone: Boolean = false,
    val date: LocalDate = LocalDate.now(),
)

/** Order of the triangle strands, as TriangleChart expects. */
val TRIANGLE_ORDER = listOf(Link.SOUND_TO_NAME, Link.NAME_TO_SOUND, Link.NAME_TO_PLACE, Link.PLACE_TO_NAME, Link.PLACE_TO_SOUND, Link.SOUND_TO_PLACE)

/** Ticket T-01: «Сегодня» on live data. */
class TodayViewModel(graph: AppGraph) : ViewModel() {
    private val base = combine(
        graph.settings.settings,
        graph.practice.observePlayableNow(),
        graph.practice.observePracticeDays(),
        graph.library.observeEntries(),
    ) { settings, playable, days, entries ->
        val today = graph.today(settings)
        val orderable = entries.filter { it.orderable }
        TodayUi(
            loading = false,
            playableNow = playable,
            libraryCount = orderable.size,
            holding = orderable.count { it.status == MemoryStatus.HOLDING },
            dueCount = orderable.count { it.card?.isDue(today) ?: true },
            streak = Metrics.streak(days, today),
            practicedToday = today in days,
        )
    }

    val ui: StateFlow<TodayUi> = combine(base, graph.practice.observeAttempts(), graph.practice.observeExams()) { b, attempts, exams ->
        val scores = Metrics.linkScores(attempts)
        b.copy(links = TRIANGLE_ORDER.map { scores[it] }, examDone = exams.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())
}
