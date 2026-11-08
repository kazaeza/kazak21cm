package com.kazaeza.muzykalka.feature.exam

import androidx.compose.foundation.layout.size
import com.kazaeza.muzykalka.ui.effect.GlowOrb
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.kazaeza.muzykalka.data.repo.ExamResult
import com.kazaeza.muzykalka.data.repo.SessionKind
import com.kazaeza.muzykalka.feature.common.ModeOrb
import com.kazaeza.muzykalka.feature.common.ModeVisuals
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.feature.game.Drills
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.feature.game.RoundSummary
import com.kazaeza.muzykalka.feature.taper.TaperScreen
import com.kazaeza.muzykalka.feature.taper.TaperSpec
import com.kazaeza.muzykalka.game.Exam
import com.kazaeza.muzykalka.game.ExamStage
import com.kazaeza.muzykalka.game.Graduation
import com.kazaeza.muzykalka.game.Skill
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExamPhase { INTRO, STAGE, MEMORY, DONE }

data class ExamUi(
    val phase: ExamPhase = ExamPhase.INTRO,
    val stage: Int = 0,
    val sessionId: Long? = null,
    val results: List<ExamResult> = emptyList(),
    val hasLibrary: Boolean = false,
)

/** Ticket G-04: the entrance exam / credit — fixed tasks, no hints, numbers into the report card. */
class ExamViewModel(private val graph: AppGraph) : ViewModel() {
    val stages: List<ExamStage> = Exam.STAGES_V01
    private val state = MutableStateFlow(ExamUi())
    val ui: StateFlow<ExamUi> = state.asStateFlow()

    init {
        viewModelScope.launch { state.update { it.copy(hasLibrary = graph.library.allOrderable().isNotEmpty()) } }
    }

    fun start() = viewModelScope.launch {
        val settings = graph.settings.settings.first()
        val kind = if (graph.practice.observeExams().first().isEmpty()) SessionKind.ENTRANCE_EXAM else SessionKind.CREDIT
        val id = graph.practice.startSession(kind, graph.today(settings))
        state.update { it.copy(sessionId = id, phase = ExamPhase.STAGE, stage = 0) }
    }

    fun stageDone(summary: RoundSummary?) {
        val s = state.value
        val stage = stages[s.stage]
        val results = if (summary != null && summary.answered > 0) {
            val measured = Exam.measure(stage.skill, summary.attempts)
            s.results + result(stage.skill, measured)
        } else {
            s.results
        }
        if (s.stage + 1 < stages.size) {
            state.update { it.copy(stage = it.stage + 1, results = results) }
        } else if (s.hasLibrary) {
            state.update { it.copy(phase = ExamPhase.MEMORY, results = results) }
        } else {
            state.update { it.copy(results = results) }
            finish()
        }
    }

    fun memoryDone() = viewModelScope.launch {
        val id = state.value.sessionId ?: return@launch
        val count = graph.practice.playableInSession(id).toDouble()
        state.update { it.copy(results = it.results + result(Skill.MEMORY, count)) }
        finish()
    }

    private fun result(skill: Skill, measured: Double): ExamResult {
        val t = Graduation.THRESHOLDS.first { it.skill == skill }
        return ExamResult(state.value.sessionId ?: 0, skill, measured, Graduation.passed(t, measured), System.currentTimeMillis())
    }

    private fun finish() = viewModelScope.launch {
        val id = state.value.sessionId
        val results = state.value.results
        if (id != null && results.isNotEmpty()) {
            graph.practice.saveExam(id, results)
            graph.practice.finishSession(id)
        }
        state.update { it.copy(phase = ExamPhase.DONE) }
    }
}

