package com.kazaeza.muzykalka.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Motion

/**
 * O-32: «once more» — big enough to hit without looking. The touch target is [GameTouchTarget]; what is seen is
 * a hairline circle with a turning arrow. The arrow is drawn: the ↻ glyph is in none of our fonts and came out
 * small and thin. [label] is what a screen reader says (and what tests look for).
 */
@Composable
fun ReplayKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tint: Color = Juicy.Text) {
    val source = remember { MutableInteractionSource() }
    TapOnPress(source)
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    val presence by animateFloatAsState(if (enabled) 1f else 0.35f, tween(Motion.SHORT), label = "presence")
    Canvas(
        modifier
            .size(GameTouchTarget)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .graphicsLayer {
                alpha = presence
                scaleX = 1f - 0.08f * press
                scaleY = 1f - 0.08f * press
            },
    ) {
        drawCircle(Color.White.copy(alpha = 0.18f + 0.2f * press), 23.dp.toPx(), style = Stroke(1.dp.toPx()))
        val r = 9.5.dp.toPx()
        val w = 2.dp.toPx()
        // clockwise from just past the gap (upper right) round to the top, where the arrowhead points on
        drawArc(tint, -25f, 295f, useCenter = false, topLeft = center - Offset(r, r), size = Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
        val tip = center + Offset(1.5.dp.toPx(), -r)
        val wing = 4.5.dp.toPx()
        drawLine(tint, tip, tip + Offset(-wing, -wing), w, StrokeCap.Round)
        drawLine(tint, tip, tip + Offset(-wing, wing), w, StrokeCap.Round)
    }
}
