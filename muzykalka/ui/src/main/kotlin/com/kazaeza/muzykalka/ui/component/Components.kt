package com.kazaeza.muzykalka.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kazaeza.muzykalka.ui.theme.Doto
import com.kazaeza.muzykalka.ui.theme.Motion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.ui.effect.gradientTint
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy

/** Minimum touch target in game screens: hands are busy with the guitar. */
val GameTouchTarget = 72.dp

/** Press feedback shared by every juicy control: squash on press, spring back. */
@Composable
private fun rememberPressScale(source: MutableInteractionSource, amount: Float = 0.94f): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) amount else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "press",
    )
    return scale
}

/** «Обертон» (O-24): a hairline, the only kind of edge the design keeps. */
private val Hairline = Color(0x24FFFFFF)

/** The main call-to-action: a warm-white pill with dark ink and a soft glow of [colors]. */
@Composable
fun JuicyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = Accents.Hero,
    enabled: Boolean = true,
    leading: String? = null,
    height: Dp = 64.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, 0.96f)
    val shape = RoundedCornerShape(height / 2)
    val fill by animateColorAsState(if (enabled) Juicy.Paper else Juicy.Ink3, tween(Motion.MEDIUM), label = "fill")
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = height)
            .shadow(if (enabled) 28.dp else 0.dp, shape, ambientColor = colors.first(), spotColor = colors.last())
            .clip(shape)
            .background(fill)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (enabled) Juicy.PaperInk else Juicy.TextFaint
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (!leading.isNullOrBlank()) {
                Text(leading, style = textStyle, color = ink)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = textStyle, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Secondary action: a hairline pill, no fill. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") colors: List<Color> = Accents.Calm,
    leading: String? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, 0.96f)
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .heightIn(min = height)
            .clip(shape)
            .border(BorderStroke(1.dp, Hairline), shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!leading.isNullOrBlank()) {
                Text(leading, style = MaterialTheme.typography.labelLarge, color = Juicy.Text)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = Juicy.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Kept for older call sites: the big primary button. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    JuicyButton(text, onClick, modifier.fillMaxWidth(), enabled = enabled)

/**
 * A block of content. «Обертон» (O-24) has no cards: a block is open space on the dark, closed
 * by a hairline below. [accent] draws a short glowing line on the left. Clickable blocks breathe
 * a faint light when pressed.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: List<Color>? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape = RoundedCornerShape(26.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val light by animateFloatAsState(if (pressed) 1f else 0f, tween(Motion.SHORT), label = "light")
    val scale = if (onClick != null) rememberPressScale(source, 0.985f) else 1f
    var m = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawBehind {
            if (light > 0f) drawRect(Color.White.copy(alpha = 0.04f * light))
            drawLine(Hairline, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1f)
            if (accent != null) {
                val h = size.height * 0.5f
                val top = (size.height - h) / 2
                drawLine(
                    Brush.verticalGradient(accent, startY = top, endY = top + h),
                    Offset(1.5.dp.toPx(), top), Offset(1.5.dp.toPx(), top + h),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }
    if (onClick != null) m = m.clickable(interactionSource = source, indication = null, onClick = onClick)
    val start = if (accent != null) 18.dp else 4.dp
    Column(m.padding(start = start, end = 4.dp, top = contentPadding, bottom = contentPadding), content = content)
}

/** A titled block used by placeholders and summaries. */
@Composable
fun InfoCard(title: String, body: String, modifier: Modifier = Modifier, badge: String? = null, accent: List<Color>? = null) {
    GlassCard(modifier.fillMaxWidth(), accent = accent) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (badge != null) Badge(badge, accent ?: Accents.Hero)
        }
        Spacer(Modifier.heightIn(min = 6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
    }
}

/** Small tag: "v0.2", "ур. 3", "1240" — accent ink in a hairline, with a dot of light. */
@Composable
fun Badge(text: String, colors: List<Color> = Accents.Hero, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).shadow(6.dp, CircleShape, ambientColor = colors.first(), spotColor = colors.first()).clip(CircleShape).background(colors.first()))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Juicy.Text)
    }
}

