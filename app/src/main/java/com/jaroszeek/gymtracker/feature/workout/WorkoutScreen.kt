package com.jaroszeek.gymtracker.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.core.notifications.SystemSettings
import com.jaroszeek.gymtracker.core.notifications.openSettings
import com.jaroszeek.gymtracker.designsystem.component.BadgeTone
import com.jaroszeek.gymtracker.designsystem.component.GlassBadge
import com.jaroszeek.gymtracker.designsystem.component.GlassBottomSheet
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassChip
import com.jaroszeek.gymtracker.designsystem.component.GlassDialog
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassNumberField
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtAlpha
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import com.jaroszeek.gymtracker.domain.workout.RestTimer
import com.jaroszeek.gymtracker.domain.workout.WorkoutExercise
import com.jaroszeek.gymtracker.domain.workout.WorkoutSet
import com.jaroszeek.gymtracker.domain.workout.WorkoutVariant
import com.jaroszeek.gymtracker.feature.exercises.ExerciseImage
import com.jaroszeek.gymtracker.feature.exercises.ExercisePicker
import com.jaroszeek.gymtracker.feature.exercises.ExercisesViewModel
import com.jaroszeek.gymtracker.feature.progress.RecordBadges
import com.jaroszeek.gymtracker.feature.routines.LocalWeightUnit
import com.jaroszeek.gymtracker.feature.routines.MASS_DECIMALS
import com.jaroszeek.gymtracker.feature.routines.input
import com.jaroszeek.gymtracker.feature.routines.label
import com.jaroszeek.gymtracker.feature.routines.massLabel
import com.jaroszeek.gymtracker.feature.routines.summary
import kotlinx.coroutines.delay

private const val MAX_REPS = 100.0
private const val RECORD_HAPTIC_DELAY_MILLIS = 150L

@Composable
fun WorkoutRoute(viewModel: WorkoutViewModel, pickerViewModel: ExercisesViewModel, onClose: () -> Unit, onDiagnostics: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.recordHit) {
        if (state.recordHit) {
            // After the tick's own haptic: a second, heavier pulse for the record
            delay(RECORD_HAPTIC_DELAY_MILLIS)
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            viewModel.onRecordShown()
        }
    }
    val finished = state.finished
    if (finished != null) {
        WorkoutSummaryScreen(
            state = finished,
            exercises = state.exercises,
            onChangeChoice = viewModel::onChangeChoice,
            onChangesDone = viewModel::onChangesDone,
            onDone = onClose,
        )
        return
    }
    val workout = state.workout ?: return
    WorkoutContent(state, workout, viewModel, onOpenSettings = context::openSettings, onDiagnostics = onDiagnostics)

    val ref = state.setActions
    val actionsExercise = workout.exercises.firstOrNull { it.id == ref?.exerciseId }
    val actionsSet = actionsExercise?.sets?.firstOrNull { it.id == ref?.setId }
    GlassBottomSheet(visible = actionsSet != null, onDismissRequest = viewModel::onSetActionsDismiss) {
        if (actionsExercise != null && actionsSet != null) {
            SetActions(
                number = actionsExercise.sets.indexOf(actionsSet) + 1,
                type = actionsSet.type,
                lastSet = actionsExercise.sets.size == 1,
                onType = viewModel::onSetType,
                onDelete = viewModel::onDeleteSet,
            )
        }
    }
    val next = workout.nextSet()
    RestOverlay(
        rest = workout.rest,
        visible = state.restVisible,
        next = next?.let { stringResource(R.string.rest_next, state.exercises[it.exercise.variant.exerciseId]?.name.orEmpty(), it.number) }
            ?: stringResource(R.string.rest_last),
        onAdjust = viewModel::onRestAdjust,
        onSkip = viewModel::onRestSkip,
        onHide = viewModel::onRestHide,
    )
    GlassDialog(
        visible = state.confirmFinish,
        onDismissRequest = viewModel::onFinishDismiss,
        title = stringResource(R.string.finish_title),
        text = stringResource(
            when {
                workout.doneSets == 0 -> R.string.finish_text_empty
                workout.doneSets < workout.totalSets -> R.string.finish_text_incomplete
                else -> R.string.finish_text
            },
        ),
        confirmButton = { GlassButton(stringResource(R.string.action_finish), onClick = viewModel::onFinishConfirm) },
        dismissButton = { GlassButton(stringResource(R.string.action_cancel), onClick = viewModel::onFinishDismiss, style = GlassButtonStyle.Ghost) },
    )
    GlassDialog(
        visible = state.confirmDiscard,
        onDismissRequest = viewModel::onDiscardDismiss,
        title = stringResource(R.string.discard_workout_title),
        text = stringResource(R.string.discard_workout_text),
        confirmButton = { GlassButton(stringResource(R.string.action_discard), onClick = viewModel::onDiscardConfirm) },
        dismissButton = { GlassButton(stringResource(R.string.action_cancel), onClick = viewModel::onDiscardDismiss, style = GlassButtonStyle.Ghost) },
    )
    // Last, so back closes the picker before anything else
    if (state.picker) ExercisePicker(pickerViewModel, onPick = viewModel::onPicked, onDismiss = viewModel::onPickerDismiss)
}

