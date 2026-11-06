package com.kazaeza.muzykalka.data

import androidx.test.core.app.ApplicationProvider
import com.kazaeza.muzykalka.data.backup.BackupManager
import com.kazaeza.muzykalka.data.db.MuzykalkaDatabase
import com.kazaeza.muzykalka.data.repo.LibraryKind
import com.kazaeza.muzykalka.data.repo.LibraryRepository
import com.kazaeza.muzykalka.data.repo.MemoryStatus
import com.kazaeza.muzykalka.data.repo.PracticeRepository
import com.kazaeza.muzykalka.data.repo.SessionKind
import com.kazaeza.muzykalka.data.settings.ProgressStore
import com.kazaeza.muzykalka.data.settings.SettingsRepository
import com.kazaeza.muzykalka.game.AttemptRecord
import com.kazaeza.muzykalka.game.SelfGrade
import com.kazaeza.muzykalka.game.TaperGrading
import com.kazaeza.muzykalka.game.TaperSession
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Melody
import com.kazaeza.muzykalka.music.MelodyKind
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.srs.Fsrs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepositoriesTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = MuzykalkaDatabase.inMemory(context)
    private val library = LibraryRepository(db)
    private val practice = PracticeRepository(db)

    @After
    fun close() = db.close()

    @Test
    fun songRoundTripAndOrderUpdatesTheCard() = runBlocking {
        val id = library.addAutumnLeavesExample()
        val entry = library.get(id)!!
        assertEquals(LibraryKind.SONG, entry.kind)
        assertEquals("AABC", entry.song!!.formString)
        assertEquals(MemoryStatus.NEW, entry.status)
        assertTrue(entry.card!!.isNew)

        val session = TaperSession(listOf(entry.toTaperItem()!!), KeyPicker(Random(1)), Fsrs(), today = 10, random = Random(1))
        session.next(0)
        session.finishPlaying()
        session.grade(SelfGrade.CLEAN)
        val result = session.submitChords(TaperGrading.expectedFormula(entry.song))
        val sessionId = practice.startSession(SessionKind.SET, 10)
        practice.recordOrder(result, sessionId, 10)
        practice.finishSession(sessionId)

        val after = library.get(id)!!
        assertFalse(after.card!!.isNew)
        assertEquals(1, practice.observePlayableNow().first())
        assertEquals(listOf(10), practice.observePracticeDays().first())
    }

    @Test
    fun riffsAndAttemptsPersist() = runBlocking {
        val riff = Melody(LibraryRepository.newId(), "Рифф", Mode.PHRYGIAN, MelodyKind.RIFF, DegreeNote.parseLine("1 b2 b3 1 5, b7,"))
        library.saveMelody(riff)
        assertEquals(riff, library.get(riff.id)!!.melody)
        practice.saveAttempts(listOf(AttemptRecord(Variant.TIR_CLASSIC, 2, "tir:L2:4", "4", "3", false, 1200, null, 0, "C MAJOR", 5)), null)
        val recent = practice.recentAttempts(Variant.TIR_CLASSIC)
        assertEquals("tir:L2:4", recent.single().itemKey)
    }

    @Test
    fun backupRoundTrip() = runBlocking {
        library.addAutumnLeavesExample()
        practice.saveAttempts(listOf(AttemptRecord(Variant.HUNT_FIND, 1, "hunt:s6:pc4", "E", "E2", true, 900, 3.0, 0, null, 7)), null)
        val manager = BackupManager(context, db, SettingsRepository(context), ProgressStore(context), "test")
        val json = manager.export()

        val other = MuzykalkaDatabase.inMemory(context)
        val summary = BackupManager(context, other, SettingsRepository(context), ProgressStore(context), "test").import(json)
        assertEquals(1, summary.items)
        assertEquals(1, summary.attempts)
        assertEquals("Autumn Leaves", LibraryRepository(other).allOrderable().single().title)
        other.close()
    }
}
