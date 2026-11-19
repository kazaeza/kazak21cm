package com.kazaeza.muzykalka.feature.loops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.game.GuessSong
import com.kazaeza.muzykalka.game.LoopAlbum
import com.kazaeza.muzykalka.game.LoopCard
import com.kazaeza.muzykalka.game.SongGuessDriver
import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.ui.component.Badge
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class LoopsUi(
    val loading: Boolean = true,
    val cards: List<LoopCard> = emptyList(),
    val notation: DegreeNotation = DegreeNotation.ARABIC,
    /** The loop that is sounding ([LoopCard.shape]) and the key it sounds in. */
    val playing: String? = null,
    val key: Key? = null,
) {
    val known: Int get() = cards.count { it.known }
}

/**
 * O-47 «Коллекция ходов»: the album behind «Чья гармония?» — every opening loop of the library, the songs that begin
 * with it, and whether the ear knows it yet. A card plays its loop (cadence first) in a key of chance. No score.
 */
class LoopsViewModel(private val graph: AppGraph) : ViewModel() {
    private val keys = KeyPicker(Random(System.nanoTime()))
    private val arranger = Arranger()
    private val sound = MutableStateFlow<Pair<String, Key>?>(null)
    private var job: Job? = null

    val ui: StateFlow<LoopsUi> = combine(
        graph.library.observeEntries(), graph.practice.observeAttempts(), graph.settings.settings, sound,
    ) { entries, attempts, settings, playing ->
        val songs = entries.mapNotNull { e -> e.song?.takeIf { !e.unsupported }?.let { GuessSong(e.id, it, learning = !e.shelved) } }
        LoopsUi(
            loading = false, cards = LoopAlbum.build(songs, attempts), notation = settings.degreeNotation,
            playing = playing?.first, key = playing?.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoopsUi())

    /** The loop after the cadence of its mode; a second tap on the same card stops it. */
    fun play(card: LoopCard) {
        val was = sound.value?.first
        stop()
        if (was == card.shape) return
        val key = keys.next(card.mode)
        sound.value = card.shape to key
        job = viewModelScope.launch {
            val cadence = arranger.chords(Harmony.cadence(key.mode), key, beatsEach = 1.0, tempoBpm = SongGuessDriver.CADENCE_TEMPO)
            val loop = SongGuessDriver.retimed(arranger.chords(card.chords, key, beatsEach = SongGuessDriver.CHORD_BEATS, tempoBpm = SongGuessDriver.TEMPO), SongGuessDriver.CADENCE_TEMPO)
            try {
                graph.audioOutput.play(cadence.then(loop, gapBeats = 1.5))
            } finally {
                sound.update { if (it?.first == card.shape) null else it }
            }
        }
    }

    fun stop() {
        if (job?.isActive == true) graph.audioOutput.stop()
        job?.cancel()
        job = null
        sound.value = null
    }

    override fun onCleared() = stop()
}

@Composable
fun LoopsScreen(onBack: () -> Unit, onOpenSong: (String) -> Unit) {
    val graph = LocalAppGraph.current
    val vm: LoopsViewModel = viewModel { LoopsViewModel(graph) }

    // O-66: the app leaves the screen (another app, the lock) — the sound stops, it does not hum on in the pocket
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { vm.stop() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    ScreenFrame(
        title = "Коллекция ходов",
        subtitle = if (ui.loading) null else "узнаю ${ui.known} из ${ui.cards.size}",
        accent = Accents.Tir,
        onBack = onBack,
    ) {
        item(key = "how") {
            Text(
                "Каждая карточка — ход, с которого начинаются песни твоей библиотеки. Нажми — услышишь его после каденции. " +
                    "«Узнаю» появляется, когда в «Чьей гармонии?» ответишь на этот ход трижды подряд.",
                style = MaterialTheme.typography.bodyMedium,
                color = Juicy.TextDim,
            )
        }
        if (!ui.loading && ui.cards.isEmpty()) {
            item(key = "empty") { Text("В библиотеке пока нет песен с ходом из трёх аккордов.", style = MaterialTheme.typography.bodyLarge, color = Juicy.Text) }
        }
        ui.cards.forEach { card ->
            item(key = card.shape) {
                LoopCardView(card, ui.notation, playingKey = ui.key.takeIf { ui.playing == card.shape }, onPlay = { vm.play(card) }, onOpenSong = onOpenSong)
            }
        }
    }
}

/** How many songs a card lists before «и ещё N». */
private const val SONGS_SHOWN = 4

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoopCardView(card: LoopCard, notation: DegreeNotation, playingKey: Key?, onPlay: () -> Unit, onOpenSong: (String) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent = if (card.known) Accents.Good else null, onClick = onPlay) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                card.chords.forEach { c -> DegreeChip(DegreeFormatter.chord(c, notation), DegreeColors.of(c.root), large = true) }
            }
            Spacer(Modifier.width(8.dp))
            Text(if (playingKey != null) "■" else "►", style = MaterialTheme.typography.titleLarge, color = Juicy.TextDim)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                card.known -> Badge("узнаю", Accents.Good)
                card.asked > 0 -> Badge("верно ${card.right} из ${card.asked}", Accents.Taper)
                else -> Text("ещё не спрашивали", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
            }
            Text(
                playingKey?.let { "играет: " + RuNames.key(it) } ?: card.mode.ruName,
                style = MaterialTheme.typography.bodySmall,
                color = Juicy.TextDim,
            )
        }
        Spacer(Modifier.height(6.dp))
        Column {
            card.songs.take(SONGS_SHOWN).forEach { song ->
                Text(
                    listOfNotNull(song.title, song.artist).joinToString(" · ") + if (song.learning) " · учу" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Juicy.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClickLabel = "Открыть песню") { onOpenSong(song.id) }
                        .padding(vertical = 9.dp),
                )
            }
            if (card.songs.size > SONGS_SHOWN) {
                Text("и ещё ${card.songs.size - SONGS_SHOWN}", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
            }
        }
    }
}
