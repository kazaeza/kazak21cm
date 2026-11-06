package com.kazaeza.muzykalka.feature.lexicon

import androidx.compose.runtime.Composable
import com.kazaeza.muzykalka.feature.ScreenFrame
import com.kazaeza.muzykalka.ui.component.InfoCard

/** «Лексикон» — phrases with labels («над V7»). Version 0.3, tickets X-02..X-03. */
@Composable
fun LexiconScreen() {
    ScreenFrame(title = "Лексикон") {
        item { InfoCard("Появится в 0.3", "Фразы из Магнитофона: ступени, над каким аккордом, твоя запись, статус.", badge = "v0.3") }
    }
}