@Composable
private fun WorkoutContent(
    state: WorkoutUiState,
    workout: ActiveWorkout,
    viewModel: WorkoutViewModel,
    onOpenSettings: (SystemSettings) -> Unit,
    onDiagnostics: () -> Unit,
) {
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = GtSpacing.xl,
            end = GtSpacing.xl,
            top = insets.calculateTopPadding() + GtSpacing.s,
            bottom = navBarContentPadding().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.m),
    ) {
        item(key = "header") {
            WorkoutHeader(
                workout = workout,
                restHidden = workout.rest != null && !state.restVisible,
                onShowRest = viewModel::onRestShow,
            )
        }
        if (state.issues.any) {
            item(key = "issues") {
                BackgroundIssuesCard(state.issues, onOpenSettings = onOpenSettings, onDiagnostics = onDiagnostics, onHide = viewModel::onHideIssues)
            }
        }
        if (workout.exercises.isEmpty()) {
            item(key = "empty") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.workout_empty), color = GtTheme.colors.textSecondary, style = GtTheme.typography.body)
                }
            }
        }
        items(workout.exercises, key = { it.id }) { exercise ->
            ExerciseCard(
                exercise = exercise,
                exercises = state.exercises,
                expandedSetId = state.expanded[exercise.id]?.takeIf { id -> exercise.sets.any { it.id == id && !it.done } }
                    ?: exercise.sets.firstOrNull { !it.done }?.id,
                onSelectVariant = { viewModel.onSelectVariant(exercise.id, it) },
                onExpand = { viewModel.onExpandSet(exercise.id, it) },
                onWeightChange = { setId, kg -> viewModel.onWeightChange(exercise.id, setId, kg) },
                onRepsChange = { setId, reps -> viewModel.onRepsChange(exercise.id, setId, reps) },
                onToggle = { viewModel.onToggleSet(exercise.id, it) },
                onSetActions = { viewModel.onSetActions(exercise.id, it) },
                onAddSet = { viewModel.onAddSet(exercise.id) },
                records = state.records,
            )
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                GlassButton(
                    stringResource(R.string.routine_add_exercise),
                    onClick = viewModel::onAddExercise,
                    icon = R.drawable.ic_add,
                    style = GlassButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.finishFailed) Text(stringResource(R.string.finish_failed), color = GtTheme.colors.danger, style = GtTheme.typography.bodySmall)
                GlassButton(
                    stringResource(R.string.workout_finish),
                    onClick = viewModel::onFinishRequest,
                    enabled = !state.finishing,
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassButton(
                    stringResource(R.string.workout_discard),
                    onClick = viewModel::onDiscardRequest,
                    style = GlassButtonStyle.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassButton(
                    stringResource(R.string.diagnostics_title),
                    onClick = onDiagnostics,
                    style = GlassButtonStyle.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Name, session clock and progress; while the rest overlay is closed, a button brings it back. */
@Composable
fun WorkoutHeader(workout: ActiveWorkout, restHidden: Boolean, onShowRest: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
        Text(workout.name, Modifier.semantics { heading() }, style = GtTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            Text(durationLabel(rememberNow() - workout.startedAt), style = GtTheme.typography.numberM)
            Text(stringResource(R.string.workout_progress, workout.doneSets, workout.totalSets), color = colors.textSecondary, style = GtTheme.typography.label)
            Box(Modifier.weight(1f))
            val rest = workout.rest
            if (restHidden && rest != null) {
                GlassButton(
                    stringResource(R.string.rest_chip, countdownLabel(rest.remainingMillis(rememberNow()))),
                    onClick = onShowRest,
                    icon = R.drawable.ic_timer,
                    style = GlassButtonStyle.Secondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseCard(
    exercise: WorkoutExercise,
    exercises: Map<Long, Exercise>,
    expandedSetId: Long?,
    onSelectVariant: (Int) -> Unit,
    onExpand: (Long) -> Unit,
    onWeightChange: (setId: Long, Double?) -> Unit,
    onRepsChange: (setId: Long, Int?) -> Unit,
    onToggle: (setId: Long) -> Unit,
    onSetActions: (setId: Long) -> Unit,
    onAddSet: () -> Unit,
    records: Map<Long, Set<RecordType>>,
    modifier: Modifier = Modifier,
) {
    val colors = GtTheme.colors
    val variant = exercise.variant
    val info = exercises[variant.exerciseId]
    GlassCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(GtSpacing.l)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            ExerciseImage(info?.images?.firstOrNull(), null, GtShapes.Small, Modifier.size(GtSizes.ThumbnailSmall))
            Column(Modifier.weight(1f)) {
                Text(info?.name.orEmpty(), style = GtTheme.typography.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                variant.targets?.let { Text(stringResource(R.string.workout_target, it.summary()), color = colors.textSecondary, style = GtTheme.typography.labelSmall) }
            }
        }
        if (exercise.variants.size > 1) {
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                exercise.variants.forEachIndexed { index, option ->
                    GlassChip(
                        exercises[option.exerciseId]?.name.orEmpty(),
                        selected = index == exercise.selected,
                        onClick = { onSelectVariant(index) },
                        role = Role.RadioButton,
                    )
                }
            }
        }
        variant.progressionKg?.let { GlassBadge(stringResource(R.string.workout_progression, massLabel(it)), tone = BadgeTone.Accent) }
        exercise.sets.forEachIndexed { index, set ->
            key(set.id) {
                SetRow(
                    number = index + 1,
                    set = set,
                    previous = exercise.previousFor(set),
                    records = records[set.id],
                    expanded = set.id == expandedSetId,
                    onExpand = { onExpand(set.id) },
                    onWeightChange = { onWeightChange(set.id, it) },
                    onRepsChange = { onRepsChange(set.id, it) },
                    onToggle = { onToggle(set.id) },
                    onActions = { onSetActions(set.id) },
                )
            }
        }
        GlassButton(stringResource(R.string.workout_add_set), onClick = onAddSet, style = GlassButtonStyle.Ghost, icon = R.drawable.ic_add)
    }
}

/**
 * One set. The open set has big number fields; the others are one line with their values, so a
 * planned set can be ticked off straight away. The tick stays in the same place in both states.
 */
@Composable
private fun SetRow(
    number: Int,
    set: WorkoutSet,
    previous: PerformedSet?,
    records: Set<RecordType>?,
    expanded: Boolean,
    onExpand: () -> Unit,
    onWeightChange: (Double?) -> Unit,
    onRepsChange: (Int?) -> Unit,
    onToggle: () -> Unit,
    onActions: () -> Unit,
) {
    val colors = GtTheme.colors
    val haptics = LocalHapticFeedback.current
    val reps = set.reps
    val title = when {
        expanded -> stringResource(R.string.workout_set_number, number)
        reps != null && reps > 0 -> setValueLabel(set.weightKg, reps)
        else -> stringResource(R.string.workout_set_empty)
    }
    val details = listOfNotNull(
        set.type.takeIf { it != SetType.Working }?.let { stringResource(it.label()) },
        previous?.let { stringResource(R.string.workout_previous, setValueLabel(it.weightKg, it.reps)) },
    ).joinToString(" • ")
    val glow = rememberRecordGlow(records != null)
    Column(Modifier.recordGlow(glow, colors.accent), verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            SetBadge(number, set.done, onActions)
            Column(
                Modifier
                    .weight(1f)
                    .then(if (!expanded && !set.done) Modifier.clickable(role = Role.Button, onClick = onExpand) else Modifier),
            ) {
                Text(title, color = if (set.done || expanded) colors.textPrimary else colors.textSecondary, style = GtTheme.typography.label)
                if (details.isNotEmpty()) Text(details, color = colors.textTertiary, style = GtTheme.typography.labelSmall)
                if (records != null) RecordBadges(records, Modifier.padding(top = GtSpacing.xs))
            }
            GlassIconButton(
                R.drawable.ic_check,
                stringResource(if (set.done) R.string.workout_set_undo else R.string.workout_set_complete, number),
                onClick = {
                    haptics.performHapticFeedback(if (set.done) HapticFeedbackType.ToggleOff else HapticFeedbackType.Confirm)
                    onToggle()
                },
                style = if (set.done) GlassButtonStyle.Primary else GlassButtonStyle.Secondary,
                enabled = set.done || set.canComplete,
            )
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
                    value = set.reps?.toDouble(),
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

/**
 * What keeps the workout from running reliably with the screen off: one sentence on the risk and a
 * short button per issue that opens the system screen fixing it. Rows wrap, so it stays small at a large font.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundIssuesCard(
    issues: BackgroundIssues,
    onOpenSettings: (SystemSettings) -> Unit,
    onDiagnostics: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.background_title), style = GtTheme.typography.title)
        Text(stringResource(R.string.background_text), color = GtTheme.colors.textSecondary, style = GtTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s), verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            listOfNotNull(
                (R.string.background_notifications_action to SystemSettings.Notifications).takeIf { issues.notificationsOff },
                (R.string.background_battery_action to SystemSettings.Battery).takeIf { issues.batteryLimited },
                (R.string.background_exact_alarms_action to SystemSettings.ExactAlarms).takeIf { issues.exactAlarmsOff },
            ).forEach { (action, screen) ->
                GlassButton(stringResource(action), onClick = { onOpenSettings(screen) }, style = GlassButtonStyle.Secondary)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            GlassButton(stringResource(R.string.background_diagnostics), onClick = onDiagnostics, style = GlassButtonStyle.Ghost)
            GlassButton(stringResource(R.string.background_hide), onClick = onHide, style = GlassButtonStyle.Ghost)
        }
    }
}

/** Set number; tapping it opens the set's type and delete options. */
@Composable
internal fun SetBadge(number: Int, done: Boolean, onClick: () -> Unit) {
    val colors = GtTheme.colors
    val description = stringResource(R.string.workout_set_edit, number)
    Box(
        Modifier
            .size(GtSizes.MinTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(GtSizes.SetBadge)
                .clip(GtShapes.Pill)
                .background(if (done) colors.accent.copy(alpha = GtAlpha.ACCENT_WASH) else colors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = if (done) colors.accentText else colors.textSecondary, style = GtTheme.typography.label)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetActions(number: Int, type: SetType, lastSet: Boolean, onType: (SetType) -> Unit, onDelete: () -> Unit) {
    Text(stringResource(R.string.workout_set_number, number), style = GtTheme.typography.headline)
    Text(stringResource(R.string.workout_set_type), color = GtTheme.colors.textSecondary, style = GtTheme.typography.label)
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        SetType.entries.forEach { option ->
            GlassChip(stringResource(option.label()), selected = type == option, onClick = { onType(option) }, role = Role.RadioButton)
        }
    }
    GlassButton(
        stringResource(R.string.workout_set_delete),
        onClick = onDelete,
        icon = R.drawable.ic_delete,
        style = GlassButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
    if (lastSet) Text(stringResource(R.string.workout_set_delete_hint), color = GtTheme.colors.textTertiary, style = GtTheme.typography.labelSmall)
}

@Preview
@Composable
private fun ExerciseCardPreview() {
    val exercise = WorkoutExercise(
        id = 1,
        variants = listOf(WorkoutVariant(exerciseId = 1, targets = RoutineTargets(3, 8, 12, 60.0, 120), previous = List(3) { PerformedSet(60.0, 12) })),
        sets = listOf(WorkoutSet(2, weightKg = 62.5, reps = 8, done = true), WorkoutSet(3, weightKg = 62.5, reps = 8), WorkoutSet(4, SetType.Failure, 62.5, 8)),
    )
    GymTrackerTheme {
        GlassScaffold {
            Column(Modifier.padding(GtSpacing.xl), verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                WorkoutHeader(
                    workout = ActiveWorkout(1, "Push", startedAt = 0, updatedAt = 0, exercises = listOf(exercise), rest = RestTimer(0, 90_000, 1, 2)),
                    restHidden = true,
                    onShowRest = {},
                )
                BackgroundIssuesCard(BackgroundIssues(notificationsOff = true, exactAlarmsOff = true, batteryLimited = true), onOpenSettings = {}, onDiagnostics = {}, onHide = {})
                ExerciseCard(
                    exercise = exercise,
                    exercises = emptyMap(),
                    expandedSetId = 3,
                    onSelectVariant = {}, onExpand = {}, onWeightChange = { _, _ -> }, onRepsChange = { _, _ -> }, onToggle = {},
                    onSetActions = {}, onAddSet = {}, records = mapOf(2L to setOf(RecordType.Weight, RecordType.E1rm)),
                )
            }
        }
    }
}
