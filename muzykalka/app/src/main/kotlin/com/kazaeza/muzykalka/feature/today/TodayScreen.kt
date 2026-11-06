package com.kazaeza.muzykalka.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.feature.ScreenFrame
import com.kazaeza.muzykalka.game.DailySet
import com.kazaeza.muzykalka.ui.component.BigButton
import com.kazaeza.muzykalka.ui.component.InfoCard

/**
 * «Сегодня» — one button. Skeleton: numbers are placeholders until tickets T-01 (TodayViewModel
 * reading OrderLogDao.observePlayableNowCount / SessionDao.observePracticeDays).
 */
@Composable
fun TodayScreen(onStartSet: () -> Unit, onMinimalDay: () -> Unit, onSettings: () -> Unit) {
    ScreenFrame(
        title = "Сегодня",
        actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") } },
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Играю с ходу", style = MaterialTheme.typography.titleLarge)
                Text("0", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                Text("вещей из библиотеки — без подсказок, в случайной тональности", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { BigButton("Начать сет", onClick = onStartSet) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onMinimalDay, modifier = Modifier.weight(1f)) { Text("Минимальный день · 5 мин") }
            }
        }
        item { InfoCard("Серия дней", "0 — минимальный день тоже засчитывается", badge = "🔥 0") }
        item {
            InfoCard(
                "Сет дня · 20 минут",
                DailySet.DEFAULT.joinToString("\n") { "${it.variant.ru} — ${it.minutes.toInt()} мин (с версии ${it.variant.since})" } +
                    "\nСвободная игра с магнитофоном — сколько хочешь",
            )
        }
        item { InfoCard("Каркас", "Это сборка-каркас. Режимы появляются по плану docs/ROADMAP.md: 0.1 → 0.2 → 0.3.", badge = "0.0") }
    }
}
