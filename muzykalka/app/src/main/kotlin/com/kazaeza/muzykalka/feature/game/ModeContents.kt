package com.kazaeza.muzykalka.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.game.Answer
import com.kazaeza.muzykalka.game.HuntDriver
import com.kazaeza.muzykalka.game.HuntKind
import com.kazaeza.muzykalka.game.HuntTask
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.game.StepsTask
import com.kazaeza.muzykalka.game.TirKind
import com.kazaeza.muzykalka.game.TirTask
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.DegreeVibes
import com.kazaeza.muzykalka.music.FretPosition
import com.kazaeza.muzykalka.music.Fretboard
import com.kazaeza.muzykalka.music.NoteName
import com.kazaeza.muzykalka.music.NoteNaming
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.DeadlineBar
import com.kazaeza.muzykalka.ui.component.DegreeKeypad
import com.kazaeza.muzykalka.ui.component.ExtraKey
import com.kazaeza.muzykalka.ui.component.ExtraKeyView
import com.kazaeza.muzykalka.ui.component.GradientText
import com.kazaeza.muzykalka.ui.component.JuicyKey
import com.kazaeza.muzykalka.ui.component.KeyHighlight
import com.kazaeza.muzykalka.ui.component.MicPulse
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.effect.breathe
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.neck.FretMarker
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy

// ---------------------------------------------------------------- Tir

