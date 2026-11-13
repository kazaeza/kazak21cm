package com.kazaeza.muzykalka.data.repo

import com.kazaeza.muzykalka.data.db.LibraryItemEntity
import com.kazaeza.muzykalka.data.db.MuzykalkaDatabase
import com.kazaeza.muzykalka.data.db.SrsCardEntity
import com.kazaeza.muzykalka.data.dto.MelodyPayload
import com.kazaeza.muzykalka.data.dto.Payloads
import com.kazaeza.muzykalka.data.dto.SongPayload
import com.kazaeza.muzykalka.game.TaperItem
import com.kazaeza.muzykalka.music.BookEntry
import com.kazaeza.muzykalka.music.Melody
import com.kazaeza.muzykalka.music.MelodyKind
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.music.SongBook
import com.kazaeza.muzykalka.srs.CardState
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

enum class LibraryKind(val ru: String) { SONG("вещь"), RIFF("рифф"), TUNE("мелодия"), PHRASE("фраза") }

/** Memory status shown in the library (ROADMAP L-01). */
enum class MemoryStatus(val ru: String) { NEW("новая"), LEARNING("учу"), HOLDING("держится") }

/** A library row with its domain content decoded (or [unsupported] when written by a newer app). */
data class LibraryEntry(
    val id: String,
    val kind: LibraryKind,
    val title: String,
    val song: Song?,
    val melody: Melody?,
    val card: CardState?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val archived: Boolean,
    val unsupported: Boolean = false,
) {
    val status: MemoryStatus
        get() = when {
            card == null || card.isNew -> MemoryStatus.NEW
            (card.stability ?: 0.0) >= 21.0 -> MemoryStatus.HOLDING
            else -> MemoryStatus.LEARNING
        }

    val orderable: Boolean get() = !unsupported && (kind == LibraryKind.SONG || kind == LibraryKind.RIFF)

    /** O-42: a piece of the book that is in the library but not in learning (its card is never due). */
    val shelved: Boolean get() = card?.isShelved == true

    /** The songbook piece this row came from, whatever the user has edited since; null for the user's own. */
    val bookEntry: BookEntry? get() = SongBook.byLibraryId(id)

    fun toTaperItem(): TaperItem? = if (!orderable) null else TaperItem(id, title, song = song, melody = melody, card = card ?: CardState())
}

fun SrsCardEntity.toCard() = CardState(stability, difficulty, lastReviewDay, dueDay, reps, lapses)

fun CardState.toEntity(itemId: String) = SrsCardEntity(itemId, stability, difficulty, lastReviewDay, dueDay, reps, lapses)

/** Ticket D-01: the user's songs, riffs, tunes and phrases, each with its FSRS card. */
class LibraryRepository(private val db: MuzykalkaDatabase, private val clock: () -> Long = System::currentTimeMillis) {

    fun observeEntries(includeArchived: Boolean = false): Flow<List<LibraryEntry>> =
        combine(db.library().observeActive(), db.srs().observeAll()) { items, cards ->
            val byId = cards.associateBy { it.itemId }
            items.filter { includeArchived || !it.archived }.map { decode(it, byId[it.id]?.toCard()) }
        }

    suspend fun get(id: String): LibraryEntry? {
        val item = db.library().get(id) ?: return null
        return decode(item, db.srs().get(id)?.toCard())
    }

    suspend fun allOrderable(): List<LibraryEntry> {
        val cards = db.srs().all().associateBy { it.itemId }
        return db.library().all().filter { !it.archived }.map { decode(it, cards[it.id]?.toCard()) }.filter { it.orderable }
    }

    /** Creates or updates a song. Orderable items get a new FSRS card on creation (due now). */
    suspend fun saveSong(song: Song): String = save(song.id, LibraryKind.SONG, song.title, Payloads.encode(Payloads.fromSong(song)))

    suspend fun saveMelody(melody: Melody): String {
        val kind = when (melody.kind) {
            MelodyKind.RIFF -> LibraryKind.RIFF
            MelodyKind.TUNE -> LibraryKind.TUNE
            MelodyKind.PHRASE -> LibraryKind.PHRASE
        }
        return save(melody.id, kind, melody.title, Payloads.encode(Payloads.fromMelody(melody)))
    }

