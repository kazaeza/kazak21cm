package com.kazaeza.muzykalka.feature.progress

import androidx.compose.runtime.Composable
import com.kazaeza.muzykalka.feature.ScreenFrame
import com.kazaeza.muzykalka.game.Comparison
import com.kazaeza.muzykalka.game.Graduation
import com.kazaeza.muzykalka.game.Link
import com.kazaeza.muzykalka.ui.component.InfoCard

/** «Прогресс» — triangle, heatmap, confusion matrix, «С ходу» chart, report card. Tickets P-01..P-03. */
@Composable
fun ProgressScreen() {
    ScreenFrame(title = "Прогресс") {
        item { InfoCard("Треугольник связей", Link.entries.joinToString("\n") { "${it.ru}: —" }) }
        item {
            InfoCard(
                "Пороги выпускного",
                Graduation.THRESHOLDS.joinToString("\n") { t ->
                    val op = when (t.comparison) {
                        Comparison.LESS_THAN -> "<"
                        Comparison.AT_LEAST -> "≥"
                        Comparison.AT_MOST -> "≤"
                    }
                    "${t.skill.ru}: $op ${t.value} ${t.unit}"
                },
            )
        }
    }
}
