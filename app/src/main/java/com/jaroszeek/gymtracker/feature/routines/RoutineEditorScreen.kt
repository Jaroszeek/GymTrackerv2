package com.jaroszeek.gymtracker.feature.routines

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.jaroszeek.gymtracker.domain.routine.RoutineBlock
import com.jaroszeek.gymtracker.domain.routine.RoutineDraft
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.feature.exercises.ExerciseImage
import com.jaroszeek.gymtracker.feature.exercises.ExercisePicker
import com.jaroszeek.gymtracker.feature.exercises.ExercisesViewModel
import com.jaroszeek.gymtracker.navigation.sharedBounds
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val DRAGGED_SCALE = 1.03f
private const val WEIGHT_STEP_KG = 2.5
private const val REST_STEP_SECONDS = 15.0
private const val MAX_REPS = 100.0
private const val MAX_SETS = 20.0
private const val MAX_REST_SECONDS = 600.0

@Composable
fun RoutineEditorRoute(viewModel: RoutineEditorViewModel, pickerViewModel: ExercisesViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    BackHandler(enabled = !state.finished) { viewModel.onBack() }
    RoutineEditorScreen(
        state = state,
        onBack = viewModel::onBack,
        onNameChange = viewModel::onNameChange,
        onAddExercise = viewModel::onAddExercise,
        onAddVariant = viewModel::onAddVariant,
        onRemoveVariant = viewModel::onRemoveVariant,
        onMoveBlock = viewModel::onMoveBlock,
        onEditTargets = viewModel::onEditTargets,
        onTargetsChange = viewModel::onTargetsChange,
        onTargetsDone = viewModel::onTargetsDone,
        onSave = viewModel::onSave,
        onDiscardDismiss = viewModel::onDiscardDismiss,
        onDiscardConfirm = viewModel::onDiscardConfirm,
    )
    if (state.picker != null) ExercisePicker(pickerViewModel, onPick = viewModel::onPicked, onDismiss = viewModel::onPickerDismiss)
}

