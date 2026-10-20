package com.jaroszeek.gymtracker.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.BadgeTone
import com.jaroszeek.gymtracker.designsystem.component.ChartPoint
import com.jaroszeek.gymtracker.designsystem.component.ChartRange
import com.jaroszeek.gymtracker.designsystem.component.ChartRangeSelector
import com.jaroszeek.gymtracker.designsystem.component.GlassBadge
import com.jaroszeek.gymtracker.designsystem.component.GlassBottomSheet
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassChip
import com.jaroszeek.gymtracker.designsystem.component.GlassDialog
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassLineChart
import com.jaroszeek.gymtracker.designsystem.component.GlassNumberField
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.GlassSegmentedControl
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.body.BmiCategory
import com.jaroszeek.gymtracker.domain.body.BodyMeasurement
import com.jaroszeek.gymtracker.domain.body.BodyMetric
import com.jaroszeek.gymtracker.domain.body.BodySummary
import com.jaroszeek.gymtracker.domain.body.bmiCategory
import com.jaroszeek.gymtracker.domain.body.bodySummary
import com.jaroszeek.gymtracker.domain.health.Calories
import com.jaroszeek.gymtracker.feature.health.HealthConnectEntry
import com.jaroszeek.gymtracker.feature.health.kcalLabel
import com.jaroszeek.gymtracker.feature.health.sourceLabel
import com.jaroszeek.gymtracker.feature.progress.StatColumn
import com.jaroszeek.gymtracker.feature.progress.shortDate
import com.jaroszeek.gymtracker.feature.progress.timeLabel
import kotlin.math.roundToInt

@Composable
fun ProfileRoute(onSettings: () -> Unit, onHealthConnect: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onRefresh()
        onPauseOrDispose {}
    }
    ProfileScreen(
        state = state,
        onSettings = onSettings,
        onHealthConnect = onHealthConnect,
        onAge = viewModel::onAge,
        onChart = viewModel::onChart,
        onRange = viewModel::onRange,
        onShowAll = viewModel::onShowAll,
        onAdd = viewModel::onAdd,
        onAddMetric = viewModel::onAddMetric,
        onAddValue = viewModel::onAddValue,
        onAddDismiss = viewModel::onAddDismiss,
        onAddSave = viewModel::onAddSave,
        onDeleteRequest = viewModel::onDeleteRequest,
        onDeleteDismiss = viewModel::onDeleteDismiss,
        onDeleteConfirm = viewModel::onDeleteConfirm,
    )
}

