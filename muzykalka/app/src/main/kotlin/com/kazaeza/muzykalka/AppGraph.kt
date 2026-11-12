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
import com.kazaeza.muzykalka.music.TapSound
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

    // --- O-36: the sound settings as the output and the buttons need them, without waiting for DataStore on every tap
    @Volatile
    private var tapSound: TapSound? = null

    @Volatile
    private var tapVolume = 1f

    @Volatile
    private var onScreen = false

    /** Gives [s] to the output: the timbre of the note over the drone, the room, and the taps' settings. */
    fun applySound(s: Settings) {
        audioOutput.toneTimbre = s.toneTimbre
        audioOutput.room = s.room
        tapSound = s.tapSound
        tapVolume = s.tapVolume
        audioOutput.keepOpen(onScreen && s.tapSound != null)
    }

    /** The app is on screen (Activity started) or not: while it is, and taps are on, the output stays open so the first tap is not late. */
    fun setOnScreen(on: Boolean) {
        onScreen = on
        audioOutput.keepOpen(on && tapSound != null)
    }

    /**
     * The tap of a pressed button ([low]: back, cancel, pause). Silent when taps are off, and whenever the microphone
     * is open — the app must not hear itself (the tap is not pitched, but a quiet room is a quiet room).
     */
    fun tap(low: Boolean) {
        val sound = tapSound ?: return
        if (pitchInput.isRunning.value) return
        audioOutput.tap(sound, low, tapVolume)
    }

    fun studyDay(settings: Settings): StudyDay = StudyDay(ZoneId.systemDefault(), settings.dayStartHour)

    fun today(settings: Settings): Int = studyDay(settings).of(Instant.now())
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }
