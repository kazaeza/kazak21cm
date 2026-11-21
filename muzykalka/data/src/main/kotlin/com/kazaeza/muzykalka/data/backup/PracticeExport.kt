package com.kazaeza.muzykalka.data.backup

import androidx.room.withTransaction
import com.kazaeza.muzykalka.data.db.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.ZoneId

/** A consistent analytical snapshot. Session IDs join attempts, orders and exams, including older rows. */
@Serializable
data class PracticeExport(
    val format: String = "muzykalka-practice",
    val version: Int = 1,
    val exportedAtEpochMs: Long,
    val appVersion: String,
    val exportZone: String,
    val dayStartHourAtExport: Int,
    val attempts: List<AttemptEntity>,
    val sessions: List<SessionEntity>,
    val orders: List<OrderLogEntity>,
    val exams: List<ExamResultEntity>,
)

suspend fun exportPractice(db: MuzykalkaDatabase, version: String, dayStartHour: Int): String {
    val snapshot = db.withTransaction {
        PracticeExport(exportedAtEpochMs = System.currentTimeMillis(), appVersion = version,
            exportZone = ZoneId.systemDefault().id, dayStartHourAtExport = dayStartHour,
            attempts = db.attempts().all(), sessions = db.sessions().all(),
            orders = db.orders().all(), exams = db.exams().all())
    }
    return Json { encodeDefaults = true }.encodeToString(PracticeExport.serializer(), snapshot)
}