/** Profil (A5.8): body data entered here merged with Health Connect, charts, calories and the measurement log. */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onSettings: () -> Unit,
    onHealthConnect: () -> Unit,
    onAge: (Int?) -> Unit,
    onChart: (BodyChart) -> Unit,
    onRange: (ChartRange) -> Unit,
    onShowAll: () -> Unit,
    onAdd: (BodyMetric) -> Unit,
    onAddMetric: (BodyMetric) -> Unit,
    onAddValue: (Double?) -> Unit,
    onAddDismiss: () -> Unit,
    onAddSave: () -> Unit,
    onDeleteRequest: (BodyMeasurement) -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    // Newest first in the log
    val log = remember(state.measurements) { state.measurements.asReversed() }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = GtSpacing.xl,
            end = GtSpacing.xl,
            top = insets.calculateTopPadding() + GtSpacing.xxl,
            bottom = navBarContentPadding().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.l),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_profile), Modifier.weight(1f), style = GtTheme.typography.display)
                GlassIconButton(R.drawable.ic_settings, stringResource(R.string.settings_title), onClick = onSettings, style = GlassButtonStyle.Ghost)
            }
        }
        when {
            state.loading -> return@LazyColumn
            state.error -> {
                item(key = "error") { Message(stringResource(R.string.progress_error)) }
                return@LazyColumn
            }
        }
        item(key = "data") { DataCard(state.summary, state.age, onAge, onAdd) }
        item(key = "composition") { CompositionCard(state.summary, onAdd) }
        item(key = "charts") { ChartsCard(state, onChart, onRange) }
        item(key = "calories") { CaloriesCard(state.calories, onHealthConnect) }
        item(key = "health") { HealthConnectEntry(onClick = onHealthConnect) }
        item(key = "log-title") {
            Text(stringResource(R.string.profile_history_title), Modifier.padding(top = GtSpacing.s), style = GtTheme.typography.headline)
        }
        if (log.isEmpty()) {
            item(key = "log-empty") { Message(stringResource(R.string.profile_history_empty)) }
        } else {
            // No keys: two apps may write the same metric at the same moment
            items(if (state.showAll) log else log.take(LOG_PREVIEW)) { measurement ->
                MeasurementRow(measurement, onDelete = { onDeleteRequest(measurement) })
            }
            if (!state.showAll && log.size > LOG_PREVIEW) {
                item(key = "log-all") {
                    GlassButton(
                        stringResource(R.string.profile_history_all, log.size),
                        onClick = onShowAll,
                        style = GlassButtonStyle.Ghost,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    val adding = state.adding
    GlassBottomSheet(visible = adding != null, onDismissRequest = onAddDismiss) {
        if (adding != null) AddForm(adding, onAddMetric, onAddValue, onAddSave)
    }
    val deleting = state.confirmDelete
    GlassDialog(
        visible = deleting != null,
        onDismissRequest = onDeleteDismiss,
        title = stringResource(R.string.profile_delete_title),
        text = deleting?.let {
            stringResource(R.string.profile_delete_text, bodyValueLabel(it.metric, it.value), shortDate(it.time))
        },
        confirmButton = { GlassButton(stringResource(R.string.action_delete), onClick = onDeleteConfirm) },
        dismissButton = { GlassButton(stringResource(R.string.action_cancel), onClick = onDeleteDismiss, style = GlassButtonStyle.Ghost) },
    )
}

private const val LOG_PREVIEW = 10
private const val MAX_AGE = 120.0

/** Weight, height, age and BMI; a tap on a value adds a new one. */
@Composable
private fun DataCard(summary: BodySummary, age: Int?, onAge: (Int?) -> Unit, onAdd: (BodyMetric) -> Unit) {
    val colors = GtTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.profile_data_title), style = GtTheme.typography.title)
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            ValueTile(BodyMetric.Weight, summary.weight, Modifier.weight(1f), onAdd)
            ValueTile(BodyMetric.Height, summary.height, Modifier.weight(1f), onAdd)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m), verticalAlignment = Alignment.CenterVertically) {
            GlassNumberField(
                value = age?.toDouble(),
                onValueChange = { onAge(it?.roundToInt()) },
                label = stringResource(R.string.health_age),
                modifier = Modifier.weight(1f),
                range = 0.0..MAX_AGE,
                placeholder = stringResource(R.string.number_empty),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
                Text(stringResource(R.string.bmi_label), color = colors.textSecondary, style = GtTheme.typography.labelSmall)
                val bmi = summary.bmi
                if (bmi == null) {
                    Text(stringResource(R.string.number_empty), style = GtTheme.typography.numberM)
                } else {
                    Text(decimalLabel(bmi), style = GtTheme.typography.numberM)
                    val category = bmiCategory(bmi)
                    GlassBadge(stringResource(category.label()), tone = if (category == BmiCategory.Normal) BadgeTone.Accent else BadgeTone.Neutral)
                }
            }
        }
        Text(stringResource(R.string.profile_bmi_text), color = colors.textTertiary, style = GtTheme.typography.labelSmall)
        GlassButton(stringResource(R.string.profile_add), onClick = { onAdd(BodyMetric.Weight) }, icon = R.drawable.ic_add)
    }
}

