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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

/** The main call-to-action: gradient pill with a coloured glow. */
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
    val scale = rememberPressScale(source)
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = height)
            .shadow(if (enabled) 18.dp else 0.dp, shape, ambientColor = colors.first(), spotColor = colors.last())
            .clip(shape)
            .background(if (enabled) Brush.horizontalGradient(colors) else Brush.horizontalGradient(listOf(Juicy.Ink4, Juicy.Ink3)))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (leading != null) {
                Text(leading, style = textStyle)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = textStyle, color = if (enabled) Color.White else Juicy.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Secondary action: glass pill with a thin gradient outline. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = Accents.Calm,
    leading: String? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = height)
            .clip(shape)
            .background(Juicy.Ink3.copy(alpha = 0.7f))
            .border(BorderStroke(1.5.dp, Brush.horizontalGradient(colors)), shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Text(leading, style = MaterialTheme.typography.labelLarge)
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

/** Frosted surface with an optional gradient border. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: List<Color>? = null,
    shape: Shape = RoundedCornerShape(26.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val scale = if (onClick != null) rememberPressScale(source, 0.98f) else 1f
    var m = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Juicy.Ink3.copy(alpha = 0.85f), Juicy.Ink2.copy(alpha = 0.85f))))
        .border(BorderStroke(1.dp, if (accent != null) Brush.linearGradient(accent.map { it.copy(alpha = 0.8f) }) else Brush.linearGradient(listOf(Juicy.Line, Juicy.Line))), shape)
    if (onClick != null) m = m.clickable(interactionSource = source, indication = null, onClick = onClick)
    Column(m.padding(contentPadding), content = content)
}

/** A titled card used by placeholders and summaries. */
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

/** Small gradient badge: "v0.2", "ур. 3", "🏆 1240". */
@Composable
fun Badge(text: String, colors: List<Color> = Accents.Hero, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(colors))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

/** Uppercase caption above a block. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Juicy.TextFaint) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

/** Text filled with a gradient. */
@Composable
fun GradientText(text: String, style: TextStyle, colors: List<Color>, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text, style = style, color = Color.White, modifier = modifier.gradientTint(colors), textAlign = textAlign)
}

/** An integer that counts up to its new value. */
@Composable
fun CountUpText(
    value: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    colors: List<Color>? = null,
    prefix: String = "",
    suffix: String = "",
) {
    val shown by animateIntAsState(value, tween(700), label = "count")
    val text = "$prefix$shown$suffix"
    if (colors != null) GradientText(text, style, colors, modifier) else Text(text, style = style, modifier = modifier)
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
    val shape = RoundedCornerShape(22.dp)
    val fill = if (highlight == KeyHighlight.WRONG) Accents.Bad else colors
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale * (1f + 0.06f * glow)
                scaleY = scale * (1f + 0.06f * glow)
                alpha = fade
            }
            .heightIn(min = height)
            .shadow((6 + 18 * glow).dp, shape, ambientColor = fill.last(), spotColor = fill.first())
            .clip(shape)
            .background(Brush.verticalGradient(fill))
            .border(BorderStroke((1 + 2 * glow).dp, Color.White.copy(alpha = 0.25f + 0.6f * glow)), shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = textStyle, color = Color(0xFF15111F), textAlign = TextAlign.Center, maxLines = 1)
            if (sublabel != null) Text(sublabel, style = MaterialTheme.typography.labelSmall, color = Color(0xCC15111F))
        }
    }
}

/**
 * The I–VII answer pad (Tir, «Что это было?», Улов). Shows [degrees] in the user's notation,
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
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        degrees.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d ->
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
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
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
) {
    val shape = RoundedCornerShape(if (large) 16.dp else 12.dp)
    val bg by animateColorAsState(if (selected) color else color.copy(alpha = 0.18f), label = "chip")
    var m = modifier
        .clip(shape)
        .background(bg)
        .border(BorderStroke(1.5.dp, color.copy(alpha = 0.9f)), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m.padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 8.dp else 5.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            color = if (selected) Color(0xFF15111F) else color,
            maxLines = 1,
        )
    }
}

/** Small stat with an emoji: "🔥 3 дня". */
@Composable
fun StatPill(icon: String, text: String, modifier: Modifier = Modifier, colors: List<Color> = Accents.Calm) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Juicy.Ink3.copy(alpha = 0.85f))
            .border(BorderStroke(1.dp, Brush.horizontalGradient(colors.map { it.copy(alpha = 0.7f) })), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(6.dp))
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
    colors: List<Color> = Accents.Hero,
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
                    .background(if (isSel) Brush.horizontalGradient(colors) else Brush.horizontalGradient(listOf(Juicy.Ink3, Juicy.Ink3)))
                    .border(BorderStroke(1.dp, if (isSel) Color.Transparent else Juicy.Line), shape)
                    .clickable { onSelect(o) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(label(o), style = MaterialTheme.typography.labelLarge, color = if (isSel) Color.White else Juicy.TextDim)
            }
        }
    }
}
