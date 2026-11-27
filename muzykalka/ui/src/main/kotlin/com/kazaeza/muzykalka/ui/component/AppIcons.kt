package com.kazaeza.muzykalka.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Compact vector glyph set for «Обертон 2.0» controls (24×24 grid, no external icon library). */
enum class AppIcon {
    PLAY,
    PAUSE,
    STOP,
    BACK,
    SETTINGS,
    REPLAY,
    CHEVRON_RIGHT,
    CHECK,
    CLOSE,
    VOLUME_OFF,
    MIC,
    GUITAR,
    PIANO,
    CLOCK,
    EXPORT,
    IMPORT,
    ADD,
    SPARK,
}

/**
 * Draws a single [AppIcon] on a 24×24 coordinate grid matching [TabGlyph] stroke geometry
 * (`1.6f` hairline, round caps and joins).
 */
@Composable
fun AppGlyph(
    icon: AppIcon,
    color: Color = Juicy.Text,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    val path = remember { Path() }
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val w = 1.6f * u
        val line = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun at(x: Float, y: Float) = Offset(x * u, y * u)
        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, at(x1, y1), at(x2, y2), w, StrokeCap.Round)

        path.rewind()
        when (icon) {
            AppIcon.PLAY -> {
                path.moveTo(8f * u, 5.5f * u)
                path.lineTo(18.5f * u, 12f * u)
                path.lineTo(8f * u, 18.5f * u)
                path.close()
                drawPath(path, color, style = line)
            }
            AppIcon.PAUSE -> {
                seg(8.5f, 5.5f, 8.5f, 18.5f)
                seg(15.5f, 5.5f, 15.5f, 18.5f)
            }
            AppIcon.STOP -> {
                drawRoundRect(
                    color,
                    topLeft = at(6f, 6f),
                    size = Size(12f * u, 12f * u),
                    cornerRadius = CornerRadius(2.5f * u, 2.5f * u),
                    style = line,
                )
            }
            AppIcon.BACK -> {
                seg(19f, 12f, 5.5f, 12f)
                path.moveTo(11f * u, 6.5f * u)
                path.lineTo(5.5f * u, 12f * u)
                path.lineTo(11f * u, 17.5f * u)
                drawPath(path, color, style = line)
            }
            AppIcon.SETTINGS -> {
                val c = at(12f, 12f)
                drawCircle(color, 3f * u, c, style = line)
                drawCircle(color, 6.4f * u, c, style = line)
                for (i in 0 until 6) {
                    val a = i * PI / 3
                    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                    drawLine(color, c + dir * (6.8f * u), c + dir * (9.2f * u), w, StrokeCap.Round)
                }
            }
            AppIcon.REPLAY -> {
                drawArc(
                    color = color,
                    startAngle = -135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = at(5.2f, 5.2f),
                    size = Size(13.6f * u, 13.6f * u),
                    style = line,
                )
                path.moveTo(7.2f * u, 3.8f * u)
                path.lineTo(7.2f * u, 7.2f * u)
                path.lineTo(10.6f * u, 7.2f * u)
                drawPath(path, color, style = line)
            }
            AppIcon.CHEVRON_RIGHT -> {
                path.moveTo(9.5f * u, 6.5f * u)
                path.lineTo(15f * u, 12f * u)
                path.lineTo(9.5f * u, 17.5f * u)
                drawPath(path, color, style = line)
            }
            AppIcon.CHECK -> {
                path.moveTo(5.5f * u, 12.5f * u)
                path.lineTo(10f * u, 17f * u)
                path.lineTo(18.5f * u, 7.5f * u)
                drawPath(path, color, style = line)
            }
            AppIcon.CLOSE -> {
                seg(6.5f, 6.5f, 17.5f, 17.5f)
                seg(17.5f, 6.5f, 6.5f, 17.5f)
            }
            AppIcon.VOLUME_OFF -> {
                path.moveTo(4.5f * u, 9.5f * u)
                path.lineTo(8f * u, 9.5f * u)
                path.lineTo(12.5f * u, 6f * u)
                path.lineTo(12.5f * u, 18f * u)
                path.lineTo(8f * u, 14.5f * u)
                path.lineTo(4.5f * u, 14.5f * u)
                path.close()
                drawPath(path, color, style = line)
                seg(16f, 9.5f, 20f, 14.5f)
                seg(20f, 9.5f, 16f, 14.5f)
            }
            AppIcon.MIC -> {
                drawRoundRect(
                    color,
                    topLeft = at(9.2f, 4f),
                    size = Size(5.6f * u, 9.5f * u),
                    cornerRadius = CornerRadius(2.8f * u, 2.8f * u),
                    style = line,
                )
                drawArc(
                    color = color,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = at(6.2f, 8.2f),
                    size = Size(11.6f * u, 8.6f * u),
                    style = line,
                )
                seg(12f, 16.8f, 12f, 20f)
                seg(9f, 20f, 15f, 20f)
            }
            AppIcon.GUITAR -> {
                drawCircle(color, 4.5f * u, at(8.8f, 15.2f), style = line)
                drawCircle(color, 1.3f * u, at(8.8f, 15.2f))
                seg(12f, 12f, 18.6f, 5.4f)
                seg(17.2f, 4.2f, 19.8f, 6.8f)
            }
            AppIcon.PIANO -> {
                drawRoundRect(
                    color,
                    topLeft = at(4f, 6f),
                    size = Size(16f * u, 12f * u),
                    cornerRadius = CornerRadius(2f * u, 2f * u),
                    style = line,
                )
                seg(9.3f, 6f, 9.3f, 18f)
                seg(14.7f, 6f, 14.7f, 18f)
                drawLine(color, at(9.3f, 6.4f), at(9.3f, 12.2f), 2.6f * u, StrokeCap.Butt)
                drawLine(color, at(14.7f, 6.4f), at(14.7f, 12.2f), 2.6f * u, StrokeCap.Butt)
            }
            AppIcon.CLOCK -> {
                drawCircle(color, 8f * u, at(12f, 12f), style = line)
                path.moveTo(12f * u, 7.5f * u)
                path.lineTo(12f * u, 12f * u)
                path.lineTo(15.5f * u, 14f * u)
                drawPath(path, color, style = line)
            }
            AppIcon.EXPORT -> {
                seg(12f, 14.5f, 12f, 5f)
                path.moveTo(8.5f * u, 8.5f * u)
                path.lineTo(12f * u, 5f * u)
                path.lineTo(15.5f * u, 8.5f * u)
                drawPath(path, color, style = line)
                path.rewind()
                path.moveTo(5f * u, 14.5f * u)
                path.lineTo(5f * u, 18.5f * u)
                path.lineTo(19f * u, 18.5f * u)
                path.lineTo(19f * u, 14.5f * u)
                drawPath(path, color, style = line)
            }
            AppIcon.IMPORT -> {
                seg(12f, 4.5f, 12f, 14f)
                path.moveTo(8.5f * u, 10.5f * u)
                path.lineTo(12f * u, 14f * u)
                path.lineTo(15.5f * u, 10.5f * u)
                drawPath(path, color, style = line)
                path.rewind()
                path.moveTo(5f * u, 14.5f * u)
                path.lineTo(5f * u, 18.5f * u)
                path.lineTo(19f * u, 18.5f * u)
                path.lineTo(19f * u, 14.5f * u)
                drawPath(path, color, style = line)
            }
            AppIcon.ADD -> {
                seg(12f, 5.5f, 12f, 18.5f)
                seg(5.5f, 12f, 18.5f, 12f)
            }
            AppIcon.SPARK -> {
                path.moveTo(12f * u, 4f * u)
                path.cubicTo(12f * u, 10.2f * u, 13.8f * u, 12f * u, 20f * u, 12f * u)
                path.cubicTo(13.8f * u, 12f * u, 12f * u, 13.8f * u, 12f * u, 20f * u)
                path.cubicTo(12f * u, 13.8f * u, 10.2f * u, 12f * u, 4f * u, 12f * u)
                path.cubicTo(10.2f * u, 12f * u, 12f * u, 10.2f * u, 12f * u, 4f * u)
                path.close()
                drawPath(path, color, style = line)
            }
        }
    }
}
