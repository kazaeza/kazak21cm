package com.kazaeza.muzykalka.feature.set

import java.time.Instant
import com.kazaeza.muzykalka.game.GuessSong
import com.kazaeza.muzykalka.game.SongFocus
import com.kazaeza.muzykalka.game.AdaptiveEar
import com.kazaeza.muzykalka.game.EarFocus
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
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
import com.kazaeza.muzykalka.feature.common.ModeOrb
import com.kazaeza.muzykalka.feature.common.ModeVisuals
import com.kazaeza.muzykalka.feature.common.PauseOverlay
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.feature.common.plural
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.feature.taper.TaperScreen
import com.kazaeza.muzykalka.feature.taper.TaperSpec
import com.kazaeza.muzykalka.game.SingUnlock
import com.kazaeza.muzykalka.game.DailySet
import com.kazaeza.muzykalka.game.GuitarNeed
import com.kazaeza.muzykalka.game.Training
import com.kazaeza.muzykalka.game.Metrics
import com.kazaeza.muzykalka.game.SetBuilder
import com.kazaeza.muzykalka.game.SetSegment
import com.kazaeza.muzykalka.game.Tracks
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.feature.common.APP_STAGE
import com.kazaeza.muzykalka.ui.component.AppIcon
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
    /** O-55: which set this is; the minimal day keeps [Training.DAILY] here. */
    val training: Training = Training.DAILY,
    val segments: List<SetSegment> = emptyList(),
    val focus: EarFocus? = null,
    val songFocus: SongFocus? = null,
    val ready: Boolean = false,
    val index: Int = 0,
    val round: Int = 0,
    val phase: SetPhase = SetPhase.INTRO,
    val sessionId: Long? = null,
    val streak: Int = 0,
    val activity: Int = 0,
    /** «Выйти из сета» before the last part (ADR-019). */
    val endedEarly: Boolean = false,
    val customTitle: String? = null,
    val customGoal: String? = null,
    val stageMinLevels: Map<Variant, Int> = emptyMap(),
) {
    val focusIndex: Int get() = focus?.let { f -> segments.indexOfFirst { it.variant == f.variant } } ?: -1
    val current: SetSegment? get() = segments.getOrNull(index)
    val guitarNeeded: Boolean get() = segments.any { it.variant.guitar == GuitarNeed.REQUIRED }
    fun roundsOf(s: SetSegment): Int = if (s.variant == Variant.TAPER_ORDER) 1 else maxOf(1, (s.minutes * 60 / 75).roundToInt())
    fun roundMsOf(s: SetSegment): Long = (s.minutes * 60_000 / roundsOf(s)).toLong().coerceIn(60_000, 90_000)
}

