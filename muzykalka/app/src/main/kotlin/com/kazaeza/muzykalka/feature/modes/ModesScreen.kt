package com.kazaeza.muzykalka.feature.modes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.data.settings.GameProgress
import com.kazaeza.muzykalka.feature.common.ModeVisuals
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.feature.common.isAvailable
import com.kazaeza.muzykalka.feature.game.Drills
import com.kazaeza.muzykalka.game.SingUnlock
import com.kazaeza.muzykalka.game.GuitarNeed
import com.kazaeza.muzykalka.game.TirDriver
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.ui.component.AppGlyph
import com.kazaeza.muzykalka.ui.component.AppIcon
import com.kazaeza.muzykalka.ui.component.Badge
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

    /** «Созвучия» — intervals and chords to hold, change and hear, no score (O-48, ADR-047). */
    data object Lab : ModeEntry

    /** «Позиция» — the chords of a key in one zone of the neck, no score (O-21, ADR-050). */
    data object Position : ModeEntry
}

/** A semantic group on «Режимы» («Обертон 2.0»: ear & phone first, physical guitar separated below). */
data class ModeSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val accent: List<Color>,
    val entries: List<ModeEntry>,
    val physicalGuitar: Boolean = false,
)

/**
 * O-12 / «Обертон 2.0»: every playable mode on one screen, organized into 7 clear semantic groups
 * with physical-guitar modes separated at the bottom.
 */
object ModesCatalog {
    private fun drill(v: Variant): ModeEntry = ModeEntry.Drill(v)

    val GROUPS: List<ModeSection> = listOf(
        ModeSection(
            id = "notes",
            title = "Ноты и ступени",
            subtitle = "слышишь звук — знаешь ступень",
            accent = Accents.Tir,
            entries = listOf(
                drill(Variant.TIR_CLASSIC),
                drill(Variant.TIR_DUEL),
                drill(Variant.TIR_OVER_CHORDS),
                drill(Variant.TIR_SIMON),
                drill(Variant.TIR_DYAD),
            ),
        ),
        ModeSection(
            id = "chords",
            title = "Аккорды и лады",
            subtitle = "функция, бас, переходы и цвет",
            accent = Accents.Taper,
            entries = listOf(
                drill(Variant.TIR_LAYERS),
                drill(Variant.TIR_ON_TONIC),
                drill(Variant.TIR_BASS),
                drill(Variant.TIR_TWO_CHORDS),
                drill(Variant.TIR_UNDER_NOTE),
                drill(Variant.TIR_COLOR),
                drill(Variant.TIR_MODE),
            ),
        ),
        ModeSection(
            id = "songs-ear",
            title = "Песни и перенос",
            subtitle = "гармония в голове без гитары",
            accent = Accents.Hero,
            entries = listOf(
                drill(Variant.TIR_LOOP3),
                drill(Variant.TIR_SONG),
                drill(Variant.TIR_NEXT),
                drill(Variant.FORMULA_ALOUD),
                drill(Variant.TIR_TRANSFER),
            ),
        ),
        ModeSection(
            id = "voice",
            title = "Голос и внутренний слух",
            subtitle = "имя → звук через голос",
            accent = Accents.Kamerton,
            entries = listOf(
                drill(Variant.KAMERTON_SEARCH),
                drill(Variant.KAMERTON_SING),
            ),
        ),
        ModeSection(
            id = "neck-quiet",
            title = "Гриф без гитары",
            subtitle = "ориентация на грифе тапами",
            accent = Accents.Hunt,
            entries = listOf(
                drill(Variant.HUNT_REVERSE),
                ModeEntry.Position,
            ),
        ),
        ModeSection(
            id = "free",
            title = "Свободная практика",
            subtitle = "без таймера, очков и спешки",
            accent = Accents.Calm,
            entries = listOf(
                ModeEntry.Feel,
                ModeEntry.Listen,
                ModeEntry.Lab,
            ),
        ),
        ModeSection(
            id = "guitar",
            title = "С физической гитарой",
            subtitle = "отдельный блок, когда инструмент в руках",
            accent = Accents.Hunt,
            entries = listOf(
                ModeEntry.Songs,
                ModeEntry.Blind,
                drill(Variant.TAPER_STEPS),
                drill(Variant.TIR_BASSLINE),
                drill(Variant.HUNT_FIND),
                drill(Variant.HUNT_ALL_OCTAVES),
            ),
            physicalGuitar = true,
        ),
    )

    fun available(section: ModeSection): List<ModeEntry> =
        section.entries.filter { e -> e !is ModeEntry.Drill || (e.variant.isAvailable() && e.variant in Drills.PLAYABLE) }

