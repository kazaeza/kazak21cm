package com.kazaeza.muzykalka.data

import androidx.test.core.app.ApplicationProvider
import com.kazaeza.muzykalka.data.backup.BackupManager
import com.kazaeza.muzykalka.data.db.MuzykalkaDatabase
import com.kazaeza.muzykalka.data.repo.BookSeeder
import com.kazaeza.muzykalka.data.repo.LibraryRepository
import com.kazaeza.muzykalka.data.repo.toEntity
import com.kazaeza.muzykalka.data.settings.BookSeedStore
import com.kazaeza.muzykalka.data.settings.ProgressStore
import com.kazaeza.muzykalka.data.settings.SettingsRepository
import com.kazaeza.muzykalka.music.SongBook
import com.kazaeza.muzykalka.srs.CardState
import com.kazaeza.muzykalka.srs.Fsrs
import com.kazaeza.muzykalka.srs.Rating
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** O-42 (ADR-041): the songbook is part of the library — put in once, on the shelf, never over the user's own. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookSeedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = MuzykalkaDatabase.inMemory(context)
    private val library = LibraryRepository(db)
    private val store = BookSeedStore(context)
    private val seeder = BookSeeder(library, store)
    private val everyId = SongBook.ENTRIES.map { SongBook.libraryId(it) }

    @Before
    fun cleanLedger() = runBlocking { store.importJson(null) }

    @After
    fun close() = db.close()

    @Test
    fun theWholeBookComesInOnTheShelfAndNothingIsDue() = runBlocking {
        assertEquals(SongBook.ENTRIES.size, seeder.run())
        val entries = library.observeEntries().first()
        assertEquals(SongBook.ENTRIES.size, entries.size)
        assertTrue(entries.all { it.shelved && it.card!!.isNew })
        assertTrue("every piece decodes", entries.all { it.song != null && !it.unsupported })
        assertEquals("a hundred songs are not a hundred reviews", 0, entries.count { it.card!!.isDue(today = 20_000) })
        assertEquals(SongBook.ENTRIES.size, library.allOrderable().size)
        assertEquals(SongBook.byId("autumn-leaves")!!.song.title, library.get("book:autumn-leaves")!!.title)
        assertEquals(everyId.toSet(), store.seen())
    }

    @Test
    fun seedingIsIdempotent() = runBlocking {
        seeder.run()
        val before = db.library().all().associateBy { it.id }
        assertEquals(0, seeder.run())
        assertEquals("a second start changes nothing", before, db.library().all().associateBy { it.id })
    }

    @Test
    fun whatTheUserDeletedOrChangedIsLeftAlone() = runBlocking {
        // an older version: one piece put in by hand and edited, a card in progress
        val book = SongBook.byId("stand-by-me")!!
        val id = library.addFromBook(book)
        library.saveSong(library.get(id)!!.song!!.copy(title = "Stand by Me (моя версия)"))
        val learned = Fsrs().review(CardState(), Rating.GOOD, today = 50)
        db.srs().upsert(learned.toEntity(id))
        val archivedId = SongBook.libraryId(SongBook.byId("autumn-leaves")!!)
        library.addFromBook(SongBook.byId("autumn-leaves")!!)
        library.setArchived(archivedId, true)

        seeder.run()
        assertEquals("no overwrite", "Stand by Me (моя версия)", library.get(id)!!.title)
        assertEquals("the card in progress stays", learned, library.get(id)!!.card)
        assertTrue("an archived copy stays archived", library.get(archivedId)!!.archived)

        val gone = everyId.first { it != id && it != archivedId }
        library.delete(gone)
        seeder.run()
        assertNull("what was deleted does not come back", library.get(gone))
    }

    @Test
    fun aPieceOfALaterVersionComesInByItself() = runBlocking {
        val first = SongBook.ENTRIES.take(5)
        val done = library.seedBook(emptySet(), first)
        store.markSeen(done)
        assertEquals(5, library.observeEntries().first().size)
        assertEquals("the rest of the book is new to this library", SongBook.ENTRIES.size - 5, seeder.run())
    }

    @Test
    fun startLearningTakesASongOffTheShelfOnlyOnce() = runBlocking {
        seeder.run()
        val id = "book:autumn-leaves"
        assertTrue(library.get(id)!!.shelved)
        library.startLearning(id)
        val started = library.get(id)!!
        assertFalse(started.shelved)
        assertTrue("an ordinary new card, due at once", started.card!!.isNew && started.card!!.isDue(0))
        val learned = Fsrs().review(started.card!!, Rating.GOOD, today = 9)
        db.srs().upsert(learned.toEntity(id))
        library.startLearning(id)
        assertEquals("a card in progress is not reset", learned, library.get(id)!!.card)
        // «В мои» (the old way in) also takes a shelved piece off the shelf
        library.addFromBook(SongBook.byId("stand-by-me")!!)
        assertFalse(library.get("book:stand-by-me")!!.shelved)
    }

    @Test
    fun theLedgerTravelsInTheBackupAndAnOlderBackupSeedsAgain() = runBlocking {
        seeder.run()
        val deleted = "book:autumn-leaves"
        library.delete(deleted)
        val manager = BackupManager(context, db, SettingsRepository(context), ProgressStore(context), "test", bookSeed = seeder)
        val json = manager.export()

        val other = MuzykalkaDatabase.inMemory(context)
        val otherSeeder = BookSeeder(LibraryRepository(other), store)
        BackupManager(context, other, SettingsRepository(context), ProgressStore(context), "test", bookSeed = otherSeeder).import(json)
        assertNull("a deleted piece is still deleted after a restore", LibraryRepository(other).get(deleted))
        assertEquals(SongBook.ENTRIES.size - 1, LibraryRepository(other).observeEntries().first().size)

        // a backup of a version before the merge has no ledger: the book comes back where it is missing
        val old = JsonObject(Json.parseToJsonElement(json).jsonObject.filterKeys { it != "bookSeedJson" }).toString()
        val third = MuzykalkaDatabase.inMemory(context)
        BackupManager(context, third, SettingsRepository(context), ProgressStore(context), "test", bookSeed = BookSeeder(LibraryRepository(third), store)).import(old)
        assertNotNull("an older backup seeds the whole book again", LibraryRepository(third).get(deleted))
        other.close()
        third.close()
    }
}
