package com.kazaeza.muzykalka

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.kazaeza.muzykalka.audio.AndroidAudioOutput
import com.kazaeza.muzykalka.audio.AndroidPitchInput
import com.kazaeza.muzykalka.audio.Announcer
import com.kazaeza.muzykalka.audio.AudioOutput
import com.kazaeza.muzykalka.audio.DuplexGate
import com.kazaeza.muzykalka.audio.HeadphoneMonitor
import com.kazaeza.muzykalka.audio.PitchInput
import com.kazaeza.muzykalka.audio.TtsAnnouncer
import com.kazaeza.muzykalka.data.backup.BackupManager
import com.kazaeza.muzykalka.data.db.MuzykalkaDatabase
import com.kazaeza.muzykalka.data.repo.LibraryRepository
import com.kazaeza.muzykalka.data.repo.PracticeRepository
import com.kazaeza.muzykalka.data.settings.AnchorStore
import com.kazaeza.muzykalka.data.settings.ProgressStore
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.data.settings.SettingsRepository
import com.kazaeza.muzykalka.srs.Fsrs
import com.kazaeza.muzykalka.srs.StudyDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Instant
import java.time.ZoneId

/**
 * Manual dependency injection (no Hilt/Koin: fewer moving parts, explicit wiring, fast builds).
 * ViewModels receive this graph; nothing else constructs repositories, audio engines or schedulers.
 * Everything heavy is lazy: nothing touches audio or disk until a screen asks.
 * Open so tests can swap the audio side for fakes.
 */
open class AppGraph(context: Context) {
    val app: Context = context.applicationContext

    /** Outlives screens: used to finish saving when a game is closed mid-round. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val progress: ProgressStore by lazy { ProgressStore(app) }
    val anchors: AnchorStore by lazy { AnchorStore(app) }
    val database: MuzykalkaDatabase by lazy { MuzykalkaDatabase.open(app) }
    val library: LibraryRepository by lazy { LibraryRepository(database) }
    val practice: PracticeRepository by lazy { PracticeRepository(database) }
    val backup: BackupManager by lazy { BackupManager(app, database, settings, progress, BuildConfig.VERSION_NAME, anchors) }

    open val audioOutput: AudioOutput by lazy { AndroidAudioOutput(app) }
    open val pitchInput: PitchInput by lazy { AndroidPitchInput(app) }
    open val announcer: Announcer by lazy { TtsAnnouncer(app) }
    val headphones: HeadphoneMonitor by lazy { HeadphoneMonitor(app) }
    val gate = DuplexGate()

    val fsrs: Fsrs by lazy { Fsrs() }

    fun studyDay(settings: Settings): StudyDay = StudyDay(ZoneId.systemDefault(), settings.dayStartHour)

    fun today(settings: Settings): Int = studyDay(settings).of(Instant.now())
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }
