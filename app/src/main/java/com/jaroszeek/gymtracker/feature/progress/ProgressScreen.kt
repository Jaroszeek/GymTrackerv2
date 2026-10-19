package com.jaroszeek.gymtracker.feature.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.GlassBadge
import com.jaroszeek.gymtracker.designsystem.component.GlassBar
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.GlassSegmentedControl
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.exercise.BodyPart
import com.jaroszeek.gymtracker.domain.health.HealthSession
import com.jaroszeek.gymtracker.domain.progress.ExerciseHistory
import com.jaroszeek.gymtracker.domain.progress.RoutineHistory
import com.jaroszeek.gymtracker.domain.workout.WorkoutListItem
import com.jaroszeek.gymtracker.domain.workout.WorkoutSummary
import com.jaroszeek.gymtracker.feature.exercises.label
import com.jaroszeek.gymtracker.feature.health.kcalLabel
import com.jaroszeek.gymtracker.feature.health.label
import com.jaroszeek.gymtracker.feature.health.sourceLabel
import com.jaroszeek.gymtracker.feature.routines.weightLabel
import com.jaroszeek.gymtracker.feature.workout.durationLabel
import com.jaroszeek.gymtracker.feature.workout.volumeLabel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun ProgressRoute(
    onOpenWorkout: (Long) -> Unit,
    onOpenExercise: (Long) -> Unit,
    onOpenRoutine: (Long) -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressScreen(
        state = state,
        onTab = viewModel::onTab,
        onOpenWorkout = onOpenWorkout,
        onOpenExercise = onOpenExercise,
        onOpenRoutine = onOpenRoutine,
        onOpenSession = onOpenSession,
        onPreviousWeek = viewModel::onPreviousWeek,
        onNextWeek = viewModel::onNextWeek,
    )
}

@Composable
fun ProgressScreen(
    state: ProgressUiState,
    onTab: (ProgressTab) -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenExercise: (Long) -> Unit,
    onOpenRoutine: (Long) -> Unit,
    onOpenSession: (String) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = GtSpacing.xl,
            end = GtSpacing.xl,
            top = insets.calculateTopPadding() + GtSpacing.xxl,
            bottom = navBarContentPadding().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.m),
    ) {
        item(key = "header") {
            Text(stringResource(R.string.nav_progress), style = GtTheme.typography.display)
        }
        item(key = "tabs") {
            GlassSegmentedControl(
                options = ProgressTab.entries.map { stringResource(it.label) },
                selectedIndex = state.tab.ordinal,
                onSelect = { onTab(ProgressTab.entries[it]) },
            )
        }
        when {
            state.loading -> Unit
            state.error -> item(key = "error") { Message(stringResource(R.string.progress_error)) }
            else -> when (state.tab) {
                ProgressTab.History -> history(state.history, onOpenWorkout, onOpenSession)
                ProgressTab.Exercises -> exercises(state.exercises, onOpenExercise)
                ProgressTab.Routines -> routines(state.routines, onOpenRoutine)
                ProgressTab.Muscles -> item(key = "week") { WeekCard(state, onPreviousWeek, onNextWeek) }
            }
        }
    }
}

private val ProgressTab.label: Int
    get() = when (this) {
        ProgressTab.History -> R.string.progress_history
        ProgressTab.Exercises -> R.string.progress_exercises
        ProgressTab.Routines -> R.string.progress_routines
        ProgressTab.Muscles -> R.string.progress_muscles
    }

private fun LazyListScope.history(history: List<HistoryEntry>, onOpenWorkout: (Long) -> Unit, onOpenSession: (String) -> Unit) {
    if (history.isEmpty()) {
        item(key = "history-empty") {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.history_empty_title), style = GtTheme.typography.title)
                Text(stringResource(R.string.history_empty_text), color = GtTheme.colors.textSecondary, style = GtTheme.typography.body)
            }
        }
        return
    }
    val zone = ZoneId.systemDefault()
    history.groupBy { YearMonth.from(Instant.ofEpochMilli(it.startedAt).atZone(zone)) }.forEach { (month, entries) ->
        item(key = "month-$month") {
            Text(
                monthLabel(month),
                color = GtTheme.colors.textSecondary,
                style = GtTheme.typography.label,
                modifier = Modifier.padding(top = GtSpacing.s),
            )
        }
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is HistoryEntry.Workout -> WorkoutCard(entry.item, onClick = { onOpenWorkout(entry.item.id) })
                is HistoryEntry.Session -> SessionCard(entry.session, onClick = { onOpenSession(entry.session.id) })
            }
        }
    }
}

