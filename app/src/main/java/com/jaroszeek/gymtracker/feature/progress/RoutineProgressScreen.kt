package com.jaroszeek.gymtracker.feature.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.ChartPoint
import com.jaroszeek.gymtracker.designsystem.component.ChartRange
import com.jaroszeek.gymtracker.designsystem.component.ChartRangeSelector
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassLineChart
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.component.start
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.progress.RoutineSession
import com.jaroszeek.gymtracker.feature.routines.weightLabel
import com.jaroszeek.gymtracker.feature.workout.volumeLabel
import java.time.Instant
import java.time.ZoneId

@Composable
fun RoutineProgressRoute(viewModel: RoutineProgressViewModel, onBack: () -> Unit, onOpenWorkout: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RoutineProgressScreen(state, onBack = onBack, onRange = viewModel::onRange, onOpenWorkout = onOpenWorkout)
}

@Composable
fun RoutineProgressScreen(
    state: RoutineProgressUiState,
    onBack: () -> Unit,
    onRange: (ChartRange) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GtTheme.colors
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(navBarContentPadding())
                .padding(horizontal = GtSpacing.xl, vertical = GtSpacing.s),
            verticalArrangement = Arrangement.spacedBy(GtSpacing.l),
        ) {
            // Room for the floating back button
            Box(Modifier.padding(top = GtSpacing.huge))
            when {
                state.loading -> Unit
                state.error || state.name == null -> GlassCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(if (state.error) R.string.progress_error else R.string.routine_progress_not_found), color = colors.textSecondary, style = GtTheme.typography.body)
                }
                else -> RoutineCharts(state, onRange, onOpenWorkout)
            }
        }
        GlassIconButton(
            R.drawable.ic_arrow_back,
            stringResource(R.string.action_back),
            onClick = onBack,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = GtSpacing.m, vertical = GtSpacing.xs),
        )
    }
}

@Composable
private fun RoutineCharts(state: RoutineProgressUiState, onRange: (ChartRange) -> Unit, onOpenWorkout: (Long) -> Unit) {
    val colors = GtTheme.colors
    val shown = remember(state.sessions, state.range) {
        val from = state.range.start(Instant.now(), ZoneId.systemDefault())
        state.sessions.filter { from == null || it.time >= from }
    }
    val kg = rememberKgLabel()
    val volume = rememberVolumeLabel()
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
        Text(state.name.orEmpty(), style = GtTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(pluralStringResource(R.plurals.progress_sessions, state.sessions.size, state.sessions.size), color = colors.textSecondary, style = GtTheme.typography.label)
    }
    ChartRangeSelector(state.range, onSelect = onRange)
    ChartCard(
        title = stringResource(R.string.routine_progress_volume),
        points = shown.map { ChartPoint(it.time, it.volumeKg) },
        valueLabel = volume,
        axisLabel = ::volumeLabel,
    )
    ChartCard(
        title = stringResource(R.string.routine_progress_max_weight),
        points = shown.mapNotNull { session -> session.maxWeightKg?.let { ChartPoint(session.time, it) } },
        valueLabel = kg,
        axisLabel = ::weightLabel,
    )
    if (shown.isNotEmpty()) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.routine_progress_sessions), style = GtTheme.typography.title)
            shown.asReversed().forEach { session -> SessionRow(session, volume, onClick = { onOpenWorkout(session.workoutId) }) }
        }
    }
}

@Composable
private fun ChartCard(title: String, points: List<ChartPoint>, valueLabel: (Double) -> String, axisLabel: (Double) -> String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, style = GtTheme.typography.title)
        GlassLineChart(
            points = points,
            valueLabel = valueLabel,
            dateLabel = { shortDate(it) },
            emptyText = stringResource(R.string.chart_range_empty),
            axisLabel = axisLabel,
        )
    }
}

@Composable
private fun SessionRow(session: RoutineSession, volume: (Double) -> String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = GtSizes.MinTouch)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dayLabel(session.time), style = GtTheme.typography.label, modifier = Modifier.weight(1f))
        Text(volume(session.volumeKg), color = GtTheme.colors.textSecondary, style = GtTheme.typography.label)
    }
}

@Preview
@Composable
private fun RoutineProgressScreenPreview() {
    val day = 86_400_000L
    val now = System.currentTimeMillis()
    val sessions = List(12) { RoutineSession(it.toLong(), now - (12 - it) * 4 * day, 7_500.0 + it * 180, 80.0 + it * 1.25) }
    GymTrackerTheme {
        GlassScaffold {
            RoutineProgressScreen(RoutineProgressUiState(loading = false, name = "Push", sessions = sessions), {}, {}, {})
        }
    }
}
