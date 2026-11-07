package com.kazaeza.muzykalka.feature.taper

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.feature.common.ChordAudition
import com.kazaeza.muzykalka.feature.common.ChordCaption
import com.kazaeza.muzykalka.feature.common.KeepScreenOn
import com.kazaeza.muzykalka.feature.common.PauseOverlay
import com.kazaeza.muzykalka.feature.common.plural
import com.kazaeza.muzykalka.feature.common.rememberChordAudition
import com.kazaeza.muzykalka.feature.common.ruDue
import com.kazaeza.muzykalka.game.HintStep
import com.kazaeza.muzykalka.game.SelfGrade
import com.kazaeza.muzykalka.game.TaperGrading
import com.kazaeza.muzykalka.game.TaperPhase
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.srs.Rating
import com.kazaeza.muzykalka.ui.component.CountUpText
import com.kazaeza.muzykalka.ui.component.DegreeTile
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.GradientText
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.component.StatPill
import com.kazaeza.muzykalka.ui.effect.AuroraBackground
import com.kazaeza.muzykalka.ui.effect.ParticleLayer
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.effect.rememberParticleState
import com.kazaeza.muzykalka.ui.rememberHaptics
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy

private val BAR = listOf(Juicy.Gold, Juicy.Orange, Juicy.Hot)

/**
 * «Тапёр» · songs (ticket G-03): library pieces, due by the SRS schedule, each in a random key.
 * ✕ and the back gesture ask first (ADR-019); [onNext] adds «Следующее упражнение» (sets only).
 */
@Composable
fun TaperScreen(
    spec: TaperSpec,
    onExit: () -> Unit,
    onFinished: (() -> Unit)? = null,
    onOpenLibrary: () -> Unit = onExit,
    vmKey: String = "taper-${spec.sessionId}-${spec.onlyItemId}-${spec.exam}",
    onNext: (() -> Unit)? = null,
    exitLabel: String = "Выйти",
) {
    val graph = LocalAppGraph.current
    val vm: TaperViewModel = viewModel(key = vmKey) { TaperViewModel(graph, spec) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val haptics = rememberHaptics(ui.settings.haptics)
    val particles = rememberParticleState()
    val audition = rememberChordAudition(ui.settings.a4Hz)
    KeepScreenOn()
    var asking by remember { mutableStateOf(false) }
    BackHandler(enabled = !asking) { asking = true }
    LaunchedEffect(ui.item?.id, ui.key, ui.phase) { audition.clear() }

    LaunchedEffect(ui.result) {
        val r = ui.result ?: return@LaunchedEffect
        if (r.playableNow) {
            haptics.celebrate()
            particles.burst(Offset(540f, 500f), BAR + listOf(Juicy.Lime, Color.White), count = 70, power = 1.2f, confetti = true)
        } else if (r.rating == Rating.AGAIN) {
            haptics.error()
        } else {
            haptics.success()
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
                    Text(if (spec.exam) "🎸 Память · замер" else "🎸 Песни", style = MaterialTheme.typography.titleLarge)
                    Text(if (ui.queueLeft > 0) "ещё ${ui.queueLeft} ${plural(ui.queueLeft, "песня", "песни", "песен")}" else "последняя на сегодня", style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
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
                    ui.emptyLibrary -> EmptyBar(onOpenLibrary)
                    ui.nothingDue -> NothingDue(onExtra = vm::orderExtra, onDone = { onFinished?.invoke() ?: onExit() })
                    ui.phase == TaperPhase.FINISHED -> ClosingTime(ui, onDone = { onFinished?.invoke() ?: onExit() })
                    else -> OrderFlow(ui, vm, audition)
                }
            }
            ChordCaption(audition, ui.settings.degreeNotation, Modifier.padding(vertical = 8.dp))
        }
        ParticleLayer(particles)
        if (asking) {
            PauseOverlay(
                onResume = { asking = false },
                onExit = onExit,
                exitLabel = exitLabel,
                onNext = onNext,
                hint = "Сыгранные песни уже сохранены.",
            )
        }
    }
}

