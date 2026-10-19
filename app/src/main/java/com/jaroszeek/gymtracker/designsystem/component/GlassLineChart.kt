package com.jaroszeek.gymtracker.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtAlpha
import com.jaroszeek.gymtracker.designsystem.theme.GtBlur
import com.jaroszeek.gymtracker.designsystem.theme.GtMotion
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A value at a time (epoch millis). */
@Immutable
data class ChartPoint(val time: Long, val value: Double)

/** The ranges of A5.7; [months] null shows everything. */
enum class ChartRange(@param:StringRes val label: Int, val months: Long?) {
    OneMonth(R.string.chart_range_1m, 1),
    ThreeMonths(R.string.chart_range_3m, 3),
    SixMonths(R.string.chart_range_6m, 6),
    OneYear(R.string.chart_range_1y, 12),
    All(R.string.chart_range_all, null),
}

/** Where the range starts before [now], in calendar months of [zone]; null for everything. */
fun ChartRange.start(now: Instant, zone: ZoneId): Long? = months?.let { now.atZone(zone).minusMonths(it).toInstant().toEpochMilli() }

/** The points of the last [range] before [now]. */
fun List<ChartPoint>.within(range: ChartRange, now: Instant, zone: ZoneId): List<ChartPoint> {
    val from = range.start(now, zone) ?: return this
    return filter { it.time >= from }
}

@Composable
fun ChartRangeSelector(selected: ChartRange, onSelect: (ChartRange) -> Unit, modifier: Modifier = Modifier) {
    GlassSegmentedControl(
        options = ChartRange.entries.map { stringResource(it.label) },
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(ChartRange.entries[it]) },
        modifier = modifier,
        weights = ChartRange.entries.map { if (it == ChartRange.All) 2f else 1f },
    )
}

/**
 * Line chart of a time series (A5.7): a smooth line with a glow and a gradient under it, spaced by time,
 * between round ticks labelled on the right. Tapping or dragging picks the nearest point with a haptic
 * tick; the header shows the picked point, otherwise the latest one.
 */
@Composable
fun GlassLineChart(
    points: List<ChartPoint>,
    valueLabel: (Double) -> String,
    dateLabel: (Long) -> String,
    emptyText: String,
    modifier: Modifier = Modifier,
    axisLabel: (Double) -> String = valueLabel,
) {
    val colors = GtTheme.colors
    val sorted = remember(points) { points.sortedBy { it.time } }
    val selected = remember(sorted) { mutableIntStateOf(-1) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(GtSizes.ChartEmptyHeight), contentAlignment = Alignment.Center) {
                Text(emptyText, color = colors.textSecondary, style = GtTheme.typography.bodySmall)
            }
            return@Column
        }
        val shown = sorted.getOrNull(selected.intValue) ?: sorted.last()
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valueLabel(shown.value), style = GtTheme.typography.numberM, modifier = Modifier.weight(1f))
            Text(dateLabel(shown.time), color = colors.textSecondary, style = GtTheme.typography.labelSmall)
        }
        val description = stringResource(
            R.string.chart_description,
            pluralStringResource(R.plurals.chart_points, sorted.size, sorted.size),
            valueLabel(sorted.first().value),
            valueLabel(sorted.last().value),
        )
        val measurer = rememberTextMeasurer()
        val labelStyle = GtTheme.typography.labelSmall.copy(color = colors.textTertiary)
        val scale = remember(sorted) { ChartScale(sorted) }
        val labels = remember(scale, measurer, labelStyle, axisLabel) { scale.ticks.map { measurer.measure(axisLabel(it), labelStyle) } }
        val gutter = GtSizes.ChartGutter + with(LocalDensity.current) { labels.maxOf { it.size.width }.toDp() }
        ChartPlot(sorted, scale, labels, gutter, selected, Modifier.semantics { contentDescription = description })
        Row(Modifier.padding(end = gutter)) {
            Text(dateLabel(sorted.first().time), color = colors.textTertiary, style = GtTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            if (sorted.size > 1) Text(dateLabel(sorted.last().time), color = colors.textTertiary, style = GtTheme.typography.labelSmall)
        }
    }
}