@Composable
internal fun TirContent(task: TirTask, ui: GameUi, accent: List<Color>, onAnswer: (Answer) -> Unit, onReplay: () -> Unit) {
    val enteredNotes = remember(task.itemKey, ui.taskSerial) { mutableStateListOf<Degree>() }
    val enteredChords = remember(task.itemKey, ui.taskSerial) { mutableStateListOf<ChordDegree>() }
    var lastTapDegree by remember(ui.taskSerial) { mutableStateOf<Degree?>(null) }
    var lastTapChord by remember(ui.taskSerial) { mutableStateOf<ChordDegree?>(null) }
    val listening = ui.phase == RoundPhase.LISTENING
    val feedback = ui.phase == RoundPhase.FEEDBACK
    val correct = feedback && ui.feedback?.correct == true

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptCard(ui, accent) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DegreeChip("🔑 " + RuNames.key(task.key), Juicy.Gold)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "🔁",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = listening, onClick = onReplay)
                            .padding(6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                when {
                    ui.phase == RoundPhase.PROMPTING -> {
                        Text("🎧", style = MaterialTheme.typography.displayMedium, modifier = Modifier.breathe(amount = 0.12f, periodMs = 900))
                        Text("Слушай…", style = MaterialTheme.typography.headlineSmall, color = Juicy.TextDim)
                    }
                    feedback -> {
                        val f = ui.feedback
                        if (correct) {
                            GradientText(f?.expected ?: "", MaterialTheme.typography.displayMedium, listOf(Juicy.Good, Juicy.Mint), Modifier.pop(ui.taskSerial, 0.3f))
                            Text("+${f?.points ?: 0}", style = MaterialTheme.typography.headlineMedium, color = Juicy.Gold, modifier = Modifier.pop(ui.taskSerial))
                        } else {
                            Text(f?.given ?: "", style = MaterialTheme.typography.headlineMedium, color = Juicy.Hot)
                            Text("→ " + (f?.expected ?: ""), style = MaterialTheme.typography.displaySmall, color = Juicy.Good, textAlign = TextAlign.Center)
                        }
                        // The feeling to hang the sound on, while its resolution is playing.
                        if (task.kind == TirKind.NOTE) {
                            val d = task.notes.single().degree
                            val vibe = DegreeVibes.of(d)
                            Spacer(Modifier.height(6.dp))
                            Text(vibe.word, style = MaterialTheme.typography.headlineSmall, color = DegreeColors.of(d))
                            Text(DegreeVibes.pathHome(d, task.key.mode, ui.notation), style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                        }
                    }
                    else -> {
                        GradientText("?", MaterialTheme.typography.displayLarge, accent, Modifier.breathe())
                        Text(
                            when (task.kind) {
                                TirKind.NOTE -> "Какая ступень?"
                                TirKind.MELODY -> "Мелодия · ${task.notes.size} нот"
                                TirKind.CHORD -> "Какой аккорд?"
                                TirKind.PROGRESSION -> "Ход: аккорды 2–4"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = Juicy.TextDim,
                        )
                    }
                }
            }
        }

        if (task.answerLength > 1) {
            AnswerSlots(
                count = task.answerLength,
                labels = if (task.kind == TirKind.MELODY) enteredNotes.map { DegreeFormatter.degree(it, ui.notation) } else enteredChords.map { DegreeFormatter.chord(it, ui.notation) },
                colors = if (task.kind == TirKind.MELODY) enteredNotes.map { DegreeColors.of(it) } else enteredChords.map { DegreeColors.of(it.root) },
                prefix = if (task.kind == TirKind.PROGRESSION) DegreeFormatter.chord(task.chords[0], ui.notation) else null,
                onUndo = {
                    if (task.kind == TirKind.MELODY) enteredNotes.removeLastOrNull() else enteredChords.removeLastOrNull()
                },
            )
        }

        Spacer(Modifier.weight(1f))

        val dontKnow = ExtraKey("🤷", "не знаю") { onAnswer(Answer.Pass) }
        when (task.kind) {
            TirKind.NOTE, TirKind.MELODY -> {
                val expected = task.notes.lastOrNull()?.degree
                DegreeKeypad(
                    degrees = task.noteChoices,
                    notation = ui.notation,
                    enabled = listening,
                    columns = 4,
                    keyHeight = if (task.noteChoices.size > 8) 64.dp else 76.dp,
                    extraKey = dontKnow,
                    highlight = { d ->
                        when {
                            !feedback || task.kind == TirKind.MELODY -> KeyHighlight.NONE
                            expected != null && d sameSoundAs expected -> KeyHighlight.CORRECT
                            !correct && lastTapDegree == d -> KeyHighlight.WRONG
                            else -> KeyHighlight.DIM
                        }
                    },
                    onDegree = { d ->
                        lastTapDegree = d
                        if (task.kind == TirKind.NOTE) {
                            onAnswer(Answer.DegreeChoice(d))
                        } else {
                            enteredNotes += d
                            if (enteredNotes.size == task.answerLength) onAnswer(Answer.Formula(enteredNotes.map { DegreeNote(it) }))
                        }
                    },
                )
            }
            TirKind.CHORD, TirKind.PROGRESSION -> {
                ChordPad(
                    chords = task.chordChoices,
                    ui = ui,
                    enabled = listening,
                    extraKey = dontKnow,
                    highlight = { c ->
                        when {
                            !feedback || task.kind == TirKind.PROGRESSION -> KeyHighlight.NONE
                            c == task.chords[0] -> KeyHighlight.CORRECT
                            !correct && lastTapChord == c -> KeyHighlight.WRONG
                            else -> KeyHighlight.DIM
                        }
                    },
                    onChord = { c ->
                        lastTapChord = c
                        if (task.kind == TirKind.CHORD) {
                            onAnswer(Answer.ChordChoice(c))
                        } else {
                            enteredChords += c
                            if (enteredChords.size == task.answerLength) onAnswer(Answer.ChordFormula(enteredChords.toList()))
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ChordPad(
    chords: List<ChordDegree>,
    ui: GameUi,
    enabled: Boolean,
    highlight: (ChordDegree) -> KeyHighlight,
    onChord: (ChordDegree) -> Unit,
    extraKey: ExtraKey? = null,
) {
    val cells: List<ChordDegree?> = chords + if (extraKey != null) listOf(null) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { c ->
                    if (c == null) {
                        if (extraKey != null) ExtraKeyView(extraKey, enabled, 72.dp, Modifier.weight(1f))
                        return@forEach
                    }
                    JuicyKey(
                        label = DegreeFormatter.chord(c, ui.notation),
                        colors = DegreeColors.gradient(c.root),
                        onClick = { onChord(c) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        highlight = highlight(c),
                        height = 72.dp,
                        textStyle = MaterialTheme.typography.headlineSmall,
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AnswerSlots(count: Int, labels: List<String>, colors: List<Color>, prefix: String?, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (prefix != null) DegreeChip(prefix, Juicy.Gold, large = true)
        repeat(count) { i ->
            val label = labels.getOrNull(i)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (label != null) colors[i].copy(alpha = 0.25f) else Juicy.Ink3.copy(alpha = 0.6f))
                    .border(1.5.dp, if (label != null) colors[i] else Juicy.Line, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(label ?: "·", style = MaterialTheme.typography.titleLarge, color = if (label != null) colors[i] else Juicy.TextFaint, modifier = Modifier.pop(if (label != null) i + 1 else 0))
            }
        }
        Text("⌫", style = MaterialTheme.typography.headlineSmall, color = Juicy.TextDim, modifier = Modifier.clip(CircleShape).clickable(onClick = onUndo).padding(8.dp))
    }
}

// ---------------------------------------------------------------- Hunt

@Composable
internal fun HuntContent(task: HuntTask, ui: GameUi, accent: List<Color>, onAnswer: (Answer) -> Unit) {
    val settings = ui.settings
    val board = remember(settings.tuningId, settings.fretCount) { Fretboard(settings.tuning, settings.fretCount) }
    val feedback = ui.phase == RoundPhase.FEEDBACK
    val correct = feedback && ui.feedback?.correct == true
    var lastNamed by remember(ui.taskSerial) { mutableStateOf<NoteName?>(null) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptCard(ui, accent) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    feedback && correct -> {
                        GradientText(task.prompt.title, MaterialTheme.typography.displayLarge, listOf(Juicy.Good, Juicy.Mint), Modifier.pop(ui.taskSerial, 0.3f))
                        Text("+${ui.feedback?.points ?: 0}", style = MaterialTheme.typography.headlineMedium, color = Juicy.Gold, modifier = Modifier.pop(ui.taskSerial))
                    }
                    feedback -> {
                        Text(ui.feedback?.given ?: "", style = MaterialTheme.typography.headlineMedium, color = Juicy.Hot)
                        Text("→ " + (ui.feedback?.expected ?: ""), style = MaterialTheme.typography.headlineSmall, color = Juicy.Good, textAlign = TextAlign.Center)
                    }
                    else -> {
                        GradientText(task.prompt.title, MaterialTheme.typography.displayLarge, accent)
                        task.prompt.subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim) }
                    }
                }
            }
        }
        ui.taskFraction?.let { DeadlineBar(it, colors = accent) }

        val acceptablePositions = task.acceptable.flatMap { n -> board.positionsOf(n) }.filter { p -> task.string == null || p.string == task.string }
        val maxFret = when (task.kind) {
            HuntKind.OCTAVES -> board.fretCount
            HuntKind.REVERSE -> maxOf(12, task.position?.fret ?: 0)
            else -> if (feedback) maxOf(12, acceptablePositions.minOfOrNull { it.fret } ?: 0) else 12
        }
        val markers = buildList {
            when (task.kind) {
                HuntKind.REVERSE -> task.position?.let {
                    add(FretMarker(it, if (feedback) (if (correct) Juicy.Good else Juicy.Gold) else Juicy.Cyan, if (feedback) RuNames.note(task.name, ui.naming) else "?", pulse = !feedback))
                }
                HuntKind.OCTAVES -> task.found.flatMap { board.positionsOf(it) }.forEach { add(FretMarker(it, Juicy.Good, null)) }
                else -> if (feedback) {
                    acceptablePositions.forEach { add(FretMarker(it, if (correct) Juicy.Good else Juicy.Gold, RuNames.note(task.name, ui.naming), pulse = !correct)) }
                }
            }
        }
        com.kazaeza.muzykalka.ui.neck.Fretboard(
            stringCount = board.tuning.stringCount,
            fromFret = 0,
            toFret = minOf(maxFret, board.fretCount),
            markers = markers,
            highlightString = if (task.kind == HuntKind.REVERSE || task.kind == HuntKind.OCTAVES) null else task.string,
            leftHanded = settings.leftHanded,
            height = 170.dp,
        )

        Spacer(Modifier.weight(1f))

        if (task.kind == HuntKind.REVERSE) {
            NoteNamePad(
                naming = ui.naming,
                enabled = ui.phase == RoundPhase.LISTENING,
                highlight = { n ->
                    when {
                        !feedback -> KeyHighlight.NONE
                        n.pitchClass == task.pitchClass -> KeyHighlight.CORRECT
                        !correct && lastNamed == n -> KeyHighlight.WRONG
                        else -> KeyHighlight.DIM
                    }
                },
                onName = {
                    lastNamed = it
                    onAnswer(Answer.NoteNamed(it))
                },
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                val heard = ui.heard
                MicPulse(
                    levelDb = ui.micLevelDb,
                    label = heard?.let { RuNames.note(HuntDriver.PAD[it.pitchClass.value], ui.naming) + it.octave },
                    color = heard?.let { DegreeColors.ofSemitone(it.pitchClass.value) } ?: Juicy.Cyan,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    SectionLabel(if (task.kind == HuntKind.OCTAVES) "найди во всех октавах" else "сыграй на гитаре")
                    Text(
                        when (task.kind) {
                            HuntKind.OCTAVES -> "найдено ${task.found.size} из ${task.total}"
                            else -> "засчитывается первая нота"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Juicy.TextDim,
                    )
                    ui.micError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Juicy.Hot) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun NoteNamePad(naming: NoteNaming, enabled: Boolean, highlight: (NoteName) -> KeyHighlight, onName: (NoteName) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HuntDriver.PAD.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { n ->
                    val flat = if (n.accidental == com.kazaeza.muzykalka.music.Accidental.SHARP) {
                        NoteName(n.letter.shift(1), com.kazaeza.muzykalka.music.Accidental.FLAT)
                    } else {
                        null
                    }
                    JuicyKey(
                        label = RuNames.note(n, naming),
                        sublabel = flat?.let { RuNames.note(it, naming) },
                        colors = DegreeColors.gradient(DegreeColors.ofSemitone(n.pitchClass.value)),
                        onClick = { onName(n) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        highlight = highlight(n),
                        height = 62.dp,
                        textStyle = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Taper «Ходы»

/**
 * Root positions of a progression on the two bass strings, one per chord, kept close to the first
 * chord's position: a hand-sized map from degree names to places (no letters — rule 1).
 */
internal fun stepRoots(task: StepsTask, board: Fretboard): List<Pair<ChordDegree, FretPosition>> {
    val bass = board.strings.toList().takeLast(2).reversed() // 6th, then 5th string
    fun candidates(c: ChordDegree) = bass.flatMap { s -> board.fretsOf(task.key.pitchClassOf(c.root), s).filter { it in 0..11 }.map { FretPosition(s, it) } }
    val chords = task.pattern.chords.distinct()
    val first = candidates(chords.first()).filter { it.fret >= 1 }.minByOrNull { it.fret } ?: candidates(chords.first()).first()
    return chords.map { c ->
        val pos = if (c == chords.first()) first else candidates(c).minByOrNull { kotlin.math.abs(it.fret - first.fret) * 2 + (if (it.string == first.string) 0 else 1) } ?: first
        c to pos
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun StepsContent(task: StepsTask, ui: GameUi, accent: List<Color>, onAnswer: (Answer) -> Unit) {
    val settings = ui.settings
    val board = remember(settings.tuningId, settings.fretCount) { Fretboard(settings.tuning, settings.fretCount) }
    var showRoots by remember(ui.taskSerial) { mutableStateOf(false) }
    val listening = ui.phase == RoundPhase.LISTENING
    val feedback = ui.phase == RoundPhase.FEEDBACK
    val correct = feedback && ui.feedback?.correct == true

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptCard(ui, accent) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DegreeChip("🔑 " + RuNames.key(task.key), Juicy.Gold, large = true)
                Spacer(Modifier.height(14.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    task.pattern.chords.forEachIndexed { i, c ->
                        if (i > 0) Text("→", style = MaterialTheme.typography.headlineSmall, color = Juicy.TextFaint, modifier = Modifier.align(Alignment.CenterVertically))
                        DegreeChip(DegreeFormatter.chord(c, ui.notation), DegreeColors.of(c.root), large = true, selected = feedback)
                    }
                }
                task.pattern.like?.let {
                    Spacer(Modifier.height(10.dp))
                    Text("как в «$it»", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        feedback && correct -> "🎧 Так это звучит. Совпало?"
                        feedback -> "🎧 Слушай, как должно звучать"
                        else -> "Сыграй на гитаре, потом честно оцени"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (feedback) Juicy.Gold else Juicy.Text,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (showRoots) {
            val roots = stepRoots(task, board)
            com.kazaeza.muzykalka.ui.neck.Fretboard(
                stringCount = board.tuning.stringCount,
                toFret = 12,
                markers = roots.map { (c, p) -> FretMarker(p, DegreeColors.of(c.root), DegreeFormatter.chord(c, ui.notation)) },
                leftHanded = settings.leftHanded,
                height = 150.dp,
            )
            Text("корни аккордов на басовых струнах", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
        }

        Spacer(Modifier.weight(1f))

        if (!showRoots) {
            com.kazaeza.muzykalka.ui.component.GhostButton(
                "Где корни на грифе?",
                onClick = { showRoots = true },
                modifier = Modifier.fillMaxWidth(),
                colors = accent,
                leading = "💡",
                enabled = listening,
                height = 72.dp,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            JuicyKey(
                label = "Не вышло",
                colors = listOf(Juicy.Hot, Juicy.Orange),
                onClick = { onAnswer(Answer.SelfReport(com.kazaeza.muzykalka.game.SelfGrade.FAILED)) },
                modifier = Modifier.weight(1f),
                enabled = listening,
                height = 80.dp,
                textStyle = MaterialTheme.typography.titleLarge,
            )
            JuicyKey(
                label = "Получилось",
                colors = listOf(Juicy.Good, Juicy.Mint),
                // With the roots shown it still counts, but is logged as «с подсказкой».
                onClick = { onAnswer(Answer.SelfReport(if (showRoots) com.kazaeza.muzykalka.game.SelfGrade.STUMBLED else com.kazaeza.muzykalka.game.SelfGrade.CLEAN)) },
                modifier = Modifier.weight(1f),
                enabled = listening,
                height = 80.dp,
                textStyle = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}
