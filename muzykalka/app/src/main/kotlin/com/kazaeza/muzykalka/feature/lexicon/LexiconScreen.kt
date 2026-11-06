package com.kazaeza.muzykalka.feature.lexicon

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.ui.component.Badge
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy

/** «Лексикон» — phrases with labels («над V7»). Version 0.3, tickets X-01..X-03. */
@Composable
fun LexiconScreen() {
    ScreenFrame(title = "Лексикон", subtitle = "из импровизации — в словарь", accent = Accents.Recorder) {
        item {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Recorder) {
                Text("📼", style = MaterialTheme.typography.displayMedium)
                Text("Скоро: Магнитофон и Лексикон", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Играешь как хочешь — приложение записывает линии и переводит их в ступени. Удачные куски становятся фразами с ярлыками «над ii–V–I», «над V7» и повторяются как карточки.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Juicy.TextDim,
                )
                Spacer(Modifier.height(12.dp))
                Badge("v0.3", Accents.Recorder)
            }
        }
    }
}
