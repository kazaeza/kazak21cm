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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.NoteName
import com.kazaeza.muzykalka.music.NoteNaming
import com.kazaeza.muzykalka.music.TapSound
import com.kazaeza.muzykalka.music.ToneTimbre
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.music.Tuning
import com.kazaeza.muzykalka.ui.component.ChoiceRow
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.LocalTapSound
import com.kazaeza.muzykalka.ui.component.SectionLabel
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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

    // O-36: choosing a timbre plays a fifth over the tonic drone, as it will sound in the game
    var auditionJob by remember { mutableStateOf<Job?>(null) }
    var auditionDrone by remember { mutableStateOf(false) }
    fun audition(timbre: ToneTimbre) {
        val key = Key(NoteName.parse("A"), Mode.MAJOR)
        val previous = auditionJob
        graph.audioOutput.toneTimbre = timbre
        graph.audioOutput.a4Hz = settings.a4Hz
        auditionJob = scope.launch {
            previous?.cancelAndJoin() // its stopAll must not land on the new notes
            auditionDrone = true
            graph.audioOutput.drone(Arranger().drone(key))
            graph.audioOutput.play(Arranger().line(DegreeNote.parseLine("1 5 1"), key, Voice.TONE, beatsEach = 1.0, tempoBpm = 84.0))
            delay(400)
            graph.audioOutput.drone(null)
            auditionDrone = false
        }
    }
    DisposableEffect(Unit) { onDispose { if (auditionDrone) graph.audioOutput.drone(null) } }

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
                Text("Проверка звука и тюнер", style = MaterialTheme.typography.titleLarge)
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
        item(key = "sound-note") {
            Column {
                SectionLabel("звук ноты над дроном")
                Spacer(Modifier.height(6.dp))
                ChoiceRow(
                    ToneTimbre.entries,
                    settings.toneTimbre,
                    { timbreName(it) },
                    { t ->
                        update { it.copy(toneTimbre = t) }
                        audition(t)
                    },
                    colors = Accents.Kamerton,
                )
                Spacer(Modifier.height(6.dp))
                Text(timbreHint(settings.toneTimbre), style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
            }
        }
        item(key = "sound-tap") {
            Column {
                SectionLabel("тап на кнопках")
                Spacer(Modifier.height(6.dp))
                // the chips of this row stay silent on the press: only the tap that was chosen is heard
                CompositionLocalProvider(LocalTapSound provides {}) {
                    ChoiceRow(
                        listOf<TapSound?>(null, TapSound.STICKS, TapSound.BLOCK, TapSound.TICK),
                        settings.tapSound,
                        { tapName(it) },
                        { t ->
                            update { it.copy(tapSound = t) }
                            graph.applySound(settings.copy(tapSound = t))
                            if (t != null) graph.audioOutput.tap(t, false, settings.tapVolume)
                        },
                        colors = Accents.Kamerton,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Дерево о дерево, еле слышно. Не звучит, пока задаётся вопрос и пока открыт микрофон.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Juicy.TextDim,
                )
            }
        }
        if (settings.tapSound != null) {
            item(key = "sound-tap-volume") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Громкость тапа: ${(settings.tapVolume * 100).roundToInt()} %", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = settings.tapVolume,
                        onValueChange = { v ->
                            update { it.copy(tapVolume = v) }
                            graph.applySound(settings.copy(tapVolume = v))
                        },
                        onValueChangeFinished = { settings.tapSound?.let { graph.audioOutput.tap(it, false, settings.tapVolume) } },
                        valueRange = 0f..2f,
                    )
                }
            }
        }
        item(key = "sound-room") {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Воздух", "маленькая комната вокруг нот и тапов; дрон остаётся сухим", settings.room) { v ->
                    update { it.copy(room = v) }
                    graph.applySound(settings.copy(room = v))
                }
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
                Text("Данные", style = MaterialTheme.typography.titleLarge)
                Text("Библиотека и прогресс хранятся на телефоне и в резервной копии Google. Файл — на всякий случай.", style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                Spacer(Modifier.height(10.dp))
                GhostButton("Экспорт в файл", onClick = { exportLauncher.launch("muzykalka-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth(), colors = Accents.Good, leading = "↑")
                Spacer(Modifier.height(8.dp))
                GhostButton("Импорт из файла", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth(), colors = Accents.Taper, leading = "↓")
                Text("Импорт заменяет всё; текущее состояние сначала сохраняется автоматически.", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
            }
        }
        item(key = "about") {
            Text("Музыкалка ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
        }
    }
}

private fun timbreName(t: ToneTimbre): String = when (t) {
    ToneTimbre.HUM -> "Голос"
    ToneTimbre.MARIMBA -> "Маримба"
    ToneTimbre.KALIMBA -> "Калимба"
    ToneTimbre.FELT -> "Фетр"
    ToneTimbre.CLASSIC -> "Как раньше"
}

private fun timbreHint(t: ToneTimbre): String = when (t) {
    ToneTimbre.HUM -> "Закрытый рот: подъезжает к ноте, чуть плавает, дышит."
    ToneTimbre.MARIMBA -> "Деревянный брусок: короткий стук, потом чистая нота, которая сама затихает."
    ToneTimbre.KALIMBA -> "Стальной язычок на коробочке: «тинь» и долгий тёплый тон."
    ToneTimbre.FELT -> "Пианино через фетр: глухой удар молоточка и струна, которая медленно дышит."
    ToneTimbre.CLASSIC -> "Звук версий до 0.0.54: три неподвижные гармоники и ровное вибрато."
}

private fun tapName(t: TapSound?): String = when (t) {
    null -> "выкл"
    TapSound.STICKS -> "Палочки"
    TapSound.BLOCK -> "Брусок"
    TapSound.TICK -> "Тик"
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
