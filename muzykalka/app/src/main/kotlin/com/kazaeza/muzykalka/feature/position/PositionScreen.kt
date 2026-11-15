package com.kazaeza.muzykalka.feature.position

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordVibes
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.Fretboard
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyCatalog
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.Positions
import com.kazaeza.muzykalka.music.RootPlace
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.music.TonePlace
import com.kazaeza.muzykalka.ui.component.ChoiceRow
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.neck.FretMarker
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class PositionUi(
    val loading: Boolean = true,
    val key: Key? = null,
    /** The first fret of the zone. */
    val start: Int = 0,
    val lastStart: Int = 12,
    /** The chord picked (step of the scale, 0 = I); null — only the roots are on the neck. */
    val step: Int? = null,
    val roots: List<RootPlace> = emptyList(),
    val tones: List<TonePlace> = emptyList(),
    val settings: Settings = Settings(),
) {
    val picked: RootPlace? get() = roots.firstOrNull { it.step == step }
}

/**
 * O-21 «Позиция» (ADR-050): where the chords of a key lie on the neck without travelling — one zone of five frets,
 * the root of every chord on one of the three bass strings. A map, not a drill: nothing is judged or recorded.
 */
class PositionViewModel(private val graph: AppGraph) : ViewModel() {
    private val keys = KeyPicker(Random(System.nanoTime()))
    private val arranger = Arranger()
    private val state = MutableStateFlow(PositionUi())
    val ui: StateFlow<PositionUi> = state.asStateFlow()
    private var board = Fretboard(Settings().tuning, Settings().fretCount)
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val settings = graph.settings.settings.first()
            graph.audioOutput.a4Hz = settings.a4Hz
            board = Fretboard(settings.tuning, settings.fretCount)
            val key = keys.next(Mode.MAJOR)
            state.update { it.copy(loading = false, settings = settings, lastStart = Positions.lastStart(board)) }
            show(key, Positions.homeZone(board, key), null)
        }
    }

    private fun show(key: Key, start: Int, step: Int?) {
        val roots = Positions.roots(board, key, start)
        val tones = step?.let { s -> Positions.tones(board, key, roots.first { it.step == s }.chord, start) }.orEmpty()
        state.update { it.copy(key = key, start = start, step = step, roots = roots, tones = tones) }
    }

    fun newKey() {
        val mode = state.value.key?.mode ?: Mode.MAJOR
        val key = keys.next(mode)
        show(key, Positions.homeZone(board, key), null)
    }

    /** The same tonic in the other mode: the roots move, the zone stays. */
    fun setMode(mode: Mode) {
        val key = state.value.key ?: return
        if (key.mode == mode) return
        show(KeyCatalog.practical(key.tonic.pitchClass, mode), state.value.start, null)
    }

    fun shift(by: Int) {
        val s = state.value
        val key = s.key ?: return
        show(key, (s.start + by).coerceIn(0, s.lastStart), s.step)
    }

    fun home() {
        val key = state.value.key ?: return
        show(key, Positions.homeZone(board, key), state.value.step)
    }

    /** A chord of the key: its tones light up across the zone, and it is strummed. */
    fun pick(step: Int) {
        val s = state.value
        val key = s.key ?: return
        show(key, s.start, step)
        val chord = s.roots.first { it.step == step }.chord
        job?.cancel()
        job = viewModelScope.launch { graph.audioOutput.play(arranger.strum(chord, key)) }
    }

    override fun onCleared() {
        job?.cancel()
        graph.audioOutput.stop()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PositionScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: PositionViewModel = viewModel { PositionViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val key = ui.key
    val notation = ui.settings.degreeNotation
    val zone = Positions.zone(ui.start)

    ScreenFrame(title = "Позиция", subtitle = "аккорды тональности в одной зоне грифа", accent = Accents.Hunt, onBack = onBack) {
        if (key == null) return@ScreenFrame
        item(key = "key") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DegreeChip(RuNames.key(key), Juicy.Gold, large = true)
                    Spacer(Modifier.weight(1f))
                    GhostButton("другая", onClick = vm::newKey, colors = Accents.Hunt, leading = "⇄")
                }
                ChoiceRow(listOf(Mode.MAJOR, Mode.MINOR), key.mode, { it.ruName }, vm::setMode, colors = Accents.Hunt)
            }
        }
        item(key = "zone") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("‹", onClick = { vm.shift(-1) }, colors = Accents.Hunt, enabled = ui.start > 0)
                Text(
                    if (zone.first == 0) "открытая позиция: лады 0–${zone.last}" else "лады ${zone.first}–${zone.last}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Juicy.Text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                GhostButton("›", onClick = { vm.shift(1) }, colors = Accents.Hunt, enabled = ui.start < ui.lastStart)
                GhostButton("домой", onClick = vm::home, colors = Accents.Hunt)
            }
        }
        item(key = "neck") {
            val lit = ui.tones.map { it.place }.toSet()
            val rootPlace = ui.picked?.place
            val markers =
                // the roots of the other chords stay on the neck as ghosts: the whole key is in sight
                ui.roots.filter { it.place !in lit }.map { r ->
                    FretMarker(r.place, DegreeColors.of(r.chord.root), DegreeFormatter.chord(r.chord, notation), ghost = ui.step != null)
                } + ui.tones.map { t ->
                    FretMarker(t.place, DegreeColors.of(t.degree), DegreeFormatter.degree(t.degree, notation), pulse = t.place == rootPlace)
                }
            com.kazaeza.muzykalka.ui.neck.Fretboard(
                stringCount = ui.settings.tuning.stringCount,
                fromFret = maxOf(0, zone.first - 1),
                toFret = zone.last + 1,
                markers = markers,
                leftHanded = ui.settings.leftHanded,
                height = 230.dp,
            )
        }
        item(key = "chords") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("аккорды лада — нажми, чтобы увидеть и услышать")
                ChoiceRow(ui.roots.map { it.step }, ui.step ?: -1, { step -> DegreeFormatter.chord(ui.roots.first { it.step == step }.chord, notation) }, vm::pick, colors = Accents.Hunt)
            }
        }
        item(key = "card") {
            val picked = ui.picked
            if (picked == null) {
                Text(
                    "Три басовые струны настроены по квартам, поэтому в любых пяти ладах на них есть корень каждого из семи аккордов лада: ехать по грифу не нужно. " +
                        "На грифе — эти корни. Нажми аккорд: загорятся все его ноты в зоне, форма сложится сама.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Juicy.TextDim,
                )
            } else {
                GlassCard(Modifier.fillMaxWidth(), accent = DegreeColors.gradient(DegreeColors.of(picked.chord.root))) {
                    Text(ChordVibes.title(picked.chord, key.mode, notation), style = MaterialTheme.typography.headlineSmall, color = Juicy.Text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Корень: ${picked.place.string}-я струна, " + if (picked.place.fret == 0) "открытая" else "${picked.place.fret}-й лад",
                        style = MaterialTheme.typography.titleMedium,
                        color = DegreeColors.of(picked.chord.root),
                    )
                    Text(
                        picked.form?.ru ?: "Удобной формы от этого корня нет — сдвинь зону на пару ладов, корень уйдёт под указательный.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (picked.form != null) Juicy.Text else Juicy.Gold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ноты в ладу:", style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
                        Spacer(Modifier.width(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ui.tones.map { it.degree }.distinctBy { it.semitones }.forEach { d -> DegreeChip(DegreeFormatter.degree(d, notation), DegreeColors.of(d)) }
                        }
                    }
                }
            }
        }
        item(key = "next") {
            Text(
                "Это карта. Упражнения «ход в одной позиции», переходы между соседними зонами и обращения — следующий шаг.",
                style = MaterialTheme.typography.bodySmall,
                color = Juicy.TextFaint,
            )
        }
    }
}
