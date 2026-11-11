package com.kazaeza.muzykalka.feature.listen

import androidx.compose.foundation.layout.size
import com.kazaeza.muzykalka.ui.effect.GlowOrb
import com.kazaeza.muzykalka.ui.effect.HarmonicWaves
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.feature.common.KeepScreenOn
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.game.ListenDeck
import com.kazaeza.muzykalka.game.ListenItem
import com.kazaeza.muzykalka.game.ListenScope
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.DegreeVibes
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.ui.component.ChoiceRow
import com.kazaeza.muzykalka.ui.component.DeadlineBar
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.GradientText
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.effect.breathe
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Where a hands-free question is. */
enum class ListenPhase { IDLE, HEARING, GUESSING, ANSWER }

data class ListenUi(
    val scope: ListenScope = ListenScope.CHORDS,
    val delayMs: Long = ListenDeck.DEFAULT_DELAY_MS,
    /** Say the answer aloud (TTS) — the phone can stay in the pocket. */
    val voice: Boolean = true,
    val running: Boolean = false,
    val phase: ListenPhase = ListenPhase.IDLE,
    val item: ListenItem? = null,
    /** Grows with every question: restarts the countdown and the pop. */
    val serial: Int = 0,
    val heard: Int = 0,
    val notation: DegreeNotation = DegreeNotation.ARABIC,
    /** false = the phone has no Russian TTS voice: the answer is only on the screen. */
    val voiceAvailable: Boolean? = null,
)

/**
 * «Угадай про себя» (O-16, ADR-030): the degree sounds over the drone or a chord, a pause lets you
 * name it silently, then the answer is shown, said and walks home — and the next one comes. No
 * buttons while it runs, no score: the delay is what turns listening into recall.
 */
class ListenViewModel(private val graph: AppGraph) : ViewModel() {
    private val random = Random(System.nanoTime())
    private val keys = KeyPicker(random)
    private val state = MutableStateFlow(ListenUi())
    val ui: StateFlow<ListenUi> = state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val settings = graph.settings.settings.first()
            graph.audioOutput.a4Hz = settings.a4Hz
            state.update { it.copy(notation = settings.degreeNotation) }
        }
        viewModelScope.launch { graph.announcer.available.collect { a -> state.update { it.copy(voiceAvailable = a) } } }
    }

    fun setScope(scope: ListenScope) {
        val wasRunning = state.value.running
        stop()
        state.update { it.copy(scope = scope, item = null) }
        if (wasRunning) start()
    }

    /** Takes effect from the next pause. */
    fun setDelay(ms: Long) = state.update { it.copy(delayMs = ms) }

    fun setVoice(on: Boolean) = state.update { it.copy(voice = on) }

    fun toggle() = if (state.value.running) stop() else start()

    fun start() {
        if (job?.isActive == true) return
        val deck = ListenDeck(keys, random, state.value.scope)
        state.update { it.copy(running = true) }
        job = viewModelScope.launch {
            var drone: List<MidiNote>? = null
            while (isActive) {
                val item = deck.next()
                state.update { it.copy(item = item, serial = it.serial + 1, phase = ListenPhase.HEARING) }
                if (item.drone != drone) {
                    graph.audioOutput.drone(item.drone)
                    drone = item.drone
                    delay(DRONE_SETTLE_MS)
                }
                graph.audioOutput.play(item.question)
                state.update { it.copy(phase = ListenPhase.GUESSING) }
                delay(state.value.delayMs)
                state.update { it.copy(phase = ListenPhase.ANSWER) }
                if (state.value.voice) graph.announcer.say(item.spoken)
                graph.audioOutput.play(item.answer)
                delay(AFTER_ANSWER_MS)
                state.update { it.copy(heard = it.heard + 1) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        graph.audioOutput.stop()
        state.update { it.copy(running = false, phase = ListenPhase.IDLE) }
    }

    override fun onCleared() = stop()

    companion object {
        /** A new drone or chord sounds alone for a moment before the note comes over it. */
        const val DRONE_SETTLE_MS = 1_500L

        /** The walk home lands before the next question. */
        const val AFTER_ANSWER_MS = 900L
    }
}

@Composable
fun ListenScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: ListenViewModel = viewModel { ListenViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (ui.running) KeepScreenOn()
    ListenContent(ui, onBack = onBack, onToggle = vm::toggle, onScope = vm::setScope, onDelay = vm::setDelay, onVoice = vm::setVoice)
}

@Composable
fun ListenContent(
    ui: ListenUi,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onScope: (ListenScope) -> Unit,
    onDelay: (Long) -> Unit,
    onVoice: (Boolean) -> Unit,
) {
    ScreenFrame(title = "Угадай про себя", subtitle = "звук → пауза → ответ, без кнопок", accent = Accents.Tir, onBack = onBack) {
        item(key = "stage") { Stage(ui) }
        item(key = "action") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.running) {
                    GhostButton("Стоп", onClick = onToggle, modifier = Modifier.fillMaxWidth(), colors = Accents.Tir, leading = "■")
                } else {
                    JuicyButton("Слушать", onClick = onToggle, modifier = Modifier.fillMaxWidth(), colors = Accents.Tir, leading = "►")
                }
                if (ui.heard > 0) {
                    Text("услышано: ${ui.heard}", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("что гудит под нотой")
                ChoiceRow(ListenScope.entries, ui.scope, { it.ru }, onScope, colors = Accents.Tir)
                SectionLabel("пауза перед ответом")
                ChoiceRow(ListenDeck.DELAYS_MS, ui.delayMs, { "${it / 1000} с" }, onDelay, colors = Accents.Tir)
                SectionLabel("ответ")
                ChoiceRow(listOf(true, false), ui.voice, { if (it) "вслух" else "только на экране" }, onVoice, colors = Accents.Tir)
                if (ui.voice && ui.voiceAvailable == false) {
                    Text(
                        "На телефоне нет русского голоса для озвучки — ответ будет только на экране. Голос ставится в настройках Android: «Синтез речи».",
                        style = MaterialTheme.typography.bodySmall,
                        color = Juicy.Gold,
                    )
                }
            }
        }
        item(key = "how") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Tir) {
                Text("Как это работает", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Гудит дом или аккорд лада, сверху звучит ступень. Пока идёт пауза — назови её про себя, по ощущению. " +
                        "Потом ответ: на экране, голосом и путём домой. Без очков и ошибок — просто слушай. В наушниках можно не смотреть в экран.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Juicy.TextDim,
                )
            }
        }
    }
}

