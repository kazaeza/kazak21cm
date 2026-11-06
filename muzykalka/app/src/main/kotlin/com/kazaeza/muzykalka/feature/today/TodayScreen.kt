package com.kazaeza.muzykalka.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.feature.common.IconBubble
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.feature.common.plural
import com.kazaeza.muzykalka.feature.common.ruDate
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.ui.chart.TriangleChart
import com.kazaeza.muzykalka.ui.component.CountUpText
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.component.StatPill
import com.kazaeza.muzykalka.ui.effect.breathe
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy

/** «Сегодня» — one big button and the one number that matters. */
@Composable
fun TodayScreen(
    onStartSet: () -> Unit,
    onMinimalDay: () -> Unit,
    onSettings: () -> Unit,
    onPlay: (Variant) -> Unit,
    onLibrary: () -> Unit,
    onProgress: () -> Unit,
    onExam: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val vm: TodayViewModel = viewModel { TodayViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    ScreenFrame(
        title = "Сегодня",
        subtitle = ruDate(ui.date),
        accent = Accents.Hero,
        actions = { IconBubble("⚙️", onSettings, description = "Настройки") },
    ) {
        item(key = "hero") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Hero, contentPadding = 22.dp) {
                SectionLabel("Играю с ходу", color = Juicy.TextDim)
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUpText(ui.playableNow, MaterialTheme.typography.displayLarge, colors = Accents.Hero)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (ui.libraryCount == 0) "библиотека пуста" else "из ${ui.libraryCount} ${plural(ui.libraryCount, "вещи", "вещей", "вещей")}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Juicy.TextDim,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "вещей, сыгранных на последнем заказе без подсказок — в случайной тональности",
                    style = MaterialTheme.typography.bodySmall,
                    color = Juicy.TextFaint,
                )
            }
        }
        item(key = "pills") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("🔥", "${ui.streak} ${plural(ui.streak, "день", "дня", "дней")}", colors = Accents.Tir)
                StatPill("🎹", "к заказу: ${ui.dueCount}", colors = Accents.Taper)
                StatPill("💪", "держатся: ${ui.holding}", colors = Accents.Good)
            }
        }
        item(key = "start") {
            JuicyButton(
                if (ui.practicedToday) "Ещё один сет · 20 мин" else "Начать сет · 20 мин",
                onClick = onStartSet,
                modifier = Modifier.fillMaxWidth().breathe(!ui.practicedToday, 0.025f, 1800),
                height = 76.dp,
                textStyle = MaterialTheme.typography.headlineSmall,
                leading = "▶️",
            )
        }
        item(key = "minimal") {
            GhostButton("Минимальный день · 5 мин", onClick = onMinimalDay, modifier = Modifier.fillMaxWidth(), colors = Accents.Taper, leading = "⚡")
        }
        if (ui.libraryCount == 0) {
            item(key = "empty") {
                GlassCard(Modifier.fillMaxWidth(), accent = Accents.Taper, onClick = onLibrary) {
                    Text("🎸 Добавь первую вещь", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Тапёру нечего заказывать. Запиши любимую вещь ступенями через «Улов» — это займёт полминуты.", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                }
            }
        }
        if (!ui.examDone) {
            item(key = "exam") {
                GlassCard(Modifier.fillMaxWidth(), accent = Accents.Calm, onClick = onExam) {
                    Text("📋 Вступительный экзамен", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Точка отсчёта: гриф, слух и память. Минут десять, без подсказок.", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                }
            }
        }
        item(key = "triangle") {
            GlassCard(Modifier.fillMaxWidth(), onClick = onProgress) {
                SectionLabel("связи · звук, имя, место")
                TriangleChart(ui.links, compact = true)
            }
        }
        item(key = "noguitar") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Без гитары — в дороге")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Тир", onClick = { onPlay(Variant.TIR_CLASSIC) }, modifier = Modifier.weight(1f), colors = Accents.Tir, leading = "🎯")
                    GhostButton("Обратная охота", onClick = { onPlay(Variant.HUNT_REVERSE) }, modifier = Modifier.weight(1f), colors = Accents.Hunt, leading = "🏹")
                }
            }
        }
    }
}
