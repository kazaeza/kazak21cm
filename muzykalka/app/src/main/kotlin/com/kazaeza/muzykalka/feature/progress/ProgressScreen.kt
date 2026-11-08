package com.kazaeza.muzykalka.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.kazaeza.muzykalka.feature.common.plural
import com.kazaeza.muzykalka.feature.today.TRIANGLE_ORDER
import com.kazaeza.muzykalka.game.Comparison
import com.kazaeza.muzykalka.game.Graduation
import com.kazaeza.muzykalka.game.Levels
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
import com.kazaeza.muzykalka.ui.component.GhostButton
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
import java.time.Instant
import java.time.ZoneId

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
    val daysTotal: Int = 0,
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val practiceMinutes: Long = 0,
    val answers: Int = 0,
    val songsPlayed: Int = 0,
    val degrees: List<Metrics.DegreeStat> = emptyList(),
    val guessing: Metrics.Guessing = Metrics.Guessing(0, 0, 0),
    val dayParts: List<Metrics.DayPart> = emptyList(),
    val steps: List<Metrics.StepsLevelStat> = emptyList(),
)

/** Tickets P-02/P-03: everything the app knows about the practice, as numbers. */
class ProgressViewModel(graph: AppGraph) : ViewModel() {
    private val zone = ZoneId.systemDefault()