    fun title(e: ModeEntry): String = when (e) {
        is ModeEntry.Drill -> e.variant.ru
        ModeEntry.Songs -> "Песни"
        ModeEntry.Blind -> "Вслепую"
        ModeEntry.Feel -> "Вайб ступеней"
        ModeEntry.Listen -> "Угадай про себя"
        ModeEntry.Lab -> "Созвучия"
        ModeEntry.Position -> "Позиция"
    }

    fun about(e: ModeEntry): String = when (e) {
        is ModeEntry.Drill -> when (e.variant) {
            Variant.TIR_CLASSIC -> "Гудит тоника, звучит нота — жми её ступень. Не уверен — «не знаю», ответ всё равно услышишь."
            Variant.TIR_OVER_CHORDS -> "Та же нота, но под ней меняются аккорды лада."
            Variant.TIR_UNDER_NOTE -> "Нота на экране гудит, поверх неё играет аккорд — какой? Как подобрать аккорд под мелодию."
            Variant.TIR_ON_TONIC -> "Гудит тоника, над ней звучит аккорд лада — на какой он ступени? Начинаем с I, IV, V."
            Variant.TIR_DYAD -> "Два звука ударяют разом — какой верхний от нижнего? Нижний звук каждый раз новый, гула нет."
            Variant.TIR_BASS -> "Гудит тоника, из аккорда слышен только бас — какой это аккорд? После ответа он прозвучит целиком."
            Variant.TIR_TWO_CHORDS -> "Первый аккорд назван, за ним идёт второй — какой? Один гармонический переход вместо целой песни."
            Variant.TIR_DUEL -> "Две ступени, которые ты путаешь чаще всего, вперемешку до десяти верных подряд. Без жизней."
            Variant.TIR_COLOR -> "Звучит аккорд — какого он цвета: мажор, минор, sus или септ? После ответа раскладывается по нотам."
            Variant.TIR_BASSLINE -> "Звучит только бас петли — сыграй его на гитаре по ноте на аккорд, затем услышишь всю группу."
            Variant.TIR_TRANSFER -> "Контрольный раунд раз в неделю: ступень аккорда в петле то над синтезатором, то в живой фактуре с пианино, басом и ударными."
            Variant.TIR_LOOP3 -> "Петля из четырёх аккордов звучит в разных тональностях — назови нужную ступень. Ступень одна, тональность меняется."
            Variant.TIR_LAYERS -> "Над тоникой звучит аккорд: сначала его роль — дом, уход или напряжение, потом — из чего он сделан."
            Variant.TIR_MODE -> "Гудит тоника, по ней пробегает гамма — какой это лад? От мажора и минора к другим ладам."
            Variant.TIR_SIMON -> "Над гулом тоники звучит цепочка нот — повтори её на круге. Верно — цепочка растёт на ноту, ошибся — проигрывается медленно."
            Variant.TIR_SONG -> "Звучат первые аккорды песни из библиотеки в случайной тональности — чья это гармония?"
            Variant.TIR_NEXT -> "Песня названа, звучат три первых аккорда — куда она пойдёт дальше? Ответь до того, как услышишь."
            Variant.KAMERTON_SEARCH -> "Спокойно ищешь заданную ступень голосом, пока не попадёшь. Промахнулся — подскажем, куда тянуть."
            Variant.KAMERTON_SING -> "Видишь ступень — сразу поёшь её. Засчитывается первая спетая нота, на скорость."
            Variant.TAPER_STEPS -> "Тональность и ступени, например «ре мажор: I – V». Сыграй ход на гитаре и честно отметь результат."
            Variant.FORMULA_ALOUD -> "Названа песня и её часть — проговори ступени, открой ответ и послушай. Удобно в дороге."
            Variant.PARROT_ECHO -> "Звучит тоника, потом нота — найди её на гитаре в любой октаве, телефон слушает."
            Variant.HUNT_FIND -> "На экране нота — найди её на грифе и сыграй на гитаре, телефон слушает."
            Variant.HUNT_ALL_OCTAVES -> "Та же нота на грифе во всех октавах подряд."
            Variant.HUNT_REVERSE -> "Показана точка на грифе — назови ноту тапом, гитара не нужна."
            else -> e.variant.mode.goal
        }
        ModeEntry.Songs -> "Песни из твоей библиотеки по графику повторений, каждый раз в новой тональности."
        ModeEntry.Blind -> "Случайная песня без названия — только ступени. Кто это был, узнаешь в конце."
        ModeEntry.Feel -> "Послушать, как звучит каждая ступень над тоникой и возвращается домой. Без очков и таймера."
        ModeEntry.Position -> "Карта аккордов тональности в одной зоне грифа в пять ладов. Без очков и спешки."
        ModeEntry.Lab -> "Держишь звук, меняешь одну ноту — слышишь характер интервала, аккорда и тяготение."
        ModeEntry.Listen -> "Руки свободны: ступень над аккордом, пауза — назови про себя, затем ответ вслух."
    }

