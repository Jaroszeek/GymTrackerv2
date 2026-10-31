package com.jaroszeek.gymtracker.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
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
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassDialog
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.GlassSwitchRow
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.component.reorderActions
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.home.HomeLayout
import com.jaroszeek.gymtracker.domain.home.HomeWidget
import com.jaroszeek.gymtracker.domain.home.WeekStats
import com.jaroszeek.gymtracker.domain.routine.Routine
import com.jaroszeek.gymtracker.feature.health.ReadinessRoute
import com.jaroszeek.gymtracker.feature.progress.todayLabel
import java.time.LocalDate
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val DRAGGED_SCALE = 1.03f

@Composable
fun HomeRoute(
    onSettings: () -> Unit,
    onOpenWorkout: () -> Unit,
    onChooseRoutine: () -> Unit,
    onOpenWorkoutDetail: (Long) -> Unit,
    onConnect: () -> Unit,
    onProfile: () -> Unit,
    onProgress: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.editing, onBack = viewModel::onEditDone)
    LifecycleResumeEffect(viewModel) {
        viewModel.onRefresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.openWorkout) {
        if (state.openWorkout) {
            viewModel.onWorkoutOpened()
            onOpenWorkout()
        }
    }
    val emptyName = stringResource(R.string.workout_default_name)
    HomeScreen(
        state = state,
        onEdit = viewModel::onEdit,
        onEditDone = viewModel::onEditDone,
        onMove = viewModel::onMove,
        onToggle = viewModel::onToggle,
        onResetLayout = viewModel::onResetLayout,
        onSettings = onSettings,
        onStart = viewModel::onStart,
        onStartEmpty = { viewModel.onStartEmpty(emptyName) },
        onOpenWorkout = onOpenWorkout,
        onChooseRoutine = onChooseRoutine,
        onOpenWorkoutDetail = onOpenWorkoutDetail,
        onConnect = onConnect,
        onProfile = onProfile,
        onProgress = onProgress,
        onRunningDismiss = viewModel::onRunningDismiss,
        onOpenRunning = viewModel::onOpenRunning,
        readiness = { ReadinessRoute(onConnect = onConnect) },
    )
}

/** Start (A5.1): the greeting and the widgets, quick start among them, in the saved order; edit mode reorders and hides them. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEdit: () -> Unit,
    onEditDone: () -> Unit,
    onMove: (HomeWidget, HomeWidget) -> Unit,
    onToggle: (HomeWidget) -> Unit,
    onResetLayout: () -> Unit,
    onSettings: () -> Unit,
    onStart: (Routine) -> Unit,
    onStartEmpty: () -> Unit,
    onOpenWorkout: () -> Unit,
    onChooseRoutine: () -> Unit,
    onOpenWorkoutDetail: (Long) -> Unit,
    onConnect: () -> Unit,
    onProfile: () -> Unit,
    onProgress: () -> Unit,
    onRunningDismiss: () -> Unit,
    onOpenRunning: () -> Unit,
    modifier: Modifier = Modifier,
    /** The readiness card loads its own data (Etap 7). */
    readiness: @Composable () -> Unit = {},
) {
    val colors = GtTheme.colors
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val widgets = HomeWidget.entries.associateBy { it.key }
        val moved = widgets[from.key]
        val target = widgets[to.key]
        if (moved != null && target != null) {
            onMove(moved, target)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = GtSpacing.xl,
            end = GtSpacing.xl,
            top = insets.calculateTopPadding() + GtSpacing.xxl,
            bottom = navBarContentPadding().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.l),
    ) {
        if (state.editing) {
            item(key = "edit-header") { EditHeader(onDone = onEditDone) }
            items(state.layout.order, key = { it.key }) { widget ->
                val order = state.layout.order
                val index = order.indexOf(widget)
                val moves = reorderActions(
                    onMoveUp = order.getOrNull(index - 1)?.let { above -> { onMove(widget, above) } },
                    onMoveDown = order.getOrNull(index + 1)?.let { below -> { onMove(widget, below) } },
                )
                ReorderableItem(reorderState, key = widget.key) { dragging ->
                    GlassCard(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val scale = if (dragging) DRAGGED_SCALE else 1f
                                scaleX = scale
                                scaleY = scale
                            },
                        contentPadding = PaddingValues(horizontal = GtSpacing.l, vertical = GtSpacing.xs),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                            Icon(
                                painterResource(R.drawable.ic_drag_indicator),
                                contentDescription = stringResource(R.string.routine_drag),
                                tint = colors.textTertiary,
                                modifier = Modifier
                                    .draggableHandle(onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) })
                                    .semantics { customActions = moves }
                                    .size(GtSizes.Icon),
                            )
                            GlassSwitchRow(
                                title = stringResource(widget.label()),
                                checked = widget !in state.layout.hidden,
                                onCheckedChange = { onToggle(widget) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            item(key = "edit-reset") {
                GlassButton(
                    stringResource(R.string.home_edit_reset),
                    onClick = onResetLayout,
                    style = GlassButtonStyle.Ghost,
                    enabled = state.layout != HomeLayout(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@LazyColumn
        }

        item(key = "header") { Header(state.today, state.evening, onEdit.takeUnless { state.loading || state.error }, onSettings) }
        when {
            state.loading -> return@LazyColumn
            state.error -> {
                item(key = "error") { Message(stringResource(R.string.progress_error)) }
                return@LazyColumn
            }
        }
        items(state.layout.visible, key = { it.key }) { widget ->
            when (widget) {
                HomeWidget.QuickStart -> QuickStartCard(state.quickStart, onStart, onStartEmpty, onOpenWorkout, onChooseRoutine)
                HomeWidget.Hero -> HeroCard(state.streak, state.week, onClick = onProgress)
                HomeWidget.LastWorkout -> LastWorkoutCard(state.lastWorkout, onOpen = onOpenWorkoutDetail, onStart = onChooseRoutine)
                HomeWidget.Readiness -> readiness()
                HomeWidget.Records -> RecordsCard(state.records, onOpen = onOpenWorkoutDetail)
                HomeWidget.Calendar -> ActivityCalendarCard(state.workoutDays, state.sessionDays, state.today)
                HomeWidget.Steps -> StepsCard(state.steps, state.today, onConnect = onConnect)
                HomeWidget.Body -> BodyCard(state.body, state.weights, onOpen = onProfile)
            }
        }
        if (state.layout.visible.isEmpty()) {
            item(key = "all-hidden") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_all_hidden), color = colors.textSecondary, style = GtTheme.typography.body)
                    GlassButton(stringResource(R.string.home_edit_layout), onClick = onEdit, style = GlassButtonStyle.Secondary, icon = R.drawable.ic_edit)
                }
            }
        }
    }

    GlassDialog(
        visible = state.workoutRunning,
        onDismissRequest = onRunningDismiss,
        title = stringResource(R.string.workout_running_title),
        text = stringResource(R.string.workout_running_text),
        confirmButton = { GlassButton(stringResource(R.string.action_open_workout), onClick = onOpenRunning) },
        dismissButton = { GlassButton(stringResource(R.string.action_cancel), onClick = onRunningDismiss, style = GlassButtonStyle.Ghost) },
    )
}

