package com.kazaeza.muzykalka.feature.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.kazaeza.muzykalka.feature.ScreenFrame
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.ui.component.DegreeKeypad
import com.kazaeza.muzykalka.ui.component.InfoCard

/** «Улов» — record a piece in 30 seconds with I–VII keys. Skeleton shows the keypad; ticket L-02. */
@Composable
fun CaptureScreen(onBack: () -> Unit) {
    val entered = remember { mutableStateListOf<Degree>() }
    ScreenFrame(title = "Улов", onBack = onBack) {
        item { InfoCard("Подобрал на слух — запиши ступенями", "Клавиши I–VII и качество аккорда. Буквы не нужны: тональность выберется случайно при заказе.") }
        item {
            Text(
                if (entered.isEmpty()) "—" else entered.joinToString(" ") { DegreeFormatter.degree(it, DegreeNotation.ROMAN) },
                style = MaterialTheme.typography.headlineLarge,
            )
        }
        item { DegreeKeypad(Degree.NATURALS, DegreeNotation.ROMAN, onDegree = { entered += it }) }
    }
}
