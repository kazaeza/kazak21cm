package com.kazaeza.muzykalka.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BookSeedLedger(val seen: List<String> = emptyList())

private val Context.bookSeedStore: DataStore<Preferences> by preferencesDataStore(name = "book_seed")

/**
 * O-42 (ADR-041): which pieces of the songbook the library has already been given. A piece the user deleted stays
 * in this list, so it is not put back at the next start; a new piece of a later version is not in it, so it comes in.
 * Kept in DataStore (no schema change) and carried in the backup.
 */
class BookSeedStore(context: Context) {
    private val store = context.applicationContext.bookSeedStore
    private val key = stringPreferencesKey("book_seed_ledger")
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(text: String?): BookSeedLedger =
        text?.let { runCatching { json.decodeFromString(BookSeedLedger.serializer(), it) }.getOrNull() } ?: BookSeedLedger()

    suspend fun seen(): Set<String> = decode(store.data.first()[key]).seen.toSet()

    suspend fun markSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        store.edit { p -> p[key] = json.encodeToString(BookSeedLedger.serializer(), BookSeedLedger((decode(p[key]).seen + ids).distinct())) }
    }

    suspend fun exportJson(): String = store.data.first()[key] ?: json.encodeToString(BookSeedLedger.serializer(), BookSeedLedger())

    /** A backup without a ledger (older than O-42): forget what was seen, so the book comes in again where it is absent. */
    suspend fun importJson(text: String?) {
        val parsed = text?.let { runCatching { json.decodeFromString(BookSeedLedger.serializer(), it) }.getOrNull() } ?: BookSeedLedger()
        store.edit { p -> p[key] = json.encodeToString(BookSeedLedger.serializer(), parsed) }
    }
}
