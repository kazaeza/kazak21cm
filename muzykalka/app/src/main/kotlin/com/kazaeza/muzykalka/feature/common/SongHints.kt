package com.kazaeza.muzykalka.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.kazaeza.muzykalka.music.DegreeNote
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.HintStyle
import com.kazaeza.muzykalka.music.SongHint
import com.kazaeza.muzykalka.ui.component.ChoiceRow
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy

/** O-57 (ADR-056): «Подсказки: Ощущение | Песни» — the same switch wherever a hint is given. */
@Composable
internal fun HintStyleSwitch(style: HintStyle, onChange: (HintStyle) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("подсказки")
        ChoiceRow(HintStyle.entries, style, { it.ru }, onChange, colors = Accents.Tir)
    }
}

/** The notes of a hint as the user reads degrees: «5 5 6 5», in his notation, octave marks as typed. */
internal fun melodyText(hint: SongHint, notation: DegreeNotation): String =
    hint.melody.joinToString(" ") { noteText(it, notation) }

private fun noteText(n: DegreeNote, notation: DegreeNotation): String =
    DegreeFormatter.degree(n.degree, notation) + when {
        n.octave > 0 -> "'".repeat(n.octave)
        n.octave < 0 -> ",".repeat(-n.octave)
        else -> ""
    }

/**
 * O-64 (ADR-063): the notes of a hint with the ones to remember standing out — in the colour of their degree, bold and
 * larger; the rest stay dim. A long note (two beats or more) gets a «—» after it, so the rhythm is seen as well.
 */
internal fun melodyAccented(hint: SongHint, notation: DegreeNotation): AnnotatedString = buildAnnotatedString {
    val color = DegreeColors.of(hint.degree)
    hint.melody.forEachIndexed { i, n ->
        if (i > 0) append(" ")
        val text = noteText(n, notation) + if (hint.beats[i] >= 2.0) " —" else ""
        if (i in hint.targets) {
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)) { append(text) }
        } else {
            append(text)
        }
    }
}

/**
 * O-57: «как в «С днём рождения»» — the tune that hangs the sound on a song, its notes in degrees (O-64: the ones to
 * remember accented, and the cue to find them), and a «▶» to hear it over the drone. Replaces the feel words where the user chose songs; a sound without a tune keeps its words.
 */
@Composable
internal fun SongHintLine(degree: Degree, hint: SongHint, notation: DegreeNotation, onPlay: (() -> Unit)?, modifier: Modifier = Modifier) {
    val color = DegreeColors.of(degree)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(hint.line, style = MaterialTheme.typography.headlineSmall, color = color, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(melodyAccented(hint, notation), style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim)
            if (onPlay != null) GhostButton("Послушать", onClick = onPlay, colors = Accents.Tir, leading = "►")
        }
        // where in the tune the sound is — the note to keep
        Text(
            "запомни ${DegreeFormatter.degree(degree, notation)}: ${hint.cue}",
            style = MaterialTheme.typography.bodyMedium,
            color = Juicy.Text,
            textAlign = TextAlign.Center,
        )
    }
}