/** The drawing and the gestures; [labels] are the measured ticks, drawn in the [gutter] on the right. */
@Composable
private fun ChartPlot(
    points: List<ChartPoint>,
    scale: ChartScale,
    labels: List<TextLayoutResult>,
    gutter: Dp,
    selected: MutableIntState,
    modifier: Modifier,
) {
    val colors = GtTheme.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val gutterPx = with(density) { gutter.toPx() }
    val reducedMotion = GtTheme.reducedMotion
    val reveal = remember(points) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, GtMotion.Reveal) }
    val strokePx = with(density) { GtSizes.ChartStroke.toPx() }
    val haloPx = with(density) { GtSizes.ChartHalo.toPx() }
    val dotPx = with(density) { GtSizes.ChartDot.toPx() }
    val glowPx = with(density) { GtBlur.ChartGlow.toPx() }
    fun plot(width: Float, height: Float) = Plot(haloPx, width - gutterPx, haloPx, height - haloPx)

    Box(
        modifier
            .fillMaxWidth()
            .height(GtSizes.ChartHeight)
            .pointerInput(scale, gutterPx) {
                detectTapGestures { offset -> selected.pick(scale.nearest(offset.x, plot(size.width.toFloat(), 0f)), haptics::tick) }
            }
            .pointerInput(scale, gutterPx) {
                detectHorizontalDragGestures(onDragStart = { selected.pick(scale.nearest(it.x, plot(size.width.toFloat(), 0f)), haptics::tick) }) { change, _ ->
                    change.consume()
                    selected.pick(scale.nearest(change.position.x, plot(size.width.toFloat(), 0f)), haptics::tick)
                }
            },
    ) {
        // The glow: the line again, blurred, under the crisp one
        Spacer(
            Modifier
                .matchParentSize()
                .graphicsLayer { renderEffect = BlurEffect(glowPx, glowPx, TileMode.Decal) }
                .drawWithCache {
                    val area = plot(size.width, size.height)
                    val line = scale.line(area)
                    onDrawBehind {
                        clipRect(right = area.left + (area.right - area.left) * reveal.value + haloPx) {
                            drawPath(line, colors.accent.copy(alpha = GtAlpha.CHART_GLOW), style = Stroke(strokePx * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                },
        )
        Spacer(
            Modifier
                .matchParentSize()
                .drawWithCache {
                    val area = plot(size.width, size.height)
                    val line = scale.line(area)
                    val fill = scale.line(area).apply {
                        lineTo(scale.x(points.last().time, area), area.bottom)
                        lineTo(scale.x(points.first().time, area), area.bottom)
                        close()
                    }
                    val fillBrush = Brush.verticalGradient(listOf(colors.accentText.copy(alpha = GtAlpha.CHART_FILL), colors.accentText.copy(alpha = 0f)), line.getBounds().top, area.bottom)
                    val stroke = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    onDrawBehind {
                        scale.ticks.forEachIndexed { index, tick ->
                            val y = scale.y(tick, area)
                            drawLine(colors.hairline, Offset(0f, y), Offset(area.right, y), strokeWidth = GtSizes.Hairline.toPx())
                            val label = labels[index]
                            val top = (y - label.size.height / 2).coerceIn(0f, size.height - label.size.height)
                            drawText(label, topLeft = Offset(area.right + GtSizes.ChartGutter.toPx(), top))
                        }
                        clipRect(right = area.left + (area.right - area.left) * reveal.value + haloPx) {
                            drawPath(fill, fillBrush)
                            drawPath(line, colors.accentText, style = stroke)
                            if (points.size <= DOTS_UP_TO) points.forEach { drawCircle(colors.accentText, dotPx * DOT_SCALE, scale.point(it, area)) }
                        }
                        points.getOrNull(selected.intValue)?.let { point ->
                            val center = scale.point(point, area)
                            drawLine(colors.textTertiary, Offset(center.x, area.top - haloPx), Offset(center.x, area.bottom + haloPx), strokeWidth = GtSizes.Hairline.toPx())
                            drawCircle(colors.accent.copy(alpha = GtAlpha.ACCENT_WASH * 2), haloPx, center)
                            drawCircle(colors.accentText, dotPx, center)
                            drawCircle(colors.surface, dotPx / 2, center)
                        }
                    }
                },
        )
    }
}

private fun MutableIntState.pick(index: Int, onChange: () -> Unit) {
    if (index != intValue) {
        intValue = index
        onChange()
    }
}

private fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

/** Where the points go, in pixels. */
private data class Plot(val left: Float, val right: Float, val top: Float, val bottom: Float)

/** Time to x over the whole span, value to y between round ticks. */
private class ChartScale(private val points: List<ChartPoint>) {
    val ticks: List<Double> = ticks(points.map { it.value })
    private val start = points.first().time
    private val span = (points.last().time - start).coerceAtLeast(1)

    fun x(time: Long, plot: Plot): Float =
        if (points.size == 1) (plot.left + plot.right) / 2 else plot.left + (time - start).toFloat() / span * (plot.right - plot.left)

    fun y(value: Double, plot: Plot): Float =
        (plot.bottom - (value - ticks.first()) / (ticks.last() - ticks.first()) * (plot.bottom - plot.top)).toFloat()

    fun point(point: ChartPoint, plot: Plot) = Offset(x(point.time, plot), y(point.value, plot))

    fun nearest(x: Float, plot: Plot): Int = points.indices.minBy { abs(x(points[it].time, plot) - x) }

    /** Monotone cubic (Fritsch–Carlson): smooth, and never above or below the points it joins. */
    fun line(plot: Plot): Path {
        val xs = FloatArray(points.size) { x(points[it].time, plot) }
        val ys = FloatArray(points.size) { y(points[it].value, plot) }
        val path = Path().apply { moveTo(xs[0], ys[0]) }
        val n = points.size
        if (n == 1) return path.apply { lineTo(xs[0], ys[0]) }
        val slopes = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]).coerceAtLeast(MIN_DX) }
        val tangents = FloatArray(n) { i ->
            when (i) {
                0 -> slopes[0]
                n - 1 -> slopes[n - 2]
                else -> if (slopes[i - 1] * slopes[i] <= 0f) 0f else (slopes[i - 1] + slopes[i]) / 2
            }
        }
        for (i in 0 until n - 1) {
            if (slopes[i] == 0f) {
                tangents[i] = 0f
                tangents[i + 1] = 0f
            } else {
                val a = tangents[i] / slopes[i]
                val b = tangents[i + 1] / slopes[i]
                val length = a * a + b * b
                if (length > MAX_TANGENT) {
                    val t = 3f / sqrt(length)
                    tangents[i] = t * a * slopes[i]
                    tangents[i + 1] = t * b * slopes[i]
                }
            }
        }
        for (i in 0 until n - 1) {
            val third = (xs[i + 1] - xs[i]) / 3
            path.cubicTo(xs[i] + third, ys[i] + tangents[i] * third, xs[i + 1] - third, ys[i + 1] - tangents[i + 1] * third, xs[i + 1], ys[i + 1])
        }
        return path
    }
}

