package com.kazaeza.muzykalka.ui.neck

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazaeza.muzykalka.music.FretPosition
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Manrope
import kotlin.math.pow

/** A dot on the neck. [pulse] makes it breathe (the current target). */
data class FretMarker(
    val position: FretPosition,
    val color: Color,
    val label: String? = null,
    val pulse: Boolean = false,
    val ghost: Boolean = false,
)

/**
 * The guitar neck, drawn: rosewood, real fret spacing, inlays, wound strings. String 1 (thinnest)
 * is on top, as in tab. Shows [markers], an optional [heat] colour per position, and an optional
 * [highlightString]. [fromFret]..[toFret] is the visible window.
 */
@Composable
fun Fretboard(
    stringCount: Int,
    modifier: Modifier = Modifier,
    fromFret: Int = 0,
    toFret: Int = 12,
    markers: List<FretMarker> = emptyList(),
    heat: Map<FretPosition, Color> = emptyMap(),
    highlightString: Int? = null,
    leftHanded: Boolean = false,
    height: Dp = 190.dp,
    onTap: ((FretPosition) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val t = rememberInfiniteTransition(label = "neck")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    val labelStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF15111F))
    val numberStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Juicy.TextFaint)

    var m = modifier.fillMaxWidth().height(height)
    if (onTap != null) {
        m = m.pointerInput(stringCount, fromFret, toFret, leftHanded) {
            detectTapGestures { p ->
                val geo = Geometry(size.width.toFloat(), size.height.toFloat(), stringCount, fromFret, toFret, leftHanded)
                geo.hit(p)?.let(onTap)
            }
        }
    }
    Canvas(m) {
        val geo = Geometry(size.width, size.height, stringCount, fromFret, toFret, leftHanded)
        // Wood.
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF3A2416), Color(0xFF2A190F), Color(0xFF3A2416)), startY = geo.top, endY = geo.bottom),
            topLeft = Offset(geo.neckLeft, geo.top), size = Size(geo.neckRight - geo.neckLeft, geo.bottom - geo.top),
            cornerRadius = CornerRadius(10f, 10f),
        )
        // Heat.
        heat.forEach { (pos, color) ->
            if (pos.fret in fromFret..toFret && pos.string in 1..stringCount) {
                val c = geo.center(pos)
                val w = geo.cellWidth(pos.fret)
                drawRoundRect(color.copy(alpha = 0.55f), Offset(c.x - w / 2 + 2, c.y - geo.stringGap / 2 + 2), Size(w - 4, geo.stringGap - 4), CornerRadius(8f, 8f))
            }
        }
        // Inlays.
        for (f in listOf(3, 5, 7, 9, 12, 15, 17, 19, 21, 24)) {
            if (f !in (fromFret + 1)..toFret) continue
            val x = geo.cellCenterX(f)
            val r = geo.stringGap * 0.16f
            if (f % 12 == 0) {
                drawCircle(Color(0x55F5E6C8), r, Offset(x, geo.top + geo.stringGap * 1.5f + geo.margin))
                drawCircle(Color(0x55F5E6C8), r, Offset(x, geo.bottom - geo.stringGap * 1.5f - geo.margin))
            } else {
                drawCircle(Color(0x55F5E6C8), r, Offset(x, (geo.top + geo.bottom) / 2))
            }
        }
        // Nut and frets.
        if (fromFret == 0) {
            val x = geo.fretX(0)
            drawRect(Brush.horizontalGradient(listOf(Color(0xFFF7EEDC), Color(0xFFD9CDB4))), Offset(x - 5f, geo.top), Size(8f, geo.bottom - geo.top))
        }
        for (f in maxOf(1, fromFret)..toFret) {
            val x = geo.fretX(f)
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF8E8E99), Color(0xFFE9E9F2), Color(0xFF8E8E99)), startX = x - 2.5f, endX = x + 2.5f), Offset(x - 2.5f, geo.top), Size(5f, geo.bottom - geo.top))
            val numbered = f in listOf(3, 5, 7, 9, 12, 15, 17, 19, 21, 24)
            if (numbered) {
                val layout = measurer.measure(f.toString(), numberStyle)
                drawText(layout, topLeft = Offset(geo.cellCenterX(f) - layout.size.width / 2, geo.bottom + 4f))
            }
        }
        // Strings.
        for (s in 1..stringCount) {
            val y = geo.stringY(s)
            val thick = 1.4f + (s - 1) * 0.9f
            val hot = highlightString == s
            val color = when {
                hot -> Juicy.Gold
                s <= 2 -> Color(0xFFE6E6EE)
                else -> Color(0xFFD6B47A)
            }
            if (hot) drawLine(Juicy.Gold.copy(alpha = 0.25f + 0.25f * pulse), Offset(geo.neckLeft, y), Offset(geo.neckRight, y), strokeWidth = thick + 10f)
            drawLine(color, Offset(geo.openX - 6f, y), Offset(geo.neckRight, y), strokeWidth = thick)
        }
        // Markers.
        for (mk in markers) {
            if (mk.position.fret !in fromFret..toFret || mk.position.string !in 1..stringCount) continue
            val c = geo.center(mk.position)
            val r = geo.stringGap * 0.42f
            if (mk.pulse) drawCircle(mk.color.copy(alpha = 0.25f + 0.25f * pulse), r * (1.5f + 0.4f * pulse), c)
            if (mk.ghost) {
                drawCircle(mk.color.copy(alpha = 0.35f), r, c)
            } else {
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), mk.color, mk.color), center = Offset(c.x - r * 0.3f, c.y - r * 0.3f), radius = r * 1.4f), r, c)
            }
            mk.label?.let {
                val layout = measurer.measure(it, labelStyle)
                drawText(layout, topLeft = Offset(c.x - layout.size.width / 2, c.y - layout.size.height / 2))
            }
        }
    }
}

