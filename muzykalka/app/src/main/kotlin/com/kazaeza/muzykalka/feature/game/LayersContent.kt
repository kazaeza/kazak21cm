package com.kazaeza.muzykalka.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.game.Answer
import com.kazaeza.muzykalka.game.Layer
import com.kazaeza.muzykalka.game.LayersTask
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordFunction
import com.kazaeza.muzykalka.music.ChordQuality
import com.kazaeza.muzykalka.music.ChordVibes
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.ExtraKey
import com.kazaeza.muzykalka.ui.component.ExtraKeyView
import com.kazaeza.muzykalka.ui.component.GradientText
import com.kazaeza.muzykalka.ui.component.JuicyKey
import com.kazaeza.muzykalka.ui.component.KeyHighlight
import com.kazaeza.muzykalka.ui.component.ReplayKey
import com.kazaeza.muzykalka.ui.effect.breathe
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy

/**
 * O-75 «Слои аккорда» (ADR-072): a chord over the humming tonic, one layer of it asked — what it DOES (home, away, tension)
 * or what it is MADE of (the colour). The layer is named above the question, so the two are never confused; the answer
 * screen tells that layer and, on the colour layer, the tones; the chord's step is shown only once its colour is no longer
 * a secret.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LayersContent(task: LayersTask, ui: GameUi, accent: List<Color>, onAnswer: (Answer) -> Unit, onReplay: () -> Unit) {
    var chosenJob by remember(ui.taskSerial) { mutableStateOf<ChordFunction?>(null) }
    var chosenColour by remember(ui.taskSerial) { mutableStateOf<ChordQuality?>(null) }
    val listening = ui.phase == RoundPhase.LISTENING
    val feedback = ui.phase == RoundPhase.FEEDBACK
    val correct = feedback && ui.feedback?.correct == true
    val job = task.layer == Layer.FUNCTION
    // on the third level a step named after the job would give the colour away: it waits for the colour layer
    val stepShown = !job || task.level < 3
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptCard(ui, accent, Modifier.weight(1f).fillMaxWidth(), fill = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (job) "Слой 1 · что аккорд делает" else "Слой 2 · из чего он сделан",
                    style = MaterialTheme.typography.labelLarge,
                    color = Juicy.TextDim,
                )
                ReplayKey("Повторить вопрос", onReplay, enabled = listening)
                Spacer(Modifier.height(10.dp))
                when {
                    ui.phase == RoundPhase.PROMPTING -> Text("Слушай…", style = MaterialTheme.typography.headlineSmall, color = Juicy.TextDim)
                    feedback -> {
                        val said = if (job) task.function.ru else RuNames.quality(task.chord.quality)
                        if (correct) {
                            GradientText(said, MaterialTheme.typography.headlineMedium, listOf(Juicy.Good, Juicy.Mint), Modifier.pop(ui.taskSerial, 0.3f), textAlign = TextAlign.Center)
                            Text("+${ui.feedback?.points ?: 0}", style = MaterialTheme.typography.headlineSmall, color = Juicy.Gold, modifier = Modifier.pop(ui.taskSerial))
                        } else {
                            val given = if (job) chosenJob?.ru else chosenColour?.let { RuNames.quality(it) }
                            Text(given ?: "не знаю", style = MaterialTheme.typography.titleMedium, color = Juicy.Hot)
                            Text("→ $said", style = MaterialTheme.typography.headlineSmall, color = Juicy.Good, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (job) {
                            if (stepShown) {
                                Text(
                                    DegreeFormatter.chord(task.chord, ui.notation) + " · " + RuNames.quality(task.chord.quality),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DegreeColors.of(task.chord.root),
                                )
                            }
                            FunctionBar(task.chord, task.key.mode, ui.notation)
                        } else {
                            Text(
                                DegreeFormatter.chord(task.chord, ui.notation) + " — " + task.function.ru.lowercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = DegreeColors.of(task.chord.root),
                            )
                            // the stack from the inside: its tones as degrees of the key
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                task.chord.quality.intervals.forEach { i ->
                                    val d = Degree.canonical(Math.floorMod(task.chord.root.semitones + i, 12))
                                    DegreeChip(DegreeFormatter.degree(d, ui.notation), DegreeColors.of(d), large = true)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(ChordVibes.of(task.chord, task.key.mode).quality, style = MaterialTheme.typography.bodyMedium, color = Juicy.Text, textAlign = TextAlign.Center)
                        }
                    }
                    else -> {
                        GradientText("?", MaterialTheme.typography.displayLarge, accent, Modifier.breathe())
                        Text(
                            if (job) "Что он делает: дом, уход или напряжение?" else "Из чего он сделан?",
                            style = MaterialTheme.typography.titleMedium,
                            color = Juicy.TextDim,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        if (job) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                task.functionChoices.forEach { f ->
                    JuicyKey(
                        label = f.ru,
                        colors = accent,
                        onClick = {
                            chosenJob = f
                            onAnswer(Answer.FunctionChoice(f))
                        },
                        modifier = Modifier.weight(1f),
                        enabled = listening,
                        highlight = when {
                            !feedback -> KeyHighlight.NONE
                            f == task.function -> KeyHighlight.CORRECT
                            !correct && chosenJob == f -> KeyHighlight.WRONG
                            else -> KeyHighlight.DIM
                        },
                        textStyle = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        } else {
            val columns = if (task.colorChoices.size <= 4) 2 else 3
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                task.colorChoices.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { q ->
                            JuicyKey(
                                label = DegreeFormatter.chord(ChordDegree(Degree.ONE, q), ui.notation),
                                colors = accent,
                                onClick = {
                                    chosenColour = q
                                    onAnswer(Answer.QualityChoice(q))
                                },
                                modifier = Modifier.weight(1f),
                                enabled = listening,
                                highlight = when {
                                    !feedback -> KeyHighlight.NONE
                                    q == task.chord.quality -> KeyHighlight.CORRECT
                                    !correct && chosenColour == q -> KeyHighlight.WRONG
                                    else -> KeyHighlight.DIM
                                },
                                sublabel = RuNames.qualityShort(q),
                                textStyle = MaterialTheme.typography.titleLarge,
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        ExtraKeyView(ExtraKey("", "не знаю") { onAnswer(Answer.Pass) }, listening, 56.dp, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
    }
}
