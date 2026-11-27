package com.kazaeza.muzykalka.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.feature.common.APP_STAGE
import com.kazaeza.muzykalka.game.AdaptiveEar
import com.kazaeza.muzykalka.game.DailySet
import com.kazaeza.muzykalka.game.EarFocus
import com.kazaeza.muzykalka.game.Link
import com.kazaeza.muzykalka.game.Metrics
import com.kazaeza.muzykalka.game.PickOfDay
import com.kazaeza.muzykalka.game.SetBuilder
import com.kazaeza.muzykalka.game.SetSegment
import com.kazaeza.muzykalka.game.SingUnlock
import com.kazaeza.muzykalka.game.TrackProgress
import com.kazaeza.muzykalka.game.Tracks
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.SongOpenings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

data class TodayUi(
    val loading: Boolean = true,
    val playableNow: Int = 0,
    /** Songs and riffs in learning (not the ones on the shelf of the book, O-42). */
    val libraryCount: Int = 0,
    val dueCount: Int = 0,
    val streak: Int = 0,
    /** O-30: what the screen leads with — it moves from the first round on, unlike «songs played unaided». */
    val weekMinutes: Int = 0,
    val answersToday: Int = 0,
    val practicedToday: Boolean = false,
    /** What «Начать сет» will run today, built via [SetBuilder.buildDaily]. */
    val set: List<SetSegment> = DailySet.available(DailySet.DEFAULT, APP_STAGE),
    val focus: EarFocus? = null,
    val date: LocalDate = LocalDate.now(),
    /** O-44: one song of the library for today — the same all day. */
    val songOfDay: SongOfDay? = null,
    val notation: DegreeNotation = DegreeNotation.ARABIC,
    val dailyMinutes: Int = 20,
    val quietMode: Boolean = false,
    val activeTrackId: String = Tracks.STEPS_BASE.id,
    val nonGuitarTracks: List<TrackProgress> = Tracks.NON_GUITAR.map { Tracks.evaluate(it, emptyList()) },
    val guitarTracks: List<TrackProgress> = Tracks.GUITAR.map { Tracks.evaluate(it, emptyList()) },
    val transferGapBig: Boolean = false,
) {
    val setMinutes: Int get() = set.sumOf { it.minutes }.roundToInt()
}

/** Order of the triangle strands, as TriangleChart expects. */
val TRIANGLE_ORDER = listOf(Link.SOUND_TO_NAME, Link.NAME_TO_SOUND, Link.NAME_TO_PLACE, Link.PLACE_TO_NAME, Link.PLACE_TO_SOUND, Link.SOUND_TO_PLACE)

/** O-44 «Песня дня»: what the card shows — the opening in degrees, never in letters. */
data class SongOfDay(val id: String, val title: String, val artist: String?, val mode: Mode, val opening: List<ChordDegree>, val learning: Boolean)

/** Ticket T-01: «Сегодня» on live data — non-guitar daily set, duration/quiet controls, guided tracks, and a separate guitar section. */
class TodayViewModel(private val graph: AppGraph) : ViewModel() {
    private val settingsAndProgress = combine(
        graph.settings.settings,
        graph.progress.progress,
    ) { settings, progress -> settings to progress }

    val ui: StateFlow<TodayUi> = combine(
        settingsAndProgress,
        graph.practice.observePlayableNow(),
        graph.practice.observePracticeSpans(),
        graph.library.observeEntries(),
        graph.practice.observeTrainingHistory(),
    ) { (settings, progress), playable, spans, entries, (attempts, lessons) ->
        val today = graph.today(settings)
        val days = spans.map { it.studyDay }.toSet()
        val singing = SingUnlock.status(attempts)
        val studyDay = graph.studyDay(settings)
        val orderable = entries.filter { it.orderable && !it.shelved }
        val due = orderable.count { it.card?.isDue(today) ?: true }
        val focus = AdaptiveEar.choose(attempts, today, lessons) { studyDay.of(Instant.ofEpochMilli(it)) }
        val transfer = Metrics.transfer(attempts) { studyDay.of(Instant.ofEpochMilli(it)) }
        val transferGapBig = transfer.trusted && (transfer.gap ?: 0.0) >= 0.2
        val unlockedLevels = Variant.entries.associateWith { progress.unlockedLevel(it) }
        val nonGuitarTracks = Tracks.NON_GUITAR.map { Tracks.evaluate(it, attempts, unlockedLevels) }
        val guitarTracks = Tracks.GUITAR.map { Tracks.evaluate(it, attempts, unlockedLevels) }
        val activeTrack = Tracks.byId(settings.activeTrackId)
        val activeProgress = (nonGuitarTracks + guitarTracks).firstOrNull { it.track.id == activeTrack.id }
            ?: Tracks.evaluate(activeTrack, attempts, unlockedLevels)
        val dailyPlan = SetBuilder.buildDaily(
            targetMinutes = settings.dailyMinutes,
            quiet = settings.quietMode,
            activeTrack = activeTrack,
            singing = singing,
            focus = focus,
            transferGapBig = transferGapBig,
            appVersion = APP_STAGE,
            activeStageIndex = activeProgress.currentStageIndex,
        )
        TodayUi(
            loading = false,
            playableNow = playable,
            libraryCount = orderable.size,
            dueCount = due,
            streak = Metrics.streak(days, today),
            weekMinutes = Metrics.weekMinutes(spans, today),
            answersToday = attempts.count { studyDay.of(Instant.ofEpochMilli(it.atEpochMs)) == today },
            practicedToday = today in days,
            focus = focus,
            set = dailyPlan,
            date = LocalDate.ofEpochDay(today.toLong()),
            notation = settings.degreeNotation,
            dailyMinutes = settings.dailyMinutes,
            quietMode = settings.quietMode,
            activeTrackId = activeTrack.id,
            nonGuitarTracks = nonGuitarTracks,
            guitarTracks = guitarTracks,
            transferGapBig = transferGapBig,
            songOfDay = entries.filter { it.song != null && !it.unsupported }.let { songs ->
                val pick = PickOfDay.pick(songs.map { it.id }, today)
                songs.firstOrNull { it.id == pick }?.let { e ->
                    val song = e.song!!
                    SongOfDay(e.id, e.title, song.artist, song.mode, SongOpenings.changes(song, SONG_OF_DAY_CHORDS), learning = !e.shelved)
                }
            },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())

    fun setMinutes(minutes: Int) {
        viewModelScope.launch { graph.settings.update { it.copy(dailyMinutes = minutes) } }
    }

    fun setQuietMode(quiet: Boolean) {
        viewModelScope.launch { graph.settings.update { it.copy(quietMode = quiet) } }
    }
}

/** O-44: how many chord changes of the song of the day are shown. */
const val SONG_OF_DAY_CHORDS = 6
