package com.kazaeza.muzykalka.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.data.repo.LibraryEntry
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.feature.common.ruDue
import com.kazaeza.muzykalka.game.HintStep
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.srs.Rating
import com.kazaeza.muzykalka.ui.component.Badge
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One library item: formula by sections, memory status, order history, actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemScreen(id: String, onBack: () -> Unit, onEdit: (String) -> Unit, onOrder: (String) -> Unit) {
    val graph = LocalAppGraph.current
    val settings by graph.settings.settings.collectAsState(initial = Settings())
    val entries by graph.library.observeEntries(includeArchived = true).collectAsState(initial = null)
    val entry: LibraryEntry? = entries?.firstOrNull { it.id == id }
    val orders by graph.practice.observeOrdersFor(id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val today = graph.today(settings)
    val notation = settings.degreeNotation
    val loaded by produceState(false, entries) { value = entries != null }

    if (loaded && entry == null) {
        onBack()
        return
    }
    val e = entry ?: return
    ScreenFrame(title = e.title, subtitle = listOfNotNull(e.song?.artist, (e.song?.mode ?: e.melody?.mode)?.ruName).joinToString(" · "), accent = e.status.colors(), onBack = onBack) {
        if (e.orderable) {
            item(key = "status") {
                GlassCard(Modifier.fillMaxWidth(), accent = e.status.colors()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(if (e.shelved) "из сборника" else e.status.ru, e.status.colors())
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Text("повтор: " + ruDue(e.card?.dueDay, today), style = MaterialTheme.typography.titleMedium)
                    }
                    e.card?.stability?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("держится ~${it.toInt()} дн. · повторений ${e.card?.reps ?: 0} · вылетала ${e.card?.lapses ?: 0}", style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (e.shelved) {
                        // O-42: the song is in the library, not in learning yet; one touch puts it into the repeats.
                        JuicyButton("Учить: поставить в повторы", onClick = { scope.launch { graph.library.startLearning(e.id) } }, modifier = Modifier.fillMaxWidth(), colors = Accents.Taper, leading = "＋")
                        Spacer(Modifier.height(8.dp))
                        GhostButton("Сыграть сейчас", onClick = { onOrder(e.id) }, modifier = Modifier.fillMaxWidth(), colors = Accents.Taper, leading = "")
                    } else {
                        JuicyButton("Сыграть сейчас", onClick = { onOrder(e.id) }, modifier = Modifier.fillMaxWidth(), colors = Accents.Taper, leading = "")
                    }
                }
            }
        }
        e.bookEntry?.let { b -> item(key = "teaches") { Text(b.teaches, style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim) } }
        val song = e.song
        val melody = e.melody
        if (song != null) {
            item(key = "form") { Text("Форма ${song.formString} · ${song.barCount} тактов" + (song.startFret?.let { " · старт с $it лада" } ?: ""), style = MaterialTheme.typography.titleMedium, color = Juicy.TextDim) }
            song.sections.forEach { section ->
                item(key = "s-" + section.label) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(section.label, style = MaterialTheme.typography.headlineSmall, color = Juicy.Gold)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            section.bars.forEach { bar ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Juicy.Ink4.copy(alpha = 0.6f))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) { bar.chords.forEach { c -> DegreeChip(DegreeFormatter.chord(c, notation), DegreeColors.of(c.root), large = true) } }
                            }
                        }
                    }
                }
            }
            if (song.notes.isNotBlank()) item(key = "notes") { Text(song.notes, style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim) }
        } else if (melody != null) {
            item(key = "melody") {
                GlassCard(Modifier.fillMaxWidth()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        melody.notes.forEach { n -> DegreeChip(DegreeFormatter.degree(n.degree, notation) + octaveMark(n.octave), DegreeColors.of(n.degree), large = true) }
                    }
                }
            }
        }
        if (orders.isNotEmpty()) {
            item(key = "history-label") { SectionLabel("история") }
            orders.take(20).forEach { o ->
                item(key = "o-" + o.atEpochMs) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (o.playableNow) "✓" else if (o.rating == Rating.AGAIN.value) "✕" else "·", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("ru")).format(Date(o.atEpochMs)) + " · " + o.keyLabel, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "подсказка: " + (runCatching { HintStep.valueOf(o.maxHint).ru }.getOrDefault(o.maxHint)) + (o.formulaAccuracy?.let { f -> " · формула ${(f * 100).toInt()}%" } ?: "") + " · +${o.tips} ★",
                                style = MaterialTheme.typography.bodySmall,
                                color = Juicy.TextDim,
                            )
                        }
                    }
                }
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Изменить", onClick = { onEdit(e.id) }, modifier = Modifier.fillMaxWidth(), leading = "✎")
                GhostButton(if (e.archived) "Вернуть из архива" else "В архив", onClick = { scope.launch { graph.library.setArchived(e.id, !e.archived) } }, modifier = Modifier.fillMaxWidth(), leading = "")
                GhostButton("Удалить", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), colors = Accents.Bad, leading = "✕")
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${e.title}»?") },
            text = { Text("Вместе с вещью удалится её история повторений. Лучше отправить в архив.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        graph.library.delete(e.id)
                        onBack()
                    }
                }) { Text("Удалить", color = Juicy.Hot) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

fun octaveMark(octave: Int): String = when {
    octave > 0 -> "'".repeat(octave)
    octave < 0 -> ",".repeat(-octave)
    else -> ""
}
