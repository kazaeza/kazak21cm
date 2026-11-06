package com.kazaeza.muzykalka.feature.set

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.data.repo.SessionKind
import com.kazaeza.muzykalka.feature.common.ModeVisuals
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.feature.common.plural
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.feature.taper.TaperScreen
import com.kazaeza.muzykalka.feature.taper.TaperSpec
import com.kazaeza.muzykalka.game.DailySet
import com.kazaeza.muzykalka.game.Metrics
import com.kazaeza.muzykalka.game.SetSegment
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.feature.common.APP_STAGE
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.GradientText
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.effect.AuroraBackground
import com.kazaeza.muzykalka.ui.effect.ParticleLayer
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.effect.rememberParticleState
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SetPhase { INTRO, NEXT_UP, RUNNING, DONE }

data class SetUi(
    val minimal: Boolean,
    val segments: List<SetSegment> = emptyList(),
    val index: Int = 0,
    val round: Int = 0,
    val phase: SetPhase = SetPhase.INTRO,
    val sessionId: Long? = null,
    val streak: Int = 0,
    val activity: Int = 0,
) {
    val current: SetSegment? get() = segments.getOrNull(index)
    fun roundsOf(s: SetSegment): Int = if (s.variant == Variant.TAPER_ORDER) 1 else maxOf(1, (s.minutes * 60 / 75).roundToInt())
    fun roundMsOf(s: SetSegment): Long = (s.minutes * 60_000 / roundsOf(s)).toLong().coerceIn(60_000, 90_000)
}

/** Ticket T-03: the daily set (or the minimal day) as one guided session. */
class SetViewModel(private val graph: AppGraph, minimal: Boolean) : ViewModel() {
    private val state = MutableStateFlow(SetUi(minimal, DailySet.available(if (minimal) DailySet.MINIMAL else DailySet.DEFAULT, APP_STAGE)))
    val ui: StateFlow<SetUi> = state.asStateFlow()

    init {
        // No song is due (or the library is empty): «Ходы» take the songs' minutes.
        viewModelScope.launch {
            val today = graph.today(graph.settings.settings.first())
            val anyDue = graph.library.allOrderable().any { it.card?.isDue(today) ?: true }
            if (!anyDue) state.update { it.copy(segments = DailySet.withoutSongs(it.segments)) }
        }
    }

    fun start() = viewModelScope.launch {
        if (state.value.sessionId == null) {
            val settings = graph.settings.settings.first()
            val id = graph.practice.startSession(if (state.value.minimal) SessionKind.MINIMAL else SessionKind.SET, graph.today(settings))
            state.update { it.copy(sessionId = id) }
        }
        state.update { it.copy(phase = SetPhase.NEXT_UP) }
    }

    fun go() = state.update { it.copy(phase = SetPhase.RUNNING) }

    /** One round (or the Taper block) is over. [didSomething] = at least one answer/order. */
    fun roundDone(didSomething: Boolean) {
        val s = state.value
        val seg = s.current ?: return
        val activity = s.activity + if (didSomething) 1 else 0
        if (s.round + 1 < s.roundsOf(seg)) {
            state.update { it.copy(round = it.round + 1, activity = activity) }
        } else {
            advance(activity)
        }
    }

    fun skipSegment() = advance(state.value.activity)

    private fun advance(activity: Int) {
        val s = state.value
        if (s.index + 1 < s.segments.size) {
            state.update { it.copy(index = it.index + 1, round = 0, phase = SetPhase.NEXT_UP, activity = activity) }
        } else {
            state.update { it.copy(activity = activity) }
            finish()
        }
    }

    private fun finish() = viewModelScope.launch {
        val id = state.value.sessionId
        if (id != null && state.value.activity > 0) graph.practice.finishSession(id)
        val settings = graph.settings.settings.first()
        val days = graph.practice.observePracticeDays().first()
        state.update { it.copy(phase = SetPhase.DONE, streak = Metrics.streak(days, graph.today(settings))) }
    }
}

