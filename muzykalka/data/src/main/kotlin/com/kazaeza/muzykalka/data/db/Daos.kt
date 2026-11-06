package com.kazaeza.muzykalka.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_item WHERE archived = 0 ORDER BY title COLLATE NOCASE")
    fun observeActive(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_item WHERE id = :id")
    suspend fun get(id: String): LibraryItemEntity?

    @Upsert
    suspend fun upsert(item: LibraryItemEntity)

    @Query("SELECT * FROM library_item")
    suspend fun all(): List<LibraryItemEntity>

    @Query("UPDATE library_item SET archived = :archived, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("DELETE FROM library_item WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SrsDao {
    @Query("SELECT * FROM srs_card WHERE itemId = :itemId")
    suspend fun get(itemId: String): SrsCardEntity?

    /** New cards (dueDay NULL) come first, then the most overdue. */
    @Query("SELECT * FROM srs_card WHERE dueDay IS NULL OR dueDay <= :today ORDER BY dueDay IS NOT NULL, dueDay")
    suspend fun due(today: Int): List<SrsCardEntity>

    @Query("SELECT COUNT(*) FROM srs_card WHERE dueDay IS NULL OR dueDay <= :today")
    fun observeDueCount(today: Int): Flow<Int>

    @Upsert
    suspend fun upsert(card: SrsCardEntity)

    @Query("SELECT * FROM srs_card")
    suspend fun all(): List<SrsCardEntity>

    @Query("SELECT * FROM srs_card")
    fun observeAll(): Flow<List<SrsCardEntity>>
}

@Dao
interface OrderLogDao {
    @Insert
    suspend fun insert(row: OrderLogEntity): Long

    /** «Играю с ходу: N» = items whose LATEST order was playable-now. */
    @Query(
        """
        SELECT COUNT(*) FROM order_log o
        WHERE o.playableNow = 1 AND o.atEpochMs = (SELECT MAX(atEpochMs) FROM order_log WHERE itemId = o.itemId)
        """,
    )
    fun observePlayableNowCount(): Flow<Int>

    @Query("SELECT * FROM order_log")
    suspend fun all(): List<OrderLogEntity>

    @Query("SELECT * FROM order_log ORDER BY atEpochMs")
    fun observeAll(): Flow<List<OrderLogEntity>>

    @Query("SELECT * FROM order_log WHERE itemId = :itemId ORDER BY atEpochMs DESC")
    fun observeFor(itemId: String): Flow<List<OrderLogEntity>>
}

@Dao
interface AttemptDao {
    @Insert
    suspend fun insertAll(rows: List<AttemptEntity>)

    @Query("SELECT * FROM attempt WHERE variant = :variant ORDER BY atEpochMs DESC LIMIT :limit")
    suspend fun recent(variant: String, limit: Int): List<AttemptEntity>

    @Query("SELECT * FROM attempt")
    suspend fun all(): List<AttemptEntity>

    @Query("SELECT * FROM attempt ORDER BY atEpochMs")
    fun observeAll(): Flow<List<AttemptEntity>>
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(row: SessionEntity): Long

    @Query("UPDATE session SET endedAtEpochMs = :endedAt WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long)

    /** Distinct study days with a finished session, newest first — the streak is computed from it. */
    @Query("SELECT DISTINCT studyDay FROM session WHERE endedAtEpochMs IS NOT NULL ORDER BY studyDay DESC")
    fun observePracticeDays(): Flow<List<Int>>

    @Query("SELECT * FROM session")
    suspend fun all(): List<SessionEntity>
}

@Dao
interface ExamDao {
    @Insert
    suspend fun insertAll(rows: List<ExamResultEntity>)

    @Query("SELECT * FROM exam_result ORDER BY atEpochMs")
    fun observeAll(): Flow<List<ExamResultEntity>>

    @Query("SELECT * FROM exam_result")
    suspend fun all(): List<ExamResultEntity>
}

/** Bulk access used only by export/import (D-03). */
@Dao
interface BackupDao {
    @Insert suspend fun insertItems(rows: List<LibraryItemEntity>)
    @Insert suspend fun insertCards(rows: List<SrsCardEntity>)
    @Insert suspend fun insertOrders(rows: List<OrderLogEntity>)
    @Insert suspend fun insertAttempts(rows: List<AttemptEntity>)
    @Insert suspend fun insertSessions(rows: List<SessionEntity>)
    @Insert suspend fun insertExams(rows: List<ExamResultEntity>)

    @Query("DELETE FROM exam_result") suspend fun clearExams()
    @Query("DELETE FROM session") suspend fun clearSessions()
    @Query("DELETE FROM attempt") suspend fun clearAttempts()
    @Query("DELETE FROM order_log") suspend fun clearOrders()
    @Query("DELETE FROM srs_card") suspend fun clearCards()
    @Query("DELETE FROM library_item") suspend fun clearItems()
}
