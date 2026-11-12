package com.kazaeza.muzykalka.data

import androidx.test.core.app.ApplicationProvider
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.data.settings.SettingsRepository
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
}
