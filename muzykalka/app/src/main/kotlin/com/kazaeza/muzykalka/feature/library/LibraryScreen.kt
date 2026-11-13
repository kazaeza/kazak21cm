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
import com.kazaeza.muzykalka.music.BookGenre
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
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

/** O-42: where a piece comes from or how far it is — one row of chips over the whole library. */
private sealed interface Shelf {
    data object All : Shelf
    data object Learning : Shelf
    data object Mine : Shelf
    data class Genre(val genre: BookGenre) : Shelf
}

private val SHELVES: List<Shelf> = listOf(Shelf.All, Shelf.Learning, Shelf.Mine) + BookGenre.entries.map { Shelf.Genre(it) }

private fun Shelf.ru() = when (this) {
    Shelf.All -> "все"
    Shelf.Learning -> "учу"
    Shelf.Mine -> "мои"
    is Shelf.Genre -> genre.ru
}

private fun Shelf.holds(e: LibraryEntry): Boolean = when (this) {
    Shelf.All -> true
    Shelf.Learning -> e.orderable && e.card != null && !e.shelved
    Shelf.Mine -> e.bookEntry == null
    is Shelf.Genre -> e.bookEntry?.genre == genre
}

/** The user's own first (the newest on top), then the book by genre and difficulty; the title order of the repository breaks ties. */
private fun libraryOrder(list: List<LibraryEntry>): List<LibraryEntry> =
    list.sortedWith(
        compareBy<LibraryEntry>({ it.bookEntry != null }, { it.bookEntry?.genre?.ordinal ?: -1 }, { it.bookEntry?.difficulty ?: 0 })
            .thenByDescending { if (it.bookEntry == null) it.updatedAtMs else 0L },
    )

/** «Библиотека» (L-01, O-42): one library — the songs of the book and the user's own, each with its own card (ADR-041). */
@Composable
fun LibraryScreen(onCapture: (LibraryKind) -> Unit, onOpen: (String) -> Unit) {
    val graph = LocalAppGraph.current
    val entries by graph.library.observeEntries().collectAsState(initial = null)
    val settings by graph.settings.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(Filter.ALL) }
    var shelf by remember { mutableStateOf<Shelf>(Shelf.All) }
    val today = graph.today(settings)
    val list = entries ?: emptyList()
    val ordered = remember(list) { libraryOrder(list) }
    val shown = ordered.filter { shelf.holds(it) }.filter {
        when (filter) {
            Filter.ALL -> true
            Filter.SONGS -> it.kind == LibraryKind.SONG
            Filter.RIFFS -> it.kind == LibraryKind.RIFF
            Filter.TUNES -> it.kind == LibraryKind.TUNE
        }
    }
    val learning = list.count { Shelf.Learning.holds(it) }
    ScreenFrame(title = "Библиотека", subtitle = "${list.size} · учу $learning", accent = Accents.Taper) {
        item(key = "capture") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                JuicyButton("Записать свою", onClick = { onCapture(LibraryKind.SONG) }, modifier = Modifier.weight(1f), colors = Accents.Taper, leading = "")
                GhostButton("Рифф", onClick = { onCapture(LibraryKind.RIFF) }, modifier = Modifier.weight(0.5f), colors = Accents.Taper, height = 64.dp)
            }
        }
        if (entries != null && list.isEmpty()) {
            item(key = "empty") {
                GlassCard(Modifier.fillMaxWidth(), accent = Accents.Taper) {
                    Text("Пока пусто", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Запиши свою песню ступенями: I–VII и качество аккорда. " +
                            "Буквы не нужны: тональность каждый раз выбирается случайно.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Juicy.TextDim,
                    )
                }
            }
        } else if (list.isNotEmpty()) {
            item(key = "shelves") {
                ChoiceRow(SHELVES, shelf, { s -> s.ru() + " " + list.count { s.holds(it) } }, { shelf = it }, colors = Accents.Taper)
            }
            if (list.any { it.kind != LibraryKind.SONG }) {
                item(key = "filter") {
                    ChoiceRow(Filter.entries, filter, { it.ru }, { filter = it }, colors = Accents.Taper)
                }
            }
            if (list.any { it.bookEntry != null }) {
                item(key = "note") {
                    Text(
                        "В библиотеке лежит гармония известных песен — обучающих, хитов 1958–1990 годов и джазовых стандартов — ступенями. " +
                            "Упрощено до основных аккордов: каждую можно поправить под свою версию или удалить. «＋ учить» ставит песню в повторы.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Juicy.TextDim,
                    )
                }
            }
            shown.forEach { e ->
                item(key = e.id) {
                    EntryCard(e, today, settings.degreeNotation, onClick = { onOpen(e.id) }, onLearn = { scope.launch { graph.library.startLearning(e.id) } })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(e: LibraryEntry, today: Int, notation: DegreeNotation, onClick: () -> Unit, onLearn: () -> Unit) {
    val book = e.bookEntry
    GlassCard(Modifier.fillMaxWidth(), accent = if (e.shelved) null else e.status.colors(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (e.kind) {
                    LibraryKind.SONG -> ""
                    LibraryKind.RIFF -> ""
                    LibraryKind.TUNE -> ""
                    LibraryKind.PHRASE -> ""
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(1.dp).fillMaxWidth(0.03f))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleLarge)
                val sub = listOfNotNull(
                    e.song?.artist,
                    e.song?.formString?.let { "форма $it" },
                    e.song?.mode?.ruName ?: e.melody?.mode?.ruName,
                    book?.let { "●".repeat(it.difficulty) + "○".repeat(3 - it.difficulty) },
                ).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
            }
            if (e.shelved) {
                Spacer(Modifier.width(8.dp))
                GhostButton("учить", onClick = onLearn, colors = Accents.Taper, leading = "＋", height = 48.dp)
            } else if (e.orderable) {
                Badge(e.status.ru, e.status.colors())
            }
        }
        if (book != null) {
            Spacer(Modifier.height(6.dp))
            Text(book.teaches, style = MaterialTheme.typography.bodyMedium, color = Juicy.Text)
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
        if (e.orderable && !e.shelved) {
            Spacer(Modifier.height(8.dp))
            Text("повтор:" + ruDue(e.card?.dueDay, today), style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
        }
    }
}
