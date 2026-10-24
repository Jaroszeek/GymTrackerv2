package com.jaroszeek.gymtracker.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.GlassBottomSheet
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassDialog
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.health.HealthSession
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.workout.LoggedExercise
import com.jaroszeek.gymtracker.domain.workout.LoggedSet
import com.jaroszeek.gymtracker.domain.workout.LoggedWorkout
import com.jaroszeek.gymtracker.feature.exercises.ExerciseImage
import com.jaroszeek.gymtracker.feature.health.HeartRateCard
import com.jaroszeek.gymtracker.feature.health.label
import com.jaroszeek.gymtracker.feature.health.sourceLabel
import com.jaroszeek.gymtracker.feature.progress.RecordBadges
import com.jaroszeek.gymtracker.feature.progress.fullDate
import com.jaroszeek.gymtracker.feature.progress.timeLabel
import com.jaroszeek.gymtracker.feature.workout.SummaryStats
import com.jaroszeek.gymtracker.feature.workout.label
import com.jaroszeek.gymtracker.feature.workout.setValueLabel

@Composable
fun WorkoutDetailRoute(viewModel: WorkoutDetailViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == WorkoutDetailUiState.Deleted) onBack() }
    WorkoutDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onDeleteRequest = viewModel::onDeleteRequest,
        onDeleteDismiss = viewModel::onDeleteDismiss,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        onLinkSession = viewModel::onLinkSession,
        onUnlinkSession = viewModel::onUnlinkSession,
        onDismissProposal = viewModel::onDismissProposal,
        onChooseSession = viewModel::onChooseSession,
        onChooseDismiss = viewModel::onChooseDismiss,
    )
}

@Composable
fun WorkoutDetailScreen(
    state: WorkoutDetailUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onLinkSession: (String) -> Unit,
    onUnlinkSession: () -> Unit,
    onDismissProposal: () -> Unit,
    onChooseSession: () -> Unit,
    onChooseDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state as? WorkoutDetailUiState.Content
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
            // Room for the floating buttons
            Box(Modifier.padding(top = GtSpacing.huge))
            when (state) {
                is WorkoutDetailUiState.Content -> {
                    if (state.deleteFailed) Text(stringResource(R.string.workout_delete_failed), color = GtTheme.colors.danger, style = GtTheme.typography.bodySmall)
                    WorkoutDetails(state.workout, state.exercises, state.records) {
                        WatchSession(state.health, onLinkSession, onUnlinkSession, onDismissProposal, onChooseSession)
                    }
                }
                WorkoutDetailUiState.NotFound, WorkoutDetailUiState.Deleted -> Message(stringResource(R.string.workout_detail_not_found))
                WorkoutDetailUiState.Error -> Message(stringResource(R.string.progress_error))
                WorkoutDetailUiState.Loading -> Unit
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = GtSpacing.m, vertical = GtSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(GtSpacing.s),
        ) {
            GlassIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClick = onBack)
            Box(Modifier.weight(1f))
            if (content != null) {
                GlassIconButton(R.drawable.ic_edit, stringResource(R.string.action_edit), onClick = { onEdit(content.workout.id) })
                GlassIconButton(R.drawable.ic_delete, stringResource(R.string.action_delete), onClick = onDeleteRequest)
            }
        }
    }
    GlassDialog(
        visible = content?.confirmDelete == true,
        onDismissRequest = onDeleteDismiss,
        title = stringResource(R.string.delete_workout_title),
        text = stringResource(R.string.delete_workout_text),
        confirmButton = { GlassButton(stringResource(R.string.action_delete), onClick = onDeleteConfirm) },
        dismissButton = { GlassButton(stringResource(R.string.action_cancel), onClick = onDeleteDismiss, style = GlassButtonStyle.Ghost) },
    )
    GlassBottomSheet(visible = content?.choosingSession == true, onDismissRequest = onChooseDismiss) {
        Text(stringResource(R.string.link_sheet_title), style = GtTheme.typography.headline)
        val candidates = content?.health?.candidates.orEmpty()
        if (candidates.isEmpty()) {
            Text(stringResource(R.string.link_sheet_empty), color = GtTheme.colors.textSecondary, style = GtTheme.typography.body)
        }
        candidates.forEach { session -> SessionChoice(session, onClick = { onLinkSession(session.id) }) }
    }
}

