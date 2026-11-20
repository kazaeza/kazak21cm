package com.kazaeza.muzykalka.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.kazaeza.muzykalka.game.LoopTask
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.music.ChordDegree
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
 * O-76 «Одна петля в трёх тональностях» (ADR-073): the key of the scene is on the card (it is the one thing that changes),
 * the loop's places are numbered 1–4 with the asked one lit, and the scene dots show that the same question comes back.
 * After the answer the loop is written in steps with the asked chord lit — the same line in every key.
 */
@Composable
internal fun LoopContent(task: LoopTask, ui: GameUi, accent: List<Color>, onAnswer: (Answer) -> Unit, onReplay: () -> Unit) {
    var chosen by remember(ui.taskSerial) { mutableStateOf<ChordDegree?>(null) }
    val listening = ui.phase == RoundPhase.LISTENING
    val feedback = ui.phase == RoundPhase.FEEDBACK
    val correct = feedback && ui.feedback?.correct == true
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptCard(ui, accent, Modifier.weight(1f).fillMaxWidth(), fill = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DegreeChip(RuNames.key(task.key), Juicy.Gold)
                    if (task.scenes > 1) {
                        Text(
                            "сцена ${task.scene} из ${task.scenes}  " + (1..task.scenes).joinToString(" ") { if (it <= task.scene) "●" else "○" },
                            style = MaterialTheme.typography.labelLarge,
                            color = Juicy.TextDim,
                        )
                    }
                }
                ReplayKey("Повторить петлю", onReplay, enabled = listening)
                Spacer(Modifier.height(8.dp))
                when {
                    ui.phase == RoundPhase.PROMPTING -> Text("Слушай…", style = MaterialTheme.typography.headlineSmall, color = Juicy.TextDim)
                    feedback -> {
                        val expected = DegreeFormatter.chord(task.answer, ui.notation)
                        if (correct) {
                            GradientText(expected, MaterialTheme.typography.headlineMedium, listOf(Juicy.Good, Juicy.Mint), Modifier.pop(ui.taskSerial, 0.3f), textAlign = TextAlign.Center)
                            Text("+${ui.feedback?.points ?: 0}", style = MaterialTheme.typography.headlineSmall, color = Juicy.Gold, modifier = Modifier.pop(ui.taskSerial))
                        } else {
                            Text(chosen?.let { DegreeFormatter.chord(it, ui.notation) } ?: "не знаю", style = MaterialTheme.typography.headlineMedium, color = Juicy.Hot)
                            // O-74: what was chosen is heard first, then the right one — the words go in the same order
                            ui.feedback?.contrast?.let { Text("ты выбрал: $it", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim, textAlign = TextAlign.Center) }
                            Text("→ $expected", style = MaterialTheme.typography.displaySmall, color = Juicy.Good, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(8.dp))
                        LoopLine(task, ui, written = true)
                        Spacer(Modifier.height(4.dp))
                        if (task.scenes > 1) {
                            Text(
                                if (task.scene < task.scenes) "Дальше — та же петля в другой тональности. Ступень та же." else "Три тональности — одна и та же ступень.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Juicy.Text,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    else -> {
                        LoopLine(task, ui, written = false)
                        Spacer(Modifier.height(8.dp))
                        GradientText("?", MaterialTheme.typography.displayMedium, accent, Modifier.breathe())
                        Text("Какая ступень у ${task.asked + 1}-го аккорда?", style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        val columns = if (task.choices.size <= 4) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            task.choices.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { c ->
                        JuicyKey(
                            label = DegreeFormatter.chord(c, ui.notation),
                            colors = DegreeColors.gradient(c.root),
                            onClick = {
                                chosen = c
                                onAnswer(Answer.ChordChoice(c))
                            },
                            modifier = Modifier.weight(1f),
                            enabled = listening,
                            highlight = when {
                                !feedback -> KeyHighlight.NONE
                                c == task.answer -> KeyHighlight.CORRECT
                                !correct && chosen == c -> KeyHighlight.WRONG
                                else -> KeyHighlight.DIM
                            },
                            textStyle = MaterialTheme.typography.titleLarge,
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        ExtraKeyView(ExtraKey("", "не знаю") { onAnswer(Answer.Pass) }, listening, 56.dp, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
    }
}

/** The loop as places 1–4 (the asked one lit), or — once answered — as its chords written in steps. */
@Composable
private fun LoopLine(task: LoopTask, ui: GameUi, written: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        task.loop.forEachIndexed { i, c ->
            val lit = i == task.asked
            if (written) {
                DegreeChip(DegreeFormatter.chord(c, ui.notation), DegreeColors.of(c.root), selected = lit)
            } else {
                DegreeChip("${i + 1}", if (lit) Juicy.Gold else Juicy.TextFaint, selected = lit)
            }
        }
    }
}
