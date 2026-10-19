package com.jaroszeek.gymtracker.feature.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.ChartPoint
import com.jaroszeek.gymtracker.designsystem.component.GlassBar
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassLineChart
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.health.HealthSession
import com.jaroszeek.gymtracker.domain.health.HeartRateSample
import com.jaroszeek.gymtracker.domain.health.ZONES
import com.jaroszeek.gymtracker.domain.health.summarize
import com.jaroszeek.gymtracker.feature.progress.timeLabel
import com.jaroszeek.gymtracker.feature.workout.durationLabel
import kotlin.math.roundToInt

/** A watch session's heart rate (A6): average and maximum, the chart, time in zones, and the calories. */
@Composable
fun HeartRateCard(session: HealthSession, samples: List<HeartRateSample>, maxHeartRate: Int?, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    val summary = remember(samples, maxHeartRate) { summarize(samples, maxHeartRate) }
    val points = remember(samples) { samples.map { ChartPoint(it.time, it.bpm.toDouble()) } }
    val resources = LocalResources.current
    val bpm = remember(resources) { { value: Double -> resources.getString(R.string.value_bpm, value.roundToInt()) } }
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            Icon(painterResource(R.drawable.ic_favorite), contentDescription = null, Modifier.size(GtSizes.IconSmall), tint = colors.accentText)
            Text(stringResource(R.string.hr_title), Modifier.weight(1f), style = GtTheme.typography.title)
            Text(stringResource(R.string.hr_source, sourceLabel(session.origin)), color = colors.textSecondary, style = GtTheme.typography.labelSmall)
        }
        if (summary == null) {
            Text(stringResource(R.string.hr_empty), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                Value(stringResource(R.string.hr_average), bpmLabel(summary.averageBpm), Modifier.weight(1f))
                Value(stringResource(R.string.hr_max), bpmLabel(summary.maxBpm), Modifier.weight(1f))
            }
            GlassLineChart(points, valueLabel = bpm, dateLabel = { timeLabel(it) }, emptyText = stringResource(R.string.hr_empty))
            Zones(summary.zoneMillis)
        }
        if (session.activeKcal != null || session.totalKcal != null) {
            Row(Modifier.padding(top = GtSpacing.xs), horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                session.activeKcal?.let { Value(stringResource(R.string.kcal_active), kcalLabel(it), Modifier.weight(1f)) }
                session.totalKcal?.let { Value(stringResource(R.string.kcal_total), kcalLabel(it), Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Value(caption: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(caption, color = GtTheme.colors.textTertiary, style = GtTheme.typography.labelSmall)
        Text(value, style = GtTheme.typography.numberM)
    }
}

/** Time per zone as a share of the time in all of them; without a maximum heart rate, where to set it. */
@Composable
private fun Zones(zoneMillis: List<Long>?) {
    val colors = GtTheme.colors
    if (zoneMillis == null) {
        Text(stringResource(R.string.hr_zones_missing), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        return
    }
    val total = zoneMillis.sum().coerceAtLeast(1)
    zoneMillis.forEachIndexed { index, millis ->
        val from = (ZONES[index] * PERCENT).roundToInt()
        val to = ZONES.getOrNull(index + 1)?.let { (it * PERCENT).roundToInt() } ?: PERCENT
        Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
            Row {
                Text(stringResource(R.string.hr_zone, index + 1, from, to), Modifier.weight(1f), style = GtTheme.typography.label)
                Text(durationLabel(millis), color = colors.textSecondary, style = GtTheme.typography.label)
            }
            GlassBar(millis.toFloat() / total)
        }
    }
}

private const val PERCENT = 100

@Preview
@Composable
private fun HeartRateCardPreview() {
    val start = 1_760_000_000_000
    val samples = List(240) { HeartRateSample(start + it * 15_000L, 95 + (it % 40) * 2 + if (it % 9 == 0) 20 else 0) }
    GymTrackerTheme {
        GlassScaffold {
            HeartRateCard(
                session = HealthSession("1", "com.sec.android.app.shealth", 70, null, start, start + 3_600_000, activeKcal = 412.0, totalKcal = 530.0),
                samples = samples,
                maxHeartRate = 188,
                modifier = Modifier.padding(GtSpacing.xl),
            )
        }
    }
}
