package com.kazaeza.muzykalka.data.dto

import com.kazaeza.muzykalka.music.Bar
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.Melody
import com.kazaeza.muzykalka.music.MelodyKind
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.Section
import com.kazaeza.muzykalka.music.Song
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persisted shape of library items (ticket D-01). Human-readable on purpose: chords are stored as
 * Roman text ("ii⁷"), notes as degree text ("♭3", "5,"), so an exported backup can be read and
 * fixed by hand. Bump [CURRENT_VERSION] and keep reading old versions when the shape changes.
 */
@Serializable
sealed interface LibraryPayload

@Serializable
@SerialName("song")
data class SongPayload(
    val title: String,
    val artist: String? = null,
    val mode: String,
    val sections: List<SectionPayload>,
    val form: List<String>,
    val startFret: Int? = null,
    val notes: String = "",
) : LibraryPayload

@Serializable
data class SectionPayload(val label: String, val bars: List<List<String>>)

@Serializable
@SerialName("melody")
data class MelodyPayload(
    val title: String,
    val mode: String,
    val kind: String,
    val notes: List<String>,
    val over: String? = null,
) : LibraryPayload

object Payloads {
    const val CURRENT_VERSION = 1

    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = false
    }

    fun encode(payload: LibraryPayload): String = json.encodeToString(LibraryPayload.serializer(), payload)

    fun decode(text: String): LibraryPayload = json.decodeFromString(LibraryPayload.serializer(), text)

    fun fromSong(song: Song) = SongPayload(
        title = song.title,
        artist = song.artist,
        mode = song.mode.name,
        sections = song.sections.map { s -> SectionPayload(s.label, s.bars.map { b -> b.chords.map(ChordParser::formatRoman) }) },
        form = song.form,
        startFret = song.startFret,
        notes = song.notes,
    )

    fun toSong(id: String, p: SongPayload) = Song(
        id = id,
        title = p.title,
        artist = p.artist,
        mode = Mode.valueOf(p.mode),
        sections = p.sections.map { s -> Section(s.label, s.bars.map { b -> Bar(b.map(ChordDegree::parse)) }) },
        form = p.form,
        startFret = p.startFret,
        notes = p.notes,
    )

    fun fromMelody(m: Melody) = MelodyPayload(
        title = m.title,
        mode = m.mode.name,
        kind = m.kind.name,
        notes = m.notes.map { it.toString() },
        over = m.over?.let(ChordParser::formatRoman),
    )

    fun toMelody(id: String, p: MelodyPayload) = Melody(
        id = id,
        title = p.title,
        mode = Mode.valueOf(p.mode),
        kind = MelodyKind.valueOf(p.kind),
        notes = p.notes.map(DegreeNote::parse),
        over = p.over?.let(ChordDegree::parse),
    )
}
