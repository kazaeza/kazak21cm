package com.kazaeza.muzykalka.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.game.GuitarNeed
import com.kazaeza.muzykalka.game.SetBuilder
import com.kazaeza.muzykalka.game.StageProgress
import com.kazaeza.muzykalka.game.Track
import com.kazaeza.muzykalka.game.TrackProgress
import com.kazaeza.muzykalka.game.Tracks
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.ui.component.AppGlyph
import com.kazaeza.muzykalka.ui.component.AppIcon
import com.kazaeza.muzykalka.ui.component.Badge
import com.kazaeza.muzykalka.ui.component.ChoiceRow
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.IconChip
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.component.TrackProgressBar
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class TrackUi(
    val track: Track,
    val progress: TrackProgress,
    val selectedMinutes: Int = 10,
    val quietMode: Boolean = false,
    val isActiveTrack: Boolean = false,
)

class TrackViewModel(private val graph: AppGraph, trackId: String) : ViewModel() {
    private val track: Track = Tracks.byId(trackId)

    val ui: StateFlow<TrackUi> = combine(
        graph.settings.settings,
        graph.progress.progress,
        graph.practice.observeTrainingHistory(),
    ) { settings, progress, (attempts, _) ->
        val unlocked = Variant.entries.associateWith { progress.unlockedLevel(it) }
        val eval = Tracks.evaluate(track, attempts, unlocked)
        TrackUi(
            track = track,
            progress = eval,
            selectedMinutes = settings.dailyMinutes.takeIf { it in SetBuilder.BUDGET_OPTIONS } ?: 10,
            quietMode = settings.quietMode,
            isActiveTrack = settings.activeTrackId == track.id,
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TrackUi(track, Tracks.evaluate(track, emptyList())),
    )

    fun setMinutes(minutes: Int) {
        viewModelScope.launch { graph.settings.update { it.copy(dailyMinutes = minutes) } }
    }

    fun setActiveTrack() {
        viewModelScope.launch { graph.settings.update { it.copy(activeTrackId = track.id) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackScreen(
    trackId: String,
    onBack: () -> Unit,
    onStartStage: (trackId: String, stageId: String, minutes: Int) -> Unit,
) {
    val graph = LocalAppGraph.current
    val vm: TrackViewModel = viewModel(key = "track-$trackId") { TrackViewModel(graph, trackId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val accent = trackAccent(ui.track.id)

    ScreenFrame(
        title = ui.track.ru,
        subtitle = ui.track.subtitle,
        accent = accent,
        onBack = onBack,
    ) {
        item(key = "overview") {
            GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Освоено этапов: ${ui.progress.completedStages} из ${ui.track.stages.size}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(ui.progress.overallFraction * 100.0).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = accent.first(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                TrackProgressBar(ui.progress.overallFraction.toFloat(), accent)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ui.track.guitar == GuitarNeed.NONE) {
                        IconChip(
                            icon = AppIcon.SPARK,
                            text = if (ui.isActiveTrack) "Основной трек дня" else "Сделать основным треком",
                            selected = ui.isActiveTrack,
                            onClick = vm::setActiveTrack,
                            accent = accent.first(),
                        )
                    }
                    if (ui.quietMode) {
                        Badge("Тихо · без голоса", Accents.Calm)
                    }
                }
            }
        }
        item(key = "duration") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Время на этап")
                ChoiceRow(
                    options = SetBuilder.BUDGET_OPTIONS,
                    selected = ui.selectedMinutes,
                    label = { "$it мин" },
                    onSelect = vm::setMinutes,
                )
            }
        }
        ui.progress.stages.forEachIndexed { idx, sp ->
            item(key = sp.stage.id) {
                StageCard(
                    index = idx,
                    sp = sp,
                    current = idx == ui.progress.currentStageIndex,
                    accent = accent,
                    minutes = ui.selectedMinutes,
                    onStart = { onStartStage(ui.track.id, sp.stage.id, ui.selectedMinutes) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StageCard(
    index: Int,
    sp: StageProgress,
    current: Boolean,
    accent: List<androidx.compose.ui.graphics.Color>,
    minutes: Int,
    onStart: () -> Unit,
) {
    val cardAccent = when {
        current -> accent
        sp.done -> Accents.Good
        else -> null
    }
    GlassCard(Modifier.fillMaxWidth(), accent = cardAccent) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Этап ${index + 1} · ${sp.stage.title}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (sp.done) {
                Spacer(Modifier.width(8.dp))
                AppGlyph(AppIcon.CHECK, color = Juicy.Good, size = 18.dp)
            } else if (current) {
                Spacer(Modifier.width(8.dp))
                Badge("сейчас", accent)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(sp.stage.goal, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
        Spacer(Modifier.height(10.dp))
        TrackProgressBar(sp.fraction.toFloat(), if (sp.done) Accents.Good else accent)
        Spacer(Modifier.height(6.dp))
        Text(sp.summary, style = MaterialTheme.typography.labelMedium, color = Juicy.TextDim)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sp.stage.drills.forEach { seg -> Badge(seg.variant.ru, accent) }
        }
        Spacer(Modifier.height(12.dp))
        if (current) {
            JuicyButton(
                text = "Тренировать этап · $minutes мин",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = accent,
                height = 54.dp,
                textStyle = MaterialTheme.typography.titleMedium,
                leadingIcon = AppIcon.PLAY,
            )
        } else {
            GhostButton(
                text = "Тренировать этап · $minutes мин",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = accent,
                leadingIcon = AppIcon.PLAY,
            )
        }
    }
}
