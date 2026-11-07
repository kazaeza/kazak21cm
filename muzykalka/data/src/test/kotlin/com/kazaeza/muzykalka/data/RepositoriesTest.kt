package com.kazaeza.muzykalka.data

import androidx.test.core.app.ApplicationProvider
import com.kazaeza.muzykalka.data.backup.BackupManager
import com.kazaeza.muzykalka.data.db.MuzykalkaDatabase
import com.kazaeza.muzykalka.data.repo.LibraryKind
import com.kazaeza.muzykalka.data.repo.LibraryRepository
import com.kazaeza.muzykalka.data.repo.MemoryStatus
import com.kazaeza.muzykalka.data.repo.PracticeRepository
import com.kazaeza.muzykalka.data.repo.SessionKind
import com.kazaeza.muzykalka.data.settings.AnchorStore
import com.kazaeza.muzykalka.data.settings.ProgressStore
import com.kazaeza.muzykalka.data.settings.SettingsRepository
import com.kazaeza.muzykalka.game.AttemptRecord
import com.kazaeza.muzykalka.game.SelfGrade
import com.kazaeza.muzykalka.game.TaperGrading
import com.kazaeza.muzykalka.game.TaperSession
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeAnchor
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Melody
import com.kazaeza.muzykalka.music.MelodyKind
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.SongBook
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
        val id = library.addFromBook(SongBook.byId("autumn-leaves")!!)
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
    fun songbookPiecesAreAddedOnceAndKeepUserEdits() = runBlocking {
        val book = SongBook.byId("stand-by-me")!!
        val id = library.addFromBook(book)
        assertEquals("book:stand-by-me", id)
        assertEquals(setOf(id), library.observeBookIds().first())
        val edited = library.get(id)!!.song!!.copy(title = "Stand by Me (моя версия)")
        library.saveSong(edited)
        assertEquals(id, library.addFromBook(book))
        assertEquals("Stand by Me (моя версия)", library.get(id)!!.title)
        library.setArchived(id, true)
        assertEquals(emptySet<String>(), library.observeBookIds().first())
        library.addFromBook(book)
        assertEquals(1, library.allOrderable().size)
    }

    @Test
    fun practiceTimeSumsFinishedSessionsOnly() = runBlocking {
        var now = 1_000_000L
        val timed = PracticeRepository(db) { now }
        val a = timed.startSession(SessionKind.SET, 1)
        now += 12 * 60_000
        timed.finishSession(a)
        timed.startSession(SessionKind.FREE, 1) // never finished: not counted
        val b = timed.startSession(SessionKind.FREE, 2)
        now += 10 * 60 * 60_000 // left open overnight
        timed.finishSession(b)
        assertEquals(12 * 60_000L + PracticeRepository.MAX_SESSION_MS, timed.observePracticeMs().first())
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
        library.addFromBook(SongBook.byId("autumn-leaves")!!)
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

    @Test
    fun anchorsKeepOnePerDegreeAndTravelInTheBackup() = runBlocking {
        val store = AnchorStore(context)
        store.set(DegreeAnchor.parse(Degree.FIVE, "Моя песня", "5 5 6 5 1' 7").getOrThrow())
        store.set(DegreeAnchor.parse(Degree.FIVE, "Другая", "5 3 1").getOrThrow()) // same degree: replaces
        store.set(DegreeAnchor.parse(Degree.SHARP_FOUR, "Тритон", "#4 5").getOrThrow())
        store.remove(Degree.FLAT_FIVE) // ♭5 is the slot of ♯4
        assertEquals(mapOf(7 to "Другая"), store.current().mapValues { it.value.title })

        val json = BackupManager(context, db, SettingsRepository(context), ProgressStore(context), "test", store).export()
        store.remove(Degree.FIVE)
        assertTrue(store.current().isEmpty())
        val other = MuzykalkaDatabase.inMemory(context)
        BackupManager(context, other, SettingsRepository(context), ProgressStore(context), "test", store).import(json)
        assertEquals("5 3 1", store.current()[7]?.melodyText)
        other.close()
    }
}
