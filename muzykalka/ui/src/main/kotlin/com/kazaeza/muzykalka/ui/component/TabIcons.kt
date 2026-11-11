package com.kazaeza.muzykalka.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** O-31: the four tabs are marks, not words. */
enum class TabIcon { TODAY, MODES, LIBRARY, PROGRESS }

/**
 * A tab's mark, drawn in the language of the game screens: one hairline weight, round ends, no fills but dots.
 * Today is the Tir's dial with its sphere, the modes are four spheres, the library is spines of books, progress
 * is a line climbing to a point. Geometry is on a 24 × 24 grid, so it scales with the box.
 */
@Composable
fun TabGlyph(icon: TabIcon, color: Color, modifier: Modifier = Modifier) {
    val path = remember { Path() }
    Canvas(modifier.size(26.dp)) {
        val u = size.minDimension / 24f
        val w = 1.5f * u
        val line = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun at(x: Float, y: Float) = Offset(x * u, y * u)
        when (icon) {
            TabIcon.TODAY -> {
                val c = at(12f, 12f)
                for (i in 0 until 12) {
                    val a = i * PI / 6
                    val dir = Offset(sin(a).toFloat(), -cos(a).toFloat())
                    drawLine(color, c + dir * (7.5f * u), c + dir * (10f * u), w, StrokeCap.Round)
                }
                drawCircle(color, 2.75f * u, c)
            }
            TabIcon.MODES -> {
                drawCircle(color, 4f * u, at(7.25f, 7.25f))
                drawCircle(color, 3.25f * u, at(16.75f, 7.25f), style = line)
                drawCircle(color, 3.25f * u, at(7.25f, 16.75f), style = line)
                drawCircle(color, 3.25f * u, at(16.75f, 16.75f), style = line)
            }
            TabIcon.LIBRARY -> {
                drawLine(color, at(6f, 5f), at(6f, 19f), w, StrokeCap.Round)
                drawLine(color, at(11f, 5f), at(11f, 19f), w, StrokeCap.Round)
                drawLine(color, at(15.5f, 6.5f), at(19f, 19f), w, StrokeCap.Round)
            }
            TabIcon.PROGRESS -> {
                path.rewind()
                path.moveTo(4f * u, 17f * u)
                path.lineTo(9f * u, 11.5f * u)
                path.lineTo(13.5f * u, 14.5f * u)
                path.lineTo(19.5f * u, 7f * u)
                drawPath(path, color, style = line)
                drawCircle(color, 2f * u, at(19.5f, 7f))
            }
        }
    }
}
