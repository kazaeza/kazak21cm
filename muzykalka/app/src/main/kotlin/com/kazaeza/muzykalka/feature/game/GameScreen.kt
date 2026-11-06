package com.kazaeza.muzykalka.feature.game

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
import com.kazaeza.muzykalka.feature.common.ModeVisuals
import com.kazaeza.muzykalka.game.HuntTask
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.game.StepsTask
import com.kazaeza.muzykalka.game.TirTask
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
import com.kazaeza.muzykalka.ui.effect.AuroraBackground
import com.kazaeza.muzykalka.ui.effect.FlashOverlay
import com.kazaeza.muzykalka.ui.effect.ParticleLayer
import com.kazaeza.muzykalka.ui.effect.rememberParticleState
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.effect.shake
import com.kazaeza.muzykalka.ui.rememberHaptics
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlin.math.ceil

/**
 * Full-screen game. [onFinished] receives the summary when the user leaves the result screen
 * (sets and exams use it to move on); [onExit] is for closing.
 */
@Composable
fun GameScreen(
    spec: RoundSpec,
    onExit: () -> Unit,
    onFinished: ((RoundSummary) -> Unit)? = null,
    vmKey: String = "game-${spec.variant}-${spec.level}-${spec.sessionId}",
) {
    if (spec.variant.needsMic()) {
        MicPermissionGate(onCancel = onExit) { GameHost(spec, onExit, onFinished, vmKey) }
    } else {
        GameHost(spec, onExit, onFinished, vmKey)
    }
}

@Composable
private fun GameHost(spec: RoundSpec, onExit: () -> Unit, onFinished: ((RoundSummary) -> Unit)?, vmKey: String) {
    val graph = LocalAppGraph.current
    var round by remember { mutableIntStateOf(0) }
    val vm: GameViewModel = viewModel(key = "$vmKey-$round") { GameViewModel(graph, spec) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val accent = ModeVisuals.accent(spec.variant)
    val particles = rememberParticleState()
    val haptics = rememberHaptics(ui.settings.haptics)
    var shakeTrigger by remember { mutableIntStateOf(0) }
    var flashTrigger by remember { mutableIntStateOf(0) }
    var flashColor by remember { mutableStateOf(Juicy.Good) }
    var burstAt by remember { mutableStateOf(Offset(540f, 700f)) }

    KeepScreenOn()
    BackHandler {
        vm.quit()
        onExit()
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
            shakeTrigger++
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
            GameTopBar(ui, accent, onClose = {
                vm.quit()
                onExit()
            })
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .shake(shakeTrigger)
                    .onGloballyPositioned { c ->
                        val b = c.boundsInRoot()
                        burstAt = Offset(b.center.x, b.top + b.height * 0.28f)
                    },
            ) {
                when (val task = ui.task) {
                    is TirTask -> TirContent(task, ui, accent, onAnswer = vm::answer, onReplay = vm::replay)
                    is HuntTask -> HuntContent(task, ui, accent, onAnswer = vm::answer)
                    is StepsTask -> StepsContent(task, ui, accent, onAnswer = vm::answer)
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(ModeVisuals.emoji(spec.variant.mode), style = MaterialTheme.typography.displayMedium)
                    }
                }
            }
        }
        FlashOverlay(flashTrigger, flashColor)
        ParticleLayer(particles)
        ui.summary?.let { s ->
            SummaryOverlay(
                summary = s,
                accent = accent,
                inSequence = onFinished != null,
                onAgain = { round++ },
                onClose = { if (onFinished != null) onFinished(s) else onExit() },
            )
        }
    }
}

@Composable
private fun GameTopBar(ui: GameUi, accent: List<Color>, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Juicy.Ink3.copy(alpha = 0.7f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Text("✕", style = MaterialTheme.typography.titleMedium, color = Juicy.Text) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${ModeVisuals.emoji(ui.variant.mode)} ${ui.variant.ru}",
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
    content: @Composable () -> Unit,
) {
    GlassCard(modifier.fillMaxWidth(), accent = accent, contentPadding = 20.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .pop(ui.taskSerial * 10 + ui.phase.ordinal, 0.06f),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun SummaryOverlay(
    summary: RoundSummary,
    accent: List<Color>,
    inSequence: Boolean,
    onAgain: () -> Unit,
    onClose: () -> Unit,
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
                    summary.stars == 3 -> "Огонь! 🔥"
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
                GradientText("🏆 Новый рекорд!", MaterialTheme.typography.titleLarge, listOf(Juicy.Gold, Juicy.Orange))
            }
            summary.unlockedLevel?.let {
                GlassCard(accent = Accents.Good) {
                    Text("🔓 Открыт уровень $it", style = MaterialTheme.typography.titleLarge, color = Juicy.Good)
                    Text(Drills.levelName(summary.variant, it), style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigStat("${(summary.accuracy * 100).toInt()}%", "точность", colors = accent)
                    BigStat(summary.medianMs?.let { String.format(java.util.Locale.US, "%.1f с", it / 1000.0) } ?: "—", "медиана", colors = accent)
                    BigStat("${summary.bestStreak}", "серия", colors = accent)
                    BigStat("${summary.answered}", "ответов", colors = accent)
                }
            }
            JuicyButton(if (inSequence) "Дальше" else "Ещё раунд", onClick = if (inSequence) onClose else onAgain, Modifier.fillMaxWidth(), colors = accent, leading = if (inSequence) "➡️" else "🔁")
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

/** Shape used by result tiles. */
internal val Pill = RoundedCornerShape(50)