    private suspend fun save(id: String, kind: LibraryKind, title: String, payload: String): String = db.withTransaction {
        val now = clock()
        val existing = db.library().get(id)
        db.library().upsert(
            LibraryItemEntity(
                id = id, kind = kind.name, title = title, payloadJson = payload, payloadVersion = Payloads.CURRENT_VERSION,
                createdAtEpochMs = existing?.createdAtEpochMs ?: now, updatedAtEpochMs = now, archived = existing?.archived ?: false,
            ),
        )
        if (kind != LibraryKind.TUNE && db.srs().get(id) == null) db.srs().upsert(CardState().toEntity(id))
        id
    }

    suspend fun setArchived(id: String, archived: Boolean) = db.library().setArchived(id, archived, clock())

    suspend fun delete(id: String) = db.library().delete(id)

    /**
     * ADR-013: puts a songbook piece in learning. The user's own edits are never overwritten: an existing copy is only
     * brought back from the archive and, if it sat on the shelf (O-42), taken off it.
     */
    suspend fun addFromBook(entry: BookEntry): String {
        val id = SongBook.libraryId(entry)
        val existing = db.library().get(id)
        when {
            existing == null -> saveSong(entry.song)
            else -> {
                if (existing.archived) db.library().setArchived(id, false, clock())
                startLearning(id)
            }
        }
        return id
    }

    /**
     * O-42 (ADR-041): the songbook is part of the library. Every piece whose id is not in [seen] and which the library
     * does not hold yet is put in on the shelf: its card is new and never due, so a hundred songs are not a hundred
     * reviews. A row that exists is left exactly as it is (the user's edits, their card); a piece in [seen] is not
     * put back, so what the user deleted stays deleted. Returns the ids that have now been dealt with.
     */
    suspend fun seedBook(seen: Set<String>, entries: List<BookEntry> = SongBook.ENTRIES): Set<String> {
        val fresh = entries.filter { SongBook.libraryId(it) !in seen }
        if (fresh.isEmpty()) return emptySet()
        db.withTransaction {
            val now = clock()
            for (e in fresh) {
                val id = SongBook.libraryId(e)
                if (db.library().get(id) != null) continue
                db.library().upsert(
                    LibraryItemEntity(
                        id = id, kind = LibraryKind.SONG.name, title = e.song.title,
                        payloadJson = Payloads.encode(Payloads.fromSong(e.song)), payloadVersion = Payloads.CURRENT_VERSION,
                        createdAtEpochMs = now, updatedAtEpochMs = now, archived = false,
                    ),
                )
                db.srs().upsert(CardState.shelved().toEntity(id))
            }
        }
        return fresh.map { SongBook.libraryId(it) }.toSet()
    }

    /** Takes a song or a riff off the shelf: its card becomes an ordinary new one, due at once. A card in progress is left alone. */
    suspend fun startLearning(id: String) {
        val item = db.library().get(id) ?: return
        if (item.kind != LibraryKind.SONG.name && item.kind != LibraryKind.RIFF.name) return
        val card = db.srs().get(id)?.toCard()
        if (card == null || card.isShelved) db.srs().upsert(CardState().toEntity(id))
    }

    private fun decode(item: LibraryItemEntity, card: CardState?): LibraryEntry {
        val kind = runCatching { LibraryKind.valueOf(item.kind) }.getOrNull()
        val payload = if (item.payloadVersion > Payloads.CURRENT_VERSION || kind == null) null else runCatching { Payloads.decode(item.payloadJson) }.getOrNull()
        val song = (payload as? SongPayload)?.let { runCatching { Payloads.toSong(item.id, it) }.getOrNull() }
        val melody = (payload as? MelodyPayload)?.let { runCatching { Payloads.toMelody(item.id, it) }.getOrNull() }
        return LibraryEntry(
            id = item.id, kind = kind ?: LibraryKind.SONG, title = item.title, song = song, melody = melody, card = card,
            createdAtMs = item.createdAtEpochMs, updatedAtMs = item.updatedAtEpochMs, archived = item.archived,
            unsupported = song == null && melody == null,
        )
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
