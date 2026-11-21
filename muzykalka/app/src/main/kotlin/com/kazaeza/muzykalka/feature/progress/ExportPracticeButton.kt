package com.kazaeza.muzykalka.feature.progress

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.kazaeza.muzykalka.BuildConfig
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.data.backup.exportPractice
import com.kazaeza.muzykalka.ui.component.GhostButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

@Composable
internal fun ExportPracticeButton(dayStartHour: Int) {
    val graph = LocalAppGraph.current
    val scope = graph.appScope
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val message = try {
                withContext(Dispatchers.IO) {
                    val data = exportPractice(graph.database, BuildConfig.VERSION_NAME, dayStartHour)
                    val stream = graph.app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Файл недоступен")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(data) }
                }
                "Статистика сохранена"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Не удалось сохранить статистику: ${e.localizedMessage.orEmpty()}"
            } finally {
                busy = false
            }
            Toast.makeText(graph.app, message, Toast.LENGTH_LONG).show()
        }
    }
    GhostButton(if (busy) "Сохраняем…" else "Экспорт статистики · JSON", enabled = !busy,
        onClick = { picker.launch("muzykalka-practice-${LocalDate.now()}.json") })
}
