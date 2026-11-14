package com.kazaeza.muzykalka.feature.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordVibes
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Dyads
import com.kazaeza.muzykalka.music.LabChord
import com.kazaeza.muzykalka.music.LabFifth
import com.kazaeza.muzykalka.music.LabSound
import com.kazaeza.muzykalka.music.LabThird
import com.kazaeza.muzykalka.music.LabTop
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.Overtones
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.ScoreNote
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.ui.component.ChoiceRow
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.DegreeRing
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.KeyHighlight
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.orb.OrbAnswer
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

enum class LabRoom(val ru: String) { INTERVAL("Интервал"), CHORD("Аккорд") }

data class LabUi(
    val room: LabRoom = LabRoom.INTERVAL,
    /** The interval picked on the dial, as the degree above the lower sound; null — nothing yet. */
    val picked: Degree? = null,
    val chord: LabChord = LabChord(),
    val sounding: Boolean = false,
    /** The chord the built one pulls to is sounding instead of it. */
    val pulled: Boolean = false,
    val notation: DegreeNotation = DegreeNotation.ARABIC,
) {
    /** Semitones of the picked interval: the «1» of the dial is the octave — a unison is one sound, nothing to hear. */
    val semitones: Int? get() = picked?.let { if (it.semitones == 0) Dyads.OCTAVE else it.semitones }
}

/**
 * O-48 «Созвучия» (ADR-047): the body of an interval and of a chord, learnt by hand. A sound is held (the drone bank),
 * one thing is changed, and the change is heard. There is no key and no score: the root is a pitch of chance that is
 * never named (rule 1), the tones are degrees above it.
 */
