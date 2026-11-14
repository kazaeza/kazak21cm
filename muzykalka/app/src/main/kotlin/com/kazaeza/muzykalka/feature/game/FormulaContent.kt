package com.kazaeza.muzykalka.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.game.Answer
import com.kazaeza.muzykalka.game.FormulaTask
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.game.SelfGrade
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GradientText
import com.kazaeza.muzykalka.ui.component.JuicyKey
import com.kazaeza.muzykalka.ui.effect.breathe
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy

/** The bars of a section take at most this much of the card; a longer section scrolls. */
private val BARS_MAX_HEIGHT = 260.dp

/**
 * F-08 «Формула вслух» (ADR-046): the song and its section are named; the degrees are recalled, the answer is opened
 * («Показать ответ» is not an answer to the engine — only the self-grade is), and then the section sounds.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FormulaContent(task: FormulaTask, ui: GameUi, accent: List<Color>, onAnswer: (Answer) -> Unit) {
    var revealed by remember(ui.taskSerial) { mutableStateOf(false) }
    val listening = ui.phase == RoundPhase.LISTENING
    val feedback = ui.phase == RoundPhase.FEEDBACK
    val open = revealed || feedback
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptCard(ui, accent, Modifier.weight(1f).fillMaxWidth(), fill = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(task.title, style = MaterialTheme.typography.headlineSmall, color = Juicy.Text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                task.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(8.dp))
                DegreeChip("часть ${task.section.label}", Juicy.Gold, large = true)
                Spacer(Modifier.height(4.dp))
                Text(
                    "форма ${task.form} · ${task.section.bars.size} тактов · ${task.mode.ruName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Juicy.TextDim,
                )
                Spacer(Modifier.height(14.dp))
                if (open) {
                    // the section bar by bar, in degrees (rule 1: never letters)
                    // a long section scrolls inside the card: the card measures its content without a height limit
                    FlowRow(
                        modifier = Modifier.heightIn(max = BARS_MAX_HEIGHT).verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        task.section.bars.forEach { bar ->
                            Row(
                                Modifier.clip(RoundedCornerShape(12.dp)).background(Juicy.Ink4.copy(alpha = 0.6f)).padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) { bar.chords.forEach { c -> DegreeChip(DegreeFormatter.chord(c, ui.notation), DegreeColors.of(c.root), large = true) } }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (feedback) "Так она звучит — в тональности ${RuNames.key(task.key)}" else "Совпало с тем, что ты сказал?",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (feedback) Juicy.Gold else Juicy.Text,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    GradientText("?", MaterialTheme.typography.displayLarge, accent, Modifier.breathe())
                    Text(
                        "Проговори ступени этой части — вслух или про себя",
                        style = MaterialTheme.typography.titleMedium,
                        color = Juicy.TextDim,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (!open) {
            GhostButton("Показать ответ", onClick = { revealed = true }, modifier = Modifier.fillMaxWidth(), colors = accent, leading = "", enabled = listening, height = 80.dp)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple(SelfGrade.FAILED, "Не вспомнил", listOf(Juicy.Hot, Juicy.Orange)),
                    Triple(SelfGrade.STUMBLED, "С запинкой", listOf(Juicy.Gold, Juicy.Orange)),
                    Triple(SelfGrade.CLEAN, "Помню", listOf(Juicy.Good, Juicy.Mint)),
                ).forEach { (grade, label, colors) ->
                    JuicyKey(
                        label = label,
                        colors = colors,
                        onClick = { onAnswer(Answer.SelfReport(grade)) },
                        modifier = Modifier.weight(1f),
                        enabled = listening,
                        height = 80.dp,
                        textStyle = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
