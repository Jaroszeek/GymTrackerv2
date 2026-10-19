package com.jaroszeek.gymtracker.feature.history

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
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
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.workout.LoggedExercise
import com.jaroszeek.gymtracker.domain.workout.LoggedSet
import com.jaroszeek.gymtracker.domain.workout.LoggedWorkout
import com.jaroszeek.gymtracker.feature.exercises.ExerciseImage
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
                    WorkoutDetails(state.workout, state.exercises, state.records)
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
}

@Composable
private fun WorkoutDetails(workout: LoggedWorkout, exercises: Map<Long, Exercise>, records: Map<Long, Set<RecordType>>) {
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
        Text(workout.name, style = GtTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.routine_meta, fullDate(workout.startedAt), stringResource(R.string.workout_detail_time, timeLabel(workout.startedAt), timeLabel(workout.endedAt))),
            color = GtTheme.colors.textSecondary,
            style = GtTheme.typography.label,
        )
    }
    SummaryStats(workout.summary())
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
                WorkoutDetailUiState.Content(workout, emptyMap(), mapOf(4L to setOf(RecordType.Weight, RecordType.E1rm))),
                {}, {}, {}, {}, {},
            )
        }
    }
}