private val HistoryEntry.key: String
    get() = when (this) {
        is HistoryEntry.Workout -> "workout-${item.id}"
        is HistoryEntry.Session -> "session-${session.id}"
    }

private fun LazyListScope.exercises(exercises: List<ExerciseHistory>, onOpen: (Long) -> Unit) {
    if (exercises.isEmpty()) {
        item(key = "exercises-empty") { Message(stringResource(R.string.progress_exercises_empty)) }
        return
    }
    items(exercises, key = { "exercise-${it.exerciseId}" }) { exercise ->
        val best = exercise.e1rmKg?.let { stringResource(R.string.progress_e1rm, e1rmLabel(it)) }
            ?: exercise.maxWeightKg?.let { stringResource(R.string.value_kg, weightLabel(it)) }
            ?: stringResource(R.string.workout_set_value_bodyweight, exercise.maxReps)
        HistoryCard(
            title = exercise.name,
            value = best,
            meta = sessionsMeta(exercise.sessions, exercise.lastTime),
            onClick = { onOpen(exercise.exerciseId) },
        )
    }
}

private fun LazyListScope.routines(routines: List<RoutineHistory>, onOpen: (Long) -> Unit) {
    if (routines.isEmpty()) {
        item(key = "routines-empty") { Message(stringResource(R.string.progress_routines_empty)) }
        return
    }
    items(routines, key = { "routine-${it.routineId}" }) { routine ->
        HistoryCard(title = routine.name, value = null, meta = sessionsMeta(routine.sessions, routine.lastTime), onClick = { onOpen(routine.routineId) })
    }
}

@Composable
private fun sessionsMeta(sessions: Int, lastTime: Long) = stringResource(
    R.string.routine_meta,
    pluralStringResource(R.plurals.progress_sessions, sessions, sessions),
    stringResource(R.string.progress_last, shortDate(lastTime)),
)

/** A workout of the history list: name, when, and its numbers. */
@Composable
internal fun WorkoutCard(item: WorkoutListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    GlassCard(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            Text(item.name, Modifier.weight(1f), style = GtTheme.typography.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.healthSessionId != null) {
                Icon(painterResource(R.drawable.ic_watch), stringResource(R.string.link_title), Modifier.size(GtSizes.IconSmall), tint = colors.textSecondary)
            }
        }
        Text(
            stringResource(R.string.routine_meta, dayLabel(item.startedAt), timeLabel(item.startedAt)),
            color = colors.textSecondary,
            style = GtTheme.typography.label,
        )
        SummaryRow(item.summary, Modifier.padding(top = GtSpacing.xs))
    }
}

/** A session from Health Connect: its type, when, how long, calories and the app that wrote it. */
@Composable
private fun SessionCard(session: HealthSession, onClick: () -> Unit) {
    val colors = GtTheme.colors
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            Text(session.label(), Modifier.weight(1f), style = GtTheme.typography.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            GlassBadge(stringResource(R.string.session_imported))
        }
        Text(
            stringResource(R.string.routine_meta, dayLabel(session.startTime), timeLabel(session.startTime)),
            color = colors.textSecondary,
            style = GtTheme.typography.label,
        )
        Row(Modifier.fillMaxWidth().padding(top = GtSpacing.xs), horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            StatColumn(durationLabel(session.durationMillis), stringResource(R.string.summary_duration), Modifier.weight(1f))
            when {
                session.activeKcal != null -> StatColumn(kcalLabel(session.activeKcal), stringResource(R.string.kcal_active), Modifier.weight(1f))
                session.totalKcal != null -> StatColumn(kcalLabel(session.totalKcal), stringResource(R.string.kcal_total), Modifier.weight(1f))
                else -> Box(Modifier.weight(1f))
            }
            StatColumn(sourceLabel(session.origin), stringResource(R.string.session_source), Modifier.weight(1.3f))
        }
    }
}