/** Shared geometry for drawing and hit-testing. */
private class Geometry(
    val width: Float,
    val height: Float,
    val strings: Int,
    val from: Int,
    val to: Int,
    val leftHanded: Boolean,
) {
    val margin = 6f
    val top = 8f
    val bottom = height - 22f
    val openWidth = if (from == 0) width * 0.07f else 0f
    val neckLeft = openWidth
    val neckRight = width - 4f
    val stringGap = (bottom - top - 2 * margin) / (strings - 1).coerceAtLeast(1) * (strings - 1) / strings
    val openX = if (from == 0) openWidth * 0.5f else neckLeft

    private fun pos(n: Int) = 1f - 2f.pow(-n / 12f)

    private fun rawX(n: Int): Float {
        val a = pos(maxOf(from, 0))
        val b = pos(to)
        return neckLeft + (pos(n) - a) / (b - a) * (neckRight - neckLeft)
    }

    fun fretX(n: Int): Float = mirror(rawX(n))

    private fun mirror(x: Float) = if (leftHanded) width - x else x

    fun stringY(s: Int): Float = top + margin + stringGap / 2 + (s - 1) * stringGap

    fun cellCenterX(f: Int): Float = if (f == 0) mirror(openX) else mirror((rawX(f - 1) + rawX(f)) / 2)

    fun cellWidth(f: Int): Float = if (f == 0) openWidth else kotlin.math.abs(rawX(f) - rawX(f - 1))

    fun center(p: FretPosition) = Offset(cellCenterX(p.fret), stringY(p.string))

    fun hit(p: Offset): FretPosition? {
        val s = (((p.y - top - margin) / stringGap).toInt() + 1).coerceIn(1, strings)
        for (f in from..to) {
            val cx = cellCenterX(f)
            if (kotlin.math.abs(p.x - cx) <= cellWidth(f) / 2 + 2) return FretPosition(s, f)
        }
        return null
    }
}
