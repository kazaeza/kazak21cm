package com.kazaeza.muzykalka.feature.position

import com.kazaeza.muzykalka.ui.theme.Manrope
import com.kazaeza.muzykalka.music.FretPosition
import com.kazaeza.muzykalka.music.FormPlace
import com.kazaeza.muzykalka.feature.common.plural
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
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
    /** O-53: the places of the picked chord (the tonic chord while none is picked) along the whole neck. */
    val forms: List<FormPlace> = emptyList(),
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
        val forms = Positions.forms(board, key, roots.first { it.step == (step ?: 0) }.chord)
        state.update { it.copy(key = key, start = start, step = step, roots = roots, tones = tones, forms = forms) }
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

    /** O-53: the zone jumps to [start] — a place of the chord picked on the strip of the whole neck. */
    fun goTo(start: Int) {
        val s = state.value
        val key = s.key ?: return
        show(key, start.coerceIn(0, s.lastStart), s.step)
    }

    /** O-53: a root touched on the neck itself picks its chord. */
    fun tapNeck(place: FretPosition) {
        state.value.roots.firstOrNull { it.place == place }?.let { pick(it.step) }
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

/**
 * O-21, redrawn in O-53: the neck speaks the app's language — dark glass, strings of light, the degrees in their
 * colours with dot-matrix numerals — and the picture is the control: the zone is framed on the neck itself, a root
 * touched on it picks its chord, and a strip of the whole neck shows the places of that chord (the five shapes of
 * CAGED) to jump between.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PositionScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: PositionViewModel = viewModel { PositionViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val key = ui.key
    val notation = ui.settings.degreeNotation
    val zone = Positions.zone(ui.start)
    val name = { r: RootPlace -> DegreeFormatter.chord(r.chord, notation) }

    ScreenFrame(title = "Позиция", subtitle = "аккорды тональности в одной зоне грифа", accent = Accents.Hunt, onBack = onBack) {
        if (key == null) return@ScreenFrame
        item(key = "key") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DegreeChip(RuNames.key(key), Juicy.Gold, large = true)
                Spacer(Modifier.weight(1f))
                ChoiceRow(listOf(Mode.MAJOR, Mode.MINOR), key.mode, { it.ruName }, vm::setMode, colors = Accents.Hunt)
                GhostButton("⇄", onClick = vm::newKey, colors = Accents.Hunt)
            }
        }
        item(key = "neck") {
            val lit = ui.tones.map { it.place }.toSet()
            val rootPlace = ui.picked?.place
            val markers =
                // the roots of the other chords stay on the neck as rings: the whole key is in sight
                ui.roots.filter { it.place !in lit }.map { r ->
                    FretMarker(r.place, DegreeColors.of(r.chord.root), name(r), ghost = true)
                } + ui.tones.map { t ->
                    FretMarker(t.place, DegreeColors.of(t.degree), DegreeFormatter.degree(t.degree, notation), pulse = t.place == rootPlace)
                }
            com.kazaeza.muzykalka.ui.neck.Fretboard(
                stringCount = ui.settings.tuning.stringCount,
                fromFret = maxOf(0, zone.first - 2),
                toFret = minOf(ui.settings.fretCount, zone.last + 2),
                markers = markers,
                leftHanded = ui.settings.leftHanded,
                height = 240.dp,
                onTap = vm::tapNeck,
                glass = true,
                zone = zone,
            )
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
        item(key = "chords") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("аккорды лада — нажми здесь или корень на грифе")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.roots.forEach { r ->
                        DegreeChip(name(r), DegreeColors.of(r.chord.root), large = true, onClick = { vm.pick(r.step) }, selected = r.step == ui.step)
                    }
                }
            }
        }
        if (ui.forms.isNotEmpty()) {
            item(key = "forms") {
                val chord = ui.roots.firstOrNull { it.step == (ui.step ?: 0) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel((chord?.let(name) ?: "") + " по всему грифу — " + ui.forms.size + " " + plural(ui.forms.size, "место", "места", "мест") + ", нажми любое")
                    FormStrip(ui.forms, ui.start, ui.lastStart, chord?.let { DegreeColors.of(it.chord.root) } ?: Juicy.Gold, ui.settings.leftHanded, vm::goTo)
                }
            }
        }
        item(key = "card") {
            val picked = ui.picked
            if (picked == null) {
                Text(
                    "Три басовые струны настроены по квартам, поэтому в любых пяти ладах на них есть корень каждого из семи аккордов лада: ехать по грифу не нужно. " +
                        "Кольца на грифе — эти корни. Нажми аккорд: загорятся все его ноты в зоне, форма сложится сама.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Juicy.TextDim,
                )
            } else {
                val color = DegreeColors.of(picked.chord.root)
                GlassCard(Modifier.fillMaxWidth(), accent = DegreeColors.gradient(color)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // the shape's name is the biggest thing on the card: it is what the hand needs first
                        picked.form?.let {
                            Text(it.name, style = MaterialTheme.typography.displayMedium, color = Juicy.Gold)
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(ChordVibes.title(picked.chord, key.mode, notation), style = MaterialTheme.typography.titleLarge, color = Juicy.Text)
                            Text(
                                "Корень: ${picked.place.string}-я струна, " + if (picked.place.fret == 0) "открытая" else "${picked.place.fret}-й лад",
                                style = MaterialTheme.typography.titleMedium,
                                color = color,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        picked.form?.ru ?: "Удобной формы от этого корня нет — нажми другое место на полоске грифа: корень уйдёт под указательный.",
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

/**
 * O-53: the whole neck in one line, and on it the places of one chord — each shape of CAGED a pill over the frets its
 * zone covers. The gold line is the zone that is on the big neck; a touch on a pill takes the zone there, a touch
 * anywhere else centres the zone on that fret.
 */
@Composable
private fun FormStrip(forms: List<FormPlace>, start: Int, lastStart: Int, color: Color, leftHanded: Boolean, onGo: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val cells = lastStart + Positions.SPAN
    val letter = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    val number = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Juicy.TextFaint)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .semantics { contentDescription = "Гриф целиком: места аккорда" }
            .pointerInput(forms, cells, leftHanded) {
                detectTapGestures { p ->
                    val x = if (leftHanded) size.width - p.x else p.x
                    val fret = (x / (size.width / cells.toFloat())).toInt().coerceIn(0, cells - 1)
                    val hit = forms.filter { fret in Positions.zone(it.start) }.minByOrNull { kotlin.math.abs(it.start + Positions.SPAN / 2 - fret) }
                    onGo(hit?.start ?: (fret - Positions.SPAN / 2).coerceIn(0, lastStart))
                }
            },
    ) {
        val cell = size.width / cells
        val mirror = { x: Float -> if (leftHanded) size.width - x else x }
        val rail = size.height - 22.dp.toPx()
        val pill = 22.dp.toPx()
        // the neck: a hairline, a tick per fret, the dots where a guitar has its inlays
        drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, rail), Offset(size.width, rail), 1.dp.toPx())
        for (f in 0..cells) drawLine(Color.White.copy(alpha = if (f == 0) 0.5f else 0.12f), Offset(mirror(f * cell), rail - 4.dp.toPx()), Offset(mirror(f * cell), rail + 4.dp.toPx()), 1.dp.toPx())
        for (f in listOf(3, 5, 7, 9, 12, 15).filter { it <= cells }) {
            val x = mirror((f - 0.5f) * cell)
            drawCircle(Color.White.copy(alpha = 0.22f), 2.dp.toPx(), Offset(x, rail + 10.dp.toPx()))
            val layout = measurer.measure(f.toString(), number)
            drawText(layout, topLeft = Offset(x - layout.size.width / 2, rail + 12.dp.toPx()))
        }
        // the places of the chord, on two rows so that neighbours do not cover each other
        forms.forEachIndexed { i, f ->
            val on = f.start == start
            val top = (i % 2) * (pill + 3.dp.toPx())
            val a = mirror(f.start * cell)
            val b = mirror((f.start + Positions.SPAN) * cell)
            val left = minOf(a, b) + 1.5f
            val width = kotlin.math.abs(b - a) - 3f
            val radius = CornerRadius(pill / 2, pill / 2)
            drawRoundRect(color.copy(alpha = if (on) 0.9f else 0.14f), Offset(left, top), Size(width, pill), radius)
            drawRoundRect(color.copy(alpha = if (on) 1f else 0.6f), Offset(left, top), Size(width, pill), radius, style = Stroke(1.2.dp.toPx()))
            val layout = measurer.measure(f.form.name, letter.copy(color = if (on) Juicy.PaperInk else color))
            drawText(layout, topLeft = Offset(left + width / 2 - layout.size.width / 2, top + pill / 2 - layout.size.height / 2))
        }
        // the zone that is on the big neck
        val z0 = mirror(start * cell)
        val z1 = mirror((start + Positions.SPAN) * cell)
        drawLine(Juicy.Gold.copy(alpha = 0.25f), Offset(z0, rail), Offset(z1, rail), 9.dp.toPx(), StrokeCap.Round)
        drawLine(Juicy.Gold, Offset(z0, rail), Offset(z1, rail), 3.dp.toPx(), StrokeCap.Round)
    }
}
