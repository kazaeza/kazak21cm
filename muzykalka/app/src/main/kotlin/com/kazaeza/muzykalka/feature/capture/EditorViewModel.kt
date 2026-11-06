package com.kazaeza.muzykalka.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.data.repo.LibraryKind
import com.kazaeza.muzykalka.data.repo.LibraryRepository
import com.kazaeza.muzykalka.music.Bar
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.Melody
import com.kazaeza.muzykalka.music.MelodyKind
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.ProgressionText
import com.kazaeza.muzykalka.music.Section
import com.kazaeza.muzykalka.music.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SectionDraft(val label: String, val bars: List<List<ChordDegree>>)

data class EditorUi(
    val id: String,
    val kind: LibraryKind,
    val isNew: Boolean = true,
    val title: String = "",
    val artist: String = "",
    val mode: Mode = Mode.MAJOR,
    val sections: List<SectionDraft> = listOf(SectionDraft("A", listOf(emptyList()))),
    val current: Int = 0,
    val form: List<String> = emptyList(),
    val startFret: String = "",
    val notes: String = "",
    val melody: List<DegreeNote> = emptyList(),
    val textMode: Boolean = false,
    val text: String = "",
    val error: String? = null,
    val saved: Boolean = false,
) {
    val effectiveForm: List<String> get() = form.ifEmpty { sections.map { it.label } }
    val chordCount: Int get() = sections.sumOf { s -> s.bars.sumOf { it.size } }
}

/** «Улов» (ticket L-02): capture a piece in degrees in ~30 seconds, or edit an existing one. */
class EditorViewModel(private val graph: AppGraph, editId: String?, kind: LibraryKind) : ViewModel() {
    private val state = MutableStateFlow(EditorUi(id = editId ?: LibraryRepository.newId(), kind = kind, isNew = editId == null))
    val ui: StateFlow<EditorUi> = state.asStateFlow()

    init {
        if (editId != null) viewModelScope.launch { load(editId) }
    }

    private suspend fun load(id: String) {
        val e = graph.library.get(id) ?: return
        val song = e.song
        val melody = e.melody
        state.update {
            when {
                song != null -> it.copy(
                    kind = LibraryKind.SONG, title = song.title, artist = song.artist ?: "", mode = song.mode,
                    sections = song.sections.map { s -> SectionDraft(s.label, s.bars.map { b -> b.chords }) },
                    form = song.form, startFret = song.startFret?.toString() ?: "", notes = song.notes, current = 0,
                )
                melody != null -> it.copy(kind = e.kind, title = melody.title, mode = melody.mode, melody = melody.notes)
                else -> it
            }
        }
    }

    fun setTitle(v: String) = state.update { it.copy(title = v, error = null) }
    fun setArtist(v: String) = state.update { it.copy(artist = v) }
    fun setMode(m: Mode) = state.update { it.copy(mode = m) }
    fun setStartFret(v: String) = state.update { it.copy(startFret = v.filter(Char::isDigit).take(2)) }
    fun setNotes(v: String) = state.update { it.copy(notes = v) }
    fun selectSection(i: Int) = state.update { it.copy(current = i.coerceIn(0, it.sections.lastIndex)) }

    fun addChord(c: ChordDegree) = state.update { ui ->
        val sections = ui.sections.toMutableList()
        val s = sections[ui.current]
        val bars = s.bars.toMutableList()
        if (bars.last().size >= 4) bars += listOf(listOf(c)) else bars[bars.lastIndex] = bars.last() + c
        sections[ui.current] = s.copy(bars = bars)
        ui.copy(sections = sections, error = null)
    }

    /** Starts a new bar (two chords in one bar = tap them without «такт» between). */
    fun newBar() = state.update { ui ->
        val sections = ui.sections.toMutableList()
        val s = sections[ui.current]
        if (s.bars.last().isEmpty()) return@update ui
        sections[ui.current] = s.copy(bars = s.bars + listOf(emptyList()))
        ui.copy(sections = sections)
    }

