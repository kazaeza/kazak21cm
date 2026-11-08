package com.kazaeza.muzykalka.feature.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme

/**
 * ADR-019: the ⏸ button and the back gesture never skip or close anything on their own — they
 * open this. Back again = continue. [onNext] is only offered inside a set.
 */
@Composable
fun PauseOverlay(
    onResume: () -> Unit,
    onExit: () -> Unit,
    exitLabel: String,
    onNext: (() -> Unit)? = null,
    hint: String? = null,
) {
    BackHandler(onBack = onResume)
    Box(
        Modifier
            .fillMaxSize()
            .background(Juicy.Ink.copy(alpha = 0.88f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("❙❙", style = MaterialTheme.typography.displayMedium)
            Text("Пауза", style = MaterialTheme.typography.headlineLarge, color = Juicy.Text)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim, textAlign = TextAlign.Center)
            }
            JuicyButton("Продолжить", onClick = onResume, modifier = Modifier.fillMaxWidth(), height = 72.dp, leading = "►")
            if (onNext != null) {
                GhostButton("Следующее упражнение", onClick = onNext, modifier = Modifier.fillMaxWidth(), colors = Accents.Tir, leading = "»", height = 60.dp)
            }
            GhostButton(exitLabel, onClick = onExit, modifier = Modifier.fillMaxWidth(), colors = Accents.Calm, leading = "✕", height = 60.dp)
        }
    }
}

@Preview
@Composable
private fun PauseOverlayPreview() = MuzykalkaTheme {
    PauseOverlay(onResume = {}, onExit = {}, exitLabel = "Выйти из сета", onNext = {}, hint = "Раунд стоит — время не идёт.")
}
