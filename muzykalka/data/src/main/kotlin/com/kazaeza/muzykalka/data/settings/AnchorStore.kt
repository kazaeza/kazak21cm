package com.kazaeza.muzykalka.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeAnchor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One stored anchor; degree and melody in the canonical text form ("♭3", "5 5 6 5 1' 7"). */
@Serializable
data class AnchorDto(val degree: String, val title: String, val melody: String)

@Serializable
data class AnchorBook(val anchors: List<AnchorDto> = emptyList())

private val Context.anchorStore: DataStore<Preferences> by preferencesDataStore(name = "anchors")

/**
 * O-09: the user's anchor songs, one per degree (enharmonic spellings share one: ♯4 = ♭5).
 * Keyed by semitones from the tonic. Unreadable entries are skipped, never fatal.
 */
class AnchorStore(context: Context) {
    private val store = context.applicationContext.anchorStore
    private val key = stringPreferencesKey("anchor_book")
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(text: String?): AnchorBook =
        text?.let { runCatching { json.decodeFromString(AnchorBook.serializer(), it) }.getOrNull() } ?: AnchorBook()

    /** Semitones (0..11) → anchor. */
    val anchors: Flow<Map<Int, DegreeAnchor>> = store.data.map { p ->
        decode(p[key]).anchors.mapNotNull { dto ->
            runCatching { DegreeAnchor.parse(Degree.parse(dto.degree), dto.title, dto.melody).getOrThrow() }.getOrNull()
        }.associateBy { it.degree.semitones }
    }

    suspend fun current(): Map<Int, DegreeAnchor> = anchors.first()

    private suspend fun edit(f: (List<AnchorDto>) -> List<AnchorDto>) {
        store.edit { p -> p[key] = json.encodeToString(AnchorBook.serializer(), AnchorBook(f(decode(p[key]).anchors))) }
    }

    private fun sameSlot(dto: AnchorDto, degree: Degree) = runCatching { Degree.parse(dto.degree).semitones == degree.semitones }.getOrDefault(false)

    suspend fun set(anchor: DegreeAnchor) = edit { list ->
        list.filterNot { sameSlot(it, anchor.degree) } + AnchorDto(anchor.degree.toString(), anchor.title, anchor.melodyText)
    }

    suspend fun remove(degree: Degree) = edit { list -> list.filterNot { sameSlot(it, degree) } }

    suspend fun exportJson(): String = store.data.first()[key] ?: json.encodeToString(AnchorBook.serializer(), AnchorBook())

    suspend fun importJson(text: String) {
        val parsed = runCatching { json.decodeFromString(AnchorBook.serializer(), text) }.getOrNull() ?: return
        store.edit { p -> p[key] = json.encodeToString(AnchorBook.serializer(), parsed) }
    }
}
