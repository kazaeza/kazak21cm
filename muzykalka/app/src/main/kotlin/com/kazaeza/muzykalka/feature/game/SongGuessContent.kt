package com.kazaeza.muzykalka.feature.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.game.Answer
import com.kazaeza.muzykalka.game.RoundPhase
import com.kazaeza.muzykalka.game.SongGuessTask
import com.kazaeza.muzykalka.game.SongOption
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.ExtraKey
import com.kazaeza.muzykalka.ui.component.ExtraKeyView
import com.kazaeza.muzykalka.ui.component.GameTouchTarget
import com.kazaeza.muzykalka.ui.component.GradientText
import com.kazaeza.muzykalka.ui.component.KeyHighlight
import com.kazaeza.muzykalka.ui.component.ReplayKey
import com.kazaeza.muzykalka.ui.component.TapOnPress
import com.kazaeza.muzykalka.ui.effect.breathe
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy

/**
 * O-43 «Чья гармония?» (ADR-042): the cadence, the opening of a library song, and four songs to pick from. On levels
 * 1–2 every option carries its opening in degrees (rule 1: never letters); on level 3 the degrees come only with the
 * answer — the right song's are shown, so a miss is a lesson, not just a red key.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongGuessContent(task: SongGuessTask, ui: GameUi, accent: List<Color>, onAnswer: (Answer) -> Unit, onReplay: () -> Unit) {
    var chosen by remember(ui.taskSerial) { mutableStateOf<String?>(null) }
    val listening = ui.phase == RoundPhase.LISTENING
    val feedback = ui.phase == RoundPhase.FEEDBACK
    val correct = feedback && ui.feedback?.correct == true
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PromptCard(ui, accent, Modifier.weight(1f).fillMaxWidth(), fill = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DegreeChip(RuNames.key(task.key), Juicy.Gold)
                    ReplayKey("Повторить вопрос", onReplay, enabled = listening)
                }
                Spacer(Modifier.height(10.dp))
                when {
                    ui.phase == RoundPhase.PROMPTING -> Text("Слушай…", style = MaterialTheme.typography.headlineSmall, color = Juicy.TextDim)
                    feedback -> {
                        val f = ui.feedback
                        if (correct) {
                            GradientText(task.answer.title, MaterialTheme.typography.headlineMedium, listOf(Juicy.Good, Juicy.Mint), Modifier.pop(ui.taskSerial, 0.3f), textAlign = TextAlign.Center)
                            Text("+${f.points}", style = MaterialTheme.typography.headlineSmall, color = Juicy.Gold, modifier = Modifier.pop(ui.taskSerial))
                        } else {
                            Text(f?.given ?: "", style = MaterialTheme.typography.titleMedium, color = Juicy.Hot, textAlign = TextAlign.Center)
                            Text("→ " + task.answer.title, style = MaterialTheme.typography.headlineSmall, color = Juicy.Good, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(8.dp))
                        // what was played, in degrees: the thing to take away from the question
                        OpeningChips(task.answer, ui.notation, large = true)
                    }
                    else -> {
                        GradientText("?", MaterialTheme.typography.displayLarge, accent, Modifier.breathe())
                        Text("Чья это гармония?", style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim)
                    }
                }
            }
        }
        task.options.forEach { option ->
            SongOptionKey(
                option = option,
                notation = ui.notation,
                showDegrees = task.showsDegrees,
                enabled = listening,
                highlight = when {
                    !feedback -> KeyHighlight.NONE
                    option.id == task.answer.id -> KeyHighlight.CORRECT
                    !correct && chosen == option.id -> KeyHighlight.WRONG
                    else -> KeyHighlight.DIM
                },
            ) {
                chosen = option.id
                onAnswer(Answer.SongChoice(option.id))
            }
        }
        ExtraKeyView(ExtraKey("", "не знаю") { onAnswer(Answer.Pass) }, listening, 56.dp, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpeningChips(option: SongOption, notation: DegreeNotation, large: Boolean = false) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        option.opening.forEach { c -> DegreeChip(DegreeFormatter.chord(c, notation), DegreeColors.of(c.root), large = large) }
    }
}

/** One song to pick: a full game target (72 dp), the title, and — where the level shows them — its opening chords. */
@Composable
private fun SongOptionKey(
    option: SongOption,
    notation: DegreeNotation,
    showDegrees: Boolean,
    enabled: Boolean,
    highlight: KeyHighlight,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    TapOnPress(source)
    val shape = RoundedCornerShape(22.dp)
    val fade by animateFloatAsState(if (highlight == KeyHighlight.DIM) 0.4f else 1f, tween(200), label = "fade")
    val edge = when (highlight) {
        KeyHighlight.CORRECT -> Juicy.Good
        KeyHighlight.WRONG -> Juicy.Bad
        else -> Juicy.Line
    }
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(fade)
            .heightIn(min = GameTouchTarget)
            .clip(shape)
            .background(if (highlight == KeyHighlight.CORRECT) Juicy.Good.copy(alpha = 0.16f) else Juicy.Ink3.copy(alpha = 0.7f))
            .border(if (highlight == KeyHighlight.NONE || highlight == KeyHighlight.DIM) 1.dp else 2.dp, edge, shape)
            .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, onClickLabel = "Выбрать песню", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val artist = option.artist
        Text(
            buildAnnotatedString {
                append(option.title)
                // with the chords on the second line the artist moves up next to the title
                if (showDegrees && artist != null) withStyle(SpanStyle(color = Juicy.TextDim, fontSize = MaterialTheme.typography.bodySmall.fontSize)) { append("  ·  $artist") }
            },
            style = MaterialTheme.typography.titleMedium,
            color = Juicy.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        when {
            showDegrees -> OpeningChips(option, notation)
            artist != null -> Text(artist, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
