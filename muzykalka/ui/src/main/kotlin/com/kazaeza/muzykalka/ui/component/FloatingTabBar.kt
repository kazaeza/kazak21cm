package com.kazaeza.muzykalka.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Motion

/** One tab of [FloatingTabBar]: its mark; [label] is only spoken (TalkBack) and looked for by tests (O-31). */
data class TabSpec(val label: String, val icon: TabIcon)

/** Height of the bar itself; screens reserve this plus [FloatingTabBarMargin] at the bottom. */
val FloatingTabBarHeight = 64.dp
val FloatingTabBarMargin = 14.dp

/**
 * «Обертон» (O-24, O-31): the bottom navigation is a dark floating pill of marks. The selected mark is
 * bright, with a dot of light under it; the rest are dim.
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
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Juicy.Ink2.copy(alpha = 0.94f))
            .border(BorderStroke(1.dp, Color(0x1FFFFFFF)), shape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEachIndexed { i, tab ->
            val isSel = i == selected
            val source = remember { MutableInteractionSource() }
            val on by animateFloatAsState(if (isSel) 1f else 0f, tween(Motion.MEDIUM), label = "tab")
            Box(
                Modifier
                    .height(FloatingTabBarHeight)
                    .widthIn(min = 72.dp)
                    .clickable(interactionSource = source, indication = null) { onSelect(i) }
                    .semantics {
                        contentDescription = tab.label
                        role = Role.Tab
                        this.selected = isSel
                    }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                // the mark sits a little above the middle, constant, so the dot of light has room under it
                TabGlyph(tab.icon, lerp(Juicy.TextDim.copy(alpha = 0.75f), Juicy.Text, on), Modifier.offset(y = (-4).dp))
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .size(5.dp)
                        .graphicsLayer {
                            alpha = on
                            scaleX = 0.4f + 0.6f * on
                            scaleY = 0.4f + 0.6f * on
                        }
                        .shadow(8.dp, CircleShape, ambientColor = colors.first(), spotColor = colors.first())
                        .clip(CircleShape)
                        .background(colors.first()),
                )
            }
        }
    }
}

/** Space a scrolling tab screen leaves at the bottom so its last item clears the bar. */
val FloatingTabBarReserve = FloatingTabBarHeight + FloatingTabBarMargin * 2
