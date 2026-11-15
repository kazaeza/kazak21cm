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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.game.Answer
import com.kazaeza.muzykalka.game.EchoTask
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeVibes
import com.kazaeza.muzykalka.music.FretPosition
import com.kazaeza.muzykalka.music.Fretboard
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
import com.kazaeza.muzykalka.ui.neck.FretMarker
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy

/** How far up the neck the answer is shown after the round's question: the first octave of every string. */
private const val SHOWN_FRETS = 12

/**
 * PR-01 «Попугай», step 1 (ADR-051): the tonic and a note have sounded; the note is played on the guitar and the
 * microphone hears it. Before the answer nothing names the note — neither a degree nor a place. After it: the degree,
 * what was played instead, and every place of that sound on the first twelve frets.
 */
@Composable
internal fun EchoContent(task: EchoTask, ui: GameUi, accent: List<Color>, onAnswer: (Answer) -> Unit, onReplay: () -> Unit) {
    val listening = ui.phase == RoundPhase.LISTENING
    val feedback = ui.phase == RoundPhase.FEEDBACK
    val correct = feedback && ui.feedback?.correct == true
    val settings = ui.settings
    val board = remember(settings.tuningId, settings.fretCount) { Fretboard(settings.tuning, settings.fretCount) }
    val degree = task.note.degree
    val color = DegreeColors.of(degree)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptCard(ui, accent, Modifier.weight(1f).fillMaxWidth(), fill = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DegreeChip(RuNames.key(task.key), Juicy.Gold)
                    ReplayKey("Повторить вопрос", onReplay, enabled = listening)
                }
                Spacer(Modifier.height(12.dp))
                when {
                    ui.phase == RoundPhase.PROMPTING -> Text("Слушай: тоника, потом нота…", style = MaterialTheme.typography.headlineSmall, color = Juicy.TextDim, textAlign = TextAlign.Center)
                    feedback -> {
                        GradientText(DegreeFormatter.degree(degree, ui.notation), MaterialTheme.typography.displayMedium, DegreeColors.gradient(color), Modifier.pop(ui.taskSerial, 0.3f))
                        Text(DegreeVibes.of(degree).word, style = MaterialTheme.typography.titleLarge, color = color)
                        Spacer(Modifier.height(6.dp))
                        if (correct) {
                            Text("нашёл · +${ui.feedback?.points ?: 0}", style = MaterialTheme.typography.titleLarge, color = Juicy.Gold, modifier = Modifier.pop(ui.taskSerial))
                        } else {
                            Text("ты сыграл: " + (ui.feedback?.given ?: ""), style = MaterialTheme.typography.titleMedium, color = Juicy.Hot)
                        }
                    }
                    else -> {
                        GradientText("?", MaterialTheme.typography.displayLarge, accent, Modifier.breathe())
                        Text("Найди эту ноту на гитаре", style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim)
                    }
                }
            }
        }
        if (feedback) {
            // where the sound lies: every string, the first octave of the neck
            val places = board.strings.flatMap { s -> board.fretsOf(task.pitchClass, s).filter { it <= SHOWN_FRETS }.map { FretPosition(s, it) } }
            com.kazaeza.muzykalka.ui.neck.Fretboard(
                stringCount = board.tuning.stringCount,
                toFret = SHOWN_FRETS,
                markers = places.map { FretMarker(it, color, DegreeFormatter.degree(degree, ui.notation)) },
                leftHanded = settings.leftHanded,
                height = 170.dp,
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                MicPulse(levelDb = ui.micLevelDb, label = if (listening) "" else null, color = Juicy.Cyan)
                Spacer(Modifier.width(16.dp))
                Column {
                    SectionLabel("сыграй на гитаре")
                    Text("засчитывается первая нота, в любой октаве", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                    ui.micError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Juicy.Hot) }
                }
            }
        }
        ExtraKeyView(ExtraKey("", "не знаю") { onAnswer(Answer.Pass) }, listening, 56.dp, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
    }
}
