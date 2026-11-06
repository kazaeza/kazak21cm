package com.kazaeza.muzykalka.feature.progress

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.data.repo.ExamResult
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.feature.today.TRIANGLE_ORDER
import com.kazaeza.muzykalka.game.Comparison
import com.kazaeza.muzykalka.game.Graduation
import com.kazaeza.muzykalka.game.Metrics
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.FretPosition
import com.kazaeza.muzykalka.music.Fretboard
import com.kazaeza.muzykalka.music.PitchClass
import com.kazaeza.muzykalka.ui.chart.ConfusionGrid
import com.kazaeza.muzykalka.ui.chart.LineChart
import com.kazaeza.muzykalka.ui.chart.TriangleChart
import com.kazaeza.muzykalka.ui.component.BigStat
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProgressUi(
    val loading: Boolean = true,
    val settings: Settings = Settings(),
    val links: List<Double?> = List(6) { null },
    val playableSeries: List<Int> = emptyList(),
    val heat: List<Metrics.HeatCell> = emptyList(),
    val confusion: Metrics.Confusion? = null,
    val worstPair: Pair<String, String>? = null,
    val retention7: Double? = null,
    val retention30: Double? = null,
    val stats: List<Metrics.VariantStats> = emptyList(),
    val exams: List<ExamResult> = emptyList(),
)

/** Tickets P-02/P-03. */
class ProgressViewModel(graph: AppGraph) : ViewModel() {
    val ui: StateFlow<ProgressUi> = combine(
        graph.settings.settings,
        graph.practice.observeAttempts(),
        graph.practice.observeOrders(),
        graph.library.observeEntries(),
        graph.practice.observeExams(),
    ) { settings, attempts, orders, entries, exams ->
        val today = graph.today(settings)
        val cards = entries.mapNotNull { it.card }
        val scores = Metrics.linkScores(attempts)
        val confusion = Metrics.confusion(attempts)
        ProgressUi(
            loading = false,
            settings = settings,
            links = TRIANGLE_ORDER.map { scores[it] },
            playableSeries = Metrics.playableNowSeries(orders).map { it.second },
            heat = Metrics.fretHeat(attempts),
            confusion = confusion.takeIf { c -> c.counts.any { it.sum() > 0 } },
            worstPair = Metrics.worstPair(confusion),
            retention7 = Metrics.retention(cards, today, 7, graph.fsrs),
            retention30 = Metrics.retention(cards, today, 30, graph.fsrs),
            stats = Metrics.variantStats(attempts),
            exams = exams,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUi())
}

@Composable
fun ProgressScreen(onExam: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: ProgressViewModel = viewModel { ProgressViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val notation = ui.settings.degreeNotation
    ScreenFrame(title = "Прогресс", subtitle = "каждый результат — число", accent = Accents.Kamerton) {
        item(key = "playable") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Hero) {
                SectionLabel("играю с ходу · по дням")
                if (ui.playableSeries.isEmpty()) {
                    Text("Появится после первых заказов Тапёра.", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                } else {
                    LineChart(ui.playableSeries)
                }
                Text("Если к 4-й неделе не растёт — пересматриваем программу.", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
            }
        }
        item(key = "triangle") {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("треугольник связей")
                TriangleChart(ui.links)
            }
        }
        item(key = "retention") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Good) {
                SectionLabel("удержание библиотеки")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigStat(ui.retention7?.let { "${(it * 100).toInt()}%" } ?: "—", "через 7 дней", colors = Accents.Good)
                    BigStat(ui.retention30?.let { "${(it * 100).toInt()}%" } ?: "—", "через 30 дней", colors = Accents.Good)
                }
            }
        }
        item(key = "heat") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Hunt) {
                SectionLabel("тепловая карта грифа · Охота")
                if (ui.heat.isEmpty()) {
                    Text("Сыграй пару раундов Охоты — здесь будут видны медленные лады.", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                } else {
                    val board = Fretboard(ui.settings.tuning, ui.settings.fretCount)
                    val heat = buildMap<FretPosition, androidx.compose.ui.graphics.Color> {
                        ui.heat.forEach { cell ->
                            if (cell.string in board.strings) {
                                val speed = (1.0 - (cell.medianMs - 800.0) / 3200.0).coerceIn(0.0, 1.0) * cell.accuracy
                                val color = if (speed < 0.5) lerp(Juicy.Hot, Juicy.Gold, (speed / 0.5).toFloat()) else lerp(Juicy.Gold, Juicy.Good, ((speed - 0.5) / 0.5).toFloat())
                                board.fretsOf(PitchClass.of(cell.pitchClass), cell.string).forEach { f -> put(FretPosition(cell.string, f), color) }
                            }
                        }
                    }
                    com.kazaeza.muzykalka.ui.neck.Fretboard(board.tuning.stringCount, toFret = 12, heat = heat, leftHanded = ui.settings.leftHanded, height = 170.dp)
                    Text("зелёный — быстро и точно, красный — медленно или с ошибками", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
                }
            }
        }
        item(key = "confusion") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Tir) {
                SectionLabel("матрица путаницы · Тир")
                val c = ui.confusion
                if (c == null) {
                    Text("Появится после раундов Тира.", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                } else {
                    val labels = c.labels.map { DegreeFormatter.degree(Degree.parse(it), notation) }
                    ConfusionGrid(labels, c.counts, labelColor = { i -> DegreeColors.ofSemitone(i) })
                    ui.worstPair?.let { (a, b) ->
                        Text(
                            "Чаще всего путаешь ${DegreeFormatter.degree(Degree.parse(a), notation)} и ${DegreeFormatter.degree(Degree.parse(b), notation)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Juicy.Gold,
                        )
                    }
                }
            }
        }
        if (ui.stats.isNotEmpty()) {
            item(key = "stats") {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionLabel("по режимам")
                    ui.stats.forEach { s ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.variant.ru, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                "${(s.accuracy * 100).toInt()}% · ${s.medianMs?.let { String.format(java.util.Locale.US, "%.1f с", it / 1000.0) } ?: "—"} · ${s.attempts}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Juicy.TextDim,
                            )
                        }
                    }
                }
            }
        }
        item(key = "report") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Calm) {
                SectionLabel("табель · пороги выпускного")
                val latest = ui.exams.groupBy { it.skill }.mapValues { (_, v) -> v.maxBy { it.atEpochMs } }
                Graduation.THRESHOLDS.forEach { t ->
                    val r = latest[t.skill]
                    val op = when (t.comparison) {
                        Comparison.LESS_THAN -> "<"
                        Comparison.AT_LEAST -> "≥"
                        Comparison.AT_MOST -> "≤"
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (r == null) "·" else if (r.passed) "✅" else "⬜", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(1.dp).fillMaxWidth(0.03f))
                        Text(t.skill.ru, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            (r?.let { formatMeasure(t.unit, it.measured) + " / " } ?: "") + "$op ${formatMeasure(t.unit, t.value)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (r?.passed == true) Juicy.Good else Juicy.TextDim,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                JuicyButton(if (ui.exams.isEmpty()) "Вступительный экзамен" else "Зачёт", onClick = onExam, modifier = Modifier.fillMaxWidth(), colors = Accents.Calm, leading = "📋")
            }
        }
    }
}

private fun formatMeasure(unit: String, v: Double): String = when (unit) {
    "верно" -> "${(v * 100).toInt()}%"
    "с" -> String.format(java.util.Locale.US, "%.1f с", v)
    else -> "${v.toInt()} $unit"
}