@Composable
private fun OrderFlow(ui: TaperUi, vm: TaperViewModel, audition: ChordAudition) {
    val item = ui.item ?: return
    OrderTicket(item.title, item.song?.artist, ui.keyText, ui.phase == TaperPhase.PLAYING)
    when (ui.phase) {
        TaperPhase.PLAYING -> {
            if (ui.hint != HintStep.NONE) HintCard(ui, audition)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val next = ui.hint.next()
                if (ui.hintsAllowed && next != null) {
                    GhostButton(
                        "Подсказка: ${next.ru} (−${next.tipCost - ui.hint.tipCost} ⭐)",
                        onClick = vm::hint,
                        modifier = Modifier.fillMaxWidth(),
                        colors = BAR,
                        leading = "💡",
                    )
                }
            }
            JuicyButton("Сыграл", onClick = vm::played, modifier = Modifier.fillMaxWidth(), colors = BAR, leading = "✅")
            Text(
                if (ui.hint == HintStep.TOP) {
                    "Пока песня новая, ступени открыты сразу. Сыграл чисто — в следующий раз подсказка будет на ступеньку меньше, и так до «по памяти»."
                } else {
                    "Сыграй в этой тональности. Застрял — бери подсказку: они открываются по одной ступеньке и срезают звёзды."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Juicy.TextDim,
            )
        }
        TaperPhase.SELF_GRADE -> {
            SectionLabel("Как прошло?")
            JuicyButton("С ходу", onClick = { vm.grade(SelfGrade.CLEAN) }, modifier = Modifier.fillMaxWidth(), colors = Accents.Good, leading = "😎")
            JuicyButton("С запинками", onClick = { vm.grade(SelfGrade.STUMBLED) }, modifier = Modifier.fillMaxWidth(), colors = BAR, leading = "😅")
            JuicyButton("Не вспомнил", onClick = { vm.grade(SelfGrade.FAILED) }, modifier = Modifier.fillMaxWidth(), colors = Accents.Bad, leading = "🙈")
        }
        TaperPhase.RESULT -> ResultCard(ui, vm)
        TaperPhase.FINISHED -> Unit
    }
}

@Composable
internal fun OrderTicket(title: String, artist: String?, keyText: String, fresh: Boolean, caption: String = "✦ СЫГРАЙ ✦") {
    val tilt by animateFloatAsState(if (fresh) -2.5f else 0f, tween(400), label = "tilt")
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .rotate(tilt)
            .shadow(16.dp, RoundedCornerShape(10.dp), ambientColor = Juicy.Orange, spotColor = Juicy.Gold)
            .clip(RoundedCornerShape(10.dp))
            .background(Juicy.Paper)
            .padding(20.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = Juicy.PaperInk.copy(alpha = 0.6f))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Juicy.PaperInk, textAlign = TextAlign.Center)
            if (!artist.isNullOrBlank()) Text(artist, style = MaterialTheme.typography.bodyMedium, color = Juicy.PaperInk.copy(alpha = 0.7f))
            Spacer(Modifier.height(10.dp))
            GradientText(keyText, MaterialTheme.typography.headlineMedium, listOf(Juicy.Hot, Juicy.Orange), textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HintCard(ui: TaperUi, audition: ChordAudition) {
    val item = ui.item ?: return
    val notation = ui.settings.degreeNotation
    GlassCard(Modifier.fillMaxWidth(), accent = BAR) {
        SectionLabel("подсказка · ${ui.hint.ru}")
        Spacer(Modifier.height(8.dp))
        val song = item.song
        val melody = item.melody
        if (song != null) {
            Text("Форма: ${song.formString} · ${song.barCount} тактов", style = MaterialTheme.typography.titleMedium)
            if (ui.hint == HintStep.FIRST_CHORD) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Начало: ", style = MaterialTheme.typography.bodyLarge, color = Juicy.TextDim)
                    DegreeTile(
                        DegreeFormatter.chord(song.firstChord, notation),
                        DegreeColors.of(song.firstChord.root),
                        onClick = ui.key?.let { k -> { audition.tap("first", song.firstChord, k) } },
                        active = audition.isHeard("first"),
                        onClickLabel = "Послушать аккорд",
                    )
                }
            }
            if (ui.hint >= HintStep.DEGREES) SongSheet(song, notation, audition, ui.key)
        } else if (melody != null) {
            Text("${melody.notes.size} нот", style = MaterialTheme.typography.titleMedium)
            if (ui.hint >= HintStep.FIRST_CHORD) {
                Text("Начало: " + DegreeFormatter.degree(melody.notes.first().degree, notation), style = MaterialTheme.typography.titleMedium)
            }
            if (ui.hint >= HintStep.DEGREES) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    melody.notes.forEach { n -> DegreeTile(DegreeFormatter.degree(n.degree, notation), DegreeColors.of(n.degree)) }
                }
            }
        }
    }
}