class HarmonyLabViewModel(private val graph: AppGraph) : ViewModel() {
    private val random = Random(System.nanoTime())
    private val state = MutableStateFlow(LabUi())
    val ui: StateFlow<LabUi> = state.asStateFlow()
    private var root = newRootNote()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val settings = graph.settings.settings.first()
            graph.audioOutput.a4Hz = settings.a4Hz
            state.update { it.copy(notation = settings.degreeNotation) }
        }
    }

    private fun newRootNote() = MidiNote(ROOT_FROM + random.nextInt(ROOT_SPAN))

    /** What the state sounds like: the dyad an octave above the root, or the chord over its bass. */
    private fun notes(s: LabUi): List<MidiNote>? = when (s.room) {
        LabRoom.INTERVAL -> s.semitones?.let { LabSound.dyad(root + 12, it) }
        LabRoom.CHORD -> LabSound.chord(root, s.chord.intervals)
    }

    private fun sound() {
        job?.cancel()
        val s = state.value
        val notes = notes(s)
        if (notes == null) {
            graph.audioOutput.drone(null)
            state.update { it.copy(sounding = false, pulled = false) }
        } else {
            graph.audioOutput.drone(notes)
            state.update { it.copy(sounding = true, pulled = false) }
        }
    }

    fun setRoom(room: LabRoom) {
        if (room == state.value.room) return
        stop()
        state.update { it.copy(room = room) }
    }

    /** A degree of the dial: it sounds with the lower sound and is held. */
    fun pick(degree: Degree) {
        state.update { it.copy(picked = degree) }
        sound()
    }

    fun setThird(third: LabThird) = change { it.copy(third = third) }

    fun setFifth(fifth: LabFifth) = change { it.copy(fifth = fifth) }

    fun setTop(top: LabTop) = change { it.copy(top = top) }

    private fun change(f: (LabChord) -> LabChord) {
        state.update { it.copy(chord = f(it.chord)) }
        sound()
    }

    /** «Играть» / «Стоп». */
    fun toggle() = if (state.value.sounding) stop() else sound()

    /** Another pitch for the same thing: the body of an interval does not depend on where it stands. */
    fun newRoot() {
        root = newRootNote()
        if (state.value.sounding) sound()
    }

    /** The two sounds of the interval one after the other, then together again. */
    fun apart() {
        val semitones = state.value.semitones ?: return
        val pair = LabSound.dyad(root + 12, semitones)
        job?.cancel()
        graph.audioOutput.drone(null)
        state.update { it.copy(sounding = true, pulled = false) }
        job = viewModelScope.launch {
            delay(APART_GAP_MS)
            graph.audioOutput.play(Score(APART_TEMPO, pair.mapIndexed { i, n -> ScoreNote(i * 1.5, 1.4, Voice.TONE, n, 0.8f) }))
            delay(APART_GAP_MS)
            graph.audioOutput.drone(pair)
        }
    }

    /** Where the chord pulls: its target sounds for a while, then the chord comes back — to hear the pull again. */
    fun pull() {
        val s = state.value
        val target = s.chord.pull.target ?: return
        job?.cancel()
        job = viewModelScope.launch {
            if (!s.sounding) {
                graph.audioOutput.drone(LabSound.chord(root, s.chord.intervals))
                state.update { it.copy(sounding = true) }
                delay(PULL_HOLD_MS)
            }
            graph.audioOutput.drone(LabSound.chord(root, target))
            state.update { it.copy(pulled = true) }
            delay(PULL_HOLD_MS)
            graph.audioOutput.drone(LabSound.chord(root, s.chord.intervals))
            state.update { it.copy(pulled = false) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        graph.audioOutput.stop()
        state.update { it.copy(sounding = false, pulled = false) }
    }

    override fun onCleared() = stop()

    companion object {
        /** The root is a pitch between A2 and G♯3: the bass of a chord, and an octave below the interval's lower sound. */
        const val ROOT_FROM = 45
        const val ROOT_SPAN = 12
        const val PULL_HOLD_MS = 2_400L
        const val APART_GAP_MS = 350L
        const val APART_TEMPO = 80.0
    }
}

/** The dial of the interval room: a fixed height, so the lazy list knows its size. */
private val RING_HEIGHT = 340.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HarmonyLabScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: HarmonyLabViewModel = viewModel { HarmonyLabViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val fmt = { d: Degree -> DegreeFormatter.degree(d, ui.notation) }

    ScreenFrame(title = "Созвучия", subtitle = "держи звук, меняй одну ноту, слушай", accent = Accents.Tir, onBack = onBack) {
        item(key = "rooms") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceRow(LabRoom.entries, ui.room, { it.ru }, vm::setRoom, colors = Accents.Tir)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(if (ui.sounding) "Стоп" else "Играть", onClick = vm::toggle, modifier = Modifier.weight(1f), colors = Accents.Tir, leading = if (ui.sounding) "■" else "►")
                    GhostButton("другая высота", onClick = vm::newRoot, modifier = Modifier.weight(1f), colors = Accents.Tir, leading = "⇄")
                }
            }
        }
        if (ui.room == LabRoom.INTERVAL) {
            val picked = ui.picked
            val semitones = ui.semitones
            item(key = "ring") {
                Box(Modifier.fillMaxWidth().height(RING_HEIGHT), contentAlignment = Alignment.Center) {
                    DegreeRing(
                        degrees = Degree.CHROMATIC,
                        notation = ui.notation,
                        onDegree = vm::pick,
                        modifier = Modifier.fillMaxWidth().height(RING_HEIGHT),
                        highlight = { d -> if (picked != null && d sameSoundAs picked) KeyHighlight.CORRECT else KeyHighlight.NONE },
                        centre = picked,
                        energy = if (ui.sounding) 0.85f else 0.4f,
                        // the sphere draws the ratio of the two sounds: its numerator in lobes, the restlessness in tension
                        lobes = if (picked != null && ui.sounding) Overtones.ratio(picked.semitones).first else 0,
                        tension = picked?.let { Overtones.tension(it.semitones) } ?: 0f,
                        answer = picked?.takeIf { ui.sounding }?.let { OrbAnswer(Math.floorMod(it.semitones, 12), DegreeColors.of(it)) },
                    )
                }
            }
            item(key = "dyad") {
                if (picked == null || semitones == null) {
                    Text(
                        "Нижний звук — опора. Нажми ступень на круге: она зазвучит вместе с ним и будет держаться. Иди по кругу и слушай, как гладкость сменяется шершавостью.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Juicy.TextDim,
                    )
                } else {
                    val vibe = Dyads.of(semitones)
                    val color = DegreeColors.of(picked)
                    GlassCard(Modifier.fillMaxWidth(), accent = DegreeColors.gradient(color)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(if (semitones == Dyads.OCTAVE) "октава" else RuNames.interval(picked), style = MaterialTheme.typography.headlineSmall, color = Juicy.Text, modifier = Modifier.weight(1f))
                            Text("${vibe.ratio.first} : ${vibe.ratio.second}", style = MaterialTheme.typography.headlineSmall, color = color)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(vibe.word, style = MaterialTheme.typography.titleLarge, color = color)
                        Text(vibe.line, style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
                        Spacer(Modifier.height(10.dp))
                        RoughBar(vibe.rough)
                        Spacer(Modifier.height(8.dp))
                        Text(vibe.meeting, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
                    }
                }
            }
            if (picked != null) {
                item(key = "apart") {
                    GhostButton("По очереди, потом вместе", onClick = vm::apart, modifier = Modifier.fillMaxWidth(), colors = Accents.Tir, leading = "")
                }
            }
        } else {
            val chord = ui.chord
            item(key = "chord") {
                GlassCard(Modifier.fillMaxWidth(), accent = Accents.Tir) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chord.tones.forEach { d -> DegreeChip(fmt(d), DegreeColors.of(d), large = true) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        chord.quality?.let { ChordVibes.of(ChordDegree(Degree.ONE, it), Mode.MAJOR).quality } ?: "У такого сочетания нет имени — слушай сами звуки.",
                        style = MaterialTheme.typography.titleLarge,
                        color = Juicy.Text,
                    )
                    chord.tritone?.let { (a, b) ->
                        Spacer(Modifier.height(4.dp))
                        Text("Внутри тритон: ${fmt(a)} и ${fmt(b)} — отсюда беспокойство.", style = MaterialTheme.typography.bodyMedium, color = Juicy.Gold)
                    }
                }
            }
            item(key = "switches") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("терция — цвет")
                    ChoiceRow(LabThird.entries, chord.third, { it.degree?.let(fmt) ?: "нет" }, vm::setThird, colors = Accents.Tir)
                    SectionLabel("квинта — каркас")
                    ChoiceRow(LabFifth.entries, chord.fifth, { fmt(it.degree) }, vm::setFifth, colors = Accents.Tir)
                    SectionLabel("верх — напряжение")
                    ChoiceRow(LabTop.entries, chord.top, { it.degree?.let(fmt) ?: "нет" }, vm::setTop, colors = Accents.Tir)
                }
            }
            item(key = "pull") {
                val pull = chord.pull
                GlassCard(Modifier.fillMaxWidth(), accent = if (pull.target != null) Accents.Taper else null) {
                    SectionLabel("куда тянет", color = Juicy.TextDim)
                    Spacer(Modifier.height(4.dp))
                    Text(pull.line, style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
                    if (pull.target != null) {
                        Spacer(Modifier.height(10.dp))
                        GhostButton(if (ui.pulled) "…и обратно" else "Послушать, куда тянет", onClick = vm::pull, modifier = Modifier.fillMaxWidth(), colors = Accents.Taper, leading = "→")
                    }
                }
            }
        }
        item(key = "why") {
            Text(
                "Здесь нет очков. У созвучия есть тело — как оно звучит само по себе — и роль в ладу. Тело узнаётся руками: держи, меняй, слушай. Роль тренируют Тир и «Над аккордами».",
                style = MaterialTheme.typography.bodySmall,
                color = Juicy.TextFaint,
            )
        }
    }
}

/** How restless the interval is: a bar from «гладко» to «шершаво». */
@Composable
private fun RoughBar(rough: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("гладко", style = MaterialTheme.typography.labelLarge, color = Juicy.TextFaint)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Juicy.Ink4)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(rough.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(3.dp)).background(Juicy.Gold))
        }
        Spacer(Modifier.width(8.dp))
        Text("шершаво", style = MaterialTheme.typography.labelLarge, color = Juicy.TextFaint)
    }
}
