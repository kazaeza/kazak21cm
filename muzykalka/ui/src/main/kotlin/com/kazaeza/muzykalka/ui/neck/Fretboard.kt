package com.kazaeza.muzykalka.ui.neck

import com.kazaeza.muzykalka.ui.theme.Doto
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.drawscope.Stroke
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
    /** O-53: the neck in the app's own language — dark glass, hairline frets, strings of light — instead of rosewood. */
    glass: Boolean = false,
    /** O-53: the frets the hand covers: they are framed, the rest of the neck is dimmed. */
    zone: IntRange? = null,
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
        // Wood — or glass.
        if (glass) {
            drawRoundRect(
                Brush.verticalGradient(listOf(Juicy.Ink4.copy(alpha = 0.55f), Juicy.Ink2.copy(alpha = 0.35f), Juicy.Ink4.copy(alpha = 0.55f)), startY = geo.top, endY = geo.bottom),
                topLeft = Offset(geo.neckLeft, geo.top), size = Size(geo.neckRight - geo.neckLeft, geo.bottom - geo.top),
                cornerRadius = CornerRadius(22f, 22f),
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.10f), topLeft = Offset(geo.neckLeft, geo.top), size = Size(geo.neckRight - geo.neckLeft, geo.bottom - geo.top),
                cornerRadius = CornerRadius(22f, 22f), style = Stroke(1.5f),
            )
        } else {
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF3A2416), Color(0xFF2A190F), Color(0xFF3A2416)), startY = geo.top, endY = geo.bottom),
                topLeft = Offset(geo.neckLeft, geo.top), size = Size(geo.neckRight - geo.neckLeft, geo.bottom - geo.top),
                cornerRadius = CornerRadius(10f, 10f),
            )
        }
        val inlay = if (glass) Color.White.copy(alpha = 0.13f) else Color(0x55F5E6C8)
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
                drawCircle(inlay, r, Offset(x, geo.top + geo.stringGap * 1.5f + geo.margin))
                drawCircle(inlay, r, Offset(x, geo.bottom - geo.stringGap * 1.5f - geo.margin))
            } else {
                drawCircle(inlay, r, Offset(x, (geo.top + geo.bottom) / 2))
            }
        }
        // Nut and frets.
        if (fromFret == 0) {
            val x = geo.fretX(0)
            if (glass) {
                drawRect(Color.White.copy(alpha = 0.55f), Offset(x - 2f, geo.top + 6f), Size(4f, geo.bottom - geo.top - 12f))
            } else {
                drawRect(Brush.horizontalGradient(listOf(Color(0xFFF7EEDC), Color(0xFFD9CDB4))), Offset(x - 5f, geo.top), Size(8f, geo.bottom - geo.top))
            }
        }
        for (f in maxOf(1, fromFret)..toFret) {
            val x = geo.fretX(f)
            if (glass) {
                drawLine(Color.White.copy(alpha = 0.16f), Offset(x, geo.top + 6f), Offset(x, geo.bottom - 6f), strokeWidth = 1.5f)
            } else {
                drawRect(Brush.horizontalGradient(listOf(Color(0xFF8E8E99), Color(0xFFE9E9F2), Color(0xFF8E8E99)), startX = x - 2.5f, endX = x + 2.5f), Offset(x - 2.5f, geo.top), Size(5f, geo.bottom - geo.top))
            }
            val numbered = f in listOf(3, 5, 7, 9, 12, 15, 17, 19, 21, 24)
            if (numbered) {
                val layout = measurer.measure(f.toString(), numberStyle)
                drawText(layout, topLeft = Offset(geo.cellCenterX(f) - layout.size.width / 2, geo.bottom + 4f))
            }
        }
        // Strings.
        for (s in 1..stringCount) {
            val y = geo.stringY(s)
            val thick = if (glass) 1f + (s - 1) * 0.55f else 1.4f + (s - 1) * 0.9f
            val hot = highlightString == s
            val color = when {
                hot -> Juicy.Gold
                glass -> Color.White.copy(alpha = 0.26f + 0.03f * s)
                s <= 2 -> Color(0xFFE6E6EE)
                else -> Color(0xFFD6B47A)
            }
            if (hot) drawLine(Juicy.Gold.copy(alpha = 0.25f + 0.25f * pulse), Offset(geo.neckLeft, y), Offset(geo.neckRight, y), strokeWidth = thick + 10f)
            drawLine(color, Offset(geo.openX - 6f, y), Offset(geo.neckRight, y), strokeWidth = thick)
        }
        // The zone: what the hand covers is framed, the rest of the neck steps back.
        if (zone != null) {
            val from = maxOf(zone.first, fromFret)
            val to = minOf(zone.last, toFret)
            if (from <= to) {
                val a = if (from == 0) geo.cellCenterX(0) + (if (leftHanded) geo.openWidth / 2 else -geo.openWidth / 2) else geo.fretX(from - 1)
                val b = geo.fretX(to)
                val left = minOf(a, b)
                val right = maxOf(a, b)
                val shade = Color.Black.copy(alpha = if (glass) 0.42f else 0.5f)
                if (left > geo.neckLeft + 1f) drawRect(shade, Offset(geo.neckLeft, geo.top), Size(left - geo.neckLeft, geo.bottom - geo.top))
                if (right < geo.neckRight - 1f) drawRect(shade, Offset(right, geo.top), Size(geo.neckRight - right, geo.bottom - geo.top))
                drawRoundRect(Color.White.copy(alpha = 0.05f), Offset(left, geo.top), Size(right - left, geo.bottom - geo.top), CornerRadius(14f, 14f))
                drawRoundRect(Juicy.Gold.copy(alpha = 0.55f), Offset(left, geo.top), Size(right - left, geo.bottom - geo.top), CornerRadius(14f, 14f), style = Stroke(2f))
            }
        }
        // Markers.
        for (mk in markers) {
            if (mk.position.fret !in fromFret..toFret || mk.position.string !in 1..stringCount) continue
            val c = geo.center(mk.position)
            val r = geo.stringGap * 0.42f
            if (mk.pulse) drawCircle(mk.color.copy(alpha = 0.25f + 0.25f * pulse), r * (1.5f + 0.4f * pulse), c)
            if (glass) {
                if (mk.ghost) {
                    // a root that waits: a ring of its colour, nothing inside but its name
                    drawCircle(Juicy.Ink2.copy(alpha = 0.85f), r, c)
                    drawCircle(mk.color.copy(alpha = 0.75f), r, c, style = Stroke(1.6f))
                } else {
                    drawCircle(Brush.radialGradient(listOf(mk.color.copy(alpha = 0.45f), Color.Transparent), c, r * 2.1f), r * 2.1f, c)
                    drawCircle(mk.color, r, c)
                    if (mk.pulse) drawCircle(Color.White.copy(alpha = 0.9f), r, c, style = Stroke(2f))
                }
            } else if (mk.ghost) {
                drawCircle(mk.color.copy(alpha = 0.35f), r, c)
            } else {
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), mk.color, mk.color), center = Offset(c.x - r * 0.3f, c.y - r * 0.3f), radius = r * 1.4f), r, c)
            }
            mk.label?.let {
                val layout = if (glass) measurer.measure(neckLabel(it, if (mk.ghost) mk.color else Color(0xFF15111F))) else measurer.measure(it, labelStyle)
                drawText(layout, topLeft = Offset(c.x - layout.size.width / 2, c.y - layout.size.height / 2))
            }
        }
    }
}

/** O-53: a label of the glass neck — digits in the dot-matrix face, as on the dial; the rest in the text face. */
private fun neckLabel(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    val size = if (text.length <= 2) 15.sp else 11.sp
    text.forEach { ch ->
        val style = if (ch.isDigit()) {
            SpanStyle(fontFamily = Doto, fontWeight = FontWeight.Bold, fontSize = size, color = color)
        } else {
            SpanStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = size * 0.85f, color = color)
        }
        withStyle(style) { append(ch) }
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
