package com.kazaeza.muzykalka.feature.taper

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.feature.common.KeepScreenOn
import com.kazaeza.muzykalka.feature.common.PauseOverlay
import com.kazaeza.muzykalka.feature.common.plural
import com.kazaeza.muzykalka.game.BlindPhase
import com.kazaeza.muzykalka.game.SelfGrade
import com.kazaeza.muzykalka.ui.component.CountUpText
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.component.StatPill
import com.kazaeza.muzykalka.ui.effect.AuroraBackground
import com.kazaeza.muzykalka.ui.effect.ParticleLayer
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.effect.rememberParticleState
import com.kazaeza.muzykalka.ui.rememberHaptics
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy

private val BAR = listOf(Juicy.Gold, Juicy.Orange, Juicy.Hot)

/** What the ticket shows instead of the name while the song is being played. */
const val BLIND_TITLE = "???"

/**
 * «Вслепую» (ticket O-10): a random song from the likes, in a random key, degrees on the sheet and
 * no name — it is revealed after the self-grade. ✕/back ask first (ADR-019).
 */
@Composable
fun BlindScreen(onExit: () -> Unit, vmKey: String = "blind") {
    val graph = LocalAppGraph.current
    val vm: BlindViewModel = viewModel(key = vmKey) { BlindViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val haptics = rememberHaptics(ui.settings.haptics)
    val particles = rememberParticleState()
    KeepScreenOn()
    var asking by remember { mutableStateOf(false) }
    BackHandler(enabled = !asking) { asking = true }

    LaunchedEffect(ui.result) {
        val r = ui.result ?: return@LaunchedEffect
        when {
            r.playableNow -> {
                haptics.celebrate()
                particles.burst(Offset(540f, 500f), BAR + listOf(Juicy.Lime, Color.White), count = 70, power = 1.2f, confetti = true)
            }
            r.self == SelfGrade.FAILED -> haptics.error()
            else -> haptics.success()
        }
    }

    AuroraBackground(BAR, Modifier.fillMaxSize(), intensity = 0.75f) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Juicy.Ink3.copy(alpha = 0.7f))
                        .clickable(onClickLabel = "Пауза") { asking = true },
                    contentAlignment = Alignment.Center,
                ) { Text("⏸", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("🎭 Вслепую", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (ui.ordersDone > 0) "песен: ${ui.ordersDone} · с ходу: ${ui.playableCount}" else "песня из твоих лайков, без имени",
                        style = MaterialTheme.typography.bodySmall,
                        color = Juicy.TextDim,
                    )
                }
                StatPill("⭐", "${ui.totalTips}", colors = BAR, modifier = Modifier.pop(ui.totalTips, 0.25f))
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when {
                    ui.loading -> Spacer(Modifier.height(1.dp))
                    ui.phase == BlindPhase.FINISHED -> ClosingTime(ui, onDone = onExit)
                    else -> BlindFlow(ui, vm)
                }
            }
        }
        ParticleLayer(particles)
        if (asking) {
            PauseOverlay(
                onResume = { asking = false },
                onExit = onExit,
                exitLabel = "Закончить",
                hint = "Сыгранные песни уже сохранены. Песня, которую ты сейчас играешь, останется без имени.",
            )
        }
    }
}

@Composable
private fun BlindFlow(ui: BlindUi, vm: BlindViewModel) {
    val entry = ui.entry ?: return
    val notation = ui.settings.degreeNotation
    when (ui.phase) {
        BlindPhase.PLAYING -> {
            OrderTicket(BLIND_TITLE, "песня из твоих лайков", ui.keyText, fresh = true, caption = "✦ ВСЛЕПУЮ ✦")
            GlassCard(Modifier.fillMaxWidth(), accent = BAR) {
                SectionLabel("лист · ступени")
                Spacer(Modifier.height(6.dp))
                Text("Форма: ${entry.song.formString} · ${entry.song.barCount} тактов", style = MaterialTheme.typography.titleMedium)
                SongSheet(entry.song, notation)
            }
            JuicyButton("Сыграл", onClick = vm::played, modifier = Modifier.fillMaxWidth(), colors = BAR, leading = "✅")
            Text(
                "Названия не будет: только ступени и тональность. Сыграй по листу, как они звучат — кто это, узнаешь в конце.",
                style = MaterialTheme.typography.bodySmall,
                color = Juicy.TextDim,
            )
        }
        BlindPhase.SELF_GRADE -> {
            OrderTicket(BLIND_TITLE, "песня из твоих лайков", ui.keyText, fresh = false, caption = "✦ ВСЛЕПУЮ ✦")
            SectionLabel("Как прошло?")
            JuicyButton("С ходу", onClick = { vm.grade(SelfGrade.CLEAN) }, modifier = Modifier.fillMaxWidth(), colors = Accents.Good, leading = "😎")
            JuicyButton("С запинками", onClick = { vm.grade(SelfGrade.STUMBLED) }, modifier = Modifier.fillMaxWidth(), colors = BAR, leading = "😅")
            JuicyButton("Не получилось", onClick = { vm.grade(SelfGrade.FAILED) }, modifier = Modifier.fillMaxWidth(), colors = Accents.Bad, leading = "🙈")
        }
        BlindPhase.RESULT -> ResultCard(ui, vm)
        BlindPhase.FINISHED -> Unit
    }
}

@Composable
private fun ResultCard(ui: BlindUi, vm: BlindViewModel) {
    val entry = ui.entry ?: return
    val r = ui.result ?: return
    OrderTicket(entry.song.title, entry.song.artist, ui.keyText, fresh = false, caption = "✦ ЭТО БЫЛА ✦")
    GlassCard(Modifier.fillMaxWidth(), accent = if (r.playableNow) Accents.Good else BAR) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    r.playableNow -> "С ходу! 🔥"
                    r.self == SelfGrade.FAILED -> "Не вышло"
                    else -> "Почти"
                },
                style = MaterialTheme.typography.displaySmall,
                color = if (r.playableNow) Juicy.Good else if (r.self == SelfGrade.FAILED) Juicy.Hot else Juicy.Gold,
            )
            Spacer(Modifier.height(6.dp))
            CountUpText(r.tips, MaterialTheme.typography.displayMedium, colors = BAR, prefix = "+", suffix = " ⭐")
            Spacer(Modifier.height(6.dp))
            Text(entry.teaches, style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
        }
    }
    GhostButton(
        if (ui.listening) "Стоп" else "Послушать, как это звучит",
        onClick = vm::listen,
        modifier = Modifier.fillMaxWidth(),
        colors = BAR,
        leading = if (ui.listening) "⏹" else "🔊",
    )
    JuicyButton(if (ui.queueLeft > 0) "Следующая песня" else "Закончить", onClick = vm::nextOrder, modifier = Modifier.fillMaxWidth(), colors = BAR, leading = "🎭")
}

@Composable
private fun ClosingTime(ui: BlindUi, onDone: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent = BAR) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("На сегодня всё", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            CountUpText(ui.totalTips, MaterialTheme.typography.displayMedium, colors = BAR, suffix = " ⭐")
            Text(
                "${ui.ordersDone} ${plural(ui.ordersDone, "песня", "песни", "песен")} · с ходу: ${ui.playableCount}",
                style = MaterialTheme.typography.titleMedium,
                color = Juicy.TextDim,
            )
        }
    }
    JuicyButton("Готово", onClick = onDone, modifier = Modifier.fillMaxWidth(), colors = BAR)
}
