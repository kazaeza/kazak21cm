package com.kazaeza.muzykalka.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.BuildConfig
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.audio.InputDevices
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.data.settings.SettingsRepository
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.NoteNaming
import com.kazaeza.muzykalka.music.Tuning
import com.kazaeza.muzykalka.ui.component.ChoiceRow
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** «Настройки». Persisted via DataStore; export/import = ticket D-03. */
@Composable
fun SettingsScreen(repo: SettingsRepository, onBack: () -> Unit, onSoundCheck: () -> Unit) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val settings by repo.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    fun update(f: (Settings) -> Settings) {
        scope.launch { repo.update(f) }
    }
    val inputs = remember { InputDevices.list(context) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = graph.backup.export()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.onSuccess { Toast.makeText(context, "Бэкап сохранён", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Не удалось сохранить: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Файл пуст")
                graph.backup.import(text)
            }.onSuccess { Toast.makeText(context, "Загружено: ${it.items} вещей, ${it.attempts} ответов", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, it.message ?: "Не удалось загрузить", Toast.LENGTH_LONG).show() }
        }
    }

    ScreenFrame(title = "Настройки", accent = Accents.Calm, onBack = onBack) {
        item(key = "sound") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Kamerton, onClick = onSoundCheck) {
                Text("🎙️ Проверка звука и тюнер", style = MaterialTheme.typography.titleLarge)
                Text("Убедись, что телефон слышит гитару, и подстрой её.", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
            }
        }
        item(key = "tuning") {
            Column {
                SectionLabel("строй")
                Spacer(Modifier.height(6.dp))
                ChoiceRow(Tuning.PRESETS, settings.tuning, { it.ruName }, { t -> update { it.copy(tuningId = t.id) } }, colors = Accents.Hunt)
            }
        }
        item(key = "frets") {
            Column {
                SectionLabel("ладов на грифе")
                Spacer(Modifier.height(6.dp))
                ChoiceRow(listOf(19, 20, 21, 22, 24), settings.fretCount, { "$it" }, { n -> update { it.copy(fretCount = n) } }, colors = Accents.Hunt)
            }
        }
        item(key = "notation") {
            Column {
                SectionLabel("запись ступеней")
                Spacer(Modifier.height(6.dp))
                ChoiceRow(
                    DegreeNotation.entries,
                    settings.degreeNotation,
                    { when (it) { DegreeNotation.ARABIC -> "1–7"; DegreeNotation.ROMAN -> "I–VII"; DegreeNotation.SOLFEGE -> "до-ре-ми" } },
                    { n -> update { it.copy(degreeNotation = n) } },
                )
            }
        }
        item(key = "naming") {
            Column {
                SectionLabel("названия нот")
                Spacer(Modifier.height(6.dp))
                ChoiceRow(
                    NoteNaming.entries,
                    settings.noteNaming,
                    { if (it == NoteNaming.LATIN) "F♯" else "фа-диез" },
                    { n -> update { it.copy(noteNaming = n) } },
                )
            }
        }
        item(key = "input") {
            Column {
                SectionLabel("вход")
                Spacer(Modifier.height(6.dp))
                val options = listOf<Int?>(null) + inputs.map { it.id }
                ChoiceRow(
                    options,
                    settings.inputDeviceId.takeIf { id -> inputs.any { it.id == id } },
                    { id -> if (id == null) "по умолчанию" else inputs.first { it.id == id }.let { d -> d.label + (d.warning?.let { w -> " ($w)" } ?: "") } },
                    { id -> update { it.copy(inputDeviceId = id) } },
                    colors = Accents.Kamerton,
                )
            }
        }
        item(key = "switches") {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Играю в наушниках", "микрофон не выключается, пока звучит приложение", settings.headphones) { v -> update { it.copy(headphones = v) } }
                SwitchRow("Вибрация в играх", null, settings.haptics) { v -> update { it.copy(haptics = v) } }
                SwitchRow("Левша", "гриф зеркально", settings.leftHanded) { v -> update { it.copy(leftHanded = v) } }
            }
        }
        item(key = "a4") {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Ля первой октавы: ${settings.a4Hz.roundToInt()} Гц", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = settings.a4Hz.toFloat(),
                    onValueChange = { v -> update { it.copy(a4Hz = v.roundToInt().toDouble()) } },
                    valueRange = 430f..450f,
                    steps = 19,
                )
            }
        }
        item(key = "minutes") {
            Column {
                SectionLabel("минут в день")
                Spacer(Modifier.height(6.dp))
                ChoiceRow(listOf(5, 10, 15, 20, 30), settings.dailyMinutes, { "$it" }, { m -> update { it.copy(dailyMinutes = m) } }, colors = Accents.Taper)
            }
        }
        item(key = "backup") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Good) {
                Text("💾 Данные", style = MaterialTheme.typography.titleLarge)
                Text("Библиотека и прогресс хранятся на телефоне и в резервной копии Google. Файл — на всякий случай.", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                Spacer(Modifier.height(10.dp))
                GhostButton("Экспорт в файл", onClick = { exportLauncher.launch("muzykalka-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth(), colors = Accents.Good, leading = "⬆️")
                Spacer(Modifier.height(8.dp))
                GhostButton("Импорт из файла", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth(), colors = Accents.Taper, leading = "⬇️")
                Text("Импорт заменяет всё; текущее состояние сначала сохраняется автоматически.", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
            }
        }
        item(key = "about") {
            Text("Музыкалка ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
