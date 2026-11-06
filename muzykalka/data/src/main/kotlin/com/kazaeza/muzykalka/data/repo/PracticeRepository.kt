package com.kazaeza.muzykalka.data.repo

import androidx.room.withTransaction
import com.kazaeza.muzykalka.data.db.AttemptEntity
import com.kazaeza.muzykalka.data.db.ExamResultEntity
import com.kazaeza.muzykalka.data.db.MuzykalkaDatabase
import com.kazaeza.muzykalka.data.db.OrderLogEntity
import com.kazaeza.muzykalka.data.db.SessionEntity
import com.kazaeza.muzykalka.game.AttemptRecord
import com.kazaeza.muzykalka.game.OrderFact
import com.kazaeza.muzykalka.game.OrderResult
import com.kazaeza.muzykalka.game.Skill
import com.kazaeza.muzykalka.game.Variant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SessionKind { SET, MINIMAL, FREE, ENTRANCE_EXAM, CREDIT }

data class ExamResult(val sessionId: Long, val skill: Skill, val measured: Double, val passed: Boolean, val atEpochMs: Long)

data class OrderLogRow(
    val itemId: String,
    val keyLabel: String,
    val maxHint: String,
    val selfGrade: String,
    val formulaAccuracy: Double,
    val rating: Int,
    val playableNow: Boolean,
    val tips: Int,
    val atEpochMs: Long,
)

/** Ticket D-04: everything the user DID — sessions, drill attempts, Taper orders, exams. */
class PracticeRepository(private val db: MuzykalkaDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val MAX_SESSION_MS = 2 * 60 * 60_000L
    }

    suspend fun startSession(kind: SessionKind, studyDay: Int): Long =
        db.sessions().insert(SessionEntity(kind = kind.name, studyDay = studyDay, startedAtEpochMs = clock(), endedAtEpochMs = null))

    suspend fun finishSession(id: Long) = db.sessions().finish(id, clock())

    fun observePracticeDays(): Flow<List<Int>> = db.sessions().observePracticeDays()

    /** Total time in finished sessions; a session left open overnight counts at most [MAX_SESSION_MS]. */
    fun observePracticeMs(): Flow<Long> = db.sessions().observeFinished().map { rows ->
        rows.sumOf { r -> ((r.endedAtEpochMs ?: r.startedAtEpochMs) - r.startedAtEpochMs).coerceIn(0, MAX_SESSION_MS) }
    }

    suspend fun saveAttempts(records: List<AttemptRecord>, sessionId: Long?) {
        if (records.isEmpty()) return
        db.attempts().insertAll(
            records.map {
                AttemptEntity(
                    sessionId = sessionId, variant = it.variant.name, level = it.level, itemKey = it.itemKey,
                    expected = it.expected, given = it.given, correct = it.correct, latencyMs = it.latencyMs,
                    centsError = it.centsError, hintLevel = it.hintLevel, keyLabel = it.keyLabel, atEpochMs = it.atEpochMs,
                )
            },
        )
    }

    /** Oldest first, as the drivers expect. */
    suspend fun recentAttempts(variant: Variant, limit: Int = 200): List<AttemptRecord> =
        db.attempts().recent(variant.name, limit).mapNotNull { it.toRecord() }.reversed()

    fun observeAttempts(): Flow<List<AttemptRecord>> = db.attempts().observeAll().map { rows -> rows.mapNotNull { it.toRecord() } }

    /** One Taper order: log row + new FSRS card, atomically. */
    suspend fun recordOrder(result: OrderResult, sessionId: Long?, studyDay: Int) = db.withTransaction {
        db.orders().insert(
            OrderLogEntity(
                itemId = result.item.id, sessionId = sessionId, studyDay = studyDay, keyLabel = result.key.toString(),
                maxHint = result.outcome.maxHint.name, selfGrade = result.outcome.self.name,
                formulaAccuracy = result.outcome.formulaAccuracy, startLatencyMs = result.outcome.startLatencyMs,
                rating = result.rating.value, playableNow = result.playableNow, tips = result.tips, atEpochMs = clock(),
            ),
        )
        db.srs().upsert(result.newCard.toEntity(result.item.id))
    }

    fun observePlayableNow(): Flow<Int> = db.orders().observePlayableNowCount()

    fun observeOrders(): Flow<List<OrderFact>> =
        db.orders().observeAll().map { rows -> rows.map { OrderFact(it.itemId, it.studyDay, it.playableNow, it.atEpochMs) } }

    fun observeOrdersFor(itemId: String): Flow<List<OrderLogRow>> = db.orders().observeFor(itemId).map { rows ->
        rows.map { OrderLogRow(it.itemId, it.keyLabel, it.maxHint, it.selfGrade, it.formulaAccuracy, it.rating, it.playableNow, it.tips, it.atEpochMs) }
    }

    fun observeDueCount(today: Int): Flow<Int> = db.srs().observeDueCount(today)

    /** Playable-now orders of one session (exam memory stage). */
    suspend fun playableInSession(sessionId: Long): Int = db.orders().inSession(sessionId).count { it.playableNow }

    suspend fun saveExam(sessionId: Long, results: List<ExamResult>) =
        db.exams().insertAll(results.map { ExamResultEntity(sessionId = sessionId, skill = it.skill.name, measured = it.measured, passed = it.passed, atEpochMs = it.atEpochMs) })

    fun observeExams(): Flow<List<ExamResult>> = db.exams().observeAll().map { rows ->
        rows.mapNotNull { r -> runCatching { Skill.valueOf(r.skill) }.getOrNull()?.let { ExamResult(r.sessionId, it, r.measured, r.passed, r.atEpochMs) } }
    }

    private fun AttemptEntity.toRecord(): AttemptRecord? {
        val v = runCatching { Variant.valueOf(variant) }.getOrNull() ?: return null
        return AttemptRecord(v, level, itemKey, expected, given, correct, latencyMs, centsError, hintLevel, keyLabel, atEpochMs)
    }
}
