package com.kazaeza.muzykalka.feature.feel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.DegreeAnchor
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.DegreeVibes
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.ui.component.ChoiceRow
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
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
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Which degrees the lesson shows. */
enum class FeelScope(val ru: String, val mode: Mode) {
    MAJOR("мажор", Mode.MAJOR),
    MINOR("минор", Mode.MINOR),
    CHROMATIC("все 12", Mode.MAJOR);

    fun degrees(): List<Degree> = if (this == CHROMATIC) Degree.CHROMATIC else mode.degrees
}

/** O-08: what sounds under the degree — the tonic drone, or a chord of the key (step of the scale). */
const val FEEL_DRONE = -1
val FEEL_CONTEXTS = listOf(FEEL_DRONE, 0, 3, 4, 5)

data class FeelUi(
    val key: Key? = null,
    val scope: FeelScope = FeelScope.MAJOR,
    /** [FEEL_DRONE] or a step of the scale (0 = I): the degree is heard over that chord. */
    val context: Int = FEEL_DRONE,
    val playing: Degree? = null,
    val droneOn: Boolean = false,
    val settings: Settings = Settings(),
    /** O-09: the user's anchor songs by semitones. */
    val anchors: Map<Int, DegreeAnchor> = emptyMap(),
    /** The anchor editor is open for this degree. */
    val editing: Degree? = null,
    val editTitle: String = "",
    val editMelody: String = "",
    val editError: String? = null,
    /** Titles from the library, offered as quick picks for an anchor. */
    val libraryTitles: List<String> = emptyList(),
) {
    val chord: ChordDegree? get() = if (context == FEEL_DRONE) null else Harmony.diatonicChord(scope.mode, context)
}

/**
 * «Вайб ступеней» (ADR-015, ADR-017): the tonic drone is home; tap a degree to hear it sung over
 * the drone, held long enough to feel its colour against home, then its walk home. No score:
 * this is listening, not a test. The drone stays on between taps until «Стоп».
 */