@Composable
fun ExamScreen(onExit: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: ExamViewModel = viewModel { ExamViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    when (ui.phase) {
        ExamPhase.INTRO -> ScreenFrame(title = "Замер уровня", subtitle = "точка отсчёта, без подсказок", accent = Accents.Calm, onBack = onExit) {
            item(key = "about") {
                GlassCard(Modifier.fillMaxWidth(), accent = Accents.Calm) {
                    Text("Это замер, а не игра", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Фиксированное число заданий, без жизней и без таймера раунда. Результаты лягут в табель рядом с порогами выпускного. Повторяй каждые две недели как зачёт.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Juicy.TextDim,
                    )
                }
            }
            vm.stages.forEachIndexed { i, s ->
                item(key = "st-$i") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ModeOrb(s.variant.mode, size = 22.dp)
                        Spacer(Modifier.height(1.dp).fillMaxWidth(0.04f))
                        Column(Modifier.weight(1f)) {
                            Text(s.skill.ru, style = MaterialTheme.typography.titleMedium)
                            Text("${s.variant.ru}, ур. ${s.level} (${Drills.levelName(s.variant, s.level)}) · ${s.tasks} заданий", style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
                        }
                    }
                }
            }
            item(key = "memory") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlowOrb(Accents.Calm, Modifier.size(22.dp))
                    Spacer(Modifier.height(1.dp).fillMaxWidth(0.04f))
                    Column(Modifier.weight(1f)) {
                        Text(Skill.MEMORY.ru, style = MaterialTheme.typography.titleMedium)
                        Text(if (ui.hasLibrary) "до ${Exam.MEMORY_ITEMS} случайных вещей из библиотеки, без подсказок" else "пропустим: библиотека пуста", style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
                    }
                }
            }
            item(key = "go") { JuicyButton("Начать", onClick = { vm.start() }, modifier = Modifier.fillMaxWidth(), colors = Accents.Calm, leading = "") }
        }
        ExamPhase.STAGE -> {
            val st = vm.stages[ui.stage]
            GameScreen(
                spec = RoundSpec(st.variant, level = st.level, exam = true, examTasks = st.tasks, sessionId = ui.sessionId),
                onExit = { vm.stageDone(null) },
                onFinished = { vm.stageDone(it) },
                vmKey = "exam-${ui.sessionId}-${ui.stage}",
                exitLabel = "Пропустить этап",
            )
        }
        ExamPhase.MEMORY -> TaperScreen(
            spec = TaperSpec(sessionId = ui.sessionId, exam = true, examItems = Exam.MEMORY_ITEMS, budgetMinutes = 30.0),
            onExit = { vm.memoryDone() },
            onFinished = { vm.memoryDone() },
            vmKey = "exam-memory-${ui.sessionId}",
            exitLabel = "Закончить этап",
        )
        ExamPhase.DONE -> ScreenFrame(title = "Табель", subtitle = "сохранён в «Прогрессе»", accent = Accents.Good, onBack = onExit) {
            ui.results.forEachIndexed { i, r ->
                item(key = "r-$i") {
                    val t = Graduation.THRESHOLDS.first { it.skill == r.skill }
                    GlassCard(Modifier.fillMaxWidth(), accent = if (r.passed) Accents.Good else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (r.passed) "✓" else "○", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(1.dp).fillMaxWidth(0.04f))
                            Column(Modifier.weight(1f)) {
                                Text(r.skill.ru, style = MaterialTheme.typography.titleMedium)
                                Text(t.check, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
                            }
                            Text(
                                when (t.unit) {
                                    "верно" -> "${(r.measured * 100).toInt()}%"
                                    "с" -> String.format(java.util.Locale.US, "%.1f с", r.measured)
                                    else -> "${r.measured.toInt()}"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (r.passed) Juicy.Good else Juicy.Gold,
                            )
                        }
                    }
                }
            }
            item(key = "hint") {
                SectionLabel("пороги — предложения плана; поправим их после вступительного")
            }
            item(key = "done") { JuicyButton("Готово", onClick = onExit, modifier = Modifier.fillMaxWidth(), colors = Accents.Good) }
            item(key = "spacer") { GhostButton("Ещё раз позже", onClick = onExit, modifier = Modifier.fillMaxWidth()) }
        }
    }
}