    fun undo() = state.update { ui ->
        if (ui.kind != LibraryKind.SONG) return@update ui.copy(melody = ui.melody.dropLast(1))
        val sections = ui.sections.toMutableList()
        val s = sections[ui.current]
        val bars = s.bars.toMutableList()
        if (bars.last().isEmpty()) {
            if (bars.size > 1) bars.removeAt(bars.lastIndex) else if (sections.size > 1) {
                sections.removeAt(ui.current)
                return@update ui.copy(sections = sections, current = (ui.current - 1).coerceAtLeast(0), form = ui.form.filter { l -> sections.any { it.label == l } })
            }
        } else {
            bars[bars.lastIndex] = bars.last().dropLast(1)
        }
        sections[ui.current] = s.copy(bars = bars)
        ui.copy(sections = sections)
    }

    fun addSection() = state.update { ui ->
        val used = ui.sections.map { it.label }.toSet()
        val label = ('A'..'Z').map { it.toString() }.first { it !in used }
        ui.copy(sections = ui.sections + SectionDraft(label, listOf(emptyList())), current = ui.sections.size)
    }

    fun appendToForm(label: String) = state.update { it.copy(form = it.form + label) }
    fun clearForm() = state.update { it.copy(form = emptyList()) }

    fun addNote(n: DegreeNote) = state.update { it.copy(melody = it.melody + n, error = null) }

    fun setTextMode(on: Boolean) = state.update { ui ->
        if (on) {
            val text = runCatching { ProgressionText.format(buildSections(ui)) }.getOrDefault("A: | I | IV | V | I |")
            ui.copy(textMode = true, text = text)
        } else {
            val parsed = runCatching { ProgressionText.parseSections(ui.text) }.getOrNull()
            if (parsed == null) ui.copy(error = "Не разобрал текст: проверь формат «A: | ii7 | V7 | I |»")
            else ui.copy(textMode = false, sections = parsed.map { SectionDraft(it.label, it.bars.map { b -> b.chords }) }, current = 0, error = null)
        }
    }

    fun setText(v: String) = state.update { it.copy(text = v, error = null) }

    private fun buildSections(ui: EditorUi): List<Section> =
        if (ui.textMode) ProgressionText.parseSections(ui.text)
        else ui.sections.map { s -> Section(s.label, s.bars.filter { it.isNotEmpty() }.map(::Bar)) }

    fun save() {
        val ui = state.value
        val result = runCatching {
            if (ui.title.isBlank()) throw IllegalArgumentException("Как называется вещь?")
            when (ui.kind) {
                LibraryKind.SONG -> {
                    val sections = buildSections(ui).filter { it.bars.isNotEmpty() }
                    if (sections.isEmpty()) throw IllegalArgumentException("Добавь хотя бы один аккорд")
                    val labels = sections.map { it.label }.toSet()
                    val form = ui.effectiveForm.filter { it in labels }.ifEmpty { sections.map { it.label } }
                    Song(
                        id = ui.id, title = ui.title.trim(), artist = ui.artist.trim().ifBlank { null }, mode = ui.mode,
                        sections = sections, form = form, startFret = ui.startFret.toIntOrNull(), notes = ui.notes.trim(),
                    )
                }
                else -> {
                    if (ui.melody.isEmpty()) throw IllegalArgumentException("Добавь хотя бы одну ноту")
                    Melody(ui.id, ui.title.trim(), ui.mode, if (ui.kind == LibraryKind.TUNE) MelodyKind.TUNE else MelodyKind.RIFF, ui.melody)
                }
            }
        }
        result.onFailure { e -> state.update { it.copy(error = e.message ?: "Не получилось сохранить") } }
        result.onSuccess { item ->
            viewModelScope.launch {
                when (item) {
                    is Song -> graph.library.saveSong(item)
                    is Melody -> graph.library.saveMelody(item)
                }
                state.update { it.copy(saved = true) }
            }
        }
    }
}
