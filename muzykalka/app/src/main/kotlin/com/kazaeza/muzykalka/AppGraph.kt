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
import com.kazaeza.muzykalka.data.repo.BookSeeder
import com.kazaeza.muzykalka.data.repo.LibraryRepository
import com.kazaeza.muzykalka.data.repo.PracticeRepository
import com.kazaeza.muzykalka.data.settings.AnchorStore
import com.kazaeza.muzykalka.data.settings.BookSeedStore
import com.kazaeza.muzykalka.data.settings.ProgressStore
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.data.settings.SettingsRepository
import com.kazaeza.muzykalka.srs.Fsrs
import com.kazaeza.muzykalka.srs.StudyDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.KeyCatalog
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
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

    /** O-42 (ADR-041): the songbook is part of the library; [seedBook] gives it, once per piece. */
    val bookSeeder: BookSeeder by lazy { BookSeeder(library, BookSeedStore(app)) }
    val practice: PracticeRepository by lazy { PracticeRepository(database) }
    val backup: BackupManager by lazy { BackupManager(app, database, settings, progress, BuildConfig.VERSION_NAME, anchors, bookSeeder) }

    /** Puts the pieces of the songbook the library has not been given yet on its shelf. Cheap when there is nothing new. */
    fun seedBook() {
        appScope.launch(Dispatchers.Default) { runCatching { bookSeeder.run() }.onFailure { android.util.Log.w("Muzykalka", "the songbook could not be put into the library", it) } }
    }

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
        prewarmGlass(s)
    }

    /** What the glass was last made ready for: (it is the chosen sound, the A4 of the notes). */
    private var warmedFor: Pair<Boolean, Double>? = null

    /**
     * O-39: the choir of the glass is made of tables, tens of milliseconds a table. The twelve drones and the notes
     * the games play are made once, in the background, as soon as the settings say the glass is the sound.
     */
    private fun prewarmGlass(s: Settings) {
        val wanted = s.toneTimbre.ownsDrone to s.a4Hz
        if (!wanted.first || wanted == warmedFor) return
        warmedFor = wanted
        audioOutput.a4Hz = s.a4Hz
        val arranger = Arranger()
        audioOutput.prewarm(
            drones = KeyCatalog.practical(Mode.MAJOR).map { arranger.drone(it) },
            notes = (WARM_FROM..WARM_TO).map { MidiNote(it) },
        )
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

/** O-39: the notes of the games lie between these (the tonic from G3 up, the degrees within two octaves); their tables are made ahead. */
private const val WARM_FROM = 52
private const val WARM_TO = 84
