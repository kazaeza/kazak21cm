package com.kazaeza.muzykalka.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kazaeza.muzykalka.music.DEFAULT_A4_HZ
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.NoteNaming
import com.kazaeza.muzykalka.music.TapSound
import com.kazaeza.muzykalka.music.ToneTimbre
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
    /** O-36: how the note over the drone sounds. The hum was the user's pick on the lab page. */
    val toneTimbre: ToneTimbre = ToneTimbre.HUM,
    /** O-36: the wooden tap on buttons; null = no taps. */
    val tapSound: TapSound? = TapSound.TICK,
    /** O-36: volume of the taps, 0…2 (the lab page's slider; the user asked for the quietest, so it starts low). */
    val tapVolume: Float = 0.35f,
    /** O-36: a small room around notes and taps. */
    val room: Boolean = true,
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
        val TONE_TIMBRE = stringPreferencesKey("tone_timbre")
        val TAP_SOUND = stringPreferencesKey("tap_sound")
        val TAP_VOLUME = floatPreferencesKey("tap_volume")
        val ROOM = booleanPreferencesKey("room")
    }

    private companion object {
        /** DataStore has no null: «no taps» is stored as this word. */
        const val TAPS_OFF = "OFF"
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
            p[K.TONE_TIMBRE] = next.toneTimbre.name
            p[K.TAP_SOUND] = next.tapSound?.name ?: TAPS_OFF
            p[K.TAP_VOLUME] = next.tapVolume
            p[K.ROOM] = next.room
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
            toneTimbre = p[K.TONE_TIMBRE]?.let { runCatching { ToneTimbre.valueOf(it) }.getOrNull() } ?: d.toneTimbre,
            tapSound = when (val t = p[K.TAP_SOUND]) {
                null -> d.tapSound
                TAPS_OFF -> null
                else -> runCatching { TapSound.valueOf(t) }.getOrNull() ?: d.tapSound
            },
            tapVolume = (p[K.TAP_VOLUME] ?: d.tapVolume).coerceIn(0f, 2f),
            room = p[K.ROOM] ?: d.room,
        )
    }

    /** Replaces everything (backup import). */
    suspend fun replace(settings: Settings) = update { settings }
}
