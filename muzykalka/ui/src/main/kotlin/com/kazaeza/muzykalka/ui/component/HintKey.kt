package com.kazaeza.muzykalka.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.ui.theme.Juicy

/** What a [HintKey] shows: a tune (the hint is a song) or a stair (the hint is how the step sounds — «ступени»). */
enum class HintGlyph { SONG, STAIRS }

/**
 * O-80: the switch of what a hint after a miss is made of — a small round key with a drawn symbol instead of the long
 * chip it was: a double note for the songs, a stair for the feel of the step. Same size of touch as [ReplayKey]
 * ([GameTouchTarget]); the ring is lit while the songs are on. [label] is what a screen reader says (and tests look for).
 */
@Composable
fun HintKey(glyph: HintGlyph, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Juicy.Cyan) {
    val source = remember { MutableInteractionSource() }
    TapOnPress(source)
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    Canvas(
        modifier
            .size(GameTouchTarget)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .graphicsLayer {
                scaleX = 1f - 0.08f * press
                scaleY = 1f - 0.08f * press
            },
    ) {
        val songs = glyph == HintGlyph.SONG
        drawCircle(tint.copy(alpha = if (songs) 0.14f else 0.05f + 0.1f * press), 23.dp.toPx())
        drawCircle(tint.copy(alpha = (if (songs) 0.7f else 0.4f) + 0.2f * press), 23.dp.toPx(), style = Stroke(1.dp.toPx()))
        if (songs) drawTune(tint) else drawStair(tint)
    }
}

/** Two note heads joined by a beam — «♫», drawn: the glyph is not in our fonts at a size that holds up. */
private fun DrawScope.drawTune(tint: Color) {
    val w = 1.8.dp.toPx()
    val head = Size(7.dp.toPx(), 5.dp.toPx())
    val leftHead = center + Offset(-6.dp.toPx(), 5.dp.toPx())
    val rightHead = center + Offset(3.dp.toPx(), 3.dp.toPx())
    for (c in listOf(leftHead, rightHead)) {
        rotate(-20f, pivot = c) { drawOval(tint, c - Offset(head.width / 2, head.height / 2), head) }
    }
    val stemLeft = leftHead + Offset(3.dp.toPx(), 0f)
    val stemRight = rightHead + Offset(3.dp.toPx(), 0f)
    val top = -7.dp.toPx()
    drawLine(tint, stemLeft, Offset(stemLeft.x, center.y + top), w, StrokeCap.Round)
    drawLine(tint, stemRight, Offset(stemRight.x, center.y + top - 2.dp.toPx()), w, StrokeCap.Round)
    drawLine(tint, Offset(stemLeft.x, center.y + top), Offset(stemRight.x, center.y + top - 2.dp.toPx()), 2.6.dp.toPx(), StrokeCap.Butt)
}

/** Four steps going up — the scale, and the «ступени» of it. */
private fun DrawScope.drawStair(tint: Color) {
    val step = 4.5.dp.toPx()
    val x0 = center.x - 2 * step
    val y0 = center.y + 7.dp.toPx()
    val path = Path().apply {
        moveTo(x0, y0)
        for (i in 0 until 4) {
            val y = y0 - (i + 1) * step * 0.9f
            lineTo(x0 + i * step, y)
            lineTo(x0 + (i + 1) * step, y)
        }
    }
    drawPath(path, tint, style = Stroke(1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