/** Ticket T-03: the daily set, track stage session, minimal day, or named set as one guided session. */
class SetViewModel(
    private val graph: AppGraph,
    minimal: Boolean,
    training: Training = Training.DAILY,
    private val trackId: String? = null,
    private val stageId: String? = null,
    private val minutes: Int? = null,
) : ViewModel() {
    private val initialStage = trackId?.let { tid -> Tracks.byId(tid).stages.firstOrNull { it.id == stageId } }
    private val initialTrack = trackId?.let { Tracks.byId(it) }
    private val state = MutableStateFlow(
        SetUi(
            minimal = minimal,
            training = training,
            segments = DailySet.available(initialStage?.drills ?: if (minimal) DailySet.MINIMAL else training.plan, APP_STAGE),
            customTitle = initialStage?.title,
            customGoal = initialStage?.goal ?: initialTrack?.subtitle,
            stageMinLevels = initialStage?.minLevelByVariant.orEmpty(),
        ),
    )
    val ui: StateFlow<SetUi> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = graph.settings.settings.first()
            val today = graph.today(settings)
            val studyDay = graph.studyDay(settings)
            val library = graph.library.allOrderable()
            val anyDue = library.any { it.card?.isDue(today) ?: true }
            val singing = SingUnlock.status(graph.practice.recentAttempts(Variant.KAMERTON_SEARCH, SingUnlock.WINDOW))
            val (attempts, lessons) = graph.practice.observeTrainingHistory().first()
            val progress = graph.progress.current()
            val unlocked = Variant.entries.associateWith { progress.unlockedLevel(it) }
            val targetMinutes = minutes ?: settings.dailyMinutes

            val wantsEarFocus = !minimal && (
                (initialStage != null && initialStage.drills.any { it.variant == Variant.TIR_CLASSIC }) ||
                    (initialStage == null && training in setOf(Training.DAILY, Training.EAR))
                )
            val focus = if (wantsEarFocus) {
                AdaptiveEar.choose(attempts, today, lessons) { studyDay.of(Instant.ofEpochMilli(it)) }
            } else null

            val songFocus = if (!minimal && initialStage == null && training in setOf(Training.SONGS, Training.ROAD, Training.GUITAR)) {
                val songs = library.mapNotNull { e -> e.song?.let { GuessSong(e.id, it, !e.shelved) } }
                SongFocus.choose(songs, attempts, today, training == Training.GUITAR) {
                    studyDay.of(Instant.ofEpochMilli(it))
                }
            } else null

            val transfer = Metrics.transfer(attempts) { studyDay.of(Instant.ofEpochMilli(it)) }
            val transferGapBig = transfer.trusted && (transfer.gap ?: 0.0) >= 0.2

            val segments = when {
                initialStage != null -> {
                    val built = SetBuilder.buildStage(initialStage, targetMinutes, settings.quietMode, singing, focus, APP_STAGE)
                    if (anyDue) built else DailySet.withoutSongs(built)
                }
                minimal -> SetBuilder.applyQuiet(DailySet.available(DailySet.MINIMAL, APP_STAGE), settings.quietMode)
                training == Training.DAILY -> {
                    val activeTrack = Tracks.byId(settings.activeTrackId)
                    val activeStageIdx = Tracks.evaluate(activeTrack, attempts, unlocked).currentStageIndex
                    SetBuilder.buildDaily(targetMinutes, settings.quietMode, activeTrack, singing, focus, transferGapBig, APP_STAGE, activeStageIdx)
                }
                else -> {
                    var plan = DailySet.available(training.plan, APP_STAGE)
                    if (!anyDue) plan = DailySet.withoutSongs(plan)
                    if (songFocus != null && training == Training.ROAD) {
                        plan = plan.map { b -> if (b.variant == Variant.TIR_UNDER_NOTE) b.copy(variant = Variant.FORMULA_ALOUD) else b }
                    }
                    plan = SingUnlock.plan(plan, singing)
                    if (training.guitar == GuitarNeed.NONE) plan = SetBuilder.applyQuiet(plan, settings.quietMode)
                    if (minutes != null) plan = SetBuilder.scale(plan, minutes)
                    AdaptiveEar.apply(plan, focus)
                }
            }

            state.update {
                it.copy(
                    ready = true,
                    focus = focus,
                    songFocus = songFocus,
                    segments = segments,
                )
            }
        }
    }

    private var starting = false

    fun start() = viewModelScope.launch {
        if (starting || !state.value.ready || state.value.phase != SetPhase.INTRO) return@launch
        starting = true
        if (state.value.sessionId == null) {
            val settings = graph.settings.settings.first()
            val kind = when {
                state.value.minimal -> SessionKind.MINIMAL
                state.value.focus?.variant == Variant.TIR_DUEL -> SessionKind.ADAPTIVE_DUEL
                state.value.focus?.retention == true -> SessionKind.ADAPTIVE_RETENTION
                state.value.focus?.variant == Variant.TIR_CLASSIC -> SessionKind.ADAPTIVE_PROBE
                state.value.focus?.variant == Variant.TIR_OVER_CHORDS -> SessionKind.ADAPTIVE_CONTEXT
                else -> SessionKind.SET
            }
            val id = graph.practice.startSession(kind, graph.today(settings), state.value.training.takeUnless { state.value.minimal }, state.value.focus, state.value.songFocus?.formulaKey)
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

    /** «Пропустить» / «Следующее упражнение»; [didSomething] = the interrupted round had answers. */
    fun skipSegment(didSomething: Boolean = false) {
        val s = state.value
        if (s.index == s.focusIndex && s.phase == SetPhase.NEXT_UP) markSkipped(s)
        advance(s.activity + if (didSomething) 1 else 0)
    }

    private fun markSkipped(s: SetUi) {
        s.sessionId?.let { id -> graph.appScope.launch { graph.practice.skipFocus(id) } }
    }

    /** «Выйти из сета» from the pause: what was done still counts for the streak. */
    fun endSet(didSomething: Boolean = false) {
        val s = state.value
        if (s.phase == SetPhase.DONE) return
        if (s.index < s.focusIndex || (s.index == s.focusIndex && s.phase == SetPhase.NEXT_UP)) markSkipped(s)
        state.update { it.copy(endedEarly = true, activity = it.activity + if (didSomething) 1 else 0) }
        finish()
    }

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
fun SetScreen(
    minimal: Boolean,
    onExit: () -> Unit,
    onLibrary: () -> Unit,
    training: Training = Training.DAILY,
    trackId: String? = null,
    stageId: String? = null,
    minutes: Int? = null,
) {
    val graph = LocalAppGraph.current
    val vm: SetViewModel = viewModel(key = "set-$minimal-${training.name}-$trackId-$stageId-$minutes") {
        SetViewModel(graph, minimal, training, trackId, stageId, minutes)
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val seg = ui.current

    when (ui.phase) {
        SetPhase.INTRO -> SetIntro(ui, onStart = vm::start, onExit = onExit)
        SetPhase.NEXT_UP -> if (seg != null) NextUp(ui, seg, onGo = vm::go, onSkip = { vm.skipSegment() }, onEndSet = { vm.endSet() })
        SetPhase.RUNNING -> if (seg != null) {
            if (seg.variant == Variant.TAPER_ORDER) {
                TaperScreen(
                    spec = TaperSpec(budgetMinutes = seg.minutes, sessionId = ui.sessionId),
                    onExit = { vm.endSet() },
                    onFinished = { vm.roundDone(true) },
                    onOpenLibrary = onLibrary,
                    vmKey = "set-taper-${ui.sessionId}-${ui.index}",
                    onNext = { vm.roundDone(true) },
                    exitLabel = EXIT_SET,
                )
            } else {
                GameScreen(
                    spec = RoundSpec(
                        seg.variant,
                        level = ui.stageMinLevels[seg.variant],
                        sessionId = ui.sessionId,
                        durationMs = ui.roundMsOf(seg),
                        songFocus = ui.songFocus?.takeIf { ui.round == 0 && ui.index == ui.segments.indexOfFirst { block ->
                            block.variant == (if (ui.training == Training.GUITAR) Variant.TAPER_STEPS else Variant.FORMULA_ALOUD)
                        } },
                        focus = ui.focus?.takeIf { ui.focusIndex == ui.index },
                    ),
                    onExit = { vm.endSet() },
                    onFinished = { vm.roundDone(it.answered > 0) },
                    vmKey = "set-${ui.sessionId}-${ui.index}-${ui.round}",
                    onNext = { vm.skipSegment(it > 0) },
                    onLeave = { vm.endSet(it > 0) },
                    exitLabel = EXIT_SET,
                )
            }
        }
        SetPhase.DONE -> SetDone(ui, onExit)
    }
}

private const val EXIT_SET = "Выйти из сета"

@Composable
private fun SetIntro(ui: SetUi, onStart: () -> Unit, onExit: () -> Unit) {
    val totalMin = formatMinutes(ui.segments.sumOf { it.minutes })
    val goal = ui.customGoal ?: if (ui.minimal) "серия не прервётся" else ui.training.goal
    ScreenFrame(
        title = ui.customTitle ?: if (ui.minimal) "Короткий сет" else ui.training.ru,
        subtitle = "$totalMin — $goal" + (if (!ui.guitarNeeded) " · гитара не нужна" else ""),
        accent = Accents.Hero,
        onBack = onExit,
    ) {
        ui.segments.forEachIndexed { i, s ->
            item(key = "seg-$i") {
                GlassCard(Modifier.fillMaxWidth(), accent = ModeVisuals.accent(s.variant)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ModeOrb(s.variant.mode, size = 26.dp)
                        Spacer(Modifier.padding(horizontal = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.variant.ru, style = MaterialTheme.typography.titleLarge)
                            Text(variantHint(s.variant), style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
                        }
                        Text(formatMinutes(s.minutes), style = MaterialTheme.typography.titleMedium, color = Juicy.Gold)
                    }
                }
            }
        }
        ui.songFocus?.let { song -> item(key = "song-reason") {
            val task = if (ui.training == Training.GUITAR) "Сыграй первый переход этой части в предложенной тональности."
                else "Попробуй вспомнить её ещё раз."
            Text("«${song.title}», часть ${song.section} в прошлый раз далась с трудом. $task")
        } }
        ui.focus?.let { focus -> item(key = "reason") { Text(focus.reason) } }
        item(key = "go") {
            JuicyButton("Поехали", enabled = ui.ready, onClick = onStart, modifier = Modifier.fillMaxWidth(), height = 72.dp, leadingIcon = AppIcon.PLAY)
        }
    }
}

private fun formatMinutes(minutes: Double): String {
    val rounded = minutes.roundToInt().toDouble()
    return if (kotlin.math.abs(minutes - rounded) < 1e-6) {
        "${rounded.toInt()} мин"
    } else {
        "${((minutes * 10.0).roundToInt() / 10.0).toString().replace('.', ',')} мин"
    }
}

private fun variantHint(v: Variant): String = when (v) {
    Variant.TIR_CLASSIC -> "нота над гулом тоники — назови ступень"
    Variant.TIR_DUEL -> "две путающиеся ступени подряд до автоматизма"
    Variant.TIR_OVER_CHORDS -> "ступень звучит поверх меняющихся аккордов"
    Variant.TIR_UNDER_NOTE -> "нота видна — угадай аккорд под ней"
    Variant.TIR_ON_TONIC -> "аккорд над тоникой — какая это ступень"
    Variant.TIR_BASS -> "только бас аккорда над тоникой"
    Variant.TIR_TWO_CHORDS -> "из одного аккорда в следующий — куда шаг"
    Variant.TIR_DYAD -> "два звука вместе — назови верхний от нижнего"
    Variant.TIR_COLOR -> "мажор, минор, уменьшённый или септаккорд"
    Variant.TIR_LAYERS -> "функция аккорда: дом, уход или напряжение"
    Variant.TIR_MODE -> "лад целиком над тоникой"
    Variant.TIR_SIMON -> "повтори растущую цепочку ступеней"
    Variant.TIR_SONG -> "узнай песню по начальной гармонии"
    Variant.TIR_NEXT -> "три аккорда прозвучали — куда ход пойдёт дальше"
    Variant.TIR_LOOP3 -> "одна петля в трёх тональностях подряд"
    Variant.TIR_TRANSFER -> "контроль: ступени над дроном и в живом миксе"
    Variant.KAMERTON_SEARCH -> "найди заданную ступень голосом над тоникой"
    Variant.KAMERTON_SING -> "спой нужную ступень с первой ноты"
    Variant.HUNT_REVERSE -> "назови ноту на показанном ладу тапами"
    Variant.HUNT_FIND -> "сыграй названную ноту на грифе"
    Variant.HUNT_ALL_OCTAVES -> "найди все октавы ноты по грифу"
    Variant.TIR_BASSLINE -> "сними басовую линию по слуху на гитаре"
    Variant.TAPER_STEPS -> "сыграй аккордовый ход в случайной тональности"
    Variant.TAPER_ORDER -> "песни из библиотеки по ступеням"
    Variant.FORMULA_ALOUD -> "вспомни формулу части песни без инструмента"
    else -> v.mode.goal
}

@Composable
private fun NextUp(ui: SetUi, seg: SetSegment, onGo: () -> Unit, onSkip: () -> Unit, onEndSet: () -> Unit) {
    var count by remember(ui.index) { mutableIntStateOf(3) }
    var paused by remember(ui.index) { mutableStateOf(false) }
    // ADR-019: back pauses the countdown and asks; it never skips or leaves on its own.
    LaunchedEffect(ui.index, paused) {
        if (paused) return@LaunchedEffect
        while (count > 0) {
            delay(800)
            count--
        }
        onGo()
    }
    BackHandler(enabled = !paused) { paused = true }
    Box(Modifier.fillMaxSize()) {
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
                ModeOrb(seg.variant.mode, size = 120.dp)
                Spacer(Modifier.height(20.dp))
                Text(seg.variant.ru, style = MaterialTheme.typography.displaySmall)
                Text("${formatMinutes(seg.minutes)} · ${seg.variant.guitar.ru}", style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim)
                Spacer(Modifier.height(24.dp))
                GradientText(if (count > 0) "$count" else "!", MaterialTheme.typography.displayLarge, ModeVisuals.accent(seg.variant), Modifier.pop(count + 1, 0.5f))
                Spacer(Modifier.height(24.dp))
                GhostButton("Пропустить", onClick = onSkip)
                Spacer(Modifier.height(10.dp))
                GhostButton("Пауза", onClick = { paused = true }, colors = Accents.Calm, leadingIcon = AppIcon.PAUSE)
            }
        }
        if (paused) {
            PauseOverlay(
                onResume = { paused = false },
                onExit = onEndSet,
                exitLabel = EXIT_SET,
                onNext = onSkip,
                hint = "Следующая часть — ${seg.variant.ru}. Отсчёт стоит.",
            )
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
            Text(if (ui.activity > 0) "Сет закрыт!" else "Сет пропущен", style = MaterialTheme.typography.displaySmall)
            if (ui.endedEarly && ui.activity > 0) {
                Text("вышли на части ${ui.index + 1} из ${ui.segments.size} — сделанное засчитано", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
            }
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
