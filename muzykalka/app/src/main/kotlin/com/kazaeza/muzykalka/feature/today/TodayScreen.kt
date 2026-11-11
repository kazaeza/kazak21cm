package com.kazaeza.muzykalka.feature.today

import com.kazaeza.muzykalka.ui.theme.Numbers
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.kazaeza.muzykalka.ui.component.CountUpText
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.component.StatPill
import com.kazaeza.muzykalka.ui.effect.breathe
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy

/**
 * «Сегодня» — one big button, the one number that matters and the no-guitar shortcuts.
 * Everything else (exam, triangle, detailed stats) lives in «Прогресс» (ADR-016).
 */
@Composable
fun TodayScreen(
    onStartSet: () -> Unit,
    onShortSet: () -> Unit,
    onSettings: () -> Unit,
    onPlay: (Variant) -> Unit,
    onFeel: () -> Unit,
    onLibrary: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val vm: TodayViewModel = viewModel { TodayViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    ScreenFrame(
        title = "Сегодня",
        subtitle = ruDate(ui.date),
        accent = Accents.Hero,
        actions = { IconBubble("⋯", onSettings, description = "Настройки") },
    ) {
        item(key = "hero") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Hero, contentPadding = 22.dp) {
                SectionLabel("За неделю", color = Juicy.TextDim)
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUpText(ui.weekMinutes, MaterialTheme.typography.displayLarge, colors = Accents.Hero)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${plural(ui.weekMinutes, "минута", "минуты", "минут")} практики",
                        style = MaterialTheme.typography.titleMedium,
                        color = Juicy.TextDim,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    MiniStat(ui.answersToday, plural(ui.answersToday, "ответ", "ответа", "ответов") + " сегодня")
                    MiniStat(ui.streak, plural(ui.streak, "день", "дня", "дней") + " подряд")
                    // «играю с ходу» is the goal of the whole app, but it stays at zero for months — shown once it is not
                    if (ui.playableNow > 0) MiniStat(ui.playableNow, plural(ui.playableNow, "песня", "песни", "песен") + " с ходу")
                }
            }
        }
        if (ui.libraryCount > 0) {
            item(key = "pills") { StatPill("", "повторить: ${ui.dueCount}", colors = Accents.Taper) }
        }
        item(key = "start") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                JuicyButton(
                    (if (ui.practicedToday) "Ещё один сет" else "Начать сет") + " · ${ui.setMinutes} мин",
                    onClick = onStartSet,
                    modifier = Modifier.fillMaxWidth().breathe(!ui.practicedToday, 0.025f, 1800),
                    height = 76.dp,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    leading = "►",
                )
                Text(
                    ui.set.joinToString(" → ") { it.variant.ru.lowercase() } + ". Любую часть можно пропустить.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Juicy.TextDim,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
        item(key = "short") {
            GhostButton("Нет времени? 5 минут", onClick = onShortSet, modifier = Modifier.fillMaxWidth(), colors = Accents.Taper, leading = "")
        }
        if (ui.libraryCount == 0) {
            item(key = "empty") {
                GlassCard(Modifier.fillMaxWidth(), accent = Accents.Taper, onClick = onLibrary) {
                    Text("Возьми песню из сборника", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "В библиотеке есть готовые аккорды обучающих песен: Stand by Me, блюз, Autumn Leaves и ещё два десятка. " +
                            "Одно касание — и песня твоя: приложение будет просить сыграть её в разных тональностях.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Juicy.TextDim,
                    )
                }
            }
        }
        item(key = "noguitar") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Без гитары — в дороге")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Тир", onClick = { onPlay(Variant.TIR_CLASSIC) }, modifier = Modifier.weight(1f), colors = Accents.Tir, leading = "")
                    GhostButton("Вайб ступеней", onClick = onFeel, modifier = Modifier.weight(1f), colors = Accents.Tir, leading = "")
                }
            }
        }
    }
}

/** A small dot-matrix number with what it counts under it (O-30). */
@Composable
private fun MiniStat(value: Int, label: String) {
    Column {
        Text(value.toString(), style = Numbers.Small, color = Juicy.Text)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Juicy.TextDim)
    }
}
