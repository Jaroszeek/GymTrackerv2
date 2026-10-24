package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.designsystem.theme.fitting

/**
 * One choice of a few on a pill track; the accent indicator moves like the navbar droplet. [weights]
 * give long labels more room, e.g. "Wszystko" next to "1M". Touch targets are 48 dp tall around the
 * slimmer track.
 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    weights: List<Float> = List(options.size) { 1f },
) {
    val colors = GtTheme.colors
    val haptics = LocalHapticFeedback.current
    val edges = remember(weights) { weights.runningFold(0f) { sum, weight -> sum + weight } }
    val indicator = remember { LiquidSpan(edges[selectedIndex], edges[selectedIndex + 1]) }
    LaunchedEffect(selectedIndex, edges) { indicator.animateTo(edges[selectedIndex], edges[selectedIndex + 1]) }
    Box(modifier.fillMaxWidth().height(GtSizes.MinTouch), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(GtSizes.ChipHeight)
                .clip(GtShapes.Pill)
                .background(colors.surfaceRaised)
                .specularEdge(GtShapes.Pill, colors)
                .drawBehind {
                    val inset = GtSizes.SegmentInset.toPx()
                    val unit = size.width / edges.last()
                    val height = size.height - inset * 2
                    drawRoundRect(
                        color = colors.accent,
                        topLeft = Offset(indicator.start * unit + inset, inset),
                        size = Size((indicator.end - indicator.start) * unit - inset * 2, height),
                        cornerRadius = CornerRadius(height / 2),
                    )
                },
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val content by animateColorAsState(if (selected) colors.onAccent else colors.textSecondary, label = "segmentContent")
                Box(
                    Modifier
                        .weight(weights[index])
                        .fillMaxHeight()
                        .selectable(selected, role = Role.Tab) {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        }
                        .padding(horizontal = GtSpacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option,
                        color = content,
                        style = GtTheme.typography.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = GtTheme.typography.label.fitting(),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun GlassSegmentedControlPreview() {
    GymTrackerTheme {
        GlassScaffold {
            Column(Modifier.padding(GtSpacing.xl), verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                var selected by remember { mutableIntStateOf(1) }
                GlassSegmentedControl(listOf("Historia", "Ćwiczenia", "Rutyny", "Partie"), selected, onSelect = { selected = it })
                GlassSegmentedControl(listOf("1M", "3M", "6M", "1R", "Wszystko"), 4, onSelect = {}, weights = listOf(1f, 1f, 1f, 1f, 2f))
            }
        }
    }
}
