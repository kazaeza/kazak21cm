package com.kazaeza.muzykalka.feature.game

import com.kazaeza.muzykalka.ui.theme.Motion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.feature.common.KeepScreenOn
import com.kazaeza.muzykalka.feature.common.MicPermissionGate
import com.kazaeza.muzykalka.feature.common.ModeOrb
import com.kazaeza.muzykalka.feature.common.ModeVisuals
import com.kazaeza.muzykalka.feature.common.PauseOverlay
import com.kazaeza.muzykalka.game.HuntTask
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.game.StepsTask
import com.kazaeza.muzykalka.game.SingTask
import com.kazaeza.muzykalka.game.TirTask
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.ui.component.ComboBadge
import com.kazaeza.muzykalka.ui.component.CountUpText
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.GradientText
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.LivesRow
import com.kazaeza.muzykalka.ui.component.StarRow
import com.kazaeza.muzykalka.ui.component.BigStat
import com.kazaeza.muzykalka.ui.component.TimerRing
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.GameTouchTarget
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.music.DegreeAnchor
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.ui.effect.AuroraBackground
import com.kazaeza.muzykalka.ui.effect.FlashOverlay
import com.kazaeza.muzykalka.ui.effect.ParticleLayer
import com.kazaeza.muzykalka.ui.effect.rememberParticleState
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.effect.rememberPulses
import com.kazaeza.muzykalka.ui.rememberHaptics
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlin.math.ceil

/**
 * Full-screen game. [onFinished] receives the summary when the user leaves the result screen
 * (sets and exams use it to move on); [onExit] is for closing. ⏸ and the back gesture pause the
 * round and ask (ADR-019); [onNext] adds «Следующее упражнение» to that question (sets only) and
 * [onLeave] replaces [onExit] there — both get how many answers this round already has.
 */
@Composable
fun GameScreen(
    spec: RoundSpec,
    onExit: () -> Unit,
    onFinished: ((RoundSummary) -> Unit)? = null,
    vmKey: String = "game-${spec.variant}-${spec.level}-${spec.sessionId}",
    onNext: ((answered: Int) -> Unit)? = null,
    onLeave: ((answered: Int) -> Unit)? = null,
    exitLabel: String = "Закончить раунд",
) {
    val leave = onLeave ?: { _: Int -> onExit() }
    if (spec.variant.needsMic()) {
        MicPermissionGate(onCancel = { onNext?.invoke(0) ?: onExit() }) { GameHost(spec, onFinished, vmKey, onNext, leave, exitLabel, onExit) }
    } else {
        GameHost(spec, onFinished, vmKey, onNext, leave, exitLabel, onExit)
    }
}