@Composable
private fun Stage(ui: ListenUi) {
    val item = ui.item
    GlassCard(Modifier.fillMaxWidth(), accent = Accents.Tir) {
        Column(Modifier.fillMaxWidth().heightIn(min = 300.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (item != null && ui.phase != ListenPhase.IDLE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DegreeChip(RuNames.key(item.key), Juicy.Gold)
                    item.over?.let { chord ->
                        Spacer(Modifier.width(8.dp))
                        DegreeChip("над " + DegreeFormatter.chord(chord, ui.notation), DegreeColors.of(chord.root))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            when {
                item == null || ui.phase == ListenPhase.IDLE -> Idle()
                ui.phase == ListenPhase.HEARING -> {
                    // O-34: while it sounds — the tone and its overtones, as on the Tir's prompt
                    HarmonicWaves(Accents.Tir, Modifier.fillMaxWidth().height(56.dp), energy = 1f)
                    Text(if (item.newKey) "Новая тональность…" else "Слушай…", style = MaterialTheme.typography.headlineSmall, color = Juicy.TextDim)
                }
                ui.phase == ListenPhase.GUESSING -> {
                    GradientText("?", MaterialTheme.typography.displayLarge, Accents.Tir, Modifier.breathe())
                    Text("Назови про себя", style = MaterialTheme.typography.headlineSmall, color = Juicy.Text)
                    Spacer(Modifier.height(12.dp))
                    Countdown(ui.serial, ui.delayMs)
                }
                else -> Answer(ui, item)
            }
        }
    }
}

@Composable
private fun Idle() {
    GlowOrb(Accents.Tir, Modifier.size(64.dp).breathe(amount = 0.06f, periodMs = 2400))
    Spacer(Modifier.height(16.dp))
    Text(
        "Нажми «Слушать» — дальше всё само",
        style = MaterialTheme.typography.titleMedium,
        color = Juicy.TextDim,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Answer(ui: ListenUi, item: ListenItem) {
    val d = item.degree
    val c = DegreeColors.of(d)
    GradientText(DegreeFormatter.degree(d, ui.notation), MaterialTheme.typography.displayLarge, DegreeColors.gradient(c), Modifier.pop(ui.serial, 0.3f))
    Text(DegreeVibes.of(d).word, style = MaterialTheme.typography.headlineSmall, color = c)
    item.over?.let { chord ->
        Text(DegreeVibes.overChord(d, chord, item.key.mode, ui.notation), style = MaterialTheme.typography.titleMedium, color = Juicy.Text, textAlign = TextAlign.Center)
    }
    Text(DegreeVibes.pathHome(d, item.key.mode, ui.notation), style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
}

/** The pause running out, left to right. */
@Composable
private fun Countdown(serial: Int, durationMs: Long) {
    val left = remember(serial) { Animatable(1f) }
    LaunchedEffect(serial) { left.animateTo(0f, tween(durationMs.toInt(), easing = LinearEasing)) }
    DeadlineBar(left.value, Modifier.fillMaxWidth(0.7f), colors = Accents.Tir)
}

@Preview
@Composable
private fun ListenIdlePreview() = MuzykalkaTheme {
    ListenContent(ListenUi(), onBack = {}, onToggle = {}, onScope = {}, onDelay = {}, onVoice = {})
}
