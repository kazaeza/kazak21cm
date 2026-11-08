package com.kazaeza.muzykalka.feature.modes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.map
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.data.settings.GameProgress
import com.kazaeza.muzykalka.feature.common.ModeVisuals
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.feature.common.isAvailable
import com.kazaeza.muzykalka.feature.game.Drills
import com.kazaeza.muzykalka.game.SingUnlock
import com.kazaeza.muzykalka.game.GuitarNeed
import com.kazaeza.muzykalka.game.ModeId
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.launch

/** What a card of the «Режимы» screen starts. */
sealed interface ModeEntry {
    /** A drill on the round engine, with levels. */
    data class Drill(val variant: Variant) : ModeEntry

    /** «Песни» — the Taper on the user's library. */
    data object Songs : ModeEntry

    /** «Вслепую» — a liked song without its name (ADR-024). */
    data object Blind : ModeEntry

    /** «Вайб ступеней» — listen to every degree over its tonic, no score (ADR-015). */
    data object Feel : ModeEntry

    /** «Угадай про себя» — hands-free, the answer after a pause (O-16, ADR-030). */
    data object Listen : ModeEntry
}

/**
 * O-12: every mode on one screen, grouped by skill. Before, the tab showed four tiles and each
 * led to its own screen; «Вслепую» and «Вайб ступеней» were two taps deep and easy to miss.
 */
object ModesCatalog {
    /** Sections in screen order: the ear first, the guitar after. */
    val SECTIONS: List<ModeId> = listOf(ModeId.TIR, ModeId.KAMERTON, ModeId.TAPER, ModeId.HUNT)

    fun entries(mode: ModeId): List<ModeEntry> {
        val drills = Variant.entries
            .filter { it.mode == mode && it.isAvailable() && it in Drills.PLAYABLE }
            .map { ModeEntry.Drill(it) }
        return when (mode) {
            ModeId.TIR -> drills + ModeEntry.Listen + ModeEntry.Feel
            ModeId.TAPER -> drills + ModeEntry.Songs + ModeEntry.Blind
            else -> drills
        }
    }

    fun title(e: ModeEntry): String = when (e) {
        is ModeEntry.Drill -> e.variant.ru
        ModeEntry.Songs -> "Песни"
        ModeEntry.Blind -> "Вслепую"
        ModeEntry.Feel -> "Вайб ступеней"
        ModeEntry.Listen -> "Угадай про себя"
    }

    fun emoji(e: ModeEntry): String = when (e) {
        is ModeEntry.Drill -> when (e.variant) {
            Variant.TIR_CLASSIC -> "🎯"
            Variant.TIR_OVER_CHORDS -> "🎹"
            Variant.TIR_UNDER_NOTE -> "🙃"
            Variant.KAMERTON_SEARCH -> "🧘"
            Variant.KAMERTON_SING -> "🎤"
            Variant.TAPER_STEPS -> "🪜"
            Variant.HUNT_FIND -> "🏹"
            Variant.HUNT_ALL_OCTAVES -> "🎼"
            Variant.HUNT_REVERSE -> "🔁"
            else -> ModeVisuals.emoji(e.variant.mode)
        }
        ModeEntry.Songs -> "🎸"
        ModeEntry.Blind -> "🎭"
        ModeEntry.Feel -> "🎧"
        ModeEntry.Listen -> "🤫"
    }

