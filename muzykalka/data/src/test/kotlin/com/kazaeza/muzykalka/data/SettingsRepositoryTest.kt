package com.kazaeza.muzykalka.data

import androidx.test.core.app.ApplicationProvider
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.data.settings.SettingsRepository
import com.kazaeza.muzykalka.music.ChordInstrument
import com.kazaeza.muzykalka.music.HintStyle
import com.kazaeza.muzykalka.music.OrbBody
import com.kazaeza.muzykalka.music.ToneTimbre
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `the glass is the sound of a fresh install, and any choice is kept`() = runBlocking {
        assertEquals(ToneTimbre.GLASS, Settings().toneTimbre)
        val repo = SettingsRepository(context)
        for (t in ToneTimbre.entries) {
            repo.update { it.copy(toneTimbre = t) }
            assertEquals(t, repo.settings.first().toneTimbre)
        }
        repo.update { it.copy(toneTimbre = ToneTimbre.GLASS, a4Hz = 442.0) } // writes every key; the choice stays
        assertEquals(ToneTimbre.GLASS, repo.settings.first().toneTimbre)
        repo.update { it.copy(a4Hz = 440.0) }
    }

    @Test
    fun `the thread is the sphere of a fresh install, and any choice is kept`() = runBlocking {
        assertEquals(OrbBody.THREAD, Settings().orbBody)
        val repo = SettingsRepository(context)
        for (b in OrbBody.entries) {
            repo.update { it.copy(orbBody = b) }
            assertEquals(b, repo.settings.first().orbBody)
        }
        repo.update { it.copy(orbBody = OrbBody.INK, a4Hz = 441.0) } // writes every key; the choice stays
        assertEquals(OrbBody.INK, repo.settings.first().orbBody)
        repo.update { it.copy(orbBody = OrbBody.THREAD, a4Hz = 440.0) }
    }

    @Test
    fun `chords are played on the piano by default, and the guitar choice is kept`() = runBlocking {
        assertEquals(ChordInstrument.PIANO, Settings().chordInstrument)
        val repo = SettingsRepository(context)
        assertEquals(ChordInstrument.PIANO, repo.settings.first().chordInstrument)
        for (c in ChordInstrument.entries) {
            repo.update { it.copy(chordInstrument = c) }
            assertEquals(c, repo.settings.first().chordInstrument)
        }
        repo.update { it.copy(chordInstrument = ChordInstrument.GUITAR, a4Hz = 441.0) } // writes every key; the choice stays
        assertEquals(ChordInstrument.GUITAR, repo.settings.first().chordInstrument)
        repo.update { it.copy(chordInstrument = ChordInstrument.PIANO, a4Hz = 440.0) }
    }

    @Test
    fun `hints speak of the feel by default, and a song choice is kept`() = runBlocking {
        assertEquals(HintStyle.FEEL, Settings().hintStyle)
        val repo = SettingsRepository(context)
        for (h in HintStyle.entries) {
            repo.update { it.copy(hintStyle = h) }
            assertEquals(h, repo.settings.first().hintStyle)
        }
        repo.update { it.copy(hintStyle = HintStyle.SONGS, a4Hz = 441.0) } // writes every key; the choice stays
        assertEquals(HintStyle.SONGS, repo.settings.first().hintStyle)
        repo.update { it.copy(hintStyle = HintStyle.FEEL, a4Hz = 440.0) }
    }

    @Test
    fun `quiet mode, mic boost and active track round-trip with clamping`() = runBlocking {
        val defaults = Settings()
        assertEquals(false, defaults.quietMode)
        assertEquals(12, defaults.micBoostDb)
        assertEquals("STEPS_BASE", defaults.activeTrackId)

        val repo = SettingsRepository(context)
        repo.update { it.copy(quietMode = true, micBoostDb = 18, activeTrackId = "CHORDS_BASE") }
        val saved = repo.settings.first()
        assertEquals(true, saved.quietMode)
        assertEquals(18, saved.micBoostDb)
        assertEquals("CHORDS_BASE", saved.activeTrackId)

        repo.update { it.copy(micBoostDb = 40) }
        assertEquals(24, repo.settings.first().micBoostDb)
        repo.update { it.copy(micBoostDb = -5) }
        assertEquals(0, repo.settings.first().micBoostDb)

        repo.update { it.copy(quietMode = false, micBoostDb = 12, activeTrackId = "STEPS_BASE") }
    }
}
