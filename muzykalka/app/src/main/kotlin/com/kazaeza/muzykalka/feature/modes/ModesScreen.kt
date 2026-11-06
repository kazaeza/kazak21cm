package com.kazaeza.muzykalka.feature.modes

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kazaeza.muzykalka.feature.ScreenFrame
import com.kazaeza.muzykalka.game.Levels
import com.kazaeza.muzykalka.game.ModeId
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.ui.component.InfoCard

/** «Режимы» — six tiles; each variant opens the game screen. Level/record come with ticket T-02. */
@Composable
fun ModesScreen(onPlay: (Variant) -> Unit) {
    ScreenFrame(title = "Режимы") {
        ModeId.entries.forEach { mode ->
            item(key = mode.name) {
                val links = if (mode.links.isEmpty()) "" else "\nСвязь: " + mode.links.joinToString(", ") { it.ru }
                val levels = Levels.of(mode).takeIf { it.isNotEmpty() }?.let { "\nУровни: " + it.joinToString(" → ") } ?: ""
                InfoCard(mode.ru, mode.goal + links + levels + "\n" + mode.guitar.ru, badge = "v${mode.since}")
            }
            Variant.entries.filter { it.mode == mode }.forEach { v ->
                item(key = v.name) {
                    Text(
                        "▶ ${v.ru} · ${v.guitar.ru} · v${v.since}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.clickable { onPlay(v) },
                    )
                }
            }
        }
    }
}
