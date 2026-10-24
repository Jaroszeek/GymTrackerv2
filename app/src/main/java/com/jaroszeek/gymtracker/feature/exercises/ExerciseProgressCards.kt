package com.jaroszeek.gymtracker.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.ChartPoint
import com.jaroszeek.gymtracker.designsystem.component.ChartRange
import com.jaroszeek.gymtracker.designsystem.component.ChartRangeSelector
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassLineChart
import com.jaroszeek.gymtracker.designsystem.component.GlassSegmentedControl
import com.jaroszeek.gymtracker.designsystem.component.within
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.domain.progress.ExerciseProgress
import com.jaroszeek.gymtracker.domain.progress.OneRepMax
import com.jaroszeek.gymtracker.feature.progress.rememberMassLabel
import com.jaroszeek.gymtracker.feature.progress.shownIn
import com.jaroszeek.gymtracker.feature.progress.shortDate
import com.jaroszeek.gymtracker.feature.routines.LocalWeightUnit
import com.jaroszeek.gymtracker.feature.routines.massLabel
import com.jaroszeek.gymtracker.feature.routines.weightLabel
import com.jaroszeek.gymtracker.feature.workout.setValueLabel
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

private val ExerciseMetric.label: Int
    get() = when (this) {
        ExerciseMetric.Weight -> R.string.chart_metric_weight
        ExerciseMetric.E1rm -> R.string.chart_metric_e1rm
        ExerciseMetric.Reps -> R.string.chart_metric_reps
    }

/** Per session the heaviest set, the best e1RM or the most reps (A5.7), over the chosen range. */
@Composable
internal fun ExerciseChartCard(
    progress: ExerciseProgress,
    metric: ExerciseMetric?,
    range: ChartRange,
    onMetric: (ExerciseMetric) -> Unit,
    onRange: (ChartRange) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.detail_chart_title), style = GtTheme.typography.title)
        if (progress.sessions.isEmpty()) {
            Text(stringResource(R.string.detail_chart_empty), color = GtTheme.colors.textSecondary, style = GtTheme.typography.bodySmall)
            return@GlassCard
        }
        // Bodyweight exercises have only reps
        val metrics = remember(progress) {
            buildList {
                if (progress.sessions.any { it.maxWeightKg != null }) add(ExerciseMetric.Weight)
                if (progress.sessions.any { it.e1rmKg != null }) add(ExerciseMetric.E1rm)
                add(ExerciseMetric.Reps)
            }
        }
        val shown = metric?.takeIf { it in metrics } ?: if (ExerciseMetric.E1rm in metrics) ExerciseMetric.E1rm else metrics.first()
        if (metrics.size > 1) {
            GlassSegmentedControl(metrics.map { stringResource(it.label) }, metrics.indexOf(shown), onSelect = { onMetric(metrics[it]) })
        }
        val unit = LocalWeightUnit.current
        val points = remember(progress, shown, range, unit) {
            progress.sessions
                .mapNotNull { session ->
                    when (shown) {
                        ExerciseMetric.Weight -> session.maxWeightKg
                        ExerciseMetric.E1rm -> session.e1rmKg
                        ExerciseMetric.Reps -> session.maxReps.toDouble()
                    }?.let { ChartPoint(session.time, it) }
                }
                .within(range, Instant.now(), ZoneId.systemDefault())
                .let { if (shown == ExerciseMetric.Reps) it else it.shownIn(unit) }
        }
        val resources = LocalResources.current
        val kg = rememberMassLabel(decimals = if (shown == ExerciseMetric.E1rm) 1 else 2)
        GlassLineChart(
            points = points,
            valueLabel = if (shown == ExerciseMetric.Reps) { reps -> resources.getString(R.string.workout_set_value_bodyweight, reps.roundToInt()) } else kg,
            dateLabel = { shortDate(it) },
            emptyText = stringResource(R.string.chart_range_empty),
            axisLabel = ::weightLabel,
        )
        ChartRangeSelector(range, onSelect = onRange)
    }
}

/** The record list (A5.7) with the date each was first reached. */
@Composable
internal fun ExerciseRecordsCard(progress: ExerciseProgress) {
    val colors = GtTheme.colors
    val records = remember(progress) { progress.records() }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.detail_records_title), style = GtTheme.typography.title)
        if (records.isEmpty) {
            Text(stringResource(R.string.detail_records_empty), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
            return@GlassCard
        }
        records.weight?.let { RecordRow(stringResource(R.string.record_weight), massLabel(it.value), shortDate(it.time)) }
        records.e1rm?.let { e1rm ->
            val from = records.e1rmFrom
            val brzycki = from?.weightKg?.let { OneRepMax.brzycki(it, from.reps) }
            RecordRow(
                stringResource(R.string.record_e1rm),
                massLabel(e1rm.value, decimals = 1),
                brzycki?.let { stringResource(R.string.record_e1rm_from, shortDate(e1rm.time), massLabel(it, decimals = 1)) } ?: shortDate(e1rm.time),
            )
        }
        records.setVolume?.let { RecordRow(stringResource(R.string.record_set_volume), massLabel(it.value, decimals = 0), shortDate(it.time)) }
        records.sessionVolume?.let { RecordRow(stringResource(R.string.record_session_volume), massLabel(it.value, decimals = 0), shortDate(it.time)) }
        if (records.reps.isNotEmpty()) {
            Text(stringResource(R.string.record_reps), style = GtTheme.typography.label, modifier = Modifier.padding(top = GtSpacing.s))
            records.reps.forEach { reps ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(setValueLabel(reps.value.weightKg, reps.value.reps), style = GtTheme.typography.label, modifier = Modifier.weight(1f))
                    Text(shortDate(reps.time), color = colors.textTertiary, style = GtTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun RecordRow(title: String, value: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = GtTheme.typography.label)
            Text(detail, color = GtTheme.colors.textTertiary, style = GtTheme.typography.labelSmall)
        }
        Text(value, color = GtTheme.colors.accentText, style = GtTheme.typography.title)
    }
}