    private val base = combine(
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
            answers = attempts.size,
            songsPlayed = orders.size,
            degrees = Metrics.degreeStats(attempts),
            guessing = Metrics.guessing(attempts),
            dayParts = Metrics.byPartOfDay(attempts) { ms -> Instant.ofEpochMilli(ms).atZone(zone).hour },
            steps = Metrics.stepsStats(attempts),
        )
    }

    val ui: StateFlow<ProgressUi> = combine(base, graph.practice.observePracticeDays(), graph.practice.observePracticeMs(), graph.settings.settings) { b, days, ms, settings ->
        b.copy(
            daysTotal = days.size,
            streak = Metrics.streak(days, graph.today(settings)),
            longestStreak = Metrics.longestStreak(days),
            practiceMinutes = ms / 60_000,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUi())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(onExam: () -> Unit, onFeel: () -> Unit = {}) {
    val graph = LocalAppGraph.current
    val vm: ProgressViewModel = viewModel { ProgressViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val notation = ui.settings.degreeNotation
    ScreenFrame(title = "Прогресс", subtitle = "всё, что приложение о тебе знает", accent = Accents.Kamerton) {
        item(key = "totals") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Hero) {
                SectionLabel("всего")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigStat("${ui.daysTotal}", plural(ui.daysTotal, "день", "дня", "дней") + "\nс практикой", colors = Accents.Hero)
                    BigStat("${ui.practiceMinutes}", "минут\nв сетах", colors = Accents.Hero)
                    BigStat("${ui.answers}", plural(ui.answers, "ответ", "ответа", "ответов") + "\nв режимах", colors = Accents.Hero)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigStat("${ui.streak}", "серия\nсейчас", colors = Accents.Tir)
                    BigStat("${ui.longestStreak}", "лучшая\nсерия", colors = Accents.Tir)
                    BigStat("${ui.songsPlayed}", "песен\nсыграно", colors = Accents.Taper)
                }
            }
        }
        item(key = "playable") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Hero) {
                SectionLabel("играю с ходу · по дням")
                if (ui.playableSeries.isEmpty()) {
                    Text("Появится, когда сыграешь первую песню из библиотеки.", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                } else {
                    LineChart(ui.playableSeries)
                }
                Text("Главное число: сколько песен ты играешь сам, без подсказок, в любой тональности. Если к 4-й неделе не растёт — пересматриваем программу.", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
            }
        }
        item(key = "degrees") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Tir) {
                SectionLabel("ступени на слух · Тир")
                if (ui.degrees.isEmpty()) {
                    Text("Появится после раундов Тира.", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                } else {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.degrees.forEach { d -> DegreeCell(d, notation) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("точность по последним 50 ответам · время верного ответа · сколько раз звучала", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
                }
                val g = ui.guessing
                val echo = g.echoShare
                if (echo != null || g.passes > 0) {
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("угадывание")
                    Text(
                        (echo?.let { "${(it * 100).toInt()}% ошибок — повтор ответа на прошлую ноту" } ?: "ошибок пока нет") +
                            " · «не знаю»: ${g.passes}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if ((echo ?: 0.0) > GUESSING_ALARM) Juicy.Gold else Juicy.Text,
                    )
                    Text(
                        if ((echo ?: 0.0) > GUESSING_ALARM) {
                            "Похоже, мозг отвечает «не как прошлая», а не слушает. Перед раундом послушай вайб ступеней, а когда не уверен — жми «не знаю»."
                        } else {
                            "Ниже 30% — значит, ты слушаешь, а не угадываешь по прошлому ответу."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Juicy.TextDim,
                    )
                    if ((echo ?: 0.0) > GUESSING_ALARM) {
                        Spacer(Modifier.height(8.dp))
                        GhostButton("Вайб ступеней", onClick = onFeel, modifier = Modifier.fillMaxWidth(), colors = Accents.Tir, leading = "")
                    }
                }
            }
        }
        item(key = "confusion") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Tir) {
                SectionLabel("что с чем путаешь · Тир")
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
        if (ui.steps.isNotEmpty()) {
            item(key = "steps") {
                GlassCard(Modifier.fillMaxWidth(), accent = Accents.Taper) {
                    SectionLabel("ходы на гитаре · Тапёр")
                    ui.steps.forEach { st ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${st.level}. " + Levels.STEPS.getOrElse(st.level - 1) { "" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${(st.accuracy * 100).toInt()}% · ${st.attempts}", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                        }
                    }
                }
            }
        }
        if (ui.dayParts.any { it.attempts > 0 }) {
            item(key = "dayparts") {
                GlassCard(Modifier.fillMaxWidth(), accent = Accents.Calm) {
                    SectionLabel("когда слух острее · все режимы")
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ui.dayParts.forEach { p ->
                            BigStat(p.accuracy?.let { "${(it * 100).toInt()}%" } ?: "—", p.ru + "\n" + p.attempts, colors = Accents.Calm)
                        }
                    }
                }
            }
        }
        item(key = "triangle") {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("треугольник связей")
                TriangleChart(ui.links)
                Text(
                    "Звук, имя ступени и место на грифе. Каждая стрелка — свой навык: Тир качает «звук → имя», Охота — «имя ↔ место», Тапёр — «имя → место».",
                    style = MaterialTheme.typography.bodySmall,
                    color = Juicy.TextFaint,
                )
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
        item(key = "retention") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Good) {
                SectionLabel("сколько песен вспомнишь")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigStat(ui.retention7?.let { "${(it * 100).toInt()}%" } ?: "—", "через 7 дней", colors = Accents.Good)
                    BigStat(ui.retention30?.let { "${(it * 100).toInt()}%" } ?: "—", "через 30 дней", colors = Accents.Good)
                }
            }
        }
        if (ui.stats.isNotEmpty()) {
            item(key = "stats") {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionLabel("по режимам · точность · время · ответов")
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
                SectionLabel("замер уровня")
                Text(
                    "Десять минут без подсказок: гриф, слух и память. Это точка отсчёта — повтори через месяц и сравни. " +
                        "Справа — твой результат и цель.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Juicy.TextDim,
                )
                Spacer(Modifier.height(8.dp))
                val latest = ui.exams.groupBy { it.skill }.mapValues { (_, v) -> v.maxBy { it.atEpochMs } }
                Graduation.THRESHOLDS.forEach { t ->
                    val r = latest[t.skill]
                    val op = when (t.comparison) {
                        Comparison.LESS_THAN -> "<"
                        Comparison.AT_LEAST -> "≥"
                        Comparison.AT_MOST -> "≤"
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (r == null) "·" else if (r.passed) "✓" else "○", style = MaterialTheme.typography.titleMedium)
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
                JuicyButton(if (ui.exams.isEmpty()) "Сделать замер" else "Замерить снова", onClick = onExam, modifier = Modifier.fillMaxWidth(), colors = Accents.Calm, leading = "")
            }
        }
    }
}

/** Above this share of «same as the last answer» errors, the ear is probably not being used. */
private const val GUESSING_ALARM = 0.3

@Composable
private fun DegreeCell(d: Metrics.DegreeStat, notation: com.kazaeza.muzykalka.music.DegreeNotation) {
    val degree = Degree.parse(d.code)
    val color = DegreeColors.of(degree)
    Column(
        Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f + 0.25f * d.accuracy.toFloat()))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(DegreeFormatter.degree(degree, notation), style = MaterialTheme.typography.titleLarge, color = color)
        Text("${(d.accuracy * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = if (d.accuracy >= 0.9) Juicy.Good else if (d.accuracy < 0.6) Juicy.Hot else Juicy.Text)
        Text(
            (d.medianMs?.let { String.format(java.util.Locale.US, "%.1fс", it / 1000.0) } ?: "—") + " · ${d.attempts}",
            style = MaterialTheme.typography.labelSmall,
            color = Juicy.TextDim,
        )
    }
}

private fun formatMeasure(unit: String, v: Double): String = when (unit) {
    "верно" -> "${(v * 100).toInt()}%"
    "с" -> String.format(java.util.Locale.US, "%.1f с", v)
    else -> "${v.toInt()} $unit"
}