/** 2–4 round steps (1, 2, 2.5 or 5 × 10ⁿ) covering the values; a flat series gets a step on each side. */
private fun ticks(values: List<Double>): List<Double> {
    val min = values.min()
    val max = values.max()
    val step = niceStep(if (max > min) max - min else max(abs(max) * FLAT_SPAN, 1.0))
    var low = floor(min / step) * step
    var high = ceil(max / step) * step
    if (high == low) {
        low -= step
        high += step
    }
    if (low < 0 && min >= 0) low = 0.0
    val count = ((high - low) / step).roundToInt()
    return List(count + 1) { low + it * step }
}

private fun niceStep(span: Double): Double {
    val raw = span / TICK_INTERVALS
    val magnitude = 10.0.pow(floor(log10(raw)))
    return NICE_STEPS.first { it * magnitude >= raw } * magnitude
}

private const val TICK_INTERVALS = 3
private val NICE_STEPS = listOf(1.0, 2.0, 2.5, 5.0, 10.0)
private const val FLAT_SPAN = 0.2
private const val DOTS_UP_TO = 24
private const val DOT_SCALE = 0.6f
private const val MIN_DX = 0.001f
private const val MAX_TANGENT = 9f

@Preview
@Composable
private fun GlassLineChartPreview() {
    val day = 86_400_000L
    val points = List(40) { ChartPoint(it * 3 * day, 80.0 + it * 0.6 + (it % 5) * 1.5) }
    GymTrackerTheme {
        GlassScaffold {
            GlassCard(Modifier.padding(GtSpacing.xl)) {
                GlassLineChart(points, valueLabel = { "%.1f kg".format(it) }, dateLabel = { "${it / day} d" }, emptyText = "")
                ChartRangeSelector(ChartRange.ThreeMonths, onSelect = {})
            }
        }
    }
}