/** Duration, exercises, working sets and volume side by side. */
@Composable
internal fun SummaryRow(summary: WorkoutSummary, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        StatColumn(durationLabel(summary.durationMillis), stringResource(R.string.summary_duration), Modifier.weight(1f))
        StatColumn("${summary.exercises}", stringResource(R.string.summary_exercises), Modifier.weight(1f))
        StatColumn("${summary.sets}", stringResource(R.string.progress_sets), Modifier.weight(1f))
        StatColumn(stringResource(R.string.summary_volume_value, volumeLabel(summary.volumeKg)), stringResource(R.string.summary_volume), Modifier.weight(1.3f))
    }
}

@Composable
private fun StatColumn(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = GtTheme.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, color = GtTheme.colors.textTertiary, style = GtTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HistoryCard(title: String, value: String?, meta: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            Text(title, style = GtTheme.typography.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (value != null) Text(value, color = GtTheme.colors.accentText, style = GtTheme.typography.label)
        }
        Text(meta, color = GtTheme.colors.textSecondary, style = GtTheme.typography.labelSmall)
    }
}

/** Working sets per body part in one week, with the week picker. */
@Composable
private fun WeekCard(state: ProgressUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    val colors = GtTheme.colors
    val most = remember(state.weekSets) { state.weekSets.perPart.values.maxOrNull()?.coerceAtLeast(1) ?: 1 }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(R.drawable.ic_chevron_left, stringResource(R.string.progress_week_previous), onClick = onPrevious, style = GlassButtonStyle.Ghost)
            Text(weekLabel(state.week), style = GtTheme.typography.title, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            GlassIconButton(
                R.drawable.ic_chevron_right,
                stringResource(R.string.progress_week_next),
                onClick = onNext,
                style = GlassButtonStyle.Ghost,
                enabled = !state.isCurrentWeek,
            )
        }
        Text(stringResource(R.string.progress_week_total, state.weekSets.total), color = colors.textSecondary, style = GtTheme.typography.label)
        BodyPart.entries.forEach { part ->
            val sets = state.weekSets.perPart[part] ?: 0
            Column(Modifier.padding(top = GtSpacing.xs), verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
                Row {
                    Text(stringResource(part.label), style = GtTheme.typography.label, modifier = Modifier.weight(1f))
                    Text("$sets", color = if (sets > 0) colors.textPrimary else colors.textTertiary, style = GtTheme.typography.label)
                }
                GlassBar(sets.toFloat() / most)
            }
        }
        Text(stringResource(R.string.progress_week_hint), color = colors.textTertiary, style = GtTheme.typography.labelSmall)
    }
}

@Composable
private fun Message(text: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(text, color = GtTheme.colors.textSecondary, style = GtTheme.typography.body)
    }
}

@Preview
@Composable
private fun ProgressScreenPreview() {
    val day = 86_400_000L
    val history = List(4) {
        HistoryEntry.Workout(WorkoutListItem(it.toLong(), "Push", 1_760_000_000_000 - it * 3 * day, WorkoutSummary(3_125_000, 8_450.0, 18, 6), "s".takeIf { _ -> it == 0 }))
    } + HistoryEntry.Session(HealthSession("w", "com.sec.android.app.shealth", 79, null, 1_760_000_000_000 - day, 1_760_000_000_000 - day + 2_400_000, activeKcal = 160.0))
    GymTrackerTheme {
        GlassScaffold {
            Box {
                ProgressScreen(ProgressUiState(loading = false, history = history.sortedByDescending { it.startedAt }), {}, {}, {}, {}, {}, {}, {})
            }
        }
    }
}
