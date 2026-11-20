package com.kazaeza.muzykalka.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.game.Answer
import com.kazaeza.muzykalka.game.BassLineTask
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.ExtraKey
import com.kazaeza.muzykalka.ui.component.ExtraKeyView
import com.kazaeza.muzykalka.ui.component.GradientText
import com.kazaeza.muzykalka.ui.component.MicPulse
import com.kazaeza.muzykalka.ui.component.ReplayKey
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.effect.breathe
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy

/**
 * O-77 «Бас-линия» (ADR-075): only the bass of a loop has sounded; it is played on the guitar, a note for each chord. The
 * places of the line are numbered and fill with the degree of each note as it is heard — what was played, not whether it was
 * right. After the answer each place says right or wrong and the whole line is written in degrees; then the band plays it.
 */
@Composable
internal fun BassLineContent(task: BassLineTask, ui: GameUi, accent: List<Color>, onAnswer: (Answer) -> Unit, onReplay: () -> Unit) {
    val listening = ui.phase == RoundPhase.LISTENING
    val feedback = ui.phase == RoundPhase.FEEDBACK
    val correct = feedback && ui.feedback?.correct == true
    val key = task.key
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptCard(ui, accent, Modifier.weight(1f).fillMaxWidth(), fill = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DegreeChip(RuNames.key(key), Juicy.Gold)
                    Text(
                        "сцена ${task.scene} из ${task.scenes}  " + (1..task.scenes).joinToString(" ") { if (it <= task.scene) "●" else "○" },
                        style = MaterialTheme.typography.labelLarge,
                        color = Juicy.TextDim,
                    )
                    ReplayKey("Повторить бас", onReplay, enabled = listening)
                }
                Spacer(Modifier.height(12.dp))
                // the places of the line: filled with what was played, and after the answer judged
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(task.expected) { i ->
                        val played = ui.phrase.getOrNull(i)
                        val degree = played?.let { BassLineTask.degreeOf(key, it.pitchClass) }
                        val right = played != null && played.pitchClass == task.pitchClasses[i]
                        when {
                            feedback && played != null -> DegreeChip(DegreeFormatter.degree(degree!!, ui.notation), if (right) Juicy.Good else Juicy.Hot, large = true, selected = true)
                            feedback -> DegreeChip("·", Juicy.TextFaint, large = true)
                            played != null -> DegreeChip(DegreeFormatter.degree(degree!!, ui.notation), DegreeColors.of(degree), large = true, selected = true)
                            else -> DegreeChip("${i + 1}", Juicy.TextFaint, large = true)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    ui.phase == RoundPhase.PROMPTING -> Text("Слушай бас…", style = MaterialTheme.typography.headlineSmall, color = Juicy.TextDim, textAlign = TextAlign.Center)
                    feedback -> {
                        val expected = task.degrees.joinToString(" ") { DegreeFormatter.degree(it, ui.notation) }
                        if (correct) {
                            GradientText(expected, MaterialTheme.typography.headlineMedium, listOf(Juicy.Good, Juicy.Mint), Modifier.pop(ui.taskSerial, 0.3f), textAlign = TextAlign.Center)
                            Text("+${ui.feedback?.points ?: 0}", style = MaterialTheme.typography.headlineSmall, color = Juicy.Gold, modifier = Modifier.pop(ui.taskSerial))
                        } else {
                            Text("→ $expected", style = MaterialTheme.typography.headlineMedium, color = Juicy.Good, textAlign = TextAlign.Center)
                        }
                        Text(
                            when (task.scene) {
                                1 -> "Теперь вся группа: бас — низ этой петли."
                                2 -> "Бас из памяти, после гармонии."
                                else -> "Та же петля в новой тональности: ступени те же."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Juicy.Text,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        GradientText("?", MaterialTheme.typography.displayMedium, accent, Modifier.breathe())
                        Text("Сыграй этот бас на гитаре: по ноте на аккорд", style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        if (!feedback) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                MicPulse(levelDb = ui.micLevelDb, label = if (listening) "" else null, color = Juicy.Cyan)
                Spacer(Modifier.width(16.dp))
                Column {
                    SectionLabel("сыграй на гитаре")
                    Text("в любой октаве, в своём темпе; нижние струны слышны лучше", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                    ui.micError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Juicy.Hot) }
                }
            }
        }
        ExtraKeyView(ExtraKey("", "не знаю") { onAnswer(Answer.Pass) }, listening, 56.dp, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
    }
}
