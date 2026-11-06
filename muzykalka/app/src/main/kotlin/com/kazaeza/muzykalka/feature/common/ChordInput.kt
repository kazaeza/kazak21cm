package com.kazaeza.muzykalka.feature.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.music.Accidental
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordQuality
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.JuicyKey
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy

/** Quality choices of the chord pad; null = "авто" (the diatonic quality of that degree in the mode). */
val QUALITY_CHOICES: List<Pair<ChordQuality?, String>> = listOf(
    null to "авто",
    ChordQuality.MAJOR to "мажор",
    ChordQuality.MINOR to "минор",
    ChordQuality.DOMINANT7 to "7",
    ChordQuality.MAJOR7 to "maj7",
    ChordQuality.MINOR7 to "m7",
    ChordQuality.HALF_DIMINISHED7 to "ø7",
    ChordQuality.DIMINISHED to "°",
    ChordQuality.DIMINISHED7 to "°7",
    ChordQuality.SUS4 to "sus4",
    ChordQuality.SUS2 to "sus2",
    ChordQuality.POWER to "5",
    ChordQuality.MAJOR6 to "6",
    ChordQuality.MINOR6 to "m6",
    ChordQuality.DOMINANT9 to "9",
    ChordQuality.ADD9 to "add9",
)

/** The chord a degree naturally carries in [mode] (triad); chromatic degrees → major (♭VII, ♭VI…). */
fun diatonicQuality(degree: Degree, mode: Mode): ChordQuality {
    val step = mode.indexOf(degree)
    return if (step >= 0) Harmony.diatonicChord(mode, step).quality else ChordQuality.MAJOR
}

/**
 * Root keys 1–7 with a ♭/♮/♯ toggle and a row of qualities. Tapping a root emits the chord at once
 * (one tap per chord on the common path: "авто" picks the diatonic quality).
 */
@Composable
fun ChordInputPanel(mode: Mode, notation: DegreeNotation, onChord: (ChordDegree) -> Unit, modifier: Modifier = Modifier) {
    var accidental by remember { mutableStateOf(Accidental.NATURAL) }
    var quality by remember { mutableStateOf<ChordQuality?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Accidental.FLAT to "♭", Accidental.NATURAL to "♮", Accidental.SHARP to "♯").forEach { (a, label) ->
                DegreeChip(label, Juicy.Cyan, large = true, selected = accidental == a, onClick = { accidental = a })
            }
            Spacer(Modifier.fillMaxWidth(0.02f))
            QUALITY_CHOICES.forEach { (q, label) ->
                DegreeChip(label, Juicy.Violet, large = true, selected = quality == q, onClick = { quality = q })
            }
        }
        Degree.NATURALS.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { natural ->
                    val d = Degree(natural.number, accidental)
                    val q = quality ?: diatonicQuality(d, mode)
                    val chord = ChordDegree(d, q)
                    JuicyKey(
                        label = DegreeFormatter.chord(chord, notation),
                        colors = DegreeColors.gradient(d),
                        onClick = { onChord(chord) },
                        modifier = Modifier.weight(1f),
                        height = 64.dp,
                        textStyle = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Degree keys with ♭/♯ and octave shift, for riffs and tunes. */
@Composable
fun DegreeInputPanel(notation: DegreeNotation, onNote: (com.kazaeza.muzykalka.music.DegreeNote) -> Unit, modifier: Modifier = Modifier) {
    var accidental by remember { mutableStateOf(Accidental.NATURAL) }
    var octave by remember { mutableStateOf(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Accidental.FLAT to "♭", Accidental.NATURAL to "♮", Accidental.SHARP to "♯").forEach { (a, label) ->
                DegreeChip(label, Juicy.Cyan, large = true, selected = accidental == a, onClick = { accidental = a })
            }
            listOf(-1 to "октава ↓", 0 to "основная", 1 to "октава ↑").forEach { (o, label) ->
                DegreeChip(label, Juicy.Violet, large = true, selected = octave == o, onClick = { octave = o })
            }
        }
        Degree.NATURALS.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { natural ->
                    val d = Degree(natural.number, accidental)
                    JuicyKey(
                        label = DegreeFormatter.degree(d, notation),
                        colors = DegreeColors.gradient(d),
                        onClick = { onNote(com.kazaeza.muzykalka.music.DegreeNote(d, octave)) },
                        modifier = Modifier.weight(1f),
                        height = 64.dp,
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
