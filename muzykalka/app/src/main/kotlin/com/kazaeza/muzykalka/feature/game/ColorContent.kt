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
import com.kazaeza.muzykalka.game.ColorTask
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordQuality
import com.kazaeza.muzykalka.music.ChordVibes
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.Mode
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
 * O-50 «Цвет аккорда» (ADR-048): a chord on a pitch that is never named; its quality is picked. A key of the pad shows
 * the chord as it would stand on the first degree («I⁷») with its word below. After the answer the card shows the tones
 * as degrees above the root and what the stack is made of, while the chord is taken apart.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorContent(task: ColorTask, ui: GameUi, accent: List<Color>, onAnswer: (Answer) -> Unit, onReplay: () -> Unit) {
    var chosen by remember(ui.taskSerial) { mutableStateOf<ChordQuality?>(null) }
    val listening = ui.phase == RoundPhase.LISTENING
    val feedback = ui.phase == RoundPhase.FEEDBACK
    val correct = feedback && ui.feedback?.correct == true
    val label = { q: ChordQuality -> DegreeFormatter.chord(ChordDegree(Degree.ONE, q), ui.notation) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptCard(ui, accent, Modifier.weight(1f).fillMaxWidth(), fill = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ReplayKey("Повторить вопрос", onReplay, enabled = listening)
                Spacer(Modifier.height(10.dp))
                when {
                    ui.phase == RoundPhase.PROMPTING -> Text("Слушай…", style = MaterialTheme.typography.headlineSmall, color = Juicy.TextDim)
                    feedback -> {
                        if (correct) {
                            GradientText(RuNames.quality(task.quality), MaterialTheme.typography.headlineMedium, listOf(Juicy.Good, Juicy.Mint), Modifier.pop(ui.taskSerial, 0.3f), textAlign = TextAlign.Center)
                            Text("+${ui.feedback?.points ?: 0}", style = MaterialTheme.typography.headlineSmall, color = Juicy.Gold, modifier = Modifier.pop(ui.taskSerial))
                        } else {
                            Text(chosen?.let { RuNames.quality(it) } ?: "не знаю", style = MaterialTheme.typography.titleMedium, color = Juicy.Hot)
                            Text("→ " + RuNames.quality(task.quality), style = MaterialTheme.typography.headlineSmall, color = Juicy.Good, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(8.dp))
                        // the stack from the inside: its tones above the root, and what it is made of
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            task.tones.forEach { d -> DegreeChip(DegreeFormatter.degree(d, ui.notation), DegreeColors.of(d), large = true) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            ChordVibes.of(ChordDegree(Degree.ONE, task.quality), Mode.MAJOR).quality,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Juicy.Text,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        GradientText("?", MaterialTheme.typography.displayLarge, accent, Modifier.breathe())
                        Text("Какого цвета аккорд?", style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim)
                    }
                }
            }
        }
        val columns = if (task.choices.size <= 4) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            task.choices.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { q ->
                        JuicyKey(
                            label = label(q),
                            colors = accent,
                            onClick = {
                                chosen = q
                                onAnswer(Answer.QualityChoice(q))
                            },
                            modifier = Modifier.weight(1f),
                            enabled = listening,
                            highlight = when {
                                !feedback -> KeyHighlight.NONE
                                q == task.quality -> KeyHighlight.CORRECT
                                !correct && chosen == q -> KeyHighlight.WRONG
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
        ExtraKeyView(ExtraKey("", "не знаю") { onAnswer(Answer.Pass) }, listening, 56.dp, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
    }
}
