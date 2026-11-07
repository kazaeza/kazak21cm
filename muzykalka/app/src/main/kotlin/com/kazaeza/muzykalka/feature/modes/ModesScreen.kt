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
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.launch

/** «Режимы»: the modes of this build as tiles; later ones collapse into one «скоро» line. */
@Composable
fun ModesScreen(onOpen: (ModeId) -> Unit) {
    val graph = LocalAppGraph.current
    val progress by graph.progress.progress.collectAsState(initial = GameProgress())
    val (ready, later) = ModeId.entries.partition { it.isAvailable() }
    ScreenFrame(title = "Режимы", subtitle = "каждый тренирует один навык", accent = Accents.Recorder) {
        ready.chunked(2).forEach { pair ->
            item(key = pair.first().name) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { mode -> ModeTile(mode, progress, Modifier.weight(1f)) { onOpen(mode) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (later.isNotEmpty()) {
            item(key = "later") {
                Text(
                    "Скоро: " + later.joinToString(", ") { it.ru } + ".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Juicy.TextFaint,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ModeTile(mode: ModeId, progress: GameProgress, modifier: Modifier, onClick: () -> Unit) {
    val accent = ModeVisuals.accent(mode)
    val main = Variant.entries.firstOrNull { it.mode == mode && it in Drills.PLAYABLE }
    GlassCard(modifier.heightIn(min = 210.dp), accent = accent, onClick = onClick, contentPadding = 16.dp) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Badge(mode.guitar.ru, listOf(Juicy.Ink4, Juicy.Ink4))
            val rec = main?.let { progress.record(it) } ?: 0
            if (rec > 0) Badge("🏆 $rec", accent)
        }
    }
}

/** What the mode is and how to play it, in plain words: the first thing on its screen. */
private fun howTo(mode: ModeId): String? = when (mode) {
    ModeId.TIR ->
        "Гудит дрон — это «дом», тоника; в новой тональности сначала пробегает гамма. Потом звучит нота: нажми её ступень. " +
            "Не уверен — жми «🤷 не знаю»: это честнее, чем угадывать, а правильный ответ и путь домой ты всё равно услышишь."
    ModeId.HUNT ->
        "На экране нота — найди её на грифе и сыграй, телефон слушает. «Обратная охота» — наоборот: " +
            "показано место на грифе, назови ноту. Гитара не нужна."
    ModeId.TAPER ->
        "Тапёр — это ты за гитарой, а приложение заказывает музыку. В «Ходах» на экране тональность и ступени, " +
            "например «ре мажор: I – V». Сыграй эти аккорды, отметь «получилось» или «не вышло» — и сразу услышишь, " +
            "как это должно звучать. Микрофон не нужен. Начни с первого уровня, даже если кажется слишком просто."
    else -> null
}

/** One mode: how to play, then its playable variants. */
@Composable
fun ModeDetailScreen(
    mode: ModeId,
    onBack: () -> Unit,
    onPlay: (Variant, Int) -> Unit,
    onTaper: () -> Unit,
    onBlind: () -> Unit = {},
    onFeel: () -> Unit = {},
    onLibrary: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val progress by graph.progress.progress.collectAsState(initial = GameProgress())
    val scope = rememberCoroutineScope()
    val accent = ModeVisuals.accent(mode)
    val (ready, later) = Variant.entries.filter { it.mode == mode }.partition { it.isAvailable() && (it in Drills.PLAYABLE || it == Variant.TAPER_ORDER || it == Variant.TAPER_BLIND) }
    ScreenFrame(title = "${ModeVisuals.emoji(mode)} ${mode.ru}", subtitle = mode.goal, accent = accent, onBack = onBack) {
        howTo(mode)?.let { text ->
            item(key = "how") {
                GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                    SectionLabel("как играть")
                    Spacer(Modifier.height(6.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
                    if (mode == ModeId.TIR) {
                        Spacer(Modifier.height(12.dp))
                        GhostButton("Сначала послушать вайб ступеней", onClick = onFeel, modifier = Modifier.fillMaxWidth(), colors = accent, leading = "🎧")
                    }
                }
            }
        }
        ready.forEach { v ->
            item(key = v.name) {
                GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(v.ru, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Badge(v.guitar.ru, accent)
                    }
                    if (v in Drills.PLAYABLE) {
                        val max = Drills.maxLevel(v)
                        val allOpen = Drills.allLevelsOpen(v)
                        val unlocked = if (allOpen) max else progress.unlockedLevel(v)
                        val selected = progress.selectedLevel(v)
                        if (max > 1) {
                            Spacer(Modifier.height(10.dp))
                            SectionLabel(if (allOpen) "уровень — любой, начни с первого" else "уровень")
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
                                        .then(
                                            if (open) {
                                                Modifier.clickable {
                                                    scope.launch {
                                                        if (allOpen) graph.progress.unlock(v, level)
                                                        graph.progress.select(v, level)
                                                    }
                                                }
                                            } else {
                                                Modifier
                                            },
                                        )
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
                        if (record > 0 && !Drills.guitarPaced(v)) {
                            Spacer(Modifier.height(6.dp))
                            Text("🏆 рекорд: $record", style = MaterialTheme.typography.bodyMedium, color = Juicy.Gold)
                        }
                        Spacer(Modifier.height(12.dp))
                        JuicyButton("Играть", onClick = { onPlay(v, selected) }, modifier = Modifier.fillMaxWidth(), colors = accent, leading = "▶️")
                    } else if (v == Variant.TAPER_BLIND) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Случайная песня из твоих лайков в случайной тональности — но без названия. На экране только ступени: " +
                                "не за что зацепиться, кроме самих аккордов. Сыграй, а кто это был, узнаешь в конце.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Juicy.TextDim,
                        )
                        Spacer(Modifier.height(12.dp))
                        JuicyButton("Играть вслепую", onClick = onBlind, modifier = Modifier.fillMaxWidth(), colors = accent, leading = "🎭")
                    } else if (v == Variant.TAPER_ORDER) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "То же, но целые песни из твоей библиотеки. Каждая приходит по графику повторений и каждый раз в новой тональности — " +
                                "так песня запоминается ступенями, а не пальцами.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Juicy.TextDim,
                        )
                        Spacer(Modifier.height(12.dp))
                        JuicyButton("Играть песни", onClick = onTaper, modifier = Modifier.fillMaxWidth(), colors = accent, leading = "🎸")
                        Spacer(Modifier.height(8.dp))
                        GhostButton("Взять песню из сборника", onClick = onLibrary, modifier = Modifier.fillMaxWidth(), colors = accent, leading = "📚")
                    }
                }
            }
        }
        item(key = "rules") {
            Text(
                (if (later.isNotEmpty()) "Скоро: " + later.joinToString(", ") { it.ru } + ". " else "") +
                    if (mode == ModeId.TAPER) "Раунд 90 секунд, без жизней: оценку ставишь ты сам, честно." else "Раунд 60–90 секунд · ошибся — сразу слышишь правильный ответ.",
                style = MaterialTheme.typography.bodySmall,
                color = Juicy.TextFaint,
            )
        }
    }
}
