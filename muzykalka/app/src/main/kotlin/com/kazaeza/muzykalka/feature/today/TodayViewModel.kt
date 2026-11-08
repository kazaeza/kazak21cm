package com.kazaeza.muzykalka.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.feature.common.APP_STAGE
import com.kazaeza.muzykalka.game.SingUnlock
import com.kazaeza.muzykalka.game.DailySet
import com.kazaeza.muzykalka.game.Link
import com.kazaeza.muzykalka.game.Metrics
import com.kazaeza.muzykalka.game.SetSegment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class TodayUi(
    val loading: Boolean = true,
    val playableNow: Int = 0,
    val libraryCount: Int = 0,
    val dueCount: Int = 0,
    val streak: Int = 0,
    val practicedToday: Boolean = false,
    /** What «Начать сет» will run today, as the set screen will build it. */
    val set: List<SetSegment> = DailySet.available(DailySet.DEFAULT, APP_STAGE),
    val date: LocalDate = LocalDate.now(),
) {
    val setMinutes: Int get() = set.sumOf { it.minutes }.toInt()
}

/** Order of the triangle strands, as TriangleChart expects. */
val TRIANGLE_ORDER = listOf(Link.SOUND_TO_NAME, Link.NAME_TO_SOUND, Link.NAME_TO_PLACE, Link.PLACE_TO_NAME, Link.PLACE_TO_SOUND, Link.SOUND_TO_PLACE)

/** Ticket T-01: «Сегодня» on live data — only what helps to start playing (ADR-016). */
class TodayViewModel(graph: AppGraph) : ViewModel() {
    val ui: StateFlow<TodayUi> = combine(
        graph.settings.settings,
        graph.practice.observePlayableNow(),
        graph.practice.observePracticeDays(),
        graph.library.observeEntries(),
        graph.practice.observeAttempts().map { SingUnlock.status(it) }.distinctUntilChanged(),
    ) { settings, playable, days, entries, singing ->
        val today = graph.today(settings)
        val orderable = entries.filter { it.orderable }
        val due = orderable.count { it.card?.isDue(today) ?: true }
        val plan = SingUnlock.plan(DailySet.available(DailySet.DEFAULT, APP_STAGE), singing)
        TodayUi(
            loading = false,
            playableNow = playable,
            libraryCount = orderable.size,
            dueCount = due,
            streak = Metrics.streak(days, today),
            practicedToday = today in days,
            set = if (due == 0) DailySet.withoutSongs(plan) else plan,
            date = LocalDate.ofEpochDay(today.toLong()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())
}
