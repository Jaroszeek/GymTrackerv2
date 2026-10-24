package com.jaroszeek.gymtracker.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.designsystem.component.GlassBottomSheet
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassDialog
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassNumberField
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.GlassTextField
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.workout.LoggedExercise
import com.jaroszeek.gymtracker.domain.workout.LoggedSet
import com.jaroszeek.gymtracker.domain.workout.LoggedWorkout
import com.jaroszeek.gymtracker.feature.exercises.ExerciseImage
import com.jaroszeek.gymtracker.feature.exercises.ExercisePicker
import com.jaroszeek.gymtracker.feature.exercises.ExercisesViewModel
import com.jaroszeek.gymtracker.feature.routines.input
import com.jaroszeek.gymtracker.feature.routines.label
import com.jaroszeek.gymtracker.feature.routines.LocalWeightUnit
import com.jaroszeek.gymtracker.feature.routines.MASS_DECIMALS
import com.jaroszeek.gymtracker.feature.workout.label
import com.jaroszeek.gymtracker.feature.workout.SetActions
import com.jaroszeek.gymtracker.feature.workout.SetBadge
import com.jaroszeek.gymtracker.feature.workout.SetRef
import com.jaroszeek.gymtracker.feature.workout.setValueLabel
import com.jaroszeek.gymtracker.R

private const val MAX_REPS = 100.0
private const val MAX_MINUTES = 600.0
private const val DURATION_STEP_MINUTES = 5.0
private const val MILLIS_PER_MINUTE = 60_000L

@Composable
fun WorkoutEditorRoute(viewModel: WorkoutEditorViewModel, pickerViewModel: ExercisesViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    BackHandler(enabled = !state.finished) { viewModel.onBack() }
    WorkoutEditorScreen(
        state = state,
        onBack = viewModel::onBack,
        onNameChange = viewModel::onNameChange,
        onDurationChange = viewModel::onDurationChange,
        onExpand = viewModel::onExpand,
        onWeightChange = viewModel::onWeightChange,
        onRepsChange = viewModel::onRepsChange,
        onSetActions = viewModel::onSetActions,
        onAddSet = viewModel::onAddSet,
        onRemoveExercise = viewModel::onRemoveExercise,
        onAddExercise = viewModel::onAddExercise,
        onSave = viewModel::onSave,
    )
    val draft = state.draft
    val ref = state.setActions
    val exercise = draft?.exercises?.firstOrNull { it.id == ref?.exerciseId }
    val set = exercise?.sets?.firstOrNull { it.id == ref?.setId }
    GlassBottomSheet(visible = set != null, onDismissRequest = viewModel::onSetActionsDismiss) {
        if (exercise != null && set != null) {
            SetActions(
                number = exercise.sets.indexOf(set) + 1,
                type = set.type,
                lastSet = exercise.sets.size == 1,
                onType = viewModel::onSetType,
                onDelete = viewModel::onDeleteSet,
            )
        }
    }
    GlassDialog(
        visible = state.confirmDiscard,
        onDismissRequest = viewModel::onDiscardDismiss,
        title = stringResource(R.string.routine_discard_title),
        text = stringResource(R.string.workout_editor_discard_text),
        confirmButton = { GlassButton(stringResource(R.string.action_discard), onClick = viewModel::onDiscardConfirm) },
        dismissButton = { GlassButton(stringResource(R.string.action_keep_editing), onClick = viewModel::onDiscardDismiss, style = GlassButtonStyle.Ghost) },
    )
    // Last, so back closes the picker before anything else
    if (state.picker) ExercisePicker(pickerViewModel, onPick = viewModel::onPicked, onDismiss = viewModel::onPickerDismiss)
}

