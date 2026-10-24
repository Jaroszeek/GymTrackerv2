package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.designsystem.theme.GtAlpha
import com.jaroszeek.gymtracker.designsystem.theme.GtMotion
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

/**
 * Bars of a few values, e.g. the steps of the last 7 days, each labelled under it. The [highlighted]
 * bar (today) is in the text accent, the others in a wash of it; [description] is read out instead.
 */
@Composable
fun GlassBarChart(values: List<Double>, labels: List<String>, description: String, modifier: Modifier = Modifier, highlighted: Int = values.lastIndex) {
    val colors = GtTheme.colors
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val grow = rememberReveal(values)
    Row(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        values.forEachIndexed { index, value ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
                Box(Modifier.fillMaxWidth().height(GtSizes.MiniChartHeight), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = GtSizes.MiniBarMin)
                            .fillMaxHeight((value / max).toFloat() * grow.value)
                            .clip(GtShapes.Pill)
                            .background(if (index == highlighted) colors.accentText else colors.accentText.copy(alpha = GtAlpha.ACCENT_WASH * 2)),
                    )
                }
                Text(
                    labels.getOrElse(index) { "" },
                    color = if (index == highlighted) colors.textPrimary else colors.textTertiary,
                    style = GtTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A small line of a series spaced by time, without axes or gestures, e.g. weight on Start. Needs two points. */
@Composable
fun GlassSparkline(points: List<ChartPoint>, description: String, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    val sorted = remember(points) { points.sortedBy { it.time } }
    val reveal = rememberReveal(sorted)
    Spacer(
        modifier
            .fillMaxWidth()
            .height(GtSizes.MiniChartHeight)
            .clearAndSetSemantics { contentDescription = description }
            .drawWithCache {
                val stroke = GtSizes.ChartStroke.toPx()
                val line = Path()
                if (sorted.size >= 2) {
                    val start = sorted.first().time
                    val span = (sorted.last().time - start).coerceAtLeast(1).toFloat()
                    val low = sorted.minOf { it.value }
                    val high = sorted.maxOf { it.value }
                    sorted.forEachIndexed { index, point ->
                        val x = stroke + (point.time - start) / span * (size.width - 2 * stroke)
                        // A flat series runs through the middle
                        val share = if (high > low) (point.value - low) / (high - low) else 0.5
                        val y = (size.height - stroke - share * (size.height - 2 * stroke)).toFloat()
                        if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    }
                }
                val fill = Path().apply {
                    addPath(line)
                    if (sorted.size >= 2) {
                        lineTo(size.width - stroke, size.height)
                        lineTo(stroke, size.height)
                        close()
                    }
                }
                val brush = Brush.verticalGradient(listOf(colors.accentText.copy(alpha = GtAlpha.CHART_FILL), colors.accentText.copy(alpha = 0f)))
                onDrawBehind {
                    clipRect(right = size.width * reveal.value) {
                        drawPath(fill, brush)
                        drawPath(line, colors.accentText, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            },
    )
}

/** 0 → 1 once per [key], at once when the system limits animations. */
@Composable
private fun rememberReveal(key: Any): Animatable<Float, *> {
    val reducedMotion = GtTheme.reducedMotion
    val reveal = remember(key) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, GtMotion.Reveal) }
    return reveal
}

@Preview
@Composable
private fun GlassMiniChartsPreview() {
    val day = 86_400_000L
    GymTrackerTheme {
        GlassScaffold {
            GlassCard(Modifier.padding(GtSpacing.xl)) {
                GlassBarChart(listOf(6_200.0, 9_800.0, 4_100.0, 0.0, 12_400.0, 8_000.0, 3_500.0), listOf("pn", "wt", "śr", "cz", "pt", "sb", "nd"), description = "")
                GlassSparkline(List(20) { ChartPoint(it * day, 82.0 - it * 0.2 + (it % 4) * 0.3) }, description = "")
            }
        }
    }
}