    fun about(e: ModeEntry): String = when (e) {
        is ModeEntry.Drill -> when (e.variant) {
            Variant.TIR_CLASSIC -> "Гудит тоника, звучит нота — жми её ступень. Не уверен — «🤷 не знаю», ответ всё равно услышишь."
            Variant.TIR_OVER_CHORDS -> "Та же нота, но под ней меняются аккорды лада."
            Variant.TIR_UNDER_NOTE -> "Наоборот: нота на экране, под ней гудит аккорд — какой? Как подобрать аккорд под мелодию."
            Variant.KAMERTON_SEARCH -> "Спокойно: «спой квинту» — и ищешь голосом, пока не попадёшь. Промахнулся — скажу, что спел и куда тянуть. Без жизней."
            Variant.KAMERTON_SING -> "Видишь ступень — поёшь её. Засчитывается первая спетая нота, на скорость."
            Variant.TAPER_STEPS -> "Тональность и ступени, например «ре мажор: I – V». Сыграй ход аккордами и отметь, как вышло."
            Variant.HUNT_FIND -> "На экране нота — найди её на грифе и сыграй, телефон слушает."
            Variant.HUNT_ALL_OCTAVES -> "Та же нота во всех октавах подряд."
            Variant.HUNT_REVERSE -> "Показано место на грифе — назови ноту."
            else -> e.variant.mode.goal
        }
        ModeEntry.Songs -> "Песни из твоей библиотеки по графику повторений, каждый раз в новой тональности."
        ModeEntry.Blind -> "Случайная песня из твоих лайков, но без названия: только ступени. Кто это был — узнаешь в конце."
        ModeEntry.Feel -> "Послушать, как звучит каждая ступень над тоникой. Без очков и таймера."
        ModeEntry.Listen -> "С задержкой и без кнопок: ступень над аккордом, пауза — назови её про себя, потом ответ вслух. Можно в наушниках, не глядя в экран."
    }

    fun guitar(e: ModeEntry): GuitarNeed = when (e) {
        is ModeEntry.Drill -> e.variant.guitar
        ModeEntry.Songs, ModeEntry.Blind -> GuitarNeed.REQUIRED
        ModeEntry.Feel, ModeEntry.Listen -> GuitarNeed.NONE
    }

    /** All cards of the screen, in order. */
    fun all(): List<ModeEntry> = SECTIONS.flatMap(::entries)
}