    fun accent(e: ModeEntry, fallback: List<Color>): List<Color> = when (e) {
        is ModeEntry.Drill -> ModeVisuals.accent(e.variant)
        else -> fallback
    }

    fun guitar(e: ModeEntry): GuitarNeed = when (e) {
        is ModeEntry.Drill -> e.variant.guitar
        ModeEntry.Songs, ModeEntry.Blind -> GuitarNeed.REQUIRED
        ModeEntry.Feel, ModeEntry.Listen, ModeEntry.Lab -> GuitarNeed.NONE
        ModeEntry.Position -> GuitarNeed.OPTIONAL
    }

    /** Short input badge for the card footer: тихо / голос / гриф / гитара. */
    fun inputBadge(e: ModeEntry): Pair<String, List<Color>> = when {
        e is ModeEntry.Drill && (e.variant == Variant.KAMERTON_SEARCH || e.variant == Variant.KAMERTON_SING) ->
            "голос" to Accents.Kamerton
        e is ModeEntry.Drill && e.variant == Variant.HUNT_REVERSE || e == ModeEntry.Position ->
            "гриф" to Accents.Hunt
        guitar(e) == GuitarNeed.REQUIRED ->
            "гитара" to Accents.Taper
        else ->
            "тихо" to Accents.Calm
    }

