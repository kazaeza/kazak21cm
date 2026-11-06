package com.kazaeza.muzykalka

import android.content.Context
import com.kazaeza.muzykalka.audio.Announcer
import com.kazaeza.muzykalka.audio.AndroidAudioOutput
import com.kazaeza.muzykalka.audio.AndroidPitchInput
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.audio.PitchInput
import com.kazaeza.muzykalka.audio.TtsAnnouncer
import com.kazaeza.muzykalka.data.db.MuzykalkaDatabase
import com.kazaeza.muzykalka.data.settings.SettingsRepository
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.srs.Fsrs
import com.kazaeza.muzykalka.srs.StudyDay
import java.time.ZoneId
import kotlin.random.Random

/**
 * Manual dependency injection (no Hilt/Koin: fewer moving parts, explicit wiring, fast builds).
 * Rule: ViewModels receive dependencies from here through their factories; nothing else
 * constructs repositories, audio engines or schedulers.
 */
class AppGraph(context: Context) {
    private val app = context.applicationContext

    val clock: () -> Long = System::currentTimeMillis
    val random: Random = Random.Default

    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val database: MuzykalkaDatabase by lazy { MuzykalkaDatabase.open(app) }

    val audioOutput: AudioOutput by lazy { AndroidAudioOutput(app) }
    val pitchInput: PitchInput by lazy { AndroidPitchInput(app) }
    val announcer: Announcer by lazy { TtsAnnouncer(app) }

    val fsrs: Fsrs by lazy { Fsrs() }
    val keyPicker: KeyPicker by lazy { KeyPicker(random) }
    fun studyDay(dayStartHour: Int): StudyDay = StudyDay(ZoneId.systemDefault(), dayStartHour)
}
