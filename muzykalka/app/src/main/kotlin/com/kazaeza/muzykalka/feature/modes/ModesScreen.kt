package com.kazaeza.muzykalka.feature.modes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.data.settings.GameProgress
import com.kazaeza.muzykalka.feature.common.ModeVisuals
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.feature.common.isAvailable
import com.kazaeza.muzykalka.feature.game.Drills
import com.kazaeza.muzykalka.game.ModeId
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.ui.component.Badge
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.launch

/** «Режимы» — six tiles (ticket T-02). */
@Composable
fun ModesScreen(onOpen: (ModeId) -> Unit) {
    val graph = LocalAppGraph.current
    val progress by graph.progress.progress.collectAsState(initial = GameProgress())
    ScreenFrame(title = "Режимы", subtitle = "шесть связей треугольника", accent = Accents.Recorder) {
        ModeId.entries.chunked(2).forEach { pair ->
            item(key = pair.first().name) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { mode -> ModeTile(mode, progress, Modifier.weight(1f)) { onOpen(mode) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ModeTile(mode: ModeId, progress: GameProgress, modifier: Modifier, onClick: () -> Unit) {
    val accent = ModeVisuals.accent(mode)
    val available = mode.isAvailable()
    val main = Variant.entries.firstOrNull { it.mode == mode }
    GlassCard(modifier.heightIn(min = 210.dp).alpha(if (available) 1f else 0.55f), accent = accent, onClick = onClick, contentPadding = 16.dp) {
        Box(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(accent.map { it.copy(alpha = 0.35f) }))
                .padding(10.dp),
        ) { Text(ModeVisuals.emoji(mode), style = MaterialTheme.typography.displaySmall) }
        Spacer(Modifier.height(10.dp))
        Text(mode.ru, style = MaterialTheme.typography.titleLarge, color = Juicy.Text)
        Spacer(Modifier.height(4.dp))
        Text(mode.goal, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim, maxLines = 3)
        Spacer(Modifier.height(10.dp))
        if (available && main != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (Drills.maxLevel(main) > 1) Badge("ур. ${progress.unlockedLevel(main)}/${Drills.maxLevel(main)}", accent)
                val rec = progress.record(main)
                if (rec > 0) Badge("🏆 $rec", listOf(Juicy.Ink4, Juicy.Ink4))
            }
        } else {
            Badge("скоро · v${mode.since}", listOf(Juicy.Ink4, Juicy.Ink4))
        }
    }
}

/** One mode: its variants with level pickers. */
@Composable
fun ModeDetailScreen(mode: ModeId, onBack: () -> Unit, onPlay: (Variant, Int) -> Unit, onTaper: () -> Unit) {
    val graph = LocalAppGraph.current
    val progress by graph.progress.progress.collectAsState(initial = GameProgress())
    val scope = rememberCoroutineScope()
    val accent = ModeVisuals.accent(mode)
    ScreenFrame(title = "${ModeVisuals.emoji(mode)} ${mode.ru}", subtitle = mode.goal, accent = accent, onBack = onBack) {
        if (mode.links.isNotEmpty()) {
            item(key = "links") {
                Text("Связь: " + mode.links.joinToString(", ") { it.ru } + " · " + mode.guitar.ru, style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
            }
        }
        Variant.entries.filter { it.mode == mode }.forEach { v ->
            item(key = v.name) {
                val available = v.isAvailable() && (v in Drills.PLAYABLE || v == Variant.TAPER_ORDER)
                GlassCard(Modifier.fillMaxWidth().alpha(if (available) 1f else 0.5f), accent = if (available) accent else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(v.ru, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Badge(if (available) v.guitar.ru else "скоро · v${v.since}", if (available) accent else listOf(Juicy.Ink4, Juicy.Ink4))
                    }
                    if (available && v in Drills.PLAYABLE) {
                        val max = Drills.maxLevel(v)
                        val unlocked = progress.unlockedLevel(v)
                        val selected = progress.selectedLevel(v)
                        if (max > 1) {
                            Spacer(Modifier.height(10.dp))
                            SectionLabel("уровень")
                            Spacer(Modifier.height(6.dp))
                            (1..max).forEach { level ->
                                val open = level <= unlocked
                                val isSel = level == selected
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSel) Brush.horizontalGradient(accent.map { it.copy(alpha = 0.45f) }) else Brush.horizontalGradient(listOf(Juicy.Ink3, Juicy.Ink3)))
                                        .then(if (open) Modifier.clickable { scope.launch { graph.progress.select(v, level) } } else Modifier)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(if (open) "$level" else "🔒", style = MaterialTheme.typography.titleMedium, color = if (isSel) Color.White else Juicy.TextDim)
                                    Spacer(Modifier.padding(horizontal = 6.dp))
                                    Text(Drills.levelName(v, level), style = MaterialTheme.typography.bodyMedium, color = if (open) Juicy.Text else Juicy.TextFaint)
                                }
                            }
                        }
                        val record = progress.record(v)
                        if (record > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text("🏆 рекорд: $record", style = MaterialTheme.typography.bodyMedium, color = Juicy.Gold)
                        }
                        Spacer(Modifier.height(12.dp))
                        JuicyButton("Играть", onClick = { onPlay(v, selected) }, modifier = Modifier.fillMaxWidth(), colors = accent, leading = "▶️")
                    } else if (available && v == Variant.TAPER_ORDER) {
                        Spacer(Modifier.height(8.dp))
                        Text("Заказы из твоей библиотеки по графику повторений, в случайной тональности.", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                        Spacer(Modifier.height(12.dp))
                        JuicyButton("Открыть бар", onClick = onTaper, modifier = Modifier.fillMaxWidth(), colors = accent, leading = "🎹")
                    }
                }
            }
        }
        item(key = "rules") {
            Text(
                "Раунд 60–90 секунд · засчитывается первая нота · ошибся — сразу слышишь правильный ответ · каждый результат — число.",
                style = MaterialTheme.typography.bodySmall,
                color = Juicy.TextFaint,
            )
        }
    }
}