/** The linked session's heart rate, a proposal to link one, or the way to pick one by hand; nothing without sessions. */
@Composable
private fun WatchSession(
    health: WorkoutHealth,
    onLink: (String) -> Unit,
    onUnlink: () -> Unit,
    onDismissProposal: () -> Unit,
    onChoose: () -> Unit,
) {
    when {
        health.session != null -> {
            HeartRateCard(health.session, health.heartRate, health.heartRateSummary)
            GlassButton(
                stringResource(R.string.link_unlink),
                onClick = onUnlink,
                style = GlassButtonStyle.Ghost,
                icon = R.drawable.ic_link_off,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        health.proposal != null -> GlassCard(Modifier.fillMaxWidth()) {
            val session = health.proposal
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                Icon(painterResource(R.drawable.ic_watch), contentDescription = null, Modifier.size(GtSizes.IconSmall), tint = GtTheme.colors.accentText)
                Text(stringResource(R.string.link_title), style = GtTheme.typography.title)
            }
            Text(
                stringResource(R.string.link_proposal, session.label(), sourceLabel(session.origin), timeLabel(session.startTime), timeLabel(session.endTime)),
                color = GtTheme.colors.textSecondary,
                style = GtTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                GlassButton(stringResource(R.string.link_confirm), onClick = { onLink(session.id) }, icon = R.drawable.ic_link)
                GlassButton(stringResource(R.string.link_dismiss), onClick = onDismissProposal, style = GlassButtonStyle.Ghost)
            }
        }
        health.candidates.isNotEmpty() -> GlassButton(
            stringResource(R.string.link_choose),
            onClick = onChoose,
            style = GlassButtonStyle.Secondary,
            icon = R.drawable.ic_watch,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SessionChoice(session: HealthSession, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick), contentPadding = PaddingValues(GtSpacing.l)) {
        Text(session.label(), style = GtTheme.typography.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(
                R.string.routine_meta,
                stringResource(R.string.workout_detail_time, timeLabel(session.startTime), timeLabel(session.endTime)),
                sourceLabel(session.origin),
            ),
            color = GtTheme.colors.textSecondary,
            style = GtTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun WorkoutDetails(
    workout: LoggedWorkout,
    exercises: Map<Long, Exercise>,
    records: Map<Long, Set<RecordType>>,
    watchSession: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
        Text(workout.name, Modifier.semantics { heading() }, style = GtTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.routine_meta, fullDate(workout.startedAt), stringResource(R.string.workout_detail_time, timeLabel(workout.startedAt), timeLabel(workout.endedAt))),
            color = GtTheme.colors.textSecondary,
            style = GtTheme.typography.label,
        )
    }
    SummaryStats(workout.summary())
    watchSession()
    workout.exercises.forEach { exercise -> ExerciseSets(exercise, exercises[exercise.exerciseId], records) }
}

@Composable
private fun ExerciseSets(exercise: LoggedExercise, info: Exercise?, records: Map<Long, Set<RecordType>>) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(GtSpacing.l)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            ExerciseImage(info?.images?.firstOrNull(), null, GtShapes.Small, Modifier.size(GtSizes.ThumbnailSmall))
            Text(info?.name.orEmpty(), style = GtTheme.typography.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        exercise.sets.forEachIndexed { index, set -> SetLine(index + 1, set, records[set.id]) }
    }
}

@Composable
private fun SetLine(number: Int, set: LoggedSet, records: Set<RecordType>?) {
    val colors = GtTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        Text("$number", color = colors.textTertiary, style = GtTheme.typography.label, modifier = Modifier.width(GtSizes.SetBadge))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GtSpacing.xxs)) {
            Text(setValueLabel(set.weightKg, set.reps), style = GtTheme.typography.label)
            if (set.type != SetType.Working) Text(stringResource(set.type.label()), color = colors.textTertiary, style = GtTheme.typography.labelSmall)
            if (records != null) RecordBadges(records)
        }
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
private fun WorkoutDetailScreenPreview() {
    val workout = LoggedWorkout(
        id = 1, routineId = 1, name = "Push", startedAt = 1_760_000_000_000, endedAt = 1_760_003_125_000,
        exercises = listOf(
            LoggedExercise(1, 1, null, listOf(LoggedSet(2, SetType.Warmup, 40.0, 10), LoggedSet(3, SetType.Working, 80.0, 8), LoggedSet(4, SetType.Working, 82.5, 6))),
        ),
    )
    GymTrackerTheme {
        GlassScaffold {
            WorkoutDetailScreen(
                WorkoutDetailUiState.Content(
                    workout,
                    emptyMap(),
                    mapOf(4L to setOf(RecordType.Weight, RecordType.E1rm)),
                    health = WorkoutHealth(proposal = HealthSession("s", "com.sec.android.app.shealth", 70, null, workout.startedAt, workout.endedAt)),
                ),
                {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }
}
