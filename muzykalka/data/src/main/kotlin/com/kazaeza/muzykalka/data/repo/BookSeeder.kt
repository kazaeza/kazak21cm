package com.kazaeza.muzykalka.data.repo

import com.kazaeza.muzykalka.data.settings.BookSeedStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * O-42 (ADR-041): gives the library the songbook, once per piece. Run at every start: it is cheap when there is
 * nothing new, and a piece added by a later version of the app comes in by itself.
 */
class BookSeeder(private val library: LibraryRepository, private val store: BookSeedStore) {
    private val lock = Mutex()

    /** Puts the unseen pieces on the shelf; returns how many were dealt with. */
    suspend fun run(): Int = lock.withLock {
        val done = library.seedBook(store.seen())
        store.markSeen(done)
        done.size
    }

    suspend fun exportJson(): String = store.exportJson()

    /** Restores the ledger of a backup (none: an older backup, the book comes in again where it is missing) and seeds. */
    suspend fun restore(ledgerJson: String?) {
        lock.withLock { store.importJson(ledgerJson) }
        run()
    }
}