@Composable
private fun ValueTile(metric: BodyMetric, measurement: BodyMeasurement?, modifier: Modifier, onAdd: (BodyMetric) -> Unit) {
    val colors = GtTheme.colors
    Column(modifier.clickable { onAdd(metric) }, verticalArrangement = Arrangement.spacedBy(GtSpacing.xxs)) {
        Text(stringResource(metric.label()), color = colors.textSecondary, style = GtTheme.typography.labelSmall)
        Text(measurement?.let { bodyValueLabel(metric, it.value) } ?: stringResource(R.string.number_empty), style = GtTheme.typography.numberM, maxLines = 1)
        Text(
            measurement?.let { shortDate(it.time) } ?: stringResource(R.string.profile_add),
            color = if (measurement == null) colors.accentText else colors.textTertiary,
            style = GtTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CompositionCard(summary: BodySummary, onAdd: (BodyMetric) -> Unit) {
    val colors = GtTheme.colors
    val empty = stringResource(R.string.number_empty)
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.profile_composition_title), style = GtTheme.typography.title)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            StatColumn(summary.bodyFat?.let { bodyValueLabel(BodyMetric.BodyFat, it.value) } ?: empty, stringResource(R.string.profile_body_fat), Modifier.weight(1f))
            StatColumn(summary.fatMassKg?.let { bodyValueLabel(BodyMetric.Weight, it) } ?: empty, stringResource(R.string.profile_fat_mass), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            StatColumn(summary.leanMassKg?.let { bodyValueLabel(BodyMetric.Weight, it) } ?: empty, stringResource(R.string.profile_lean_mass), Modifier.weight(1f))
            StatColumn(
                summary.skeletalMuscle?.let { bodyValueLabel(BodyMetric.SkeletalMuscle, it.value) } ?: empty,
                stringResource(R.string.profile_skeletal_muscle),
                Modifier.weight(1f),
            )
        }
        Text(stringResource(R.string.profile_composition_text), color = colors.textTertiary, style = GtTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s), verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            GlassButton(stringResource(R.string.profile_body_fat), onClick = { onAdd(BodyMetric.BodyFat) }, style = GlassButtonStyle.Secondary, icon = R.drawable.ic_add)
            GlassButton(stringResource(R.string.profile_skeletal_muscle), onClick = { onAdd(BodyMetric.SkeletalMuscle) }, style = GlassButtonStyle.Secondary, icon = R.drawable.ic_add)
        }
    }
}

@Composable
private fun ChartsCard(state: ProfileUiState, onChart: (BodyChart) -> Unit, onRange: (ChartRange) -> Unit) {
    val resources = LocalResources.current
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.profile_charts_title), style = GtTheme.typography.title)
        GlassSegmentedControl(
            options = listOf(R.string.profile_weight, R.string.bmi_label, R.string.profile_body_fat).map { stringResource(it) },
            selectedIndex = state.chart.ordinal,
            onSelect = { onChart(BodyChart.entries[it]) },
        )
        GlassLineChart(
            points = state.chartPoints,
            valueLabel = { value ->
                when (state.chart) {
                    BodyChart.Weight -> resources.getString(R.string.value_kg, decimalLabel(value))
                    BodyChart.Bmi -> decimalLabel(value)
                    BodyChart.BodyFat -> resources.getString(R.string.value_percent, decimalLabel(value))
                }
            },
            dateLabel = { shortDate(it) },
            emptyText = stringResource(if (state.chart == BodyChart.Bmi && state.chartEmpty) R.string.profile_chart_bmi_empty else R.string.profile_chart_empty),
            axisLabel = { decimalLabel(it) },
        )
        ChartRangeSelector(state.range, onSelect = onRange)
    }
}