@Composable
private fun GameHost(
    spec: RoundSpec,
    onFinished: ((RoundSummary) -> Unit)?,
    vmKey: String,
    onNext: ((Int) -> Unit)?,
    onLeave: (Int) -> Unit,
    exitLabel: String,
    onExit: () -> Unit,
) {
    val graph = LocalAppGraph.current
    var round by remember { mutableIntStateOf(0) }
    // O-27: a newly opened level taken on the summary — «Ещё раунд» plays it even when the level came with the spec
    var takenLevel by remember { mutableStateOf<Int?>(null) }
    val vm: GameViewModel = viewModel(key = "$vmKey-$round") { GameViewModel(graph, takenLevel?.let { spec.copy(level = it) } ?: spec) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val accent = ModeVisuals.accent(spec.variant)
    val particles = rememberParticleState()
    val haptics = rememberHaptics(ui.settings.haptics)
    val pulses = rememberPulses(vm.pulses)
    var flashTrigger by remember { mutableIntStateOf(0) }
    var flashColor by remember { mutableStateOf(Juicy.Good) }
    var burstAt by remember { mutableStateOf(Offset(540f, 700f)) }

    KeepScreenOn()
    BackHandler {
        val s = ui.summary
        when {
            s != null -> if (onFinished != null) onFinished(s) else onExit()
            else -> vm.pause()
        }
    }

    LaunchedEffect(ui.feedback?.serial) {
        val f = ui.feedback ?: return@LaunchedEffect
        if (f.correct) {
            haptics.success()
            flashColor = Juicy.Good
            particles.burst(burstAt, accent + listOf(Juicy.Gold, Color.White), count = 18 + 6 * ui.multiplier, power = 0.9f + 0.15f * ui.multiplier)
        } else {
            haptics.error()
            flashColor = Juicy.Bad
        }
        flashTrigger++
    }
    LaunchedEffect(ui.summary) {
        val s = ui.summary ?: return@LaunchedEffect
        if (s.newRecord || s.unlockedLevel != null || s.stars == 3) {
            haptics.celebrate()
            particles.burst(Offset(burstAt.x, burstAt.y * 0.6f), Accents.Hero + listOf(Juicy.Gold, Juicy.Cyan, Juicy.Lime), count = 90, power = 1.4f, confetti = true)
        }
    }

    AuroraBackground(accent, Modifier.fillMaxSize(), intensity = 0.7f) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            GameTopBar(ui, accent, onPause = vm::pause)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { c ->
                        val b = c.boundsInRoot()
                        burstAt = Offset(b.center.x, b.top + b.height * 0.28f)
                    },
            ) {
                when (val task = ui.task) {
                    is TirTask -> TirContent(task, ui, accent, onAnswer = vm::answer, onReplay = vm::replay, pulses = pulses, haptics = haptics)
                    is SingTask -> if (ui.variant == Variant.KAMERTON_SEARCH) {
                        SearchContent(task, ui, accent, onAnswer = vm::answer, onReplay = vm::replay)
                    } else {
                        SingContent(task, ui, accent, onAnswer = vm::answer, onReplay = vm::replay)
                    }
                    is HuntTask -> HuntContent(task, ui, accent, onAnswer = vm::answer)
                    is StepsTask -> StepsContent(task, ui, accent, onAnswer = vm::answer)
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ModeOrb(spec.variant.mode, size = 96.dp)
                    }
                }
            }
        }
        FlashOverlay(flashTrigger, flashColor)
        ParticleLayer(particles)
        if (ui.paused && ui.summary == null) {
            PauseOverlay(
                onResume = vm::resume,
                onExit = {
                    val answered = ui.answered
                    vm.quit()
                    onLeave(answered)
                },
                exitLabel = exitLabel,
                onNext = onNext?.let { next ->
                    {
                        val answered = ui.answered
                        vm.quit()
                        next(answered)
                    }
                },
                hint = if (ui.exam) "Замер стоит — время ответа не идёт." else "Таймер стоит. Вопрос прозвучит ещё раз.",
            )
        }
        ui.summary?.let { s ->
            SummaryOverlay(
                summary = s,
                accent = accent,
                inSequence = onFinished != null,
                onAgain = {
                    vm.stopAnchor()
                    round++
                },
                onClose = {
                    vm.stopAnchor()
                    if (onFinished != null) onFinished(s) else onExit()
                },
                anchors = ui.summaryAnchors,
                playing = ui.playingAnchor,
                notation = ui.notation,
                onAnchor = vm::playAnchor,
                onTakeLevel = { level ->
                    vm.takeLevel(level)
                    takenLevel = level
                },
            )
        }
    }
}

@Composable
private fun GameTopBar(ui: GameUi, accent: List<Color>, onPause: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Juicy.Ink3.copy(alpha = 0.7f))
                .clickable(onClickLabel = "Пауза", onClick = onPause),
            contentAlignment = Alignment.Center,
        ) { Text("❙❙", style = MaterialTheme.typography.titleMedium, color = Juicy.Text) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ui.variant.ru,
                style = MaterialTheme.typography.titleMedium,
                color = Juicy.Text,
            )
            Text(
                if (ui.exam) "экзамен · ${ui.answered}/${ui.taskLimit}" else "ур. ${ui.level} · ${Drills.levelName(ui.variant, ui.level)}",
                style = MaterialTheme.typography.bodySmall,
                color = Juicy.TextDim,
                maxLines = 1,
            )
        }
        ComboBadge(ui.streak, ui.multiplier)
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            CountUpText(ui.score, MaterialTheme.typography.titleLarge, colors = accent)
            LivesRow(ui.lives, ui.maxLives)
        }
        Spacer(Modifier.width(10.dp))
        if (!ui.exam) {
            val secs = ceil(ui.remainingMs / 1000.0).toInt()
            TimerRing(
                fraction = if (ui.totalMs > 0) ui.remainingMs.toFloat() / ui.totalMs else 1f,
                label = "$secs",
                colors = accent,
                diameter = 56.dp,
            )
        }
    }
}

