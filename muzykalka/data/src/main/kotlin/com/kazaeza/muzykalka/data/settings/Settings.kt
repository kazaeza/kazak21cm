package com.kazaeza.muzykalka.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kazaeza.muzykalka.music.DEFAULT_A4_HZ
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.NoteNaming
import com.kazaeza.muzykalka.music.Tuning
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** Everything on the «Настройки» screen. Defaults answer the plan's open questions; all changeable. */
@Serializable
data class Settings(
    val tuningId: String = Tuning.STANDARD.id,
    val fretCount: Int = 22,
    /** AudioDeviceInfo.id of the preferred input (USB interface); null = built-in mic. */
    val inputDeviceId: Int? = null,
    val degreeNotation: DegreeNotation = DegreeNotation.ARABIC,
    val noteNaming: NoteNaming = NoteNaming.LATIN,
    /** Spoken orders/keys use Russian syllables regardless of [noteNaming]. */
    val speakKeys: Boolean = true,
    val headphones: Boolean = false,
    val a4Hz: Double = DEFAULT_A4_HZ,
    val dailyMinutes: Int = 20,
    /** Study day starts at this local hour (see core:srs StudyDay). */
    val dayStartHour: Int = 4,
    val leftHanded: Boolean = false,
    /** Haptic feedback in games. */
    val haptics: Boolean = true,
) {
    val tuning: Tuning get() = Tuning.byId(tuningId) ?: Tuning.STANDARD
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object K {
        val TUNING = stringPreferencesKey("tuning")
        val FRETS = intPreferencesKey("frets")
        val INPUT = intPreferencesKey("input_device")
        val NOTATION = stringPreferencesKey("degree_notation")
        val NAMING = stringPreferencesKey("note_naming")
        val SPEAK = booleanPreferencesKey("speak_keys")
        val PHONES = booleanPreferencesKey("headphones")
        val A4 = doublePreferencesKey("a4")
        val MINUTES = intPreferencesKey("daily_minutes")
        val DAY_START = intPreferencesKey("day_start_hour")
        val LEFTY = booleanPreferencesKey("left_handed")
        val HAPTICS = booleanPreferencesKey("haptics")
    }

    val settings: Flow<Settings> = store.data.map(::read)

    /** Read-modify-write of the whole object keeps call sites simple: `update { it.copy(a4Hz = 442.0) }`. */
    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val next = transform(read(p))
            p[K.TUNING] = next.tuningId
            p[K.FRETS] = next.fretCount
            if (next.inputDeviceId == null) p.remove(K.INPUT) else p[K.INPUT] = next.inputDeviceId
            p[K.NOTATION] = next.degreeNotation.name
            p[K.NAMING] = next.noteNaming.name
            p[K.SPEAK] = next.speakKeys
            p[K.PHONES] = next.headphones
            p[K.A4] = next.a4Hz
            p[K.MINUTES] = next.dailyMinutes
            p[K.DAY_START] = next.dayStartHour
            p[K.LEFTY] = next.leftHanded
            p[K.HAPTICS] = next.haptics
        }
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            tuningId = p[K.TUNING] ?: d.tuningId,
            fretCount = p[K.FRETS] ?: d.fretCount,
            inputDeviceId = p[K.INPUT],
            degreeNotation = p[K.NOTATION]?.let { runCatching { DegreeNotation.valueOf(it) }.getOrNull() } ?: d.degreeNotation,
            noteNaming = p[K.NAMING]?.let { runCatching { NoteNaming.valueOf(it) }.getOrNull() } ?: d.noteNaming,
            speakKeys = p[K.SPEAK] ?: d.speakKeys,
            headphones = p[K.PHONES] ?: d.headphones,
            a4Hz = p[K.A4] ?: d.a4Hz,
            dailyMinutes = p[K.MINUTES] ?: d.dailyMinutes,
            dayStartHour = p[K.DAY_START] ?: d.dayStartHour,
            leftHanded = p[K.LEFTY] ?: d.leftHanded,
            haptics = p[K.HAPTICS] ?: d.haptics,
        )
    }

    /** Replaces everything (backup import). */
    suspend fun replace(settings: Settings) = update { settings }
}