@Composable
fun WorkoutEditorScreen(
    state: WorkoutEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onDurationChange: (Double?) -> Unit,
    onExpand: (exerciseId: Long, setId: Long) -> Unit,
    onWeightChange: (exerciseId: Long, setId: Long, Double?) -> Unit,
    onRepsChange: (exerciseId: Long, setId: Long, Int?) -> Unit,
    onSetActions: (exerciseId: Long, setId: Long) -> Unit,
    onAddSet: (exerciseId: Long) -> Unit,
    onRemoveExercise: (exerciseId: Long) -> Unit,
    onAddExercise: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GtTheme.colors
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = GtSpacing.xl,
            end = GtSpacing.xl,
            top = insets.calculateTopPadding() + GtSpacing.s,
            bottom = navBarContentPadding().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.m),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                GlassIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClick = onBack)
                Text(stringResource(R.string.workout_editor_title), style = GtTheme.typography.headline)
            }
        }
        val draft = state.draft
        if (state.loading) return@LazyColumn
        if (draft == null) {
            item(key = "missing") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.workout_detail_not_found), color = colors.textSecondary, style = GtTheme.typography.body)
                }
            }
            return@LazyColumn
        }
        item(key = "details") {
            GlassCard(Modifier.fillMaxWidth()) {
                GlassTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.label_name),
                    placeholder = stringResource(R.string.routine_name_placeholder),
                    error = stringResource(R.string.workout_editor_name_error).takeIf { draft.name.isBlank() },
                )
                GlassNumberField(
                    value = (draft.durationMillis / MILLIS_PER_MINUTE).toDouble(),
                    onValueChange = onDurationChange,
                    label = stringResource(R.string.workout_editor_duration),
                    modifier = Modifier.fillMaxWidth(),
                    step = DURATION_STEP_MINUTES,
                    unit = stringResource(R.string.unit_min),
                    range = 1.0..MAX_MINUTES,
                )
            }
        }
        items(draft.exercises, key = { it.id }) { exercise ->
            ExerciseEditor(
                exercise = exercise,
                info = state.exercises[exercise.exerciseId],
                expanded = state.expanded,
                onExpand = { onExpand(exercise.id, it) },
                onWeightChange = { setId, kg -> onWeightChange(exercise.id, setId, kg) },
                onRepsChange = { setId, reps -> onRepsChange(exercise.id, setId, reps) },
                onSetActions = { onSetActions(exercise.id, it) },
                onAddSet = { onAddSet(exercise.id) },
                onRemove = { onRemoveExercise(exercise.id) },
            )
        }
        item(key = "add") {
            GlassButton(
                stringResource(R.string.routine_add_exercise),
                onClick = onAddExercise,
                icon = R.drawable.ic_add,
                style = GlassButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "save") {
            Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                val error = when {
                    draft.exercises.isEmpty() -> R.string.routine_blocks_error
                    draft.exercises.any { e -> e.sets.any { it.reps <= 0 } } -> R.string.workout_editor_reps_missing
                    else -> null
                }
                if (error != null) Text(stringResource(error), color = colors.danger, style = GtTheme.typography.bodySmall)
                if (state.saveFailed) Text(stringResource(R.string.workout_editor_save_failed), color = colors.danger, style = GtTheme.typography.bodySmall)
                GlassButton(
                    stringResource(R.string.action_save),
                    onClick = onSave,
                    enabled = draft.canSave && !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ExerciseEditor(
    exercise: LoggedExercise,
    info: Exercise?,
    expanded: SetRef?,
    onExpand: (setId: Long) -> Unit,
    onWeightChange: (setId: Long, Double?) -> Unit,
    onRepsChange: (setId: Long, Int?) -> Unit,
    onSetActions: (setId: Long) -> Unit,
    onAddSet: () -> Unit,
    onRemove: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(GtSpacing.l)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            ExerciseImage(info?.images?.firstOrNull(), null, GtShapes.Small, Modifier.size(GtSizes.ThumbnailSmall))
            Text(info?.name.orEmpty(), style = GtTheme.typography.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            GlassIconButton(R.drawable.ic_delete, stringResource(R.string.workout_editor_remove_exercise), onClick = onRemove, style = GlassButtonStyle.Ghost)
        }
        exercise.sets.forEachIndexed { index, set ->
            key(set.id) {
                SetEditor(
                    number = index + 1,
                    set = set,
                    expanded = expanded?.exerciseId == exercise.id && expanded.setId == set.id,
                    onExpand = { onExpand(set.id) },
                    onWeightChange = { onWeightChange(set.id, it) },
                    onRepsChange = { onRepsChange(set.id, it) },
                    onActions = { onSetActions(set.id) },
                )
            }
        }
        GlassButton(stringResource(R.string.workout_add_set), onClick = onAddSet, style = GlassButtonStyle.Ghost, icon = R.drawable.ic_add)
    }
}

/** One set: its values in a line, or open with the number fields; the badge opens the type and delete options. */
@Composable
private fun SetEditor(
    number: Int,
    set: LoggedSet,
    expanded: Boolean,
    onExpand: () -> Unit,
    onWeightChange: (Double?) -> Unit,
    onRepsChange: (Int?) -> Unit,
    onActions: () -> Unit,
) {
    val colors = GtTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            SetBadge(number, done = set.reps > 0, onClick = onActions)
            Column(Modifier.weight(1f).clickable(onClick = onExpand)) {
                Text(
                    if (set.reps > 0) setValueLabel(set.weightKg, set.reps) else stringResource(R.string.workout_set_empty),
                    color = if (set.reps > 0) colors.textPrimary else colors.danger,
                    style = GtTheme.typography.label,
                )
                if (set.type != SetType.Working) Text(stringResource(set.type.label()), color = colors.textTertiary, style = GtTheme.typography.labelSmall)
            }
        }
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                val unit = LocalWeightUnit.current
                GlassNumberField(
                    value = unit.input(set.weightKg),
                    onValueChange = { onWeightChange(it?.let(unit::toKg)) },
                    label = stringResource(R.string.workout_label_weight),
                    modifier = Modifier.weight(1f),
                    step = unit.step,
                    decimals = MASS_DECIMALS,
                    unit = stringResource(unit.label()),
                    placeholder = stringResource(R.string.number_empty),
                )
                GlassNumberField(
                    value = set.reps.takeIf { it > 0 }?.toDouble(),
                    onValueChange = { onRepsChange(it?.toInt()) },
                    label = stringResource(R.string.workout_label_reps),
                    modifier = Modifier.weight(1f),
                    range = 0.0..MAX_REPS,
                    placeholder = stringResource(R.string.number_empty),
                )
            }
        }
    }
}

@Preview
@Composable
private fun WorkoutEditorScreenPreview() {
    val draft = LoggedWorkout(
        id = 1, routineId = null, name = "Push", startedAt = 0, endedAt = 3_000_000,
        exercises = listOf(LoggedExercise(1, 1, null, listOf(LoggedSet(2, SetType.Working, 80.0, 8), LoggedSet(3, SetType.Failure, 80.0, 0)))),
    )
    GymTrackerTheme {
        GlassScaffold {
            WorkoutEditorScreen(
                state = WorkoutEditorUiState(loading = false, draft = draft, expanded = SetRef(1, 3)),
                onBack = {}, onNameChange = {}, onDurationChange = {}, onExpand = { _, _ -> }, onWeightChange = { _, _, _ -> },
                onRepsChange = { _, _, _ -> }, onSetActions = { _, _ -> }, onAddSet = {}, onRemoveExercise = {}, onAddExercise = {}, onSave = {},
            )
        }
    }
}
