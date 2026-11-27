package com.kazaeza.muzykalka.feature.lab

import com.kazaeza.muzykalka.ui.theme.Doto
import com.kazaeza.muzykalka.ui.effect.HarmonicLadder
import com.kazaeza.muzykalka.ui.component.RingFigure
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
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
import com.kazaeza.muzykalka.feature.common.IconBubble
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordVibes
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Dyads
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.LabChord
import com.kazaeza.muzykalka.music.LabPresets
import com.kazaeza.muzykalka.music.LabFifth
import com.kazaeza.muzykalka.music.LabSound
import com.kazaeza.muzykalka.music.LabThird
import com.kazaeza.muzykalka.music.LabTop
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.ScoreNote
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.ui.component.AppIcon
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
import com.kazaeza.muzykalka.feature.common.HintStyleSwitch
import com.kazaeza.muzykalka.feature.common.SongHintLine
import com.kazaeza.muzykalka.music.HintStyle
import com.kazaeza.muzykalka.music.KeyCatalog
import com.kazaeza.muzykalka.music.PitchClass
import com.kazaeza.muzykalka.music.SongHint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class LabRoom(val ru: String) { INTERVAL("Интервал"), CHORD("Аккорд"), KEY("В ладу") }

data class LabUi(
    val room: LabRoom = LabRoom.INTERVAL,
    /** The interval picked on the dial, as the degree above the lower sound; null — nothing yet. */
    val picked: Degree? = null,
    val chord: LabChord = LabChord(),
    /** O-49 «В ладу»: the mode of the key and the step (0 = I) whose chord is held over the tonic. */
    val mode: Mode = Mode.MAJOR,
    val step: Int = 0,
    val sounding: Boolean = false,
    /** The chord the built one pulls to is sounding instead of it. */
    val pulled: Boolean = false,
    val notation: DegreeNotation = DegreeNotation.ARABIC,
    /** O-57: the interval's card names a song that has it, or only describes how it feels. */
    val hintStyle: HintStyle = HintStyle.FEEL,
    /**
     * O-60 «Собери на слух»: a chord that hums without a name, to be built by ear on the tracks. The lab judges nothing
     * (the engine does that): [puzzleOpen] shows what it was, and the two are compared by eye and ear.
     */
    val puzzle: LabChord? = null,
    val puzzleOpen: Boolean = false,
    /** What hums now is the puzzle, not the chord on the tracks. */
    val puzzleHeard: Boolean = false,
    val puzzleRound: Int = 0,
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
            state.update { it.copy(notation = settings.degreeNotation, hintStyle = settings.hintStyle) }
        }
    }

    private fun newRootNote() = MidiNote(ROOT_FROM + random.nextInt(ROOT_SPAN))

    /** What the state sounds like: the dyad an octave above the root, or the chord over its bass. */
    private fun notes(s: LabUi): List<MidiNote>? = when (s.room) {
        LabRoom.INTERVAL -> s.semitones?.let { LabSound.dyad(root + 12, it) }
        LabRoom.CHORD -> LabSound.chord(root, s.chord.intervals)
        LabRoom.KEY -> LabSound.inKey(root, Harmony.diatonicChord(s.mode, s.step))
    }

    private fun sound() {
        job?.cancel()
        val s = state.value
        val notes = notes(s)
        if (notes == null) {
            graph.audioOutput.drone(null)
            state.update { it.copy(sounding = false, pulled = false, puzzleHeard = false) }
        } else {
            graph.audioOutput.drone(notes)
            state.update { it.copy(sounding = true, pulled = false, puzzleHeard = false) }
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

    /** O-57: the switch of the hints, kept for every mode. */
    fun setHintStyle(style: HintStyle) {
        state.update { it.copy(hintStyle = style) }
        viewModelScope.launch { graph.settings.update { it.copy(hintStyle = style) } }
    }

    /** O-57: the tune that has the picked interval, over a hum of the lower sound; the interval comes back after it. */
    fun playSongHint(song: SongHint) {
        job?.cancel()
        val key = KeyCatalog.practical(PitchClass.of(root.value), Mode.MAJOR)
        graph.audioOutput.drone(listOf(root))
        state.update { it.copy(sounding = true, pulled = false) }
        job = viewModelScope.launch {
            delay(SONG_SETTLE_MS)
            graph.audioOutput.play(song.score(key))
            delay(SONG_SETTLE_MS)
            sound()
        }
    }

    /** O-60: a named chord in one press: all three voices go where it has them. */
    fun setChord(chord: LabChord) = change { chord }

    /** O-60 «Собери на слух»: an unnamed chord hums; the user builds the same one on the tracks. */
    fun newPuzzle() {
        val s = state.value
        val pool = LabPresets.puzzlePool(s.puzzleRound / PUZZLE_STEP + 1).filter { it != s.puzzle }
        val target = pool[random.nextInt(pool.size)]
        state.update { it.copy(puzzle = target, puzzleOpen = false, puzzleRound = it.puzzleRound + 1) }
        playPuzzle()
    }

    /** The puzzle again (the chord on the tracks has its own «►»). */
    fun playPuzzle() {
        val p = state.value.puzzle ?: return
        job?.cancel()
        graph.audioOutput.drone(LabSound.chord(root, p.intervals))
        state.update { it.copy(sounding = true, pulled = false, puzzleHeard = true) }
    }

    /** What it was: named and shown, and heard once more. */
    fun openPuzzle() {
        state.update { it.copy(puzzleOpen = true) }
        playPuzzle()
    }

    fun closePuzzle() {
        state.update { it.copy(puzzle = null, puzzleOpen = false, puzzleHeard = false) }
        if (state.value.sounding) sound()
    }

    fun setThird(third: LabThird) = change { it.copy(third = third) }

    fun setFifth(fifth: LabFifth) = change { it.copy(fifth = fifth) }

    fun setTop(top: LabTop) = change { it.copy(top = top) }

    private fun change(f: (LabChord) -> LabChord) {
        state.update { it.copy(chord = f(it.chord)) }
        sound()
    }

    /** O-49: the chord on another step of the key — the home tone stays, the chord moves. */
    fun setStep(step: Int) {
        state.update { it.copy(step = step) }
        sound()
    }

    fun setMode(mode: Mode) {
        state.update { it.copy(mode = mode) }
        if (state.value.sounding) sound()
    }

    /**
     * O-52: the chord is built on the dial itself. An hour in the third's band (2, ♭3, 3, 4), the fifth's (♭5, 5, ♯5)
     * or the top voice's (6, ♭7, 7) puts that voice there; the same hour again takes the voice away — except the
     * fifth, which a chord of the lab always has. The «1» is the root: it only starts and stops the sound.
     */
    fun tapChordTone(degree: Degree) {
        val at = Math.floorMod(degree.semitones, 12)
        when (at) {
            0 -> toggle()
            in 2..5 -> LabThird.entries.first { it.degree?.semitones == at }.let { v -> change { it.copy(third = if (it.third == v) LabThird.NONE else v) } }
            in 6..8 -> change { it.copy(fifth = LabFifth.entries.first { f -> f.degree.semitones == at }) }
            in 9..11 -> LabTop.entries.first { it.degree?.semitones == at }.let { v -> change { it.copy(top = if (it.top == v) LabTop.NONE else v) } }
        }
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
        state.update { it.copy(sounding = false, pulled = false, puzzleHeard = false) }
    }

    override fun onCleared() = stop()

    companion object {
        /** The root is a pitch between A2 and G♯3: the bass of a chord, and an octave below the interval's lower sound. */
        /** O-60: every this many puzzles the pool of chords grows by a step. */
        const val PUZZLE_STEP = 3

        /** O-57: the hum of the lower sound before the tune, and the breath after it. */
        const val SONG_SETTLE_MS = 900L

        const val ROOT_FROM = 45
        const val ROOT_SPAN = 12
        const val PULL_HOLD_MS = 2_400L
        const val APART_GAP_MS = 350L
        const val APART_TEMPO = 80.0
    }
}

/** The dial of every room: a fixed height, so the lazy list knows its size. */
private val RING_HEIGHT = 340.dp

/** The hours a chord of the lab can stand on: the root, the three bands of voices — and no ♭2, which is no voice of it. */
private val CHORD_DIAL: List<Degree> =
    listOf(Degree.ONE) + LabThird.entries.mapNotNull { it.degree } + LabFifth.entries.map { it.degree } + LabTop.entries.mapNotNull { it.degree }

private fun hourOf(semitones: Int) = Math.floorMod(semitones, 12)

/**
 * O-48, redrawn in O-52: every room is the same dial, and what is held is a figure on it — an interval is a chord of
 * the circle, a chord a polygon, the same chord on another degree the same polygon turned. The sound is changed by
 * touching the dial; the cards under it only say what the picture shows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HarmonyLabScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: HarmonyLabViewModel = viewModel { HarmonyLabViewModel(graph) }

    // O-66: the app leaves the screen (another app, the lock) — the sound stops, it does not hum on in the pocket
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { vm.stop() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val fmt = { d: Degree -> DegreeFormatter.degree(d, ui.notation) }
    val home = DegreeColors.of(Degree.ONE)

    ScreenFrame(title = "Созвучия", subtitle = "держи звук, меняй одну ноту, слушай", accent = Accents.Tir, onBack = onBack) {
        item(key = "rooms") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceRow(LabRoom.entries, ui.room, { it.ru }, vm::setRoom, modifier = Modifier.weight(1f), colors = Accents.Tir)
                IconBubble(
                    icon = if (ui.sounding) AppIcon.STOP else AppIcon.PLAY,
                    onClick = vm::toggle,
                    description = if (ui.sounding) "Стоп" else "Играть",
                )
                IconBubble(
                    icon = AppIcon.REPLAY,
                    onClick = vm::newRoot,
                    description = "Другая высота",
                )
            }
        }
        when (ui.room) {
            LabRoom.INTERVAL -> {
                val picked = ui.picked
                val semitones = ui.semitones
                item(key = "ring") {
                    DegreeRing(
                        degrees = Degree.CHROMATIC,
                        notation = ui.notation,
                        onDegree = vm::pick,
                        modifier = Modifier.fillMaxWidth().height(RING_HEIGHT),
                        highlight = { d -> if (picked != null && (d sameSoundAs picked || d.semitones == 0)) KeyHighlight.CORRECT else KeyHighlight.NONE },
                        centre = picked,
                        energy = if (ui.sounding) 0.85f else 0.4f,
                        answer = picked?.takeIf { ui.sounding }?.let { OrbAnswer(hourOf(it.semitones), DegreeColors.of(it)) },
                        // the interval is a chord of the circle: from the lower sound at the top to the picked hour
                        figure = picked?.takeIf { it.semitones != 0 }?.let { RingFigure(listOf(0, hourOf(it.semitones)), listOf(home, DegreeColors.of(it)), closed = false) },
                    )
                }
                item(key = "hints") { HintStyleSwitch(ui.hintStyle, vm::setHintStyle) }
                item(key = "dyad") {
                    if (picked == null || semitones == null) {
                        Text(
                            "Наверху — нижний звук, опора. Нажми любую ступень на круге: она зазвучит вместе с ним и будет держаться. Иди по кругу и слушай, как гладкость сменяется шершавостью.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Juicy.TextDim,
                        )
                    } else {
                        val vibe = Dyads.of(semitones)
                        val color = DegreeColors.of(picked)
                        GlassCard(Modifier.fillMaxWidth(), accent = DegreeColors.gradient(color)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Column(Modifier.weight(1f)) {
                                    SectionLabel(if (semitones == Dyads.OCTAVE) "октава" else RuNames.interval(picked), color = Juicy.TextDim)
                                    Text(vibe.word, style = MaterialTheme.typography.headlineSmall, color = color)
                                }
                                Text("${vibe.ratio.first} : ${vibe.ratio.second}", style = MaterialTheme.typography.displaySmall.copy(fontFamily = Doto), color = color)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(vibe.line, style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
                            // O-57: the interval hung on a tune everyone knows (the first one is from the tonic up, as the dial has it)
                            val song = if (ui.hintStyle == HintStyle.SONGS) SongHint.forSemitone(picked.semitones).firstOrNull() else null
                            if (song != null) {
                                Spacer(Modifier.height(10.dp))
                                SongHintLine(picked, song, ui.notation, onPlay = { vm.playSongHint(song) })
                            }
                            Spacer(Modifier.height(12.dp))
                            // the two sounds as two combs of harmonics: where the teeth meet it is smooth, where they miss it rubs
                            HarmonicLadder(vibe.ratio, vibe.meetings(), vibe.rubs(), lower = home, upper = color, modifier = Modifier.fillMaxWidth().height(86.dp))
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Legend(Color.White, "совпали — ${vibe.meetings().size}")
                                Legend(Juicy.Bad, if (vibe.rubs().isEmpty()) "ничего не трётся" else "трутся — ${vibe.rubs().size}")
                            }
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
            }
            LabRoom.CHORD -> {
                val chord = ui.chord
                // while the pull is sounding the figure is the chord it went to: the voices are seen to move
                val shown = if (ui.pulled) chord.pull.target.orEmpty().map { Degree.canonical(it) } else chord.tones
                item(key = "ring") {
                    DegreeRing(
                        degrees = CHORD_DIAL,
                        notation = ui.notation,
                        onDegree = vm::tapChordTone,
                        modifier = Modifier.fillMaxWidth().height(RING_HEIGHT),
                        highlight = { d -> if (chord.tones.any { it sameSoundAs d }) KeyHighlight.CORRECT else KeyHighlight.NONE },
                        energy = if (ui.sounding) 0.85f else 0.4f,
                        figure = RingFigure(shown.map { hourOf(it.semitones) }, shown.map { DegreeColors.of(it) }, home = 0.takeIf { ui.pulled }),
                    )
                }
                item(key = "presets") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("готовые аккорды", color = Juicy.TextDim)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabPresets.ALL.forEach { p ->
                                DegreeChip(p.ru, Juicy.Cyan, selected = chord == p.chord, onClick = { vm.setChord(p.chord) }, onClickLabel = "Собрать «${p.ru}»")
                            }
                        }
                    }
                }
                item(key = "tracks") {
                    ChordTracks(chord, fmt, vm::setThird, vm::setFifth, vm::setTop)
                }
                item(key = "puzzle") {
                    PuzzleCard(ui, fmt, onNew = vm::newPuzzle, onPlay = vm::playPuzzle, onOpen = vm::openPuzzle, onClose = vm::closePuzzle)
                }
                item(key = "chord") {
                    GlassCard(Modifier.fillMaxWidth(), accent = Accents.Tir) {
                        // the chord as a stack: its tones, and between them the steps it is built of
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            chord.tones.forEachIndexed { i, d ->
                                if (i > 0) Text(RuNames.intervalShort(chord.stack[i - 1]), style = MaterialTheme.typography.labelLarge, color = Juicy.TextDim)
                                DegreeChip(fmt(d), DegreeColors.of(d), large = true)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            chord.quality?.let { ChordVibes.of(ChordDegree(Degree.ONE, it), Mode.MAJOR).quality } ?: "У такого сочетания нет имени — слушай сами звуки.",
                            style = MaterialTheme.typography.titleLarge,
                            color = Juicy.Text,
                        )
                        chord.tritone?.let { (a, b) ->
                            Spacer(Modifier.height(4.dp))
                            Text("Внутри тритон: ${fmt(a)} и ${fmt(b)} — на круге они друг против друга. Отсюда беспокойство.", style = MaterialTheme.typography.bodyMedium, color = Juicy.Gold)
                        }
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
                            GhostButton(
                                if (ui.pulled) "…и обратно" else "Послушать, куда тянет",
                                onClick = vm::pull,
                                modifier = Modifier.fillMaxWidth(),
                                colors = Accents.Taper,
                                leadingIcon = AppIcon.CHEVRON_RIGHT,
                            )
                        }
                    }
                }
            }
            LabRoom.KEY -> {
                val chord = Harmony.diatonicChord(ui.mode, ui.step)
                val vibe = ChordVibes.of(chord, ui.mode)
                val name = { step: Int -> DegreeFormatter.chord(Harmony.diatonicChord(ui.mode, step), ui.notation) }
                val tones = chord.quality.intervals.take(3).map { ui.mode.diatonicSpelling(chord.root.semitones + it) ?: Degree.canonical(chord.root.semitones + it) }
                item(key = "mode") { ChoiceRow(listOf(Mode.MAJOR, Mode.MINOR), ui.mode, { it.ruName }, vm::setMode, colors = Accents.Tir) }
                item(key = "ring") {
                    DegreeRing(
                        degrees = ui.mode.degrees,
                        notation = ui.notation,
                        onDegree = { d -> vm.setStep(ui.mode.indexOf(d).coerceAtLeast(0)) },
                        modifier = Modifier.fillMaxWidth().height(RING_HEIGHT),
                        highlight = { d -> if (ui.sounding && tones.any { it sameSoundAs d }) KeyHighlight.CORRECT else KeyHighlight.NONE },
                        centre = chord.root.takeIf { ui.sounding },
                        energy = if (ui.sounding) 0.85f else 0.4f,
                        answer = chord.root.takeIf { ui.sounding }?.let { OrbAnswer(hourOf(it.semitones), DegreeColors.of(it)) },
                        // the same triangle, turned to another degree; the ring at the top is home, which goes on humming
                        figure = if (ui.sounding) RingFigure(tones.map { hourOf(it.semitones) }, tones.map { DegreeColors.of(it) }, home = 0) else null,
                    )
                }
                item(key = "role") {
                    if (!ui.sounding) {
                        Text(
                            "Гудит тоника — дом. Нажми ступень на круге: на ней встанет аккорд лада. Фигура та же, только повёрнута — а против дома звучит иначе.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Juicy.TextDim,
                        )
                    } else {
                        GlassCard(Modifier.fillMaxWidth(), accent = DegreeColors.gradient(DegreeColors.of(chord.root))) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(name(ui.step), style = MaterialTheme.typography.headlineSmall, color = DegreeColors.of(chord.root))
                                Spacer(Modifier.width(10.dp))
                                Text(vibe.role, style = MaterialTheme.typography.headlineSmall, color = Juicy.Text)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(vibe.line, style = MaterialTheme.typography.bodyLarge, color = Juicy.Text)
                            Spacer(Modifier.height(8.dp))
                            Text(vibe.quality, style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                            val twins = LabSound.sameBody(ui.mode, ui.step)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (twins.isEmpty()) "Такая фигура в этом ладу одна." else "Та же фигура стоит на ${twins.joinToString(" и ") { name(it) }} — нажми и смотри, как она поворачивается: звук тот же, роль другая.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Juicy.Gold,
                            )
                        }
                    }
                }
                if (ui.sounding && ui.step != 0) {
                    item(key = "home") {
                        GhostButton(
                            "Домой — в ${name(0)}",
                            onClick = { vm.setStep(0) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = Accents.Tir,
                            leadingIcon = AppIcon.CHEVRON_RIGHT,
                        )
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

/** A dot of the ladder's colour and what it stands for. */
/**
 * O-60: the chord as three tracks of beads. The «1» is fixed — the root always sounds; the fifth always has one bead,
 * the third and the top voice may have none. What was a hidden nest on the dial is a row of beads that can be seen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChordTracks(chord: LabChord, fmt: (Degree) -> String, onThird: (LabThird) -> Unit, onFifth: (LabFifth) -> Unit, onTop: (LabTop) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent = Accents.Tir) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DegreeChip(fmt(Degree.ONE), DegreeColors.of(Degree.ONE), large = true, selected = true)
            Text("корень — звучит всегда", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
        }
        Spacer(Modifier.height(10.dp))
        Track("терция", "одна из четырёх — или без неё") {
            LabThird.entries.forEach { v ->
                DegreeChip(v.degree?.let(fmt) ?: "нет", v.degree?.let { DegreeColors.of(it) } ?: Juicy.TextDim, selected = chord.third == v, onClick = { onThird(v) }, onClickLabel = "Терция")
            }
        }
        Track("квинта", "всегда одна из трёх") {
            LabFifth.entries.forEach { v ->
                DegreeChip(fmt(v.degree), DegreeColors.of(v.degree), selected = chord.fifth == v, onClick = { onFifth(v) }, onClickLabel = "Квинта")
            }
        }
        Track("верх", "шестая, септима — или без неё") {
            LabTop.entries.forEach { v ->
                DegreeChip(v.degree?.let(fmt) ?: "нет", v.degree?.let { DegreeColors.of(it) } ?: Juicy.TextDim, selected = chord.top == v, onClick = { onTop(v) }, onClickLabel = "Верхний голос")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Track(label: String, hint: String, beads: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(label, color = Juicy.TextDim)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { beads() }
    }
}

/**
 * O-60 «Собери на слух»: the chord hums without a name; build the same on the tracks (its «►» is the one above), then
 * «Открыть». The lab says nothing about right or wrong — the figures are put side by side and the ear compares.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PuzzleCard(ui: LabUi, fmt: (Degree) -> String, onNew: () -> Unit, onPlay: () -> Unit, onOpen: () -> Unit, onClose: () -> Unit) {
    val puzzle = ui.puzzle
    GlassCard(Modifier.fillMaxWidth(), accent = if (puzzle != null) Accents.Taper else null) {
        SectionLabel("собери на слух", color = Juicy.TextDim)
        Spacer(Modifier.height(4.dp))
        if (puzzle == null) {
            Text("Звучит аккорд без имени. Подбери такой же на дорожках, сравни на слух — и открой, что это было.", style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
            Spacer(Modifier.height(10.dp))
            GhostButton("Загадай аккорд", onClick = onNew, modifier = Modifier.fillMaxWidth(), colors = Accents.Taper, leadingIcon = AppIcon.SPARK)
        } else {
            Text(
                if (ui.puzzleOpen) "Это был " + (LabPresets.nameOf(puzzle) ?: "аккорд без имени") else if (ui.puzzleHeard) "Слушаешь загадку…" else "Слушаешь свой — загадка ждёт.",
                style = MaterialTheme.typography.titleMedium,
                color = Juicy.Text,
            )
            if (ui.puzzleOpen) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    puzzle.tones.forEachIndexed { i, d ->
                        if (i > 0) Text(RuNames.intervalShort(puzzle.stack[i - 1]), style = MaterialTheme.typography.labelLarge, color = Juicy.TextDim)
                        DegreeChip(fmt(d), DegreeColors.of(d), large = true)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Сверь со своим на дорожках: где голос стоит иначе?", style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Загадка", onClick = onPlay, colors = Accents.Taper, leadingIcon = AppIcon.PLAY)
                if (!ui.puzzleOpen) GhostButton("Открыть", onClick = onOpen, colors = Accents.Taper, leadingIcon = AppIcon.CHECK)
                GhostButton(if (ui.puzzleOpen) "Новая" else "Другая", onClick = onNew, colors = Accents.Taper, leadingIcon = AppIcon.REPLAY)
                GhostButton("Хватит", onClick = onClose, colors = Accents.Calm, leadingIcon = AppIcon.CLOSE)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Juicy.TextDim)
    }
}