@Composable
private fun CaloriesCard(calories: Calories?, onConnect: () -> Unit) {
    val colors = GtTheme.colors
    val empty = stringResource(R.string.number_empty)
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.profile_kcal_title), style = GtTheme.typography.title)
        when {
            calories == null -> {
                Text(stringResource(R.string.profile_kcal_connect), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
                GlassButton(stringResource(R.string.health_connect), onClick = onConnect, style = GlassButtonStyle.Secondary, icon = R.drawable.ic_link)
            }
            calories.isEmpty ->
                Text(stringResource(R.string.profile_kcal_none), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
            else -> {
                listOf(
                    R.string.profile_kcal_today to (calories.activeToday to calories.totalToday),
                    R.string.profile_kcal_average to (calories.activeAverage to calories.totalAverage),
                ).forEach { (label, values) ->
                    Text(stringResource(label), color = colors.textSecondary, style = GtTheme.typography.label)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                        StatColumn(values.first?.let { kcalLabel(it) } ?: empty, stringResource(R.string.profile_kcal_active), Modifier.weight(1f))
                        StatColumn(values.second?.let { kcalLabel(it) } ?: empty, stringResource(R.string.profile_kcal_total), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** A value of the log with its source; only the ones entered here can be deleted. */
@Composable
private fun MeasurementRow(measurement: BodyMeasurement, onDelete: () -> Unit) {
    val colors = GtTheme.colors
    val source = measurement.origin?.let { sourceLabel(it) } ?: stringResource(R.string.profile_source_manual)
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = GtSpacing.l, end = GtSpacing.xs, top = GtSpacing.s, bottom = GtSpacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.value_measurement, stringResource(measurement.metric.label()), bodyValueLabel(measurement.metric, measurement.value)),
                    style = GtTheme.typography.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.profile_measurement_meta, shortDate(measurement.time), timeLabel(measurement.time), source),
                    color = colors.textTertiary,
                    style = GtTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (measurement.manualId != null) {
                GlassIconButton(R.drawable.ic_delete, stringResource(R.string.action_delete), onClick = onDelete, style = GlassButtonStyle.Ghost)
            }
        }
    }
}

@Composable
private fun AddForm(adding: NewMeasurement, onMetric: (BodyMetric) -> Unit, onValue: (Double?) -> Unit, onSave: () -> Unit) {
    val colors = GtTheme.colors
    val metric = adding.metric
    val unit = stringResource(metric.unit())
    Text(stringResource(R.string.profile_add_title), style = GtTheme.typography.headline)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s), verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        BodyMetric.entries.forEach { GlassChip(stringResource(it.label()), selected = it == metric, onClick = { onMetric(it) }) }
    }
    // The field takes any number; the range is checked on saving, so "1" on the way to "180" fits
    GlassNumberField(
        value = adding.value,
        onValueChange = onValue,
        label = stringResource(metric.label()),
        modifier = Modifier.fillMaxWidth(),
        step = if (metric == BodyMetric.Height) 1.0 else 0.1,
        decimals = if (metric == BodyMetric.Height) 0 else 1,
        unit = unit,
        range = 0.0..metric.toInput(metric.range.endInclusive),
        placeholder = stringResource(R.string.number_empty),
    )
    Text(
        stringResource(
            R.string.profile_add_range,
            decimalLabel(metric.toInput(metric.range.start), decimals = 0),
            decimalLabel(metric.toInput(metric.range.endInclusive), decimals = 0),
            unit,
        ),
        color = if (adding.invalid) colors.danger else colors.textSecondary,
        style = GtTheme.typography.bodySmall,
    )
    Text(stringResource(R.string.profile_add_now), color = colors.textTertiary, style = GtTheme.typography.labelSmall)
    GlassButton(stringResource(R.string.action_save), onClick = onSave, enabled = adding.value != null, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Message(text: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(text, color = GtTheme.colors.textSecondary, style = GtTheme.typography.body)
    }
}

@Composable
private fun PreviewScreen(state: ProfileUiState) {
    GymTrackerTheme {
        GlassScaffold {
            ProfileScreen(
                state = state,
                onSettings = {}, onHealthConnect = {}, onAge = {}, onChart = {}, onRange = {}, onShowAll = {}, onAdd = {}, onAddMetric = {}, onAddValue = {},
                onAddDismiss = {}, onAddSave = {}, onDeleteRequest = {}, onDeleteDismiss = {}, onDeleteConfirm = {},
            )
        }
    }
}

@Preview
@Composable
private fun ProfileScreenPreview() {
    val now = 1_760_000_000_000
    val day = 86_400_000L
    val measurements = List(20) { BodyMeasurement(BodyMetric.Weight, now - (20 - it) * 4 * day, 84.0 - it * 0.2, origin = "com.sec.android.app.shealth") } +
        BodyMeasurement(BodyMetric.Height, now, 1.82, manualId = 1) +
        BodyMeasurement(BodyMetric.BodyFat, now, 17.5, origin = "com.sec.android.app.shealth")
    PreviewScreen(
        ProfileUiState(
            loading = false,
            measurements = measurements,
            summary = bodySummary(measurements),
            age = 29,
            calories = Calories(420.0, 2_350.0, 510.0, 2_480.0),
            chartPoints = measurements.filter { it.metric == BodyMetric.Weight }.map { ChartPoint(it.time, it.value) },
            chartEmpty = false,
        ),
    )
}

@Preview
@Composable
private fun ProfileScreenEmptyPreview() {
    PreviewScreen(ProfileUiState(loading = false))
}
