package com.kazaeza.muzykalka.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.data.repo.LibraryKind
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.feature.common.ChordInputPanel
import com.kazaeza.muzykalka.feature.common.DegreeInputPanel
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.feature.library.octaveMark
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.ui.component.ChoiceRow
import com.kazaeza.muzykalka.ui.component.DegreeChip
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.effect.pop
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy

/** «Улов» — the editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(editId: String?, kind: LibraryKind, onDone: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: EditorViewModel = viewModel(key = "editor-$editId-$kind") { EditorViewModel(graph, editId, kind) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsState(initial = Settings())
    val notation = settings.degreeNotation
    LaunchedEffect(ui.saved) { if (ui.saved) onDone() }

    val title = when {
        !ui.isNew -> "Правка"
        ui.kind == LibraryKind.SONG -> "Улов"
        ui.kind == LibraryKind.TUNE -> "Мелодия"
        else -> "Рифф"
    }
    ScreenFrame(title = title, subtitle = "ступенями, без букв", accent = Accents.Taper, onBack = onDone) {
        item(key = "title") {
            OutlinedTextField(ui.title, vm::setTitle, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (ui.kind == LibraryKind.SONG) {
            item(key = "artist") {
                OutlinedTextField(ui.artist, vm::setArtist, label = { Text("Исполнитель (необязательно)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        item(key = "mode") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("лад")
                ChoiceRow(Mode.entries, ui.mode, { it.ruName }, vm::setMode, colors = Accents.Taper)
            }
        }
        if (ui.kind == LibraryKind.SONG) {
            item(key = "mode-switch") {
                ChoiceRow(listOf(false, true), ui.textMode, { if (it) "текстом" else "кнопками" }, vm::setTextMode, colors = Accents.Calm)
            }
            if (ui.textMode) {
                item(key = "text") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(ui.text, vm::setText, label = { Text("Части и такты") }, modifier = Modifier.fillMaxWidth().height(220.dp))
                        Text("Формат: «A: | ii7 | V7 | Imaj7 |» — часть на строку, такты через |, два аккорда в такте через пробел. ♭ можно писать как b.", style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
                    }
                }
            } else {
                ui.sections.forEachIndexed { i, s ->
                    item(key = "sec-${s.label}") {
                        val active = i == ui.current
                        GlassCard(Modifier.fillMaxWidth(), accent = if (active) Accents.Taper else null, onClick = { vm.selectSection(i) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.label, style = MaterialTheme.typography.headlineSmall, color = if (active) Juicy.Gold else Juicy.TextDim)
                                Spacer(Modifier.padding(horizontal = 6.dp))
                                Text("${s.bars.count { it.isNotEmpty() }} тактов" + if (active) " · пишу сюда" else "", style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
                            }
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                s.bars.forEachIndexed { bi, bar ->
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Juicy.Ink4.copy(alpha = 0.6f))
                                            .border(1.dp, if (active && bi == s.bars.lastIndex) Juicy.Gold else Juicy.Line, RoundedCornerShape(12.dp))
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (bar.isEmpty()) Text("·", color = Juicy.TextFaint, style = MaterialTheme.typography.titleMedium)
                                        bar.forEachIndexed { ci, c -> DegreeChip(DegreeFormatter.chord(c, notation), DegreeColors.of(c.root), modifier = Modifier.pop(bi * 4 + ci + 1, 0.2f)) }
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "bar-actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("| такт", onClick = vm::newBar, modifier = Modifier.weight(1f), colors = Accents.Taper)
                        GhostButton("+ часть", onClick = vm::addSection, modifier = Modifier.weight(1f), colors = Accents.Taper)
                        GhostButton("⌫", onClick = vm::undo, modifier = Modifier.weight(0.6f), colors = Accents.Bad)
                    }
                }
                item(key = "chord-pad") { ChordInputPanel(ui.mode, notation, onChord = vm::addChord) }
            }
            item(key = "form") {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionLabel("форма")
                    Text(ui.effectiveForm.joinToString(""), style = MaterialTheme.typography.headlineMedium, color = Juicy.Gold)
                    Text(if (ui.form.isEmpty()) "по умолчанию — части по порядку; нажимай буквы, чтобы задать свою" else "своя форма", style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ui.sections.forEach { s -> DegreeChip(s.label, Juicy.Gold, large = true, onClick = { vm.appendToForm(s.label) }) }
                        if (ui.form.isNotEmpty()) DegreeChip("сброс", Juicy.Hot, large = true, onClick = vm::clearForm)
                    }
                }
            }
            item(key = "fret") {
                OutlinedTextField(ui.startFret, vm::setStartFret, label = { Text("Стартовый лад (необязательно)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
            item(key = "notes") {
                OutlinedTextField(ui.notes, vm::setNotes, label = { Text("Заметки") }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            item(key = "melody") {
                GlassCard(Modifier.fillMaxWidth(), accent = Accents.Taper) {
                    SectionLabel("ноты · ${ui.melody.size}")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (ui.melody.isEmpty()) Text("нажимай ступени ниже", color = Juicy.TextFaint)
                        ui.melody.forEachIndexed { i, n -> DegreeChip(DegreeFormatter.degree(n.degree, notation) + octaveMark(n.octave), DegreeColors.of(n.degree), large = true, modifier = Modifier.pop(i + 1, 0.2f)) }
                    }
                }
            }
            item(key = "melody-pad") { DegreeInputPanel(notation, onNote = vm::addNote) }
            item(key = "melody-undo") { GhostButton("⌫ убрать последнюю", onClick = vm::undo, modifier = Modifier.fillMaxWidth(), colors = Accents.Bad) }
        }
        ui.error?.let { err -> item(key = "error") { Text(err, color = Juicy.Hot, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { }) } }
        item(key = "save") {
            JuicyButton("Сохранить", onClick = vm::save, modifier = Modifier.fillMaxWidth(), colors = Accents.Taper, leading = "✓")
        }
    }
}
