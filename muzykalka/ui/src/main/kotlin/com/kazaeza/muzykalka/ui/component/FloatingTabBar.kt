package com.kazaeza.muzykalka.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy

/** One tab of [FloatingTabBar]: an emoji icon and a short label. */
data class TabSpec(val label: String, val icon: String)

/** Height of the bar itself; screens reserve this plus [FloatingTabBarMargin] at the bottom. */
val FloatingTabBarHeight = 64.dp
val FloatingTabBarMargin = 14.dp

/**
 * The bottom navigation as a floating pill: icons only, the selected tab grows into a gradient
 * capsule with its label. Every tab keeps its label in semantics, so tests and TalkBack see it.
 */
@Composable
fun FloatingTabBar(
    tabs: List<TabSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = Accents.Hero,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .heightIn(min = FloatingTabBarHeight)
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = colors.last().copy(alpha = 0.6f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Juicy.Ink3.copy(alpha = 0.96f), Juicy.Ink2.copy(alpha = 0.96f))))
            .border(BorderStroke(1.dp, Juicy.Line), shape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { i, tab ->
            val isSel = i == selected
            val source = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .height(FloatingTabBarHeight - 12.dp)
                    .widthIn(min = 56.dp)
                    .clip(shape)
                    .background(if (isSel) Brush.horizontalGradient(colors) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable(interactionSource = source, indication = null) { onSelect(i) }
                    .semantics {
                        contentDescription = tab.label
                        role = Role.Tab
                        this.selected = isSel
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(tab.icon, style = MaterialTheme.typography.titleLarge)
                AnimatedVisibility(
                    visible = isSel,
                    enter = fadeIn() + expandHorizontally(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text(tab.label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Space a scrolling tab screen leaves at the bottom so its last item clears the bar. */
val FloatingTabBarReserve = FloatingTabBarHeight + FloatingTabBarMargin * 2