class FeelViewModel(private val graph: AppGraph) : ViewModel() {
    private val random = Random(System.nanoTime())
    private val keys = KeyPicker(random)
    private val arranger = Arranger()
    private val state = MutableStateFlow(FeelUi())
    val ui: StateFlow<FeelUi> = state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val settings = graph.settings.settings.first()
            graph.audioOutput.a4Hz = settings.a4Hz
            state.update { it.copy(settings = settings, key = keys.next(it.scope.mode)) }
            val titles = graph.library.allOrderable().map { it.title }.distinct().take(8)
            state.update { it.copy(libraryTitles = titles) }
        }
        viewModelScope.launch { graph.anchors.anchors.collect { a -> state.update { it.copy(anchors = a) } } }
    }

    // ------------------------------------------------------------ O-09 anchors

    fun editAnchor(degree: Degree) {
        val a = state.value.anchors[degree.semitones]
        state.update { it.copy(editing = degree, editTitle = a?.title ?: "", editMelody = a?.melodyText ?: degree.toString(), editError = null) }
    }

    fun setEditTitle(text: String) = state.update { it.copy(editTitle = text, editError = null) }

    fun setEditMelody(text: String) = state.update { it.copy(editMelody = text, editError = null) }

    fun cancelEdit() = state.update { it.copy(editing = null, editError = null) }

    private fun parsedEdit(): DegreeAnchor? {
        val s = state.value
        val degree = s.editing ?: return null
        val result = DegreeAnchor.parse(degree, s.editTitle, s.editMelody, s.settings.degreeNotation)
        result.exceptionOrNull()?.let { e -> state.update { it.copy(editError = e.message) } }
        return result.getOrNull()
    }

    fun saveAnchor() {
        val anchor = parsedEdit() ?: return
        viewModelScope.launch {
            graph.anchors.set(anchor)
            state.update { it.copy(editing = null) }
        }
    }

    fun deleteAnchor() {
        val degree = state.value.editing ?: return
        viewModelScope.launch {
            graph.anchors.remove(degree)
            state.update { it.copy(editing = null) }
        }
    }

    /** Hear what is being typed, before saving. */
    fun previewAnchor() {
        parsedEdit()?.let(::playAnchor)
    }

    /** The anchor's opening over the tonic drone of the current key. */
    fun playAnchor(anchor: DegreeAnchor) {
        val key = state.value.key ?: return
        val wasOn = state.value.droneOn && state.value.context == FEEL_DRONE
        job?.cancel()
        job = viewModelScope.launch {
            state.update { it.copy(context = FEEL_DRONE, droneOn = true, playing = anchor.degree) }
            if (!wasOn) {
                graph.audioOutput.drone(arranger.drone(key))
                delay(1_200)
            }
            graph.audioOutput.play(anchor.score(key, arranger))
            state.update { it.copy(playing = null) }
        }
    }

    fun newKey() {
        stop()
        state.update { it.copy(key = keys.next(it.scope.mode)) }
    }

    fun setScope(scope: FeelScope) {
        stop()
        state.update { it.copy(scope = scope, key = if (scope.mode != it.scope.mode) keys.next(scope.mode) else it.key) }
    }

    /** Same degree, another chord under it (RESEARCH §4 D): «5 над I — опора, 5 над IV — тянет вниз». */
    fun setContext(step: Int) {
        state.update { it.copy(context = step) }
        val s = state.value
        val key = s.key ?: return
        if (s.droneOn) graph.audioOutput.drone(droneFor(key, s))
    }

    private fun droneFor(key: Key, s: FeelUi): List<MidiNote> = s.chord?.let { arranger.chordDrone(it, key) } ?: arranger.drone(key)

    fun play(degree: Degree) = launchPlay(listOf(degree))

    /** Every degree in turn over one drone: a passive listening loop for the commute. */
    fun playAll() = launchPlay(state.value.scope.degrees())

    /** Just the drone: sing degrees against it, or simply let «home» sink in. */
    fun droneOnly() {
        val key = state.value.key ?: return
        job?.cancel()
        graph.audioOutput.drone(droneFor(key, state.value))
        state.update { it.copy(droneOn = true, playing = null) }
    }

    private fun launchPlay(degrees: List<Degree>) {
        val key = state.value.key ?: return
        val wasOn = state.value.droneOn
        job?.cancel()
        job = viewModelScope.launch {
            if (!wasOn) {
                graph.audioOutput.drone(droneFor(key, state.value))
                state.update { it.copy(droneOn = true) }
                delay(1_500)
            }
            for (d in degrees) {
                state.update { it.copy(playing = d) }
                graph.audioOutput.play(demo(d, key))
                delay(700)
            }
            state.update { it.copy(playing = null) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        graph.audioOutput.stop()
        state.update { it.copy(playing = null, droneOn = false) }
    }

    /** The degree held over the drone, then its resolution — one soft timbre throughout. */
    private fun demo(degree: Degree, key: Key): Score {
        val floor = MidiNote(55)
        val held = arranger.line(listOf(DegreeNote(degree)), key, Voice.TONE, beatsEach = 3.0, tempoBpm = TEMPO, tonicFloor = floor)
        val path = Harmony.resolution(DegreeNote(degree), key.mode)
        return if (path.isEmpty()) held else held.then(arranger.line(path, key, Voice.TONE, beatsEach = 0.75, tempoBpm = TEMPO, tonicFloor = floor), gapBeats = 0.25)
    }

    override fun onCleared() = stop()

    companion object {
        const val TEMPO = 96.0
    }
}

@Composable
fun FeelScreen(onBack: () -> Unit, onTir: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: FeelViewModel = viewModel { FeelViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val notation = ui.settings.degreeNotation
    val key = ui.key

    ScreenFrame(title = "Вайб ступеней", subtitle = "как звучит каждая ступень рядом с домом", accent = Accents.Tir, onBack = onBack) {
        item(key = "how") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Tir) {
                Text("Ступень — это не высота, а ощущение", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Гудит дрон — это «дом», тоника. Нажми карточку: ступень зазвучит поверх дома, подольше, чтобы ты успел " +
                        "почувствовать её цвет, а потом пройдёт путь домой. Узнавай не «выше или ниже прошлой», а характер: свет, тяжесть, напряжение.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Juicy.TextDim,
                )
                Spacer(Modifier.height(6.dp))
                Text("🎤 Подпевай ступень и путь домой — голос запоминает лучше ушей.", style = MaterialTheme.typography.bodyMedium, color = Juicy.Gold)
            }
        }
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceRow(FeelScope.entries, ui.scope, { it.ru }, vm::setScope, colors = Accents.Tir)
                SectionLabel("под ступенью")
                ChoiceRow(
                    FEEL_CONTEXTS,
                    ui.context,
                    { step -> if (step == FEEL_DRONE) "дрон" else DegreeFormatter.chord(Harmony.diatonicChord(ui.scope.mode, step), notation) },
                    vm::setContext,
                    colors = Accents.Tir,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (key != null) DegreeChip("🔑 " + RuNames.key(key), Juicy.Gold, large = true)
                    Spacer(Modifier.weight(1f))
                    GhostButton("другая", onClick = vm::newKey, colors = Accents.Tir, leading = "🔀")
                }
            }
        }
        ui.scope.degrees().chunked(2).forEach { pair ->
            item(key = "row-" + pair.joinToString { it.toString() }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { d ->
                        VibeCard(
                            d, playing = ui.playing == d, notation = notation, mode = ui.scope.mode, over = ui.chord,
                            anchor = ui.anchors[d.semitones],
                            onAnchor = { a -> if (a != null) vm.playAnchor(a) else vm.editAnchor(d) },
                            onEditAnchor = { vm.editAnchor(d) },
                            modifier = Modifier.weight(1f),
                        ) { vm.play(d) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ui.playing != null || ui.droneOn) {
                    GhostButton("Стоп", onClick = vm::stop, modifier = Modifier.fillMaxWidth(), colors = Accents.Tir, leading = "⏹")
                }
                if (ui.playing == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("Все по кругу", onClick = vm::playAll, modifier = Modifier.weight(1f), colors = Accents.Tir, leading = "🔁")
                        if (!ui.droneOn) GhostButton("Только дрон", onClick = vm::droneOnly, modifier = Modifier.weight(1f), colors = Accents.Tir, leading = "〰️")
                    }
                }
                JuicyButton("Проверить себя в Тире", onClick = onTir, modifier = Modifier.fillMaxWidth(), colors = Accents.Tir, leading = "🎯")
            }
        }
    }
    ui.editing?.let { d ->
        AnchorEditor(
            degree = d,
            ui = ui,
            hasAnchor = ui.anchors.containsKey(d.semitones),
            onTitle = vm::setEditTitle,
            onMelody = vm::setEditMelody,
            onPreview = vm::previewAnchor,
            onSave = vm::saveAnchor,
            onDelete = vm::deleteAnchor,
            onCancel = vm::cancelEdit,
        )
    }
}

