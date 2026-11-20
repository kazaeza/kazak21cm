package com.kazaeza.muzykalka.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * O-79: how much a block of [height] px is shrunk to stand in [room] px: not at all when it fits, otherwise the ratio,
 * but never below [minScale] (smaller than that is not readable — the rest is left to scrolling).
 */
fun fitScale(height: Int, room: Int, minScale: Float): Float =
    if (height <= 0 || height <= room) 1f else (room.toFloat() / height).coerceIn(minScale, 1f)

/**
 * O-79: a block taller than the [roomPx] it is given is drawn smaller — about its middle, text and keys alike — instead of
 * running out of the card. «Иногда текст и знак вопроса не помещаются»: a long answer (the contrast line, the vibe,
 * the function bar) on a short phone. The block is measured at its natural size first, then only drawn smaller, so
 * lines wrap where they always did and nothing is laid out twice; touches follow the drawing.
 */
fun Modifier.fitHeight(roomPx: Int, minScale: Float = 0.6f): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val scale = fitScale(placeable.height, roomPx, minScale)
    val h = (placeable.height * scale).roundToInt()
    layout(placeable.width, h) {
        // the block is centred on the slot it is shrunk into and scaled about its own middle: it fills the slot exactly
        placeable.placeWithLayer(0, (h - placeable.height) / 2) {
            scaleX = scale
            scaleY = scale
        }
    }
}
