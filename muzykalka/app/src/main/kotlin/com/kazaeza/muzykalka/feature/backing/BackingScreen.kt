package com.kazaeza.muzykalka.feature.backing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.music.Backing
import com.kazaeza.muzykalka.music.BackingStep
import com.kazaeza.muzykalka.music.ChordVibes
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.ui.component.ChoiceRow
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.GradientText
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy
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

data class BackingUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val title: String = "",
    val mode: Mode = Mode.MAJOR,
    val key: Key? = null,
    val tempo: Int = BackingViewModel.TEMPOS[1],
    val steps: List<BackingStep> = emptyList(),
    /** The step that is sounding; null while silent. */
    val current: Int? = null,
    val playing: Boolean = false,
    val notation: DegreeNotation = DegreeNotation.ARABIC,
) {
    val now: BackingStep? get() = current?.let { steps.getOrNull(it) }

    /** What comes after [now] — the first step of the song again after the last one: the backing goes round. */
    val next: BackingStep? get() = current?.let { steps.getOrNull((it + 1) % steps.size.coerceAtLeast(1)) }
}

/**
 * X-03 «Под бэкинг», step 1 (ADR-052): a song of the library sounds bar by bar in a key of chance, round and round, and
 * the screen says which chord is on and which comes next — to play over it. Nothing is judged; the microphone is off.
 */
class BackingViewModel(private val graph: AppGraph, private val id: String) : ViewModel() {
    private val keys = KeyPicker(Random(System.nanoTime()))
    private val state = MutableStateFlow(BackingUi())
    val ui: StateFlow<BackingUi> = state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val settings = graph.settings.settings.first()
            graph.audioOutput.a4Hz = settings.a4Hz
            val song = graph.library.get(id)?.song
            if (song == null) {
                state.update { it.copy(loading = false, missing = true) }
            } else {
                state.update {
                    it.copy(loading = false, title = song.title, mode = song.mode, key = keys.next(song.mode), steps = Backing.steps(song), notation = settings.degreeNotation)
                }
            }
        }
    }

    fun newKey() {
        val wasPlaying = state.value.playing
        stop()
        state.update { it.copy(key = keys.next(it.mode)) }
        if (wasPlaying) play()
    }

    fun setTempo(tempo: Int) {
        val wasPlaying = state.value.playing
        stop()
        state.update { it.copy(tempo = tempo) }
        if (wasPlaying) play()
    }

    fun toggle() = if (state.value.playing) stop() else play()

    private fun play() {
        val s = state.value
        val key = s.key ?: return
        if (s.steps.isEmpty()) return
        val score = Backing.score(s.steps, key, s.tempo.toDouble())
        val msPerBeat = 60_000.0 / s.tempo
        state.update { it.copy(playing = true, current = 0) }
        job = viewModelScope.launch {
            while (isActive) {
                val startedNs = System.nanoTime()
                // the picture follows the sound: each step lights up when its time comes
                val lights = launch {
                    s.steps.forEachIndexed { i, step ->
                        val due = (step.startBeat * msPerBeat).toLong() + LATENCY_MS - (System.nanoTime() - startedNs) / 1_000_000
                        if (due > 0) delay(due)
                        state.update { it.copy(current = i) }
                    }
                }
                try {
                    graph.audioOutput.play(score)
                } finally {
                    lights.cancel()
                }
                delay(ROUND_GAP_MS) // a breath between rounds — and no tight loop if the output gives up at once
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (state.value.playing) graph.audioOutput.stop()
        state.update { it.copy(playing = false, current = null) }
    }

    override fun onCleared() = stop()

    companion object {
        val TEMPOS = listOf(60, 80, 100, 120)

        /** The screen lags the speaker by about this much; set by eye on a phone (as the pulses of the Tir are). */
        const val LATENCY_MS = 60L
        const val ROUND_GAP_MS = 200L
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackingScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: BackingViewModel = viewModel(key = "backing-$id") { BackingViewModel(graph, id) }

    // O-66: the app leaves the screen (another app, the lock) — the sound stops, it does not hum on in the pocket
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { vm.stop() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val key = ui.key
    val chord = { step: BackingStep -> DegreeFormatter.chord(step.chord, ui.notation) }

    ScreenFrame(title = ui.title.ifBlank { "Под бэкинг" }, subtitle = "под бэкинг — играй поверх", accent = Accents.Taper, onBack = onBack) {
        if (ui.missing) {
            item(key = "missing") { Text("Этой песни больше нет в библиотеке.", style = MaterialTheme.typography.bodyLarge, color = Juicy.Text) }
            return@ScreenFrame
        }
        if (key == null) return@ScreenFrame
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DegreeChip(RuNames.key(key), Juicy.Gold, large = true)
                    Spacer(Modifier.weight(1f))
                    GhostButton("другая", onClick = vm::newKey, colors = Accents.Taper, leading = "⇄")
                }
                SectionLabel("темп, ударов в минуту")
                ChoiceRow(BackingViewModel.TEMPOS, ui.tempo, { it.toString() }, vm::setTempo, colors = Accents.Taper)
                JuicyButton(if (ui.playing) "Стоп" else "Играть", onClick = vm::toggle, modifier = Modifier.fillMaxWidth(), colors = Accents.Taper, leading = if (ui.playing) "■" else "►")
            }
        }
        item(key = "now") {
            val now = ui.now
            val next = ui.next
            GlassCard(Modifier.fillMaxWidth(), accent = now?.let { DegreeColors.gradient(DegreeColors.of(it.chord.root)) }) {
                if (now == null) {
                    Text("Нажми «Играть»: песня пойдёт по кругу, а здесь будет видно, какой аккорд звучит и какой следующий.", style = MaterialTheme.typography.bodyLarge, color = Juicy.TextDim)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("сейчас · часть ${now.label}, такт ${now.bar + 1}", color = Juicy.TextDim)
                            GradientText(chord(now), MaterialTheme.typography.displayMedium, DegreeColors.gradient(DegreeColors.of(now.chord.root)))
                            Text(ChordVibes.of(now.chord, ui.mode).role, style = MaterialTheme.typography.titleMedium, color = Juicy.Text)
                        }
                        if (next != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                SectionLabel("дальше", color = Juicy.TextDim)
                                Text(chord(next), style = MaterialTheme.typography.headlineMedium, color = DegreeColors.of(next.chord.root))
                            }
                        }
                    }
                }
            }
        }
        // the form, part by part: every bar a box, the sounding chord lit
        ui.steps.withIndex().groupBy { it.value.part }.forEach { (part, steps) ->
            item(key = "part-$part") {
                Column {
                    Text(steps.first().value.label, style = MaterialTheme.typography.titleLarge, color = Juicy.Gold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        steps.groupBy { it.value.bar }.forEach { (_, bar) ->
                            val on = bar.any { it.index == ui.current }
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                Modifier
                                    .clip(shape)
                                    .background(Juicy.Ink4.copy(alpha = if (on) 0.95f else 0.5f))
                                    .border(if (on) 2.dp else 1.dp, if (on) Juicy.Gold else Juicy.Line, shape)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                bar.forEach { (index, step) ->
                                    DegreeChip(chord(step), DegreeColors.of(step.chord.root), large = true, selected = index == ui.current)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "why") {
            Spacer(Modifier.width(1.dp))
            Text(
                "Такт — четыре доли; аккорды такта делят его поровну. Фортепиано держит аккорд, бас идёт по долям. Микрофон выключен: слушаешь себя сам.",
                style = MaterialTheme.typography.bodySmall,
                color = Juicy.TextFaint,
            )
        }
    }
}
