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
import androidx.compose.ui.text.style.TextAlign
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
    hint.melody.joinToString(" ") { n ->
        DegreeFormatter.degree(n.degree, notation) + when {
            n.octave > 0 -> "'".repeat(n.octave)
            n.octave < 0 -> ",".repeat(-n.octave)
            else -> ""
        }
    }

/**
 * O-57: «как в «С днём рождения»» — the tune that hangs the sound on a song, its notes in degrees, and a «▶» to hear it
 * over the drone. Replaces the feel words where the user chose songs; a sound without a tune keeps its words.
 */
@Composable
internal fun SongHintLine(degree: Degree, hint: SongHint, notation: DegreeNotation, onPlay: (() -> Unit)?, modifier: Modifier = Modifier) {
    val color = DegreeColors.of(degree)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(hint.line, style = MaterialTheme.typography.headlineSmall, color = color, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(melodyText(hint, notation), style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim)
            if (onPlay != null) GhostButton("Послушать", onClick = onPlay, colors = Accents.Tir, leading = "►")
        }
    }
}