/** Big prompt/result card shared by the modes. */
@Composable
internal fun PromptCard(
    ui: GameUi,
    accent: List<Color>,
    modifier: Modifier = Modifier,
    /** O-25: fill the height it is given and centre the content in it, so nothing below moves. */
    fill: Boolean = false,
    content: @Composable () -> Unit,
) {
    // O-25: a new question or the answer fades in; nothing pops or jumps
    val shown = remember { Animatable(1f) }
    LaunchedEffect(ui.taskSerial, ui.phase) {
        shown.snapTo(0f)
        shown.animateTo(1f, tween(Motion.MEDIUM))
    }
    GlassCard(modifier.fillMaxWidth(), accent = accent, contentPadding = 20.dp) {
        Box(
            (if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .graphicsLayer { alpha = shown.value },
            contentAlignment = Alignment.Center,
        ) {
            if (fill) {
                Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { content() }
            } else {
                content()
            }
        }
    }
}

@Composable
private fun SummaryOverlay(
    summary: RoundSummary,
    accent: List<Color>,
    inSequence: Boolean,
    onAgain: () -> Unit,
    onTakeLevel: (Int) -> Unit = {},
    onClose: () -> Unit,
    anchors: List<DegreeAnchor> = emptyList(),
    playing: DegreeAnchor? = null,
    notation: DegreeNotation = DegreeNotation.ARABIC,
    onAnchor: (DegreeAnchor) -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Juicy.Ink.copy(alpha = 0.82f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                when {
                    summary.answered == 0 -> "Раунд прерван"
                    summary.stars == 3 -> "Огонь!"
                    summary.stars == 2 -> "Отлично!"
                    summary.stars == 1 -> "Неплохо"
                    else -> "Разминка засчитана"
                },
                style = MaterialTheme.typography.headlineLarge,
                color = Juicy.Text,
            )
            StarRow(summary.stars)
            CountUpText(summary.score, MaterialTheme.typography.displayMedium, colors = accent)
            if (summary.newRecord) {
                GradientText("Новый рекорд!", MaterialTheme.typography.titleLarge, listOf(Juicy.Gold, Juicy.Orange))
            }
            summary.unlockedLevel?.let {
                GlassCard(Modifier.fillMaxWidth(), accent = Accents.Good) {
                    Text("Открыт уровень $it", style = MaterialTheme.typography.titleLarge, color = Juicy.Good)
                    Text(Drills.levelName(summary.variant, it), style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                    Spacer(Modifier.height(10.dp))
                    // O-27: an offer, not a switch — the level changes only when asked to
                    if (summary.levelTaken) {
                        Text("Дальше играем на уровне $it.", style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
                    } else {
                        GhostButton("Перейти на уровень $it", onClick = { onTakeLevel(it) }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Пока остаёшься на уровне ${summary.level}. Перейти можно и потом — в «Режимах».",
                            style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint,
                        )
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (summary.variant == Variant.KAMERTON_SEARCH) {
                        // ADR-028: a search is told by how soon the note was found, not by misses.
                        BigStat("${summary.foundOn.size}", "нашёл", colors = accent)
                        BigStat("${summary.foundOn.count { it == 1 }}", "с первой", colors = accent)
                        BigStat(summary.averageTry?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—", "попыток в ср.", colors = accent)
                    } else {
                        BigStat("${(summary.accuracy * 100).toInt()}%", "точность", colors = accent)
                        BigStat(summary.medianMs?.let { String.format(java.util.Locale.US, "%.1f с", it / 1000.0) } ?: "—", "медиана", colors = accent)
                        BigStat("${summary.bestStreak}", "серия", colors = accent)
                        BigStat("${summary.answered}", "ответов", colors = accent)
                    }
                }
            }
            if (anchors.isNotEmpty()) AnchorsCard(anchors.take(MAX_SUMMARY_ANCHORS), playing, notation, onAnchor)
            JuicyButton(if (inSequence) "Дальше" else "Ещё раунд", onClick = if (inSequence) onClose else onAgain, Modifier.fillMaxWidth(), colors = accent, leading = if (inSequence) "→" else "↻")
            if (!inSequence) GhostButton("Готово", onClick = onClose, Modifier.fillMaxWidth())
            Text(
                "Каждый ответ сохранён в статистику",
                style = MaterialTheme.typography.bodySmall,
                color = Juicy.TextFaint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val MAX_SUMMARY_ANCHORS = 3

/** O-09: the degrees this round missed, each with the user's own song that leans on it. */
@Composable
private fun AnchorsCard(anchors: List<DegreeAnchor>, playing: DegreeAnchor?, notation: DegreeNotation, onAnchor: (DegreeAnchor) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        SectionLabel("якоря к ошибкам")
        anchors.forEach { anchor ->
            val color = DegreeColors.of(anchor.degree)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(GameTouchTarget)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = if (anchor == playing) "Стоп" else "Послушать") { onAnchor(anchor) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DegreeChip(DegreeFormatter.degree(anchor.degree, notation), color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(anchor.title, style = MaterialTheme.typography.titleMedium, color = Juicy.Text, maxLines = 1)
                    Text(anchor.melodyText, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim, maxLines = 1)
                }
                Text(if (anchor == playing) "■" else "►", style = MaterialTheme.typography.headlineSmall, color = color)
            }
        }
    }
}

/** Shape used by result tiles. */
internal val Pill = RoundedCornerShape(50)
