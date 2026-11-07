package com.kazaeza.muzykalka.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.data.repo.LibraryEntry
import com.kazaeza.muzykalka.data.repo.LibraryKind
import com.kazaeza.muzykalka.data.repo.MemoryStatus
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.feature.common.ruDue
import com.kazaeza.muzykalka.music.BookEntry
import com.kazaeza.muzykalka.music.BookGenre
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.SongBook
import com.kazaeza.muzykalka.ui.component.Badge
import com.kazaeza.muzykalka.ui.component.ChoiceRow
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.launch

fun MemoryStatus.colors() = when (this) {
    MemoryStatus.NEW -> listOf(Juicy.Blue, Juicy.Violet)
    MemoryStatus.LEARNING -> Accents.Taper
    MemoryStatus.HOLDING -> Accents.Good
}

private enum class Filter(val ru: String) { ALL("все"), SONGS("песни"), RIFFS("риффы"), TUNES("мелодии") }

/** «Библиотека» (ticket L-01): the user's pieces, then the built-in songbook (ADR-013). */
@Composable
fun LibraryScreen(onCapture: (LibraryKind) -> Unit, onOpen: (String) -> Unit) {
    val graph = LocalAppGraph.current
    val entries by graph.library.observeEntries().collectAsState(initial = null)
    val inLibrary by graph.library.observeBookIds().collectAsState(initial = emptySet())
    val settings by graph.settings.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(Filter.ALL) }
    var genre by remember { mutableStateOf(BookGenre.LIKED) }
    val today = graph.today(settings)
    val list = entries ?: emptyList()
    val shown = list.filter {
        when (filter) {
            Filter.ALL -> true
            Filter.SONGS -> it.kind == LibraryKind.SONG
            Filter.RIFFS -> it.kind == LibraryKind.RIFF
            Filter.TUNES -> it.kind == LibraryKind.TUNE
        }
    }
    ScreenFrame(title = "Библиотека", subtitle = "мои песни · ${list.size}", accent = Accents.Taper) {
        item(key = "capture") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                JuicyButton("Записать свою", onClick = { onCapture(LibraryKind.SONG) }, modifier = Modifier.weight(1f), colors = Accents.Taper, leading = "🎣")
                GhostButton("Рифф", onClick = { onCapture(LibraryKind.RIFF) }, modifier = Modifier.weight(0.5f), colors = Accents.Taper, height = 64.dp)
            }
        }
        if (entries != null && list.isEmpty()) {
            item(key = "empty") {
                GlassCard(Modifier.fillMaxWidth(), accent = Accents.Taper) {
                    Text("Пока пусто", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Возьми песню из сборника ниже — одним касанием. Или запиши свою ступенями: I–VII и качество аккорда. " +
                            "Буквы не нужны: тональность каждый раз выбирается случайно.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Juicy.TextDim,
                    )
                }
            }
        } else if (list.isNotEmpty()) {
            item(key = "filter") {
                ChoiceRow(Filter.entries, filter, { it.ru }, { filter = it }, colors = Accents.Taper)
            }
            shown.forEach { e -> item(key = e.id) { EntryCard(e, today, settings.degreeNotation) { onOpen(e.id) } } }
        }
        item(key = "book-label") {
            Column {
                Spacer(Modifier.height(10.dp))
                Text("📖 Сборник", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Гармония известных песен — обучающих, хитов 1958–1990 годов и джазовых стандартов — ступенями. Упрощено до основных аккордов: после добавления можно поправить под свою версию.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Juicy.TextDim,
                )
                Spacer(Modifier.height(10.dp))
                ChoiceRow(BookGenre.entries, genre, { it.ru }, { genre = it }, colors = Accents.Taper)
            }
        }
        SongBook.ENTRIES.filter { it.genre == genre }.sortedBy { it.difficulty }.forEach { b ->
            item(key = "book-" + b.id) {
                val id = SongBook.libraryId(b)
                val added = id in inLibrary
                BookCard(b, added, settings.degreeNotation, onAdd = { scope.launch { graph.library.addFromBook(b) } }, onOpen = { onOpen(id) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookCard(b: BookEntry, added: Boolean, notation: DegreeNotation, onAdd: () -> Unit, onOpen: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent = if (added) Accents.Good else null, onClick = if (added) onOpen else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(b.song.title, style = MaterialTheme.typography.titleLarge)
                val sub = listOfNotNull(b.song.artist, b.song.mode.ruName, "●".repeat(b.difficulty) + "○".repeat(3 - b.difficulty)).joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
            }
            Spacer(Modifier.width(8.dp))
            if (added) {
                Badge("✓ в моих", Accents.Good)
            } else {
                GhostButton("В мои", onClick = onAdd, colors = Accents.Taper, leading = "＋", height = 48.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(b.teaches, style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            b.song.chordsInOrder.distinct().take(8).forEach { c -> DegreeChip(DegreeFormatter.chord(c, notation), DegreeColors.of(c.root)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(e: LibraryEntry, today: Int, notation: DegreeNotation, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent = e.status.colors(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (e.kind) {
                    LibraryKind.SONG -> "🎵"
                    LibraryKind.RIFF -> "🎸"
                    LibraryKind.TUNE -> "🎶"
                    LibraryKind.PHRASE -> "💬"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(1.dp).fillMaxWidth(0.03f))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleLarge)
                val sub = listOfNotNull(e.song?.artist, e.song?.formString?.let { "форма $it" }, e.song?.mode?.ruName ?: e.melody?.mode?.ruName).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
            }
            if (e.orderable) Badge(e.status.ru, e.status.colors())
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val song = e.song
            val melody = e.melody
            when {
                e.unsupported -> Text("нужна новая версия приложения", color = Juicy.Hot, style = MaterialTheme.typography.bodySmall)
                song != null -> song.sections.first().bars.flatMap { it.chords }.take(8).forEach { c ->
                    DegreeChip(DegreeFormatter.chord(c, notation), DegreeColors.of(c.root))
                }
                melody != null -> melody.notes.take(10).forEach { n -> DegreeChip(DegreeFormatter.degree(n.degree, notation), DegreeColors.of(n.degree)) }
            }
        }
        if (e.orderable) {
            Spacer(Modifier.height(8.dp))
            Text("🔁 повтор: " + ruDue(e.card?.dueDay, today), style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
        }
    }
}
