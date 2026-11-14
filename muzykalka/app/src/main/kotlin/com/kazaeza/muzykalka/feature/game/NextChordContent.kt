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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.game.Answer
import com.kazaeza.muzykalka.game.NextChordTask
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.music.SongOpenings
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.ExtraKey
import com.kazaeza.muzykalka.ui.component.KeyHighlight
import com.kazaeza.muzykalka.ui.component.ReplayKey
import com.kazaeza.muzykalka.ui.effect.breathe
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy

/**
 * O-45 «Куда пойдёт?» (ADR-044): the song is named, its first chords have sounded, the next one is picked before it is
 * heard. Level 1 writes the heard chords in degrees (rule 1: never letters), level 2 leaves the title and the sound.
 * After the answer the whole way is on the card, the next chord in its colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NextChordContent(task: NextChordTask, ui: GameUi, accent: List<Color>, onAnswer: (Answer) -> Unit, onReplay: () -> Unit) {
    var chosen by remember(ui.taskSerial) { mutableStateOf<ChordDegree?>(null) }
    val listening = ui.phase == RoundPhase.LISTENING
    val feedback = ui.phase == RoundPhase.FEEDBACK
    val correct = feedback && ui.feedback?.correct == true
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptCard(ui, accent, Modifier.weight(1f).fillMaxWidth(), fill = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DegreeChip(RuNames.key(task.key), Juicy.Gold)
                    ReplayKey("Повторить вопрос", onReplay, enabled = listening)
                }
                Spacer(Modifier.height(10.dp))
                Text(task.title, style = MaterialTheme.typography.headlineSmall, color = Juicy.Text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                task.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(12.dp))
                // the way so far: written on level 1, dots on level 2 — and the place of the next chord
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    task.shown.forEach { c ->
                        if (task.showsDegrees || feedback) {
                            DegreeChip(DegreeFormatter.chord(c, ui.notation), DegreeColors.of(c.root), large = true)
                        } else {
                            DegreeChip("♪", Juicy.TextDim, large = true)
                        }
                    }
                    if (feedback) {
                        DegreeChip(DegreeFormatter.chord(task.answer, ui.notation), DegreeColors.of(task.answer.root), Modifier.pop(ui.taskSerial, 0.3f), large = true, selected = true)
                    } else {
                        DegreeChip("?", accent.first(), if (listening) Modifier.breathe() else Modifier, large = true)
                    }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    ui.phase == RoundPhase.PROMPTING -> Text("Слушай…", style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim)
                    feedback && correct -> Text("+${ui.feedback?.points ?: 0}", style = MaterialTheme.typography.headlineSmall, color = Juicy.Gold, modifier = Modifier.pop(ui.taskSerial))
                    feedback -> Text((ui.feedback?.given ?: "") + " → " + (ui.feedback?.expected ?: ""), style = MaterialTheme.typography.titleLarge, color = Juicy.Hot)
                    else -> Text("Куда пойдёт дальше?", style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim)
                }
            }
        }
        ChordPad(
            chords = task.choices,
            ui = ui,
            enabled = listening,
            extraKey = ExtraKey("", "не знаю") { onAnswer(Answer.Pass) },
            highlight = { c ->
                when {
                    !feedback -> KeyHighlight.NONE
                    SongOpenings.sameChord(c, task.answer) -> KeyHighlight.CORRECT
                    !correct && chosen == c -> KeyHighlight.WRONG
                    else -> KeyHighlight.DIM
                }
            },
            onChord = { c ->
                chosen = c
                onAnswer(Answer.ChordChoice(c))
            },
        )
        Spacer(Modifier.height(6.dp))
    }
}
