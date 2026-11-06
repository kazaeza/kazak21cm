package com.kazaeza.muzykalka.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Schema v1. RULES (see docs/ARCHITECTURE.md §Data):
 *  - Never use fallbackToDestructiveMigration. The library is the user's memory work.
 *  - Every schema change = version bump + Migration + test in MigrationTest + committed schema JSON.
 *  - Domain objects are stored as versioned JSON payloads (see dto/), so musical model changes
 *    rarely need SQL migrations.
 */

/** Songs, riffs, tunes and lexicon phrases. [payloadJson] is a dto.LibraryPayload. */
@Serializable
@Entity(tableName = "library_item", indices = [Index("kind"), Index("archived")])
data class LibraryItemEntity(
    @PrimaryKey val id: String,
    /** SONG | RIFF | TUNE | PHRASE */
    val kind: String,
    val title: String,
    val payloadJson: String,
    val payloadVersion: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val archived: Boolean = false,
)

/** FSRS memory state, one per orderable library item (songs, riffs, phrases). */
@Serializable
@Entity(
    tableName = "srs_card",
    foreignKeys = [ForeignKey(LibraryItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dueDay")],
)
data class SrsCardEntity(
    @PrimaryKey val itemId: String,
    val stability: Double?,
    val difficulty: Double?,
    val lastReviewDay: Int?,
    val dueDay: Int?,
    val reps: Int,
    val lapses: Int,
)

/** One Taper order (or Lexicon review): the facts behind each SRS rating and «Играю с ходу». */
@Serializable
@Entity(
    tableName = "order_log",
    foreignKeys = [ForeignKey(LibraryItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId"), Index("atEpochMs")],
)
data class OrderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val sessionId: Long?,
    val studyDay: Int,
    val keyLabel: String,
    /** HintStep.name */
    val maxHint: String,
    /** SelfGrade.name */
    val selfGrade: String,
    val formulaAccuracy: Double,
    val startLatencyMs: Long?,
    /** Rating.value 1..4 */
    val rating: Int,
    val playableNow: Boolean,
    val tips: Int,
    val atEpochMs: Long,
)

/** Rule 8: one row per judged answer in any drill. All drill metrics are queries over this table. */
@Serializable
@Entity(tableName = "attempt", indices = [Index("variant", "atEpochMs"), Index("sessionId")])
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long?,
    /** Variant.name */
    val variant: String,
    val level: Int,
    val itemKey: String,
    val expected: String,
    val given: String,
    val correct: Boolean,
    val latencyMs: Long,
    val centsError: Double?,
    val hintLevel: Int,
    val keyLabel: String?,
    val atEpochMs: Long,
)

/** A practice session: daily set, minimal day, free play, exam or credit. Drives the streak. */
@Serializable
@Entity(tableName = "session", indices = [Index("studyDay")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SET | MINIMAL | FREE | ENTRANCE_EXAM | CREDIT | GRADUATION */
    val kind: String,
    val studyDay: Int,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
)

/** Result of one skill check inside an exam/credit session (report card «табель»). */
@Serializable
@Entity(
    tableName = "exam_result",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class ExamResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** Skill.name */
    val skill: String,
    val measured: Double,
    val passed: Boolean,
    val atEpochMs: Long,
)
