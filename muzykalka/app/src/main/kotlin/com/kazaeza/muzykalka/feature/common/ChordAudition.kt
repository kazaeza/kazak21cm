package com.kazaeza.muzykalka.feature.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordVibes
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The tile that was tapped last: which one ([id] is its place on the sheet), what chord, in which key. */
data class HeardChord(val id: String, val chord: ChordDegree, val key: Key)

/**
 * O-17 (ADR-031): a tap on a chord of a sheet makes it sound, in the key of the order, so
 * «где доминанта, где девятка, где минорный» can be settled by ear. Latest tap wins — the previous
 * strum is cut. Hearing a chord names nothing, so it is NOT a hint: grade, stars and the hint
 * ladder are untouched.
 */
@Stable
class ChordAudition internal constructor(
    private val output: AudioOutput,
    private val scope: CoroutineScope,
    private val a4Hz: () -> Double,
    private val arranger: Arranger = Arranger(),
) {
    /** The last tapped tile — it keeps its ring and its caption until the next tap or [clear]. */
    var heard: HeardChord? by mutableStateOf(null)
        private set

    private var job: Job? = null

    fun tap(id: String, chord: ChordDegree, key: Key) {
        job?.cancel()
        output.a4Hz = a4Hz()
        heard = HeardChord(id, chord, key)
        job = scope.launch { output.play(arranger.strum(chord, key)) }
    }

    /** True for the tile [id] that was tapped last: it wears the ring while its caption is shown. */
    fun isHeard(id: String): Boolean = heard?.id == id

    /** A new order, a new key: hush the chord and forget the caption. */
    fun clear() {
        job?.cancel()
        heard = null
    }
}

@Composable
fun rememberChordAudition(a4Hz: Double): ChordAudition {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val hz by rememberUpdatedState(a4Hz)
    return remember(graph, scope) { ChordAudition(graph.audioOutput, scope, { hz }) }
}

/**
 * The words for the chord that was just tapped: «V⁷ — доминанта», what its stack is, what it does.
 * Chords are named by degrees in the user's notation, never by letters (rule 1).
 */
@Composable
fun ChordCaption(audition: ChordAudition, notation: DegreeNotation, modifier: Modifier = Modifier) {
    val heard = audition.heard ?: return
    val vibe = ChordVibes.of(heard.chord, heard.key.mode)
    val color = DegreeColors.of(heard.chord.root)
    // A border gradient needs two colours: the chord's own, then a softer one.
    GlassCard(modifier.fillMaxWidth(), accent = listOf(color, color.copy(alpha = 0.5f)), contentPadding = 14.dp) {
        Text(
            ChordVibes.title(heard.chord, heard.key.mode, notation),
            style = MaterialTheme.typography.titleLarge,
            color = color,
        )
        Spacer(Modifier.height(2.dp))
        Text(vibe.quality, style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
        Text(vibe.line, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
    }
}