@Composable
fun SetScreen(minimal: Boolean, onExit: () -> Unit, onLibrary: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: SetViewModel = viewModel(key = "set-$minimal") { SetViewModel(graph, minimal) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val seg = ui.current

    when (ui.phase) {
        SetPhase.INTRO -> SetIntro(ui, onStart = vm::start, onExit = onExit)
        SetPhase.NEXT_UP -> if (seg != null) NextUp(ui, seg, onGo = vm::go, onSkip = vm::skipSegment, onExit = onExit)
        SetPhase.RUNNING -> if (seg != null) {
            if (seg.variant == Variant.TAPER_ORDER) {
                TaperScreen(
                    spec = TaperSpec(budgetMinutes = seg.minutes, sessionId = ui.sessionId),
                    onExit = { vm.roundDone(true) },
                    onFinished = { vm.roundDone(true) },
                    onOpenLibrary = onLibrary,
                    vmKey = "set-taper-${ui.sessionId}-${ui.index}",
                )
            } else {
                GameScreen(
                    spec = RoundSpec(seg.variant, sessionId = ui.sessionId, durationMs = ui.roundMsOf(seg)),
                    onExit = { vm.skipSegment() },
                    onFinished = { vm.roundDone(it.answered > 0) },
                    vmKey = "set-${ui.sessionId}-${ui.index}-${ui.round}",
                )
            }
        }
        SetPhase.DONE -> SetDone(ui, onExit)
    }
}

@Composable
private fun SetIntro(ui: SetUi, onStart: () -> Unit, onExit: () -> Unit) {
    ScreenFrame(
        title = if (ui.minimal) "Короткий сет" else "Сет дня",
        subtitle = if (ui.minimal) "${ui.segments.sumOf { it.minutes }.roundToInt()} минут — серия не прервётся" else "${ui.segments.sumOf { it.minutes }.roundToInt()} минут, по кусочку каждого режима",
        accent = Accents.Hero,
        onBack = onExit,
    ) {
        ui.segments.forEachIndexed { i, s ->
            item(key = "seg-$i") {
                GlassCard(Modifier.fillMaxWidth(), accent = ModeVisuals.accent(s.variant)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ModeVisuals.emoji(s.variant.mode), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.padding(horizontal = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.variant.ru, style = MaterialTheme.typography.titleLarge)
                            Text(s.variant.mode.goal, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
                        }
                        Text("${s.minutes.roundToInt()} мин", style = MaterialTheme.typography.titleMedium, color = Juicy.Gold)
                    }
                }
            }
        }
        item(key = "go") { JuicyButton("Поехали", onClick = onStart, modifier = Modifier.fillMaxWidth(), height = 72.dp, leading = "▶️") }
    }
}

@Composable
private fun NextUp(ui: SetUi, seg: SetSegment, onGo: () -> Unit, onSkip: () -> Unit, onExit: () -> Unit) {
    var count by remember(ui.index) { mutableIntStateOf(3) }
    LaunchedEffect(ui.index) {
        while (count > 0) {
            delay(800)
            count--
        }
        onGo()
    }
    BackHandler(onBack = onExit)
    AuroraBackground(ModeVisuals.accent(seg.variant), Modifier.fillMaxSize(), intensity = 0.8f) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SectionLabel("часть ${ui.index + 1} из ${ui.segments.size}")
            Spacer(Modifier.height(12.dp))
            Text(ModeVisuals.emoji(seg.variant.mode), style = MaterialTheme.typography.displayLarge)
            Text(seg.variant.ru, style = MaterialTheme.typography.displaySmall)
            Text("${seg.minutes.roundToInt()} мин · ${seg.variant.guitar.ru}", style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim)
            Spacer(Modifier.height(24.dp))
            GradientText(if (count > 0) "$count" else "!", MaterialTheme.typography.displayLarge, ModeVisuals.accent(seg.variant), Modifier.pop(count + 1, 0.5f))
            Spacer(Modifier.height(24.dp))
            GhostButton("Пропустить", onClick = onSkip)
        }
    }
}

@Composable
private fun SetDone(ui: SetUi, onExit: () -> Unit) {
    val particles = rememberParticleState()
    LaunchedEffect(Unit) {
        if (ui.activity > 0) particles.burst(androidx.compose.ui.geometry.Offset(540f, 600f), Accents.Hero + listOf(Juicy.Gold, Juicy.Lime, Juicy.Cyan), count = 110, power = 1.5f, confetti = true)
    }
    AuroraBackground(Accents.Hero, Modifier.fillMaxSize(), intensity = 0.8f) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (ui.activity > 0) "🔥" else "😴", style = MaterialTheme.typography.displayLarge)
            Text(if (ui.activity > 0) "Сет закрыт!" else "Сет пропущен", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(10.dp))
            if (ui.activity > 0) {
                GradientText("серия: ${ui.streak} ${plural(ui.streak, "день", "дня", "дней")}", MaterialTheme.typography.headlineMedium, Accents.Tir)
            }
            Spacer(Modifier.height(28.dp))
            JuicyButton("Готово", onClick = onExit, modifier = Modifier.fillMaxWidth())
        }
        ParticleLayer(particles)
    }
}
