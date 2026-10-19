package com.jaroszeek.gymtracker.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtAlpha
import com.jaroszeek.gymtracker.designsystem.theme.GtBlur
import com.jaroszeek.gymtracker.designsystem.theme.GtMotion
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

@Immutable
data class GlassNavItem(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
)

// Droplet geometry, as in vynox
private val DropletInset = 6.dp
private const val DROPLET_SQUASH = 0.18f
private const val DROPLET_STROKE_ALPHA = 0.35f
private const val HIGHLIGHT_GLOW_ALPHA = 0.3f
private val HighlightGlowRadius = 22.dp

/**
 * Floating glass "island" navigation, recreated from vynox (D-010). The droplet indicator
 * stretches toward the motion on tab change and springs back; [highlightedIndex] is emphasized.
 */
@Composable
fun GlassNavBar(
    items: List<GlassNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    highlightedIndex: Int = items.size / 2,
) {
    val accent = GtTheme.colors.accent
    val haptics = LocalHapticFeedback.current
    val droplet = remember { LiquidSpan(selectedIndex.toFloat(), selectedIndex + 1f) }
    LaunchedEffect(selectedIndex) { droplet.animateTo(selectedIndex.toFloat(), selectedIndex + 1f) }
    GlassSurface(
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .padding(horizontal = GtSizes.NavBarMargin, vertical = GtSizes.NavBarGap)
            .widthIn(max = GtSizes.NavBarMaxWidth)
            .fillMaxWidth()
            .height(GtSizes.NavBarHeight),
        shape = GtShapes.Pill,
        refraction = true,
        blurRadius = GtBlur.Bar,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .drawBehind { drawDroplet(droplet, items.size, accent) }
                .selectableGroup(),
        ) {
            items.forEachIndexed { index, item ->
                NavItem(
                    item = item,
                    selected = index == selectedIndex,
                    highlighted = index == highlightedIndex,
                    onClick = {
                        if (index != selectedIndex) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun DrawScope.drawDroplet(droplet: LiquidSpan, count: Int, accent: Color) {
    val slot = size.width / count
    val inset = DropletInset.toPx()
    val left = droplet.start * slot + inset
    val right = droplet.end * slot - inset
    // A stretched droplet flattens a little, like liquid
    val height = (size.height - inset * 2) * (1f - DROPLET_SQUASH * droplet.stretch(1f).coerceAtMost(1f))
    val topLeft = Offset(left, (size.height - height) / 2)
    val dropletSize = Size(right - left, height)
    val radius = CornerRadius(height / 2)
    drawRoundRect(accent.copy(alpha = GtAlpha.ACCENT_WASH), topLeft, dropletSize, radius)
    drawRoundRect(accent.copy(alpha = DROPLET_STROKE_ALPHA), topLeft, dropletSize, radius, style = Stroke(GtSizes.Hairline.toPx()))
}

@Composable
private fun NavItem(
    item: GlassNavItem,
    selected: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GtTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, pressedScale = GtMotion.NAV_PRESSED_SCALE)
    val tint by animateColorAsState(if (selected) colors.textPrimary else colors.textSecondary, label = "navItemTint")
    Column(
        modifier
            .fillMaxHeight()
            .selectable(selected, interaction, indication = null, role = Role.Tab, onClick = onClick)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Fixed icon box, so all labels sit on one line
        Box(
            Modifier
                .size(GtSizes.IconLarge)
                .drawBehind {
                    if (highlighted) {
                        val radius = HighlightGlowRadius.toPx()
                        drawCircle(
                            Brush.radialGradient(listOf(colors.accent.copy(alpha = HIGHLIGHT_GLOW_ALPHA), Color.Transparent), center, radius),
                            radius,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (selected) item.selectedIcon else item.icon),
                contentDescription = null,
                modifier = Modifier.size(if (highlighted) GtSizes.IconLarge else GtSizes.Icon),
                tint = tint,
            )
        }
        Text(
            stringResource(item.label),
            color = tint,
            style = GtTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun GlassNavBarPreview() {
    GymTrackerTheme {
        GlassScaffold(
            chrome = {
                GlassNavBar(
                    items = listOf(
                        GlassNavItem(R.string.nav_home, R.drawable.ic_home, R.drawable.ic_home_filled),
                        GlassNavItem(R.string.nav_exercises, R.drawable.ic_fitness_center, R.drawable.ic_fitness_center_filled),
                        GlassNavItem(R.string.nav_workout, R.drawable.ic_exercise, R.drawable.ic_exercise_filled),
                        GlassNavItem(R.string.nav_progress, R.drawable.ic_monitoring, R.drawable.ic_monitoring_filled),
                        GlassNavItem(R.string.nav_profile, R.drawable.ic_person, R.drawable.ic_person_filled),
                    ),
                    selectedIndex = 0,
                    onSelect = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            },
        ) {}
    }
}