/** «Режимы»: every mode on one screen, grouped by skill (O-12). */
@Composable
fun ModesScreen(
    onPlay: (Variant, Int) -> Unit,
    onSongs: () -> Unit = {},
    onBlind: () -> Unit = {},
    onFeel: () -> Unit = {},
    onListen: () -> Unit = {},
    onLibrary: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val progress by graph.progress.progress.collectAsState(initial = GameProgress())
    val scope = rememberCoroutineScope()
    val singing by remember { graph.practice.observeAttempts().map { SingUnlock.status(it) } }.collectAsState(initial = SingUnlock.Status(false, 0, 0))
    ScreenFrame(title = "Режимы", subtitle = "всё в одном месте: выбирай и играй", accent = Accents.Recorder) {
        ModesCatalog.SECTIONS.forEach { mode ->
            val entries = ModesCatalog.entries(mode)
            if (entries.isEmpty()) return@forEach
            item(key = "head-${mode.name}") { SectionHead(mode) }
            entries.forEach { e ->
                item(key = "entry-" + ModesCatalog.title(e)) {
                    EntryCard(
                        entry = e,
                        mode = mode,
                        progress = progress,
                        singing = singing,
                        onSelectLevel = { v, level, allOpen ->
                            scope.launch {
                                if (allOpen) graph.progress.unlock(v, level)
                                graph.progress.select(v, level)
                            }
                        },
                        onStart = {
                            when (e) {
                                is ModeEntry.Drill -> onPlay(e.variant, progress.selectedLevel(e.variant))
                                ModeEntry.Songs -> onSongs()
                                ModeEntry.Blind -> onBlind()
                                ModeEntry.Feel -> onFeel()
                                ModeEntry.Listen -> onListen()
                            }
                        },
                        onLibrary = onLibrary,
                    )
                }
            }
        }
        item(key = "rules") {
            Text(
                "Игры идут раундами по 60–90 секунд: ошибся — сразу слышишь правильный ответ. В Тапёре оценку ставишь сам, честно.",
                style = MaterialTheme.typography.bodySmall,
                color = Juicy.TextFaint,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

@Composable
private fun SectionHead(mode: ModeId) {
    val accent = ModeVisuals.accent(mode)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(accent.map { it.copy(alpha = 0.4f) })),
            contentAlignment = Alignment.Center,
        ) { Text(ModeVisuals.emoji(mode), style = MaterialTheme.typography.headlineSmall) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(mode.ru, style = MaterialTheme.typography.headlineSmall, color = Juicy.Text)
            Text(mode.goal, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(
    entry: ModeEntry,
    mode: ModeId,
    progress: GameProgress,
    singing: SingUnlock.Status,
    onSelectLevel: (Variant, Int, Boolean) -> Unit,
    onStart: () -> Unit,
    onLibrary: () -> Unit,
) {
    val accent = ModeVisuals.accent(mode)
    val title = ModesCatalog.title(entry)
    val closed = entry is ModeEntry.Drill && entry.variant == Variant.KAMERTON_SING && !singing.open
    GlassCard(Modifier.fillMaxWidth(), accent = accent, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${ModesCatalog.emoji(entry)} $title", style = MaterialTheme.typography.titleLarge, color = Juicy.Text)
                Spacer(Modifier.height(4.dp))
                Text(ModesCatalog.about(entry), style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
            }
            Spacer(Modifier.width(12.dp))
            PlayDisc(accent, label = "Играть: $title", enabled = !closed, onClick = onStart)
        }
        if (closed) {
            Spacer(Modifier.height(6.dp))
            Text(
                "🔒 Откроется, когда в «Пропевке» с первой попытки выходит половина нот (последние ${SingUnlock.WINDOW}, не меньше ${SingUnlock.MIN_TRIES}). Сейчас: ${singing.correct} из ${singing.tries}.",
                style = MaterialTheme.typography.bodyMedium, color = Juicy.Gold,
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(guitarText(ModesCatalog.guitar(entry)), style = MaterialTheme.typography.labelLarge, color = Juicy.TextFaint)
            val v = (entry as? ModeEntry.Drill)?.variant
            val record = v?.let { progress.record(it) } ?: 0
            if (v != null && record > 0 && !Drills.guitarPaced(v)) {
                Text("🏆 $record", style = MaterialTheme.typography.labelLarge, color = Juicy.Gold)
            }
        }
        if (entry is ModeEntry.Drill) Levels(entry.variant, accent, progress, onSelectLevel)
        if (entry == ModeEntry.Songs) {
            Spacer(Modifier.height(10.dp))
            GhostButton("Взять песню из сборника", onClick = onLibrary, modifier = Modifier.fillMaxWidth(), colors = accent, leading = "📚")
        }
    }
}

private fun guitarText(g: GuitarNeed): String = when (g) {
    GuitarNeed.REQUIRED -> "🎸 нужна гитара"
    GuitarNeed.OPTIONAL -> "🎸 можно без гитары"
    GuitarNeed.NONE -> "🎧 гитара не нужна"
}

/** Round «play» button on the right of a card. */
@Composable
private fun PlayDisc(accent: List<Color>, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(CircleShape)
            .background(Brush.linearGradient(accent))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text("▶", style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center) }
}

/** Chips for the open levels, one line for the locked rest, and the name of the chosen level. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Levels(v: Variant, accent: List<Color>, progress: GameProgress, onSelect: (Variant, Int, Boolean) -> Unit) {
    val max = Drills.maxLevel(v)
    if (max <= 1) return
    val allOpen = Drills.allLevelsOpen(v)
    val unlocked = (if (allOpen) max else progress.unlockedLevel(v)).coerceIn(1, max)
    val selected = progress.selectedLevel(v).coerceIn(1, unlocked)
    Spacer(Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text("уровень", style = MaterialTheme.typography.labelLarge, color = Juicy.TextFaint)
        (1..unlocked).forEach { level ->
            val isSel = level == selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) Brush.linearGradient(accent) else Brush.linearGradient(listOf(Juicy.Ink4, Juicy.Ink3)))
                    .clickable { onSelect(v, level, allOpen) }
                    .semantics { contentDescription = "Уровень $level" },
                contentAlignment = Alignment.Center,
            ) {
                Text("$level", style = MaterialTheme.typography.titleMedium, color = if (isSel) Color.White else Juicy.Text)
            }
        }
        if (unlocked < max) {
            Text("🔒 ещё ${max - unlocked}", style = MaterialTheme.typography.labelLarge, color = Juicy.TextFaint)
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(Drills.levelName(v, selected), style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
}
