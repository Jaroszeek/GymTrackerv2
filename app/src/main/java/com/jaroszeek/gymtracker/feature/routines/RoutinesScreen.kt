package com.jaroszeek.gymtracker.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.routine.Routine
import com.jaroszeek.gymtracker.navigation.sharedBounds

private const val PREVIEW_EXERCISES = 3

@Composable
fun RoutinesRoute(
    onEdit: (Long) -> Unit,
    onNew: () -> Unit,
    onTemplates: () -> Unit,
    onOpenWorkout: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.openWorkout) {
        if (state.openWorkout) {
            viewModel.onWorkoutOpened()
            onOpenWorkout()
        }
    }
    val copyName = stringResource(R.string.routine_copy_name, state.actionsFor?.name.orEmpty())
    val emptyName = stringResource(R.string.workout_default_name)
    RoutinesScreen(
        state = state,
        onOpen = { onEdit(it.id) },
        onNew = onNew,
        onTemplates = onTemplates,
        onStart = viewModel::onStart,
        onStartEmpty = { viewModel.onStartEmpty(emptyName) },
        onMore = viewModel::onMore,
        onActionsDismiss = viewModel::onActionsDismiss,
        onDuplicate = { viewModel.onDuplicate(it, copyName) },
        onDeleteRequest = viewModel::onDeleteRequest,
        onDeleteDismiss = viewModel::onDeleteDismiss,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        onRunningDismiss = viewModel::onRunningDismiss,
        onOpenRunning = viewModel::onOpenRunning,
    )
}

@Composable
fun RoutinesScreen(
    state: RoutinesUiState,
    onOpen: (Routine) -> Unit,
    onNew: () -> Unit,
    onTemplates: () -> Unit,
    onStart: (Routine) -> Unit,
    onStartEmpty: () -> Unit,
    onMore: (Routine) -> Unit,
    onActionsDismiss: () -> Unit,
    onDuplicate: (Routine) -> Unit,
    onDeleteRequest: (Routine) -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onRunningDismiss: () -> Unit,
    onOpenRunning: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(GtSpacing.l),
    ) {
        item(key = "header") {
            Text(stringResource(R.string.nav_workout), Modifier.semantics { heading() }, style = GtTheme.typography.display)
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                // Equal halves; with a large font the labels wrap and both pills take the taller height
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(GtSpacing.s),
                ) {
                    GlassButton(
                        stringResource(R.string.routines_new),
                        onClick = onNew,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = R.drawable.ic_add,
                    )
                    GlassButton(
                        stringResource(R.string.routines_templates),
                        onClick = onTemplates,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        style = GlassButtonStyle.Secondary,
                    )
                }
                GlassButton(
                    stringResource(R.string.routines_start_empty),
                    onClick = onStartEmpty,
                    modifier = Modifier.fillMaxWidth(),
                    icon = R.drawable.ic_play_arrow,
                    style = GlassButtonStyle.Ghost,
                )
            }
        }
        when {
            state.loading -> Unit
            state.error -> item(key = "error") { Message(stringResource(R.string.exercises_error)) }
            state.routines.isEmpty() -> item(key = "empty") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.routines_empty_title), style = GtTheme.typography.title)
                    Text(stringResource(R.string.routines_empty_text), color = GtTheme.colors.textSecondary, style = GtTheme.typography.body)
                }
            }
            else -> items(state.routines, key = { it.id }) { routine ->
                RoutineCard(routine, onOpen = { onOpen(routine) }, onStart = { onStart(routine) }, onMore = { onMore(routine) })
            }
        }
    }

    val actionsFor = state.actionsFor
    GlassBottomSheet(visible = actionsFor != null, onDismissRequest = onActionsDismiss) {
        if (actionsFor != null) {
            Text(actionsFor.name, style = GtTheme.typography.headline)
            GlassButton(stringResource(R.string.action_edit), onClick = { onActionsDismiss(); onOpen(actionsFor) }, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
            GlassButton(stringResource(R.string.action_duplicate), onClick = { onDuplicate(actionsFor) }, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
            GlassButton(stringResource(R.string.action_delete), onClick = { onDeleteRequest(actionsFor) }, style = GlassButtonStyle.Ghost, modifier = Modifier.fillMaxWidth())
        }
    }
    val confirmDeleteFor = state.confirmDeleteFor
    GlassDialog(
        visible = confirmDeleteFor != null,
        onDismissRequest = onDeleteDismiss,
        title = stringResource(R.string.delete_routine_title),
        text = stringResource(R.string.delete_routine_text, confirmDeleteFor?.name.orEmpty()),
        confirmButton = { GlassButton(stringResource(R.string.action_delete), onClick = onDeleteConfirm) },
        dismissButton = { GlassButton(stringResource(R.string.action_cancel), onClick = onDeleteDismiss, style = GlassButtonStyle.Ghost) },
    )
    GlassDialog(
        visible = state.workoutRunning,
        onDismissRequest = onRunningDismiss,
        title = stringResource(R.string.workout_running_title),
        text = stringResource(R.string.workout_running_text),
        confirmButton = { GlassButton(stringResource(R.string.action_open_workout), onClick = onOpenRunning) },
        dismissButton = { GlassButton(stringResource(R.string.action_cancel), onClick = onRunningDismiss, style = GlassButtonStyle.Ghost) },
    )
}

@Composable
private fun RoutineCard(routine: Routine, onOpen: () -> Unit, onStart: () -> Unit, onMore: () -> Unit) {
    val colors = GtTheme.colors
    GlassCard(
        Modifier
            .fillMaxWidth()
            .sharedBounds(routineKey(routine.id))
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        Text(routine.name, style = GtTheme.typography.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(
                R.string.routine_meta,
                pluralStringResource(R.plurals.routine_exercises, routine.blocks.size, routine.blocks.size),
                pluralStringResource(R.plurals.routine_sets, routine.totalSets, routine.totalSets),
            ),
            color = colors.textSecondary,
            style = GtTheme.typography.label,
        )
        Text(
            routine.blocks.take(PREVIEW_EXERCISES).joinToString(", ") { it.variants.first().exercise.name } +
                if (routine.blocks.size > PREVIEW_EXERCISES) "…" else "",
            color = colors.textTertiary,
            style = GtTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = GtSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(stringResource(R.string.action_start), onClick = onStart, icon = R.drawable.ic_play_arrow)
            Box(Modifier.weight(1f))
            GlassIconButton(R.drawable.ic_more_horiz, stringResource(R.string.action_more), onClick = onMore, style = GlassButtonStyle.Ghost)
        }
    }
}

@Composable
private fun Message(text: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(text, color = GtTheme.colors.textSecondary, style = GtTheme.typography.body)
    }
}

internal fun routineKey(id: Long) = "routine-$id"

@Preview
@Composable
private fun RoutinesScreenEmptyPreview() {
    GymTrackerTheme {
        GlassScaffold {
            RoutinesScreen(
                state = RoutinesUiState(loading = false),
                onOpen = {}, onNew = {}, onTemplates = {}, onStart = {}, onStartEmpty = {}, onMore = {}, onActionsDismiss = {}, onDuplicate = {},
                onDeleteRequest = {}, onDeleteDismiss = {}, onDeleteConfirm = {}, onRunningDismiss = {}, onOpenRunning = {},
            )
        }
    }
}