/** Uppercase caption above a block. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Juicy.TextFaint) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp), color = color, modifier = modifier)
}

/** Text filled with a gradient. */
@Composable
fun GradientText(text: String, style: TextStyle, colors: List<Color>, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text, style = style, color = Color.White, modifier = modifier.gradientTint(colors), textAlign = textAlign)
}

/** An integer that counts up to its new value, in the dot-matrix numerals. */
@Composable
fun CountUpText(
    value: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    colors: List<Color>? = null,
    prefix: String = "",
    suffix: String = "",
) {
    val shown by animateIntAsState(value, tween(Motion.LONG), label = "count")
    val text = "$prefix$shown$suffix"
    val dots = style.copy(fontFamily = Doto, fontWeight = FontWeight.Bold)
    if (colors != null) GradientText(text, dots, colors, modifier) else Text(text, style = dots, modifier = modifier)
}

/** Highlight state of an answer key. */
enum class KeyHighlight { NONE, CORRECT, WRONG, DIM }

/**
 * A big, coloured, squishy answer key. [colors] is the key gradient (degree colour by default);
 * CORRECT glows, WRONG turns red, DIM fades.
 */
@Composable
fun JuicyKey(
    label: String,
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlight: KeyHighlight = KeyHighlight.NONE,
    height: Dp = GameTouchTarget,
    sublabel: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, 0.88f)
    val glow by animateFloatAsState(if (highlight == KeyHighlight.CORRECT) 1f else 0f, tween(200), label = "glow")
    val fade by animateFloatAsState(if (highlight == KeyHighlight.DIM) 0.35f else 1f, tween(200), label = "fade")
    val shape = RoundedCornerShape(height / 2)
    val fill = if (highlight == KeyHighlight.WRONG) Accents.Bad else colors
    val inkColor = lerp(fill.first(), DegreeInk, glow)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale * (1f + 0.06f * glow)
                scaleY = scale * (1f + 0.06f * glow)
                alpha = fade
            }
            .heightIn(min = height)
            .shadow((4 + 24 * glow).dp, shape, ambientColor = fill.last(), spotColor = fill.first())
            .clip(shape)
            .background(Juicy.Ink2)
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(fill.first().copy(alpha = 0.22f + 0.78f * glow), fill.last().copy(alpha = 0.05f + 0.95f * glow)), center = Offset(size.width / 2, size.height * 0.3f), radius = size.maxDimension * 0.8f))
            }
            .border(BorderStroke(1.dp, fill.first().copy(alpha = 0.45f + 0.55f * glow)), shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = textStyle, color = inkColor, textAlign = TextAlign.Center, maxLines = 1)
            if (sublabel != null) Text(sublabel, style = MaterialTheme.typography.labelSmall, color = inkColor.copy(alpha = 0.8f))
        }
    }
}

/**
 * The I–VII answer pad (Tir, Улов). Shows [degrees] in the user's notation,
 * coloured by [DegreeColors], [columns] keys per row.
 */