    /** All available cards of the screen, in order. */
    fun all(): List<ModeEntry> = GROUPS.flatMap(::available)
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
    onLoops: () -> Unit = {},
    onLab: () -> Unit = {},
    onPosition: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val progress by graph.progress.progress.collectAsState(initial = GameProgress())
    val scope = rememberCoroutineScope()
    val singing by remember { graph.practice.observeAttempts().map { SingUnlock.status(it) } }.collectAsState(initial = SingUnlock.Status(false, 0, 0))
    val tirHalves by remember { graph.practice.observeAttempts().map { TirDriver.halves(it) } }.collectAsState<TirDriver.Companion.Halves?, TirDriver.Companion.Halves?>(initial = null)
    // S-03: the pair the next duel opens with — the user's own worst confusion, or the first of the usual ones
    val duelPair by remember {
        graph.practice.observeAttempts().map { all ->
            Drills.duelPairs(all.filter { it.variant == Variant.TIR_CLASSIC }.takeLast(Drills.DUEL_LOOKBACK)).firstOrNull()?.let { it to true }
                ?: (TirDriver.DUEL_DEFAULTS.first() to false)
        }
    }.collectAsState(initial = null)
    val notation by remember { graph.settings.settings.map { it.degreeNotation } }.collectAsState(initial = DegreeNotation.ARABIC)
    ScreenFrame(title = "Режимы", subtitle = "всё в одном месте: выбирай и играй", accent = Accents.Recorder) {
        ModesCatalog.GROUPS.forEach { section ->
            val entries = ModesCatalog.available(section)
            if (entries.isEmpty()) return@forEach
            item(key = "head-${section.id}") { SectionHead(section) }
            entries.forEach { e ->
                item(key = "entry-" + ModesCatalog.title(e)) {
                    EntryCard(
                        entry = e,
                        accent = ModesCatalog.accent(e, section.accent),
                        progress = progress,
                        singing = singing,
                        tirHalves = tirHalves,
                        duelLine = duelPair?.takeIf { e == ModeEntry.Drill(Variant.TIR_DUEL) }?.let { (pair, own) ->
                            val names = listOf(pair.first, pair.second).sortedBy { it.semitones }.joinToString(" против ") { DegreeFormatter.degree(it, notation) }
                            if (own) "твоя пара сейчас: $names" else "своей путаницы пока нет — начнём с классики: $names"
                        },
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
                                ModeEntry.Lab -> onLab()
                                ModeEntry.Position -> onPosition()
                            }
                        },
                        onLibrary = onLibrary,
                        onLoops = onLoops,
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
private fun SectionHead(section: ModeSection) {
    Column(Modifier.fillMaxWidth().padding(top = if (section.physicalGuitar) 14.dp else 8.dp)) {
        if (section.physicalGuitar) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Juicy.LineStrong),
            )
            Spacer(Modifier.height(14.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (section.physicalGuitar) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Juicy.Ink3)
                        .border(BorderStroke(1.dp, Juicy.LineStrong), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AppGlyph(AppIcon.GUITAR, section.accent.first(), size = 20.dp)
                }
            } else {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.verticalGradient(section.accent)),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.headlineSmall, color = Juicy.Text)
                Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(
    entry: ModeEntry,
    accent: List<Color>,
    progress: GameProgress,
    singing: SingUnlock.Status,
    tirHalves: TirDriver.Companion.Halves?,
    duelLine: String?,
    onSelectLevel: (Variant, Int, Boolean) -> Unit,
    onStart: () -> Unit,
    onLibrary: () -> Unit,
    onLoops: () -> Unit,
) {
    val title = ModesCatalog.title(entry)
    val closed = entry is ModeEntry.Drill && entry.variant == Variant.KAMERTON_SING && !singing.open
    GlassCard(Modifier.fillMaxWidth(), accent = accent, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Juicy.Text)
                Spacer(Modifier.height(4.dp))
                Text(ModesCatalog.about(entry), style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
            }
            Spacer(Modifier.width(12.dp))
            PlayDisc(accent, label = "Играть: $title", enabled = !closed, onClick = onStart)
        }
        if (closed) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Откроется, когда в «Пропевке» с первой попытки выходит половина нот (последние ${SingUnlock.WINDOW}, не меньше ${SingUnlock.MIN_TRIES}). Сейчас: ${singing.correct} из ${singing.tries}.",
                style = MaterialTheme.typography.bodyMedium, color = Juicy.Gold,
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val (badgeText, badgeColors) = ModesCatalog.inputBadge(entry)
            Badge(badgeText, badgeColors)
            val v = (entry as? ModeEntry.Drill)?.variant
            val record = v?.let { progress.record(it) } ?: 0
            if (v != null && record > 0 && !Drills.guitarPaced(v)) {
                Text("рекорд $record", style = MaterialTheme.typography.labelMedium, color = Juicy.Gold)
            }
        }
        if (duelLine != null) {
            Spacer(Modifier.height(6.dp))
            Text(duelLine, style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
        }
        if (entry is ModeEntry.Drill) Levels(entry.variant, accent, progress, onSelectLevel, tirHalves.takeIf { entry.variant == Variant.TIR_CLASSIC })
        if (entry == ModeEntry.Drill(Variant.TIR_SONG)) {
            // O-47: the album of what the quiz teaches — every loop of the library and the songs that open with it
            Spacer(Modifier.height(10.dp))
            GhostButton("Коллекция ходов", onClick = onLoops, modifier = Modifier.fillMaxWidth(), colors = accent, leadingIcon = AppIcon.CHEVRON_RIGHT)
        }
        if (entry == ModeEntry.Songs) {
            Spacer(Modifier.height(10.dp))
            GhostButton("Взять песню из сборника", onClick = onLibrary, modifier = Modifier.fillMaxWidth(), colors = accent, leadingIcon = AppIcon.CHEVRON_RIGHT)
        }
    }
}

/** Sleek studio round «play» button on the right of a card. */
@Composable
private fun PlayDisc(accent: List<Color>, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val color = accent.first()
    Box(
        Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(CircleShape)
            .background(Juicy.Ink3)
            .border(BorderStroke(1.5.dp, color.copy(alpha = 0.75f)), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        AppGlyph(AppIcon.PLAY, color, size = 20.dp)
    }
}

/** Chips for the open levels, one line for the locked rest, and the name of the chosen level. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Levels(
    v: Variant,
    accent: List<Color>,
    progress: GameProgress,
    onSelect: (Variant, Int, Boolean) -> Unit,
    tirHalves: TirDriver.Companion.Halves? = null,
) {
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
            Text("ещё ${max - unlocked}", style = MaterialTheme.typography.labelLarge, color = Juicy.TextFaint)
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(Drills.levelName(v, selected), style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
    // O-27: level 2 of the Tir is two halves — major first, then minor; both open level 3
    if (tirHalves != null && selected == 2) {
        fun half(name: String, done: Boolean, right: Int) = if (done) "$name ✓" else "$name $right из ${TirDriver.HALF_WINDOW}"
        Text(
            half("мажор", tirHalves.major, tirHalves.majorRight) + " · " +
                if (tirHalves.major) half("минор", tirHalves.minor, tirHalves.minorRight) else "минор — после мажора",
            style = MaterialTheme.typography.bodySmall,
            color = Juicy.TextDim,
        )
    }
}
