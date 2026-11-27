package com.kazaeza.muzykalka.feature.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.ui.component.AppGlyph
import com.kazaeza.muzykalka.ui.component.AppIcon
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme

/**
 * ADR-019: the pause button and the back gesture never skip or close anything on their own — they
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
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentPadding = 24.dp,
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Juicy.Ink3)
                        .border(BorderStroke(1.dp, Juicy.LineStrong), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AppGlyph(AppIcon.PAUSE, Juicy.Text, size = 24.dp)
                }
                Text("Пауза", style = MaterialTheme.typography.headlineLarge, color = Juicy.Text)
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(4.dp))
                JuicyButton(
                    "Продолжить",
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                    height = 64.dp,
                    leadingIcon = AppIcon.PLAY,
                )
                if (onNext != null) {
                    GhostButton(
                        "Следующее упражнение",
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth(),
                        colors = Accents.Tir,
                        height = 56.dp,
                        leadingIcon = AppIcon.CHEVRON_RIGHT,
                    )
                }
                GhostButton(
                    exitLabel,
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                    colors = Accents.Calm,
                    height = 56.dp,
                    leadingIcon = AppIcon.CLOSE,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PauseOverlayPreview() = MuzykalkaTheme {
    PauseOverlay(onResume = {}, onExit = {}, exitLabel = "Выйти из сета", onNext = {}, hint = "Раунд стоит — время не идёт.")
}