/** The greeting with today's date, without an app bar (A5.1); no layout editing until the layout is loaded. */
@Composable
private fun Header(today: LocalDate, evening: Boolean, onEdit: (() -> Unit)?, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(if (evening) R.string.home_greeting_evening else R.string.home_greeting_day), Modifier.semantics { heading() }, style = GtTheme.typography.display)
            Text(todayLabel(today), color = GtTheme.colors.textSecondary, style = GtTheme.typography.label)
        }
        if (onEdit != null) GlassIconButton(R.drawable.ic_edit, stringResource(R.string.home_edit_layout), onClick = onEdit, style = GlassButtonStyle.Ghost)
        GlassIconButton(R.drawable.ic_settings, stringResource(R.string.settings_title), onClick = onSettings, style = GlassButtonStyle.Ghost)
    }
}

@Composable
private fun EditHeader(onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_edit_title), Modifier.weight(1f).semantics { heading() }, style = GtTheme.typography.displaySmall)
            GlassButton(stringResource(R.string.action_done), onClick = onDone, icon = R.drawable.ic_check)
        }
        Text(stringResource(R.string.home_edit_text), color = GtTheme.colors.textSecondary, style = GtTheme.typography.bodySmall)
    }
}

@Composable
private fun Message(text: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(text, color = GtTheme.colors.textSecondary, style = GtTheme.typography.body)
    }
}

private fun HomeWidget.label(): Int = when (this) {
    HomeWidget.QuickStart -> R.string.home_quick_title
    HomeWidget.Hero -> R.string.home_widget_hero
    HomeWidget.LastWorkout -> R.string.home_widget_last_workout
    HomeWidget.Readiness -> R.string.home_widget_readiness
    HomeWidget.Records -> R.string.home_widget_records
    HomeWidget.Calendar -> R.string.home_widget_calendar
    HomeWidget.Steps -> R.string.home_widget_steps
    HomeWidget.Body -> R.string.home_widget_body
}

@Composable
private fun PreviewScreen(state: HomeUiState) {
    GymTrackerTheme {
        GlassScaffold {
            HomeScreen(
                state = state,
                onEdit = {}, onEditDone = {}, onMove = { _, _ -> }, onToggle = {}, onResetLayout = {}, onSettings = {}, onStart = {}, onStartEmpty = {},
                onOpenWorkout = {}, onChooseRoutine = {}, onOpenWorkoutDetail = {}, onConnect = {}, onProfile = {}, onProgress = {},
                onRunningDismiss = {}, onOpenRunning = {},
            )
        }
    }
}

@Preview
@Composable
private fun HomeScreenEmptyPreview() {
    PreviewScreen(HomeUiState(loading = false, today = LocalDate.of(2026, 10, 20)))
}

@Preview
@Composable
private fun HomeScreenEditPreview() {
    PreviewScreen(HomeUiState(loading = false, editing = true, layout = HomeLayout().toggle(HomeWidget.Steps), week = WeekStats()))
}
