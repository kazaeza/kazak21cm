package com.kazaeza.muzykalka.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kazaeza.muzykalka.game.Variant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Unlocked and selected level plus best score per variant (keys are Variant names). */
@Serializable
data class GameProgress(
    val unlocked: Map<String, Int> = emptyMap(),
    val selected: Map<String, Int> = emptyMap(),
    val records: Map<String, Int> = emptyMap(),
) {
    fun unlockedLevel(v: Variant) = unlocked[v.name] ?: 1
    fun selectedLevel(v: Variant) = (selected[v.name] ?: unlockedLevel(v)).coerceIn(1, unlockedLevel(v))
    fun record(v: Variant) = records[v.name] ?: 0
}

private val Context.progressStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

class ProgressStore(context: Context) {
    private val store = context.applicationContext.progressStore
    private val key = stringPreferencesKey("game_progress")
    private val json = Json { ignoreUnknownKeys = true }

    val progress: Flow<GameProgress> = store.data.map { p ->
        p[key]?.let { runCatching { json.decodeFromString(GameProgress.serializer(), it) }.getOrNull() } ?: GameProgress()
    }

    suspend fun current(): GameProgress = progress.first()

    private suspend fun edit(f: (GameProgress) -> GameProgress) {
        store.edit { p ->
            val cur = p[key]?.let { runCatching { json.decodeFromString(GameProgress.serializer(), it) }.getOrNull() } ?: GameProgress()
            p[key] = json.encodeToString(GameProgress.serializer(), f(cur))
        }
    }

    suspend fun select(v: Variant, level: Int) = edit { it.copy(selected = it.selected + (v.name to level)) }

    /**
     * Returns true when [level] was newly unlocked. The level played stays where it was (O-27): a new level is
     * offered, and taken with [select] — it used to switch by itself, and the next round was suddenly harder.
     */
    suspend fun unlock(v: Variant, level: Int): Boolean {
        val before = current()
        if (level <= before.unlockedLevel(v)) return false
        edit { it.copy(unlocked = it.unlocked + (v.name to level), selected = it.selected + (v.name to before.selectedLevel(v))) }
        return true
    }

    /** Returns true for a new record. */
    suspend fun submitScore(v: Variant, score: Int): Boolean {
        val before = current().record(v)
        if (score <= before) return false
        edit { it.copy(records = it.records + (v.name to score)) }
        return true
    }

    suspend fun exportJson(): String = json.encodeToString(GameProgress.serializer(), current())

    suspend fun importJson(text: String) {
        val parsed = runCatching { json.decodeFromString(GameProgress.serializer(), text) }.getOrNull() ?: return
        edit { parsed }
    }
}