@Composable
fun DegreeKeypad(
    degrees: List<Degree>,
    notation: DegreeNotation,
    onDegree: (Degree) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlight: (Degree) -> KeyHighlight = { KeyHighlight.NONE },
    columns: Int = 4,
    keyHeight: Dp = GameTouchTarget,
    /** Optional extra key in the last free cell, e.g. «не знаю». */
    extraKey: ExtraKey? = null,
) {
    val cells: List<Degree?> = degrees + if (extraKey != null) listOf(null) else emptyList()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d ->
                    if (d == null && extraKey != null) {
                        ExtraKeyView(extraKey, enabled, keyHeight, Modifier.weight(1f))
                    } else if (d != null) {
                        JuicyKey(
                            label = DegreeFormatter.degree(d, notation),
                            colors = DegreeColors.gradient(d),
                            onClick = { onDegree(d) },
                            modifier = Modifier.weight(1f),
                            enabled = enabled,
                            highlight = highlight(d),
                            height = keyHeight,
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A non-degree key on an answer pad («не знаю»). */
data class ExtraKey(val label: String, val sublabel: String?, val onClick: () -> Unit)

/** Glass key for [ExtraKey]: same size as the coloured keys, visibly not an answer. */
@Composable
fun ExtraKeyView(key: ExtraKey, enabled: Boolean, height: Dp, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, 0.9f)
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .heightIn(min = height)
            .clip(shape)
            .border(BorderStroke(1.dp, Hairline), shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = key.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (key.label.isNotBlank()) Text(key.label, style = MaterialTheme.typography.headlineSmall, color = Juicy.Text, maxLines = 1)
            if (key.sublabel != null) Text(key.sublabel, style = if (key.label.isBlank()) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall, color = Juicy.TextDim, maxLines = 1)
        }
    }
}

/** A chord or degree in its colour: "ii⁷", "♭VII". */
@Composable
fun DegreeChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    onClickLabel: String? = null,
) {
    val shape = RoundedCornerShape(if (large) 16.dp else 12.dp)
    val bg by animateColorAsState(if (selected) color else color.copy(alpha = 0.12f), tween(Motion.SHORT), label = "chip")
    var m = modifier
        .clip(shape)
        .background(bg)
        .border(BorderStroke(1.dp, color.copy(alpha = 0.5f)), shape)
    if (onClick != null) m = m.clickable(onClickLabel = onClickLabel, onClick = onClick)
    Box(m.padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 8.dp else 5.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            color = if (selected) Color(0xFF15111F) else color,
            maxLines = 1,
        )
    }
}

/**
 * O-13: a chord or degree on a song sheet — big, solid colour, dark ink. All [DegreeColors] share
 * one lightness, so every tile reads the same and none of them hides in the dark background.
 *
 * O-17: with [onClick] the tile is a button (a chord that sounds when tapped) and grows to the
 * game touch target; [active] draws the white ring around the one tapped last.
 */
@Composable
fun DegreeTile(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    active: Boolean = false,
    onClickLabel: String? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    var m = modifier
        .heightIn(min = if (onClick != null) GameTouchTarget else 56.dp)
        .widthIn(min = 64.dp)
        .clip(shape)
        .background(color)
    if (active) m = m.border(BorderStroke(3.dp, Color.White), shape)
    if (onClick != null) m = m.clickable(onClickLabel = onClickLabel, onClick = onClick)
    Box(m.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.headlineSmall, color = DegreeInk, maxLines = 1)
    }
}

/** Ink on a degree colour. */
val DegreeInk = Color(0xFF15111F)

/** Small stat: "3 дня" — a glowing dot (or a sign in [icon]) and the text, in a hairline. */
@Composable
fun StatPill(icon: String, text: String, modifier: Modifier = Modifier, colors: List<Color> = Accents.Calm) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon.isBlank()) {
            Box(Modifier.size(6.dp).shadow(6.dp, CircleShape, ambientColor = colors.first(), spotColor = colors.first()).clip(CircleShape).background(colors.first()))
        } else {
            Text(icon, style = MaterialTheme.typography.labelLarge, color = colors.first())
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Juicy.Text, maxLines = 1)
    }
}

/** Row of selectable pills (settings, mode pickers). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") colors: List<Color> = Accents.Hero,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            val isSel = o == selected
            val shape = RoundedCornerShape(50)
            Box(
                Modifier
                    .clip(shape)
                    .background(if (isSel) Juicy.Paper else Color.Transparent)
                    .border(BorderStroke(1.dp, if (isSel) Color.Transparent else Hairline), shape)
                    .clickable { onSelect(o) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(label(o), style = MaterialTheme.typography.labelLarge, color = if (isSel) Juicy.PaperInk else Juicy.TextDim)
            }
        }
    }
}
