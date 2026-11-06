package com.kazaeza.muzykalka.data.repo

import com.kazaeza.muzykalka.data.db.LibraryItemEntity
import com.kazaeza.muzykalka.data.db.MuzykalkaDatabase
import com.kazaeza.muzykalka.data.db.SrsCardEntity
import com.kazaeza.muzykalka.data.dto.MelodyPayload
import com.kazaeza.muzykalka.data.dto.Payloads
import com.kazaeza.muzykalka.data.dto.SongPayload
import com.kazaeza.muzykalka.game.TaperItem
import com.kazaeza.muzykalka.music.Melody
import com.kazaeza.muzykalka.music.MelodyKind
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.ProgressionText
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.srs.CardState
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    /** ROADMAP L-03: the optional example, only on the user's request (rule 7). */
    suspend fun addAutumnLeavesExample(): String = saveSong(
        Song(
            id = newId(),
            title = "Autumn Leaves",
            artist = "пример",
            mode = Mode.MINOR,
            sections = ProgressionText.parseSections(
                """
                A: | iv7 | bVII7 | bIIImaj7 | bVImaj7 | iiø7 | V7 | i | i |
                B: | iiø7 | V7 | i | i | iv7 | bVII7 | bIIImaj7 | bVImaj7 |
                C: | iiø7 | V7 | i7 | bVI7 | iiø7 V7 | i | i |
                """.trimIndent(),
            ),
            form = ProgressionText.parseForm("AABC"),
            notes = "Пример из плана. Отредактируй под свою версию или удали.",
        ),
    )

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