/**
 * O-09: «якорь» — the start of a song YOU know that leans on this degree, typed in degrees. Shown
 * after a Tir mistake and in the round summary as a hint; rule 7 — nothing like it ships built in.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AnchorEditor(
    degree: Degree,
    ui: FeelUi,
    hasAnchor: Boolean,
    onTitle: (String) -> Unit,
    onMelody: (String) -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val name = DegreeFormatter.degree(degree, ui.settings.degreeNotation)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("⚓ Якорь для $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Начало песни, которую ты знаешь, где мелодия опирается на $name. Запиши ступенями через пробел: " +
                        "«5 5 6 5 1' 7» (' — октава выше, , — ниже).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Juicy.TextDim,
                )
                OutlinedTextField(ui.editTitle, onTitle, label = { Text("Песня") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (ui.libraryTitles.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ui.libraryTitles.forEach { t ->
                            Text(
                                t,
                                style = MaterialTheme.typography.labelLarge,
                                color = Juicy.Text,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Juicy.Ink3)
                                    .clickable { onTitle(t) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                OutlinedTextField(ui.editMelody, onMelody, label = { Text("Мелодия ступенями") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ui.editError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Juicy.Hot) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Послушать", onClick = onPreview, colors = Accents.Tir, leading = "▶")
                    if (hasAnchor) GhostButton("Удалить", onClick = onDelete, colors = Accents.Calm, leading = "🗑")
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } },
    )
}

@Composable
private fun VibeCard(
    degree: Degree,
    playing: Boolean,
    notation: com.kazaeza.muzykalka.music.DegreeNotation,
    mode: Mode,
    over: ChordDegree?,
    anchor: DegreeAnchor?,
    onAnchor: (DegreeAnchor?) -> Unit,
    onEditAnchor: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = DegreeColors.of(degree)
    val vibe = DegreeVibes.of(degree)
    val glow by animateFloatAsState(if (playing) 1f else 0f, label = "vibe")
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .graphicsLayer {
                scaleX = 1f + 0.04f * glow
                scaleY = 1f + 0.04f * glow
            }
            .heightIn(min = 150.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.18f + 0.3f * glow), Juicy.Ink2.copy(alpha = 0.9f))))
            .border(BorderStroke((1.5f + 1.5f * glow).dp, color.copy(alpha = 0.6f + 0.4f * glow)), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(DegreeFormatter.degree(degree, notation), style = MaterialTheme.typography.displaySmall, color = color)
            Spacer(Modifier.width(8.dp))
            Text(if (playing) "🔊" else "▶", style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim)
        }
        Text(vibe.word, style = MaterialTheme.typography.titleLarge, color = Juicy.Text)
        Spacer(Modifier.height(4.dp))
        Text(vibe.line, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
        if (over != null) {
            Spacer(Modifier.height(4.dp))
            Text(DegreeVibes.overChord(degree, over, mode, notation), style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
        }
        Spacer(Modifier.height(6.dp))
        SectionLabel(DegreeVibes.pathHome(degree, mode, notation), color = color.copy(alpha = 0.9f))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (anchor != null) "⚓ ${anchor.title}" else "⚓ + якорь",
                style = MaterialTheme.typography.bodyMedium,
                color = if (anchor != null) Juicy.Text else Juicy.TextFaint,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = if (anchor != null) "Послушать якорь" else "Добавить якорь") { onAnchor(anchor) }
                    .padding(vertical = 6.dp),
            )
            if (anchor != null) {
                Text(
                    "✎",
                    style = MaterialTheme.typography.titleMedium,
                    color = Juicy.TextDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClickLabel = "Изменить якорь", onClick = onEditAnchor)
                        .padding(6.dp),
                )
            }
        }
    }
}