@Composable
fun RoutineEditorScreen(
    state: RoutineEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onAddExercise: () -> Unit,
    onAddVariant: (Long) -> Unit,
    onRemoveVariant: (blockId: Long, variantId: Long) -> Unit,
    onMoveBlock: (fromKey: Any, toKey: Any) -> Unit,
    onEditTargets: (blockId: Long, variantId: Long) -> Unit,
    onTargetsChange: (RoutineTargets) -> Unit,
    onTargetsDone: () -> Unit,
    onSave: () -> Unit,
    onDiscardDismiss: () -> Unit,
    onDiscardConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GtTheme.colors
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        onMoveBlock(from.key, to.key)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    val draft = state.draft
    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
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
                Text(
                    stringResource(if (state.routineId == null) R.string.routine_editor_new else R.string.routine_editor_edit),
                    style = GtTheme.typography.headline,
                )
            }
        }
        if (state.loading) return@LazyColumn
        item(key = "name") {
            GlassCard(Modifier.fillMaxWidth().then(state.routineId?.let { Modifier.sharedBounds(routineKey(it)) } ?: Modifier)) {
                GlassTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.label_routine_name),
                    placeholder = stringResource(R.string.routine_name_placeholder),
                    error = stringResource(R.string.routine_name_error).takeIf { state.showErrors && draft.name.isBlank() },
                )
            }
        }
        items(draft.blocks, key = { it.id }) { block ->
            ReorderableItem(reorderState, key = block.id) { dragging ->
                BlockCard(
                    block = block,
                    dragging = dragging,
                    onEditTargets = { variantId -> onEditTargets(block.id, variantId) },
                    onRemoveVariant = { variantId -> onRemoveVariant(block.id, variantId) },
                    onAddVariant = { onAddVariant(block.id) },
                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                )
            }
        }
        item(key = "add") {
            Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                GlassButton(
                    stringResource(R.string.routine_add_exercise),
                    onClick = onAddExercise,
                    icon = R.drawable.ic_add,
                    style = GlassButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.showErrors && draft.blocks.isEmpty()) {
                    Text(stringResource(R.string.routine_blocks_error), color = colors.danger, style = GtTheme.typography.labelSmall)
                }
            }
        }
        item(key = "save") {
            Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                if (state.saveFailed) Text(stringResource(R.string.routine_save_failed), color = colors.danger, style = GtTheme.typography.bodySmall)
                GlassButton(stringResource(R.string.action_save), onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    val edit = state.targetsEdit
    GlassBottomSheet(visible = edit != null, onDismissRequest = onTargetsDone) {
        if (edit != null) TargetsForm(edit.exercise, edit.targets, onTargetsChange, onTargetsDone)
    }
    GlassDialog(
        visible = state.confirmDiscard,
        onDismissRequest = onDiscardDismiss,
        title = stringResource(R.string.routine_discard_title),
        text = stringResource(R.string.routine_discard_text),
        confirmButton = { GlassButton(stringResource(R.string.action_discard), onClick = onDiscardConfirm) },
        dismissButton = { GlassButton(stringResource(R.string.action_keep_editing), onClick = onDiscardDismiss, style = GlassButtonStyle.Ghost) },
    )
}

@Composable
private fun ReorderableCollectionItemScope.BlockCard(
    block: RoutineBlock,
    dragging: Boolean,
    onEditTargets: (Long) -> Unit,
    onRemoveVariant: (Long) -> Unit,
    onAddVariant: () -> Unit,
    onDragStarted: () -> Unit,
) {
    val colors = GtTheme.colors
    val dragDescription = stringResource(R.string.routine_drag)
    GlassCard(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val scale = if (dragging) DRAGGED_SCALE else 1f
                scaleX = scale
                scaleY = scale
            },
        contentPadding = PaddingValues(GtSpacing.m),
    ) {
        block.variants.forEachIndexed { index, variant ->
            if (index > 0) {
                Text(stringResource(R.string.routine_or), color = colors.accentText, style = GtTheme.typography.labelSmall, modifier = Modifier.padding(start = GtSpacing.huge))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                if (index == 0) {
                    Icon(
                        painterResource(R.drawable.ic_drag_indicator),
                        contentDescription = dragDescription,
                        tint = colors.textTertiary,
                        modifier = Modifier
                            .draggableHandle(onDragStarted = { onDragStarted() })
                            .size(GtSizes.Icon),
                    )
                } else {
                    Box(Modifier.size(GtSizes.Icon))
                }
                Row(
                    Modifier
                        .weight(1f)
                        .clickable { onEditTargets(variant.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GtSpacing.m),
                ) {
                    ExerciseImage(variant.exercise.images.firstOrNull(), null, GtShapes.Small, Modifier.size(GtSizes.ThumbnailSmall))
                    Column(Modifier.weight(1f)) {
                        Text(variant.exercise.name, style = GtTheme.typography.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(variant.targets.summary(), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
                    }
                }
                GlassIconButton(
                    R.drawable.ic_close,
                    stringResource(R.string.routine_remove_variant, variant.exercise.name),
                    onClick = { onRemoveVariant(variant.id) },
                    style = GlassButtonStyle.Ghost,
                )
            }
        }
        GlassButton(
            stringResource(R.string.routine_add_variant),
            onClick = onAddVariant,
            style = GlassButtonStyle.Ghost,
            icon = R.drawable.ic_add,
            modifier = Modifier.padding(start = GtSpacing.xxxl),
        )
    }
}

@Composable
private fun TargetsForm(exercise: Exercise, targets: RoutineTargets, onChange: (RoutineTargets) -> Unit, onDone: () -> Unit) {
    val colors = GtTheme.colors
    Text(exercise.name, style = GtTheme.typography.headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        GlassNumberField(
            value = targets.sets.toDouble(),
            onValueChange = { onChange(targets.copy(sets = it?.toInt() ?: 0)) },
            label = stringResource(R.string.label_sets),
            modifier = Modifier.weight(1f),
            range = 0.0..MAX_SETS,
        )
        GlassNumberField(
            value = targets.weightKg,
            onValueChange = { onChange(targets.copy(weightKg = it)) },
            label = stringResource(R.string.label_weight),
            modifier = Modifier.weight(1f),
            step = WEIGHT_STEP_KG,
            decimals = 2,
            unit = stringResource(R.string.unit_kg),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        GlassNumberField(
            value = targets.repsMin.toDouble(),
            onValueChange = { onChange(targets.copy(repsMin = it?.toInt() ?: 0)) },
            label = stringResource(R.string.label_reps_min),
            modifier = Modifier.weight(1f),
            range = 0.0..MAX_REPS,
        )
        GlassNumberField(
            value = targets.repsMax.toDouble(),
            onValueChange = { onChange(targets.copy(repsMax = it?.toInt() ?: 0)) },
            label = stringResource(R.string.label_reps_max),
            modifier = Modifier.weight(1f),
            range = 0.0..MAX_REPS,
        )
    }
    GlassNumberField(
        value = targets.restSeconds?.toDouble(),
        onValueChange = { onChange(targets.copy(restSeconds = it?.toInt())) },
        label = stringResource(R.string.label_rest),
        modifier = Modifier.fillMaxWidth(),
        step = REST_STEP_SECONDS,
        unit = stringResource(R.string.unit_seconds),
        range = 0.0..MAX_REST_SECONDS,
    )
    Text(
        stringResource(R.string.targets_weight_hint) + "\n" + stringResource(R.string.targets_rest_hint),
        color = colors.textTertiary,
        style = GtTheme.typography.labelSmall,
    )
    GlassButton(stringResource(R.string.action_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
}

@Preview
@Composable
private fun RoutineEditorScreenPreview() {
    GymTrackerTheme {
        GlassScaffold {
            RoutineEditorScreen(
                state = RoutineEditorUiState(routineId = null, loading = false, draft = RoutineDraft(), showErrors = true),
                onBack = {}, onNameChange = {}, onAddExercise = {}, onAddVariant = {}, onRemoveVariant = { _, _ -> },
                onMoveBlock = { _, _ -> }, onEditTargets = { _, _ -> }, onTargetsChange = {}, onTargetsDone = {}, onSave = {},
                onDiscardDismiss = {}, onDiscardConfirm = {},
            )
        }
    }
}