/**
 * The song as bars of big degree tiles (O-13): solid colours of one lightness, dark ink, so each
 * chord is read at a glance. Degrees only — chords in letters are never shown (rule 1, ADR-027).
 * O-17: with an [audition] and the order's [key] every chord is a button that sounds (ADR-031).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongSheet(song: Song, notation: DegreeNotation, audition: ChordAudition? = null, key: Key? = null) {
    val tappable = audition != null && key != null
    if (tappable) {
        Text(
            "👆 Нажми на аккорд — он прозвучит в этой тональности",
            style = MaterialTheme.typography.bodySmall,
            color = Juicy.TextDim,
        )
    }
    song.sections.forEachIndexed { si, section ->
        Spacer(Modifier.height(12.dp))
        Text(section.label, style = MaterialTheme.typography.titleLarge, color = Juicy.Gold)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            section.bars.forEachIndexed { bi, bar ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Juicy.Ink4.copy(alpha = 0.6f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    bar.chords.forEachIndexed { ci, c ->
                        val id = "$si.$bi.$ci"
                        DegreeTile(
                            DegreeFormatter.chord(c, notation),
                            DegreeColors.of(c.root),
                            onClick = if (tappable) ({ audition?.tap(id, c, key!!) }) else null,
                            active = audition?.isHeard(id) == true,
                            onClickLabel = "Послушать аккорд",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(ui: TaperUi, vm: TaperViewModel) {
    val r = ui.result ?: return
    GlassCard(Modifier.fillMaxWidth(), accent = if (r.playableNow) Accents.Good else BAR) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    r.playableNow -> "С ходу! 🔥"
                    r.rating == Rating.AGAIN -> "Вылетела"
                    else -> "Почти"
                },
                style = MaterialTheme.typography.displaySmall,
                color = if (r.playableNow) Juicy.Good else if (r.rating == Rating.AGAIN) Juicy.Hot else Juicy.Gold,
            )
            Spacer(Modifier.height(6.dp))
            CountUpText(r.tips, MaterialTheme.typography.displayMedium, colors = BAR, prefix = "+", suffix = " ⭐")
            Spacer(Modifier.height(8.dp))
            Text("подсказка: ${r.outcome.maxHint.ru}", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
            val nextStart = TaperGrading.startHint(r.outcome.maxHint, r.outcome.self)
            Text(
                if (nextStart == HintStep.NONE) "В следующий раз — по памяти" else "В следующий раз начнём с подсказки «${nextStart.ru}»",
                style = MaterialTheme.typography.bodyMedium,
                color = Juicy.TextDim,
            )
            Text(
                "Снова попрошу: " + ruDue(r.newCard.dueDay, ui.today),
                style = MaterialTheme.typography.titleMedium,
                color = Juicy.Text,
            )
        }
    }
    JuicyButton(if (ui.queueLeft > 0) "Следующая песня" else "Закончить", onClick = vm::nextOrder, modifier = Modifier.fillMaxWidth(), colors = BAR, leading = "🎸")
}

@Composable
private fun ClosingTime(ui: TaperUi, onDone: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent = BAR) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("На сегодня всё", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            CountUpText(ui.totalTips, MaterialTheme.typography.displayMedium, colors = BAR, suffix = " ⭐")
            Text("песен: ${ui.ordersDone} · с ходу: ${ui.playableCount}", style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim)
        }
    }
    JuicyButton("Готово", onClick = onDone, modifier = Modifier.fillMaxWidth(), colors = BAR)
}

@Composable
private fun EmptyBar(onOpenLibrary: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent = BAR) {
        Text("🎸", style = MaterialTheme.typography.displayMedium)
        Text("Пока нечего играть", style = MaterialTheme.typography.headlineMedium)
        Text("Возьми песню из сборника в библиотеке или запиши свою ступенями. Потом приложение будет просить сыграть её — каждый раз в новой тональности.", style = MaterialTheme.typography.bodyLarge, color = Juicy.TextDim)
    }
    JuicyButton("В библиотеку", onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth(), colors = BAR, leading = "📚")
}

@Composable
private fun NothingDue(onExtra: () -> Unit, onDone: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent = Accents.Good) {
        Text("🎉", style = MaterialTheme.typography.displayMedium)
        Text("Всё держится", style = MaterialTheme.typography.headlineMedium)
        Text("Сегодня повторять нечего: все песни ещё помнятся. Можно сыграть сверх плана — самые шаткие первыми.", style = MaterialTheme.typography.bodyLarge, color = Juicy.TextDim)
    }
    JuicyButton("Сыграть сверх плана", onClick = onExtra, modifier = Modifier.fillMaxWidth(), colors = BAR, leading = "🎸")
    GhostButton("Готово", onClick = onDone, modifier = Modifier.fillMaxWidth())
}
