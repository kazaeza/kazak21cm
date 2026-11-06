package com.kazaeza.muzykalka.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.data.settings.SettingsRepository
import com.kazaeza.muzykalka.feature.ScreenFrame
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.NoteNaming
import com.kazaeza.muzykalka.music.Tuning
import kotlinx.coroutines.launch

/** «Настройки». Persisted via DataStore. Input device picker = ticket M-06; export/import = D-03. */
@Composable
fun SettingsScreen(repo: SettingsRepository, onBack: () -> Unit) {
    val settings by repo.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    fun update(f: (Settings) -> Settings) { scope.launch { repo.update(f) } }

    ScreenFrame(title = "Настройки", onBack = onBack) {
        item { Text("Строй", style = MaterialTheme.typography.titleLarge) }
        Tuning.PRESETS.forEach { t ->
            item(key = t.id) { Choice(t.ruName, settings.tuningId == t.id) { update { it.copy(tuningId = t.id) } } }
        }
        item { Text("Запись ступеней", style = MaterialTheme.typography.titleLarge) }
        listOf(DegreeNotation.ARABIC to "1–7", DegreeNotation.ROMAN to "римские (I–VII)", DegreeNotation.SOLFEGE to "до-ре-ми").forEach { (n, label) ->
            item(key = n.name) { Choice(label, settings.degreeNotation == n) { update { it.copy(degreeNotation = n) } } }
        }
        item { Text("Названия нот", style = MaterialTheme.typography.titleLarge) }
        listOf(NoteNaming.LATIN to "латиница (F♯)", NoteNaming.SOLFEGE_RU to "по-русски (фа-диез)").forEach { (n, label) ->
            item(key = n.name) { Choice(label, settings.noteNaming == n) { update { it.copy(noteNaming = n) } } }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Играю в наушниках", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = settings.headphones, onCheckedChange = { v -> update { it.copy(headphones = v) } })
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
