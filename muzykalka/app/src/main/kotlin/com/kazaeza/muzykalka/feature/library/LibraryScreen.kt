package com.kazaeza.muzykalka.feature.library

import androidx.compose.runtime.Composable
import com.kazaeza.muzykalka.feature.ScreenFrame
import com.kazaeza.muzykalka.ui.component.BigButton
import com.kazaeza.muzykalka.ui.component.InfoCard

/** «Библиотека» — the user's pieces in degrees. Ticket L-01 (list) + L-02 (song editor). */
@Composable
fun LibraryScreen(onCapture: () -> Unit) {
    ScreenFrame(title = "Библиотека") {
        item {
            InfoCard(
                "Пока пусто",
                "Сюда попадают твои вещи и риффы: ступени по частям, форма (AABC), стартовый лад, " +
                    "статус (новая, учу, держится) и дата следующего заказа.",
            )
        }
        item { BigButton("Улов: записать вещь", onClick = onCapture) }
    }
}
