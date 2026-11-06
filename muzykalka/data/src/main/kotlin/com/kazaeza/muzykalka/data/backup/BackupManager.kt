package com.kazaeza.muzykalka.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.kazaeza.muzykalka.data.db.AttemptEntity
import com.kazaeza.muzykalka.data.db.ExamResultEntity
import com.kazaeza.muzykalka.data.db.LibraryItemEntity
import com.kazaeza.muzykalka.data.db.MuzykalkaDatabase
import com.kazaeza.muzykalka.data.db.OrderLogEntity
import com.kazaeza.muzykalka.data.db.SessionEntity
import com.kazaeza.muzykalka.data.db.SrsCardEntity
import com.kazaeza.muzykalka.data.settings.ProgressStore
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Ticket D-03: the whole app state as one JSON file. */
@Serializable
data class BackupV1(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAtMs: Long,
    val appVersion: String,
    val items: List<LibraryItemEntity>,
    val cards: List<SrsCardEntity>,
    val orders: List<OrderLogEntity>,
    val attempts: List<AttemptEntity>,
    val sessions: List<SessionEntity>,
    val exams: List<ExamResultEntity>,
    val settings: Settings? = null,
    val progressJson: String? = null,
) {
    companion object {
        const val FORMAT = "muzykalka-backup"
    }
}

data class ImportSummary(val items: Int, val orders: Int, val attempts: Int)

class BackupManager(
    private val context: Context,
    private val db: MuzykalkaDatabase,
    private val settings: SettingsRepository,
    private val progress: ProgressStore,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun export(): String {
        val backup = BackupV1(
            exportedAtMs = clock(),
            appVersion = appVersion,
            items = db.library().all(),
            cards = db.srs().all(),
            orders = db.orders().all(),
            attempts = db.attempts().all(),
            sessions = db.sessions().all(),
            exams = db.exams().all(),
            settings = settings.settings.first(),
            progressJson = progress.exportJson(),
        )
        return json.encodeToString(BackupV1.serializer(), backup)
    }

    /** Validates, saves the CURRENT state to an automatic backup file, then replaces everything. */
    suspend fun import(text: String): ImportSummary {
        val backup = runCatching { json.decodeFromString(BackupV1.serializer(), text) }.getOrNull()
            ?: throw IllegalArgumentException("Это не файл бэкапа Музыкалки")
        require(backup.format == BackupV1.FORMAT) { "Это не файл бэкапа Музыкалки" }
        require(backup.version <= 1) { "Бэкап сделан более новой версией приложения" }
        autoSave()
        db.withTransaction {
            val d = db.backup()
            d.clearExams(); d.clearSessions(); d.clearAttempts(); d.clearOrders(); d.clearCards(); d.clearItems()
            d.insertItems(backup.items)
            d.insertCards(backup.cards)
            d.insertOrders(backup.orders.map { it.copy(id = 0) })
            d.insertSessions(backup.sessions)
            d.insertAttempts(backup.attempts.map { it.copy(id = 0) })
            d.insertExams(backup.exams.map { it.copy(id = 0) })
        }
        backup.settings?.let { settings.replace(it) }
        backup.progressJson?.let { progress.importJson(it) }
        return ImportSummary(backup.items.size, backup.orders.size, backup.attempts.size)
    }

    /** Last-resort copy before an import replaces the data: filesDir/backups/auto-<time>.json. */
    private suspend fun autoSave() {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        File(dir, "auto-${clock()}.json").writeText(export())
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
    }
}
