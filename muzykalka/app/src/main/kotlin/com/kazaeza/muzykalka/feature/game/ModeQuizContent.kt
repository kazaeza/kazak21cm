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
import com.kazaeza.muzykalka.game.ModeQuizTask
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.ModeVibes
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
 * O-51 «Какой лад?» (ADR-049): the scale of a mode over the tonic drone; the mode is picked. The key is not shown —
 * its name is the answer. A key of the pad says the mode and which degrees it lowers or raises; after the answer the
 * card shows the scale in degrees, the one that gives the mode away lit, and how the mode is made.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModeQuizContent(task: ModeQuizTask, ui: GameUi, accent: List<Color>, onAnswer: (Answer) -> Unit, onReplay: () -> Unit) {
    var chosen by remember(ui.taskSerial) { mutableStateOf<Mode?>(null) }
    val listening = ui.phase == RoundPhase.LISTENING
    val feedback = ui.phase == RoundPhase.FEEDBACK
    val correct = feedback && ui.feedback?.correct == true
    val fmt = { mode: Mode -> ModeVibes.altered(mode).joinToString(" ") { DegreeFormatter.degree(it, ui.notation) }.ifEmpty { "без знаков" } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptCard(ui, accent, Modifier.weight(1f).fillMaxWidth(), fill = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ReplayKey("Повторить вопрос", onReplay, enabled = listening)
                Spacer(Modifier.height(10.dp))
                when {
                    ui.phase == RoundPhase.PROMPTING -> Text("Слушай…", style = MaterialTheme.typography.headlineSmall, color = Juicy.TextDim)
                    feedback -> {
                        val vibe = ModeVibes.of(task.mode)
                        if (correct) {
                            GradientText(task.mode.ruName, MaterialTheme.typography.headlineMedium, listOf(Juicy.Good, Juicy.Mint), Modifier.pop(ui.taskSerial, 0.3f), textAlign = TextAlign.Center)
                            Text("+${ui.feedback?.points ?: 0}", style = MaterialTheme.typography.headlineSmall, color = Juicy.Gold, modifier = Modifier.pop(ui.taskSerial))
                        } else {
                            Text(chosen?.ruName ?: "не знаю", style = MaterialTheme.typography.titleMedium, color = Juicy.Hot)
                            Text("→ " + task.mode.ruName, style = MaterialTheme.typography.headlineSmall, color = Juicy.Good, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            task.mode.degrees.forEach { d ->
                                DegreeChip(DegreeFormatter.degree(d, ui.notation), DegreeColors.of(d), selected = vibe.mark?.let { it sameSoundAs d } == true)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(vibe.word, style = MaterialTheme.typography.titleLarge, color = Juicy.Text)
                        Text(vibe.recipe, style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim, textAlign = TextAlign.Center)
                    }
                    else -> {
                        GradientText("?", MaterialTheme.typography.displayLarge, accent, Modifier.breathe())
                        Text("Какой это лад?", style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            task.choices.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { mode ->
                        JuicyKey(
                            label = mode.ruName,
                            colors = accent,
                            onClick = {
                                chosen = mode
                                onAnswer(Answer.ModeChoice(mode))
                            },
                            modifier = Modifier.weight(1f),
                            enabled = listening,
                            highlight = when {
                                !feedback -> KeyHighlight.NONE
                                mode == task.mode -> KeyHighlight.CORRECT
                                !correct && chosen == mode -> KeyHighlight.WRONG
                                else -> KeyHighlight.DIM
                            },
                            sublabel = fmt(mode),
                            textStyle = MaterialTheme.typography.titleMedium,
                        )
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        ExtraKeyView(ExtraKey("", "не знаю") { onAnswer(Answer.Pass) }, listening, 56.dp, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
    }
}
