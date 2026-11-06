package com.kazaeza.muzykalka.feature.game

import androidx.compose.runtime.Composable
import com.kazaeza.muzykalka.feature.ScreenFrame
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.ui.component.InfoCard

/**
 * Full-screen game host. Each variant gets a driver (core:game) + a ViewModel wrapping RoundEngine
 * + a content composable. Skeleton: placeholder until the variant's ticket lands.
 */
@Composable
fun GameScreen(variant: Variant, onExit: () -> Unit) {
    ScreenFrame(title = variant.ru, onBack = onExit) {
        item {
            InfoCard(
                "Скоро",
                "Режим «${variant.ru}» (${variant.mode.ru}) появится в версии ${variant.since}. " +
                    "Раунд 60–90 секунд, засчитывается первая нота, ошибка — сразу правильный ответ.",
                badge = "v${variant.since}",
            )
        }
    }
}
