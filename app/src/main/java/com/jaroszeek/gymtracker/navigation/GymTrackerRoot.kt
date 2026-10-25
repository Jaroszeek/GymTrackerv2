package com.jaroszeek.gymtracker.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jaroszeek.gymtracker.designsystem.component.GlassNavBar
import com.jaroszeek.gymtracker.designsystem.component.GlassNavItem
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.HideOnScrollState
import com.jaroszeek.gymtracker.designsystem.component.LocalChromeAboveNavBar
import com.jaroszeek.gymtracker.designsystem.theme.GtMotion
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.feature.diagnostics.LiveUpdateDiagnosticsRoute
import com.jaroszeek.gymtracker.feature.exercises.ExerciseDetailRoute
import com.jaroszeek.gymtracker.feature.exercises.ExerciseDetailViewModel
import com.jaroszeek.gymtracker.feature.exercises.ExerciseEditorRoute
import com.jaroszeek.gymtracker.feature.exercises.ExerciseEditorViewModel
import com.jaroszeek.gymtracker.feature.exercises.ExercisesRoute
import com.jaroszeek.gymtracker.feature.health.HealthConnectRoute
import com.jaroszeek.gymtracker.feature.health.ImportedSessionRoute
import com.jaroszeek.gymtracker.feature.health.ImportedSessionViewModel
import com.jaroszeek.gymtracker.feature.health.PrivacyPolicyScreen
import com.jaroszeek.gymtracker.feature.history.WorkoutDetailRoute
import com.jaroszeek.gymtracker.feature.history.WorkoutDetailViewModel
import com.jaroszeek.gymtracker.feature.history.WorkoutEditorRoute
import com.jaroszeek.gymtracker.feature.history.WorkoutEditorViewModel
import com.jaroszeek.gymtracker.feature.home.HomeRoute
import com.jaroszeek.gymtracker.feature.profile.ProfileRoute
import com.jaroszeek.gymtracker.feature.progress.ProgressRoute
import com.jaroszeek.gymtracker.feature.progress.RoutineProgressRoute
import com.jaroszeek.gymtracker.feature.progress.RoutineProgressViewModel
import com.jaroszeek.gymtracker.feature.routines.RoutineEditorRoute
import com.jaroszeek.gymtracker.feature.routines.RoutineEditorViewModel
import com.jaroszeek.gymtracker.feature.routines.RoutinesRoute
import com.jaroszeek.gymtracker.feature.routines.TemplatesRoute
import com.jaroszeek.gymtracker.feature.settings.LicensesRoute
import com.jaroszeek.gymtracker.feature.settings.SettingsRoute
import com.jaroszeek.gymtracker.feature.update.UpdatePrompt
import com.jaroszeek.gymtracker.feature.workout.NotificationPrompt
import com.jaroszeek.gymtracker.feature.workout.NotificationPromptViewModel
import com.jaroszeek.gymtracker.feature.workout.WorkoutBarViewModel
import com.jaroszeek.gymtracker.feature.workout.WorkoutMiniBar
import com.jaroszeek.gymtracker.feature.workout.WorkoutRoute

private val NavItems = TopLevelTab.entries.map { GlassNavItem(it.label, it.icon, it.selectedIcon) }

/**
 * App shell: content under the glass navbar, one back stack per tab. Every change of
 * [openWorkoutRequests] (a tap on the workout notification) brings the workout up.
 */
@Composable
fun GymTrackerRoot(openWorkoutRequests: Int = 0) {
    val navigator = rememberTabNavigator()
    LaunchedEffect(openWorkoutRequests) { if (openWorkoutRequests > 0) navigator.open(TopLevelTab.Workout, ActiveWorkoutKey) }
    val density = LocalDensity.current
    val hideOnScroll = remember(density) { HideOnScrollState(threshold = with(density) { GtSizes.HideBarThreshold.toPx() }) }
    // Every screen starts with the navbar: the one before may have scrolled it away, and a screen
    // that does not scroll (Start, Profil) could not bring it back
    val topKey = navigator.stacks.getValue(navigator.currentTab).lastOrNull()
    LaunchedEffect(topKey) { hideOnScroll.show() }

    val entryProvider = remember(navigator) { entryProvider(navigator) }
    val workoutBar: WorkoutBarViewModel = hiltViewModel()
    val workout by workoutBar.workout.collectAsStateWithLifecycle()
    val showWorkoutBar = workout != null && !navigator.isOnTop(ActiveWorkoutKey)
    // The mini bar rides above the navbar and drops to the bottom when the navbar hides
    val workoutBarLift by animateDpAsState(if (hideOnScroll.visible) GtSizes.NavBarHeight + GtSizes.NavBarGap else 0.dp, label = "workoutBarLift")
    // Measured: the mini bar grows with a large font, and the content must clear all of it
    var workoutBarHeight by remember { mutableStateOf(GtSizes.MiniBarHeight) }

    // Every stack is decorated (keeps its saved state and view models); only the visible ones go on screen
    val decorated = TopLevelTab.entries.associateWith { tab ->
        rememberDecoratedNavEntries(
            backStack = navigator.stacks.getValue(tab),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            entryProvider = entryProvider,
        )
    }

    GlassScaffold(
        modifier = Modifier.nestedScroll(hideOnScroll.connection),
        chrome = {
            AnimatedVisibility(
                visible = showWorkoutBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(GtMotion.Slide) { it } + fadeIn(tween(GtMotion.FADE_IN_MILLIS)),
                exit = slideOutVertically(GtMotion.Slide) { it } + fadeOut(tween(GtMotion.FADE_OUT_MILLIS)),
            ) {
                workout?.let {
                    WorkoutMiniBar(
                        workout = it,
                        onClick = { navigator.open(TopLevelTab.Workout, ActiveWorkoutKey) },
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = GtSizes.NavBarGap + workoutBarLift)
                            .onSizeChanged { workoutBarHeight = with(density) { it.height.toDp() } },
                    )
                }
            }
            AnimatedVisibility(
                visible = hideOnScroll.visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(GtMotion.Slide) { it } + fadeIn(tween(GtMotion.FADE_IN_MILLIS)),
                exit = slideOutVertically(GtMotion.Slide) { it } + fadeOut(tween(GtMotion.FADE_OUT_MILLIS)),
            ) {
                GlassNavBar(
                    items = NavItems,
                    selectedIndex = navigator.currentTab.ordinal,
                    onSelect = { index ->
                        navigator.select(TopLevelTab.entries[index])
                        hideOnScroll.show()
                    },
                )
            }
        },
    ) {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalChromeAboveNavBar provides if (showWorkoutBar) workoutBarHeight + GtSizes.NavBarGap else 0.dp,
            ) {
                NavDisplay(
                    entries = navigator.visibleTabs.flatMap { decorated.getValue(it) },
                    onBack = navigator::back,
                    sharedTransitionScope = this,
                    transitionSpec = { forward() },
                    popTransitionSpec = { backward() },
                    predictivePopTransitionSpec = { backward() },
                )
            }
        }
        // Inside the scaffold: dialogs need its overlay host. One at a time: the update waits for the notification question
        val notificationPrompt: NotificationPromptViewModel = hiltViewModel()
        val askingNotifications by notificationPrompt.visible.collectAsStateWithLifecycle()
        NotificationPrompt(notificationPrompt)
        if (!askingNotifications) UpdatePrompt()
    }
}

private fun entryProvider(navigator: TabNavigator): (NavKey) -> NavEntry<NavKey> = { key ->
    when (key) {
        ExercisesKey -> NavEntry(key) {
            ExercisesRoute(
                onOpen = { navigator.navigate(ExerciseDetailKey(it)) },
                onAdd = { navigator.navigate(ExerciseEditorKey(null)) },
            )
        }
        is ExerciseDetailKey -> NavEntry(key) {
            ExerciseDetailRoute(
                viewModel = hiltViewModel<ExerciseDetailViewModel, ExerciseDetailViewModel.Factory> { it.create(key.id) },
                onBack = navigator::back,
                onEdit = { navigator.navigate(ExerciseEditorKey(it)) },
            )
        }
        is ExerciseEditorKey -> NavEntry(key) {
            ExerciseEditorRoute(
                viewModel = hiltViewModel<ExerciseEditorViewModel, ExerciseEditorViewModel.Factory> { it.create(key.id) },
                onBack = navigator::back,
                onSaved = { navigator.back() },
            )
        }
        WorkoutKey -> NavEntry(key) {
            RoutinesRoute(
                onEdit = { navigator.navigate(RoutineEditorKey(it)) },
                onNew = { navigator.navigate(RoutineEditorKey(null)) },
                onTemplates = { navigator.navigate(TemplatesKey) },
                onOpenWorkout = { navigator.open(TopLevelTab.Workout, ActiveWorkoutKey) },
            )
        }
        ActiveWorkoutKey -> NavEntry(key) {
            WorkoutRoute(
                viewModel = hiltViewModel(),
                pickerViewModel = hiltViewModel(key = "exercisePicker"),
                onClose = navigator::back,
                onDiagnostics = { navigator.navigate(LiveUpdateDiagnosticsKey) },
            )
        }
        LiveUpdateDiagnosticsKey -> NavEntry(key) { LiveUpdateDiagnosticsRoute(viewModel = hiltViewModel(), onBack = navigator::back) }
        is RoutineEditorKey -> NavEntry(key) {
            RoutineEditorRoute(
                viewModel = hiltViewModel<RoutineEditorViewModel, RoutineEditorViewModel.Factory> { it.create(key.id) },
                pickerViewModel = hiltViewModel(key = "exercisePicker"),
                onClose = navigator::back,
            )
        }
        TemplatesKey -> NavEntry(key) { TemplatesRoute(onBack = navigator::back) }
        ProgressKey -> NavEntry(key) {
            ProgressRoute(
                onOpenWorkout = { navigator.navigate(WorkoutDetailKey(it)) },
                onOpenExercise = { navigator.navigate(ExerciseDetailKey(it)) },
                onOpenRoutine = { navigator.navigate(RoutineProgressKey(it)) },
                onOpenSession = { navigator.navigate(ImportedSessionKey(it)) },
            )
        }
        is ImportedSessionKey -> NavEntry(key) {
            ImportedSessionRoute(
                viewModel = hiltViewModel<ImportedSessionViewModel, ImportedSessionViewModel.Factory> { it.create(key.id) },
                onBack = navigator::back,
            )
        }
        is WorkoutDetailKey -> NavEntry(key) {
            WorkoutDetailRoute(
                viewModel = hiltViewModel<WorkoutDetailViewModel, WorkoutDetailViewModel.Factory> { it.create(key.id) },
                onBack = navigator::back,
                onEdit = { navigator.navigate(WorkoutEditorKey(it)) },
                onOpenExercise = { navigator.navigate(ExerciseDetailKey(it)) },
            )
        }
        is WorkoutEditorKey -> NavEntry(key) {
            WorkoutEditorRoute(
                viewModel = hiltViewModel<WorkoutEditorViewModel, WorkoutEditorViewModel.Factory> { it.create(key.id) },
                pickerViewModel = hiltViewModel(key = "exercisePicker"),
                onClose = navigator::back,
            )
        }
        is RoutineProgressKey -> NavEntry(key) {
            RoutineProgressRoute(
                viewModel = hiltViewModel<RoutineProgressViewModel, RoutineProgressViewModel.Factory> { it.create(key.id) },
                onBack = navigator::back,
                onOpenWorkout = { navigator.navigate(WorkoutDetailKey(it)) },
            )
        }
        HomeKey -> NavEntry(key) {
            HomeRoute(
                onSettings = { navigator.navigate(SettingsKey) },
                onOpenWorkout = { navigator.open(TopLevelTab.Workout, ActiveWorkoutKey) },
                onChooseRoutine = { navigator.select(TopLevelTab.Workout) },
                onOpenWorkoutDetail = { navigator.navigate(WorkoutDetailKey(it)) },
                onConnect = { navigator.open(TopLevelTab.Profile, HealthConnectKey) },
                onProfile = { navigator.select(TopLevelTab.Profile) },
                onProgress = { navigator.select(TopLevelTab.Progress) },
            )
        }
        ProfileKey -> NavEntry(key) {
            ProfileRoute(onSettings = { navigator.navigate(SettingsKey) }, onHealthConnect = { navigator.navigate(HealthConnectKey) })
        }
        SettingsKey -> NavEntry(key) {
            SettingsRoute(
                viewModel = hiltViewModel(),
                onBack = navigator::back,
                onHealthConnect = { navigator.navigate(HealthConnectKey) },
                onDiagnostics = { navigator.navigate(LiveUpdateDiagnosticsKey) },
                onLicenses = { navigator.navigate(LicensesKey) },
            )
        }
        LicensesKey -> NavEntry(key) { LicensesRoute(viewModel = hiltViewModel(), onBack = navigator::back) }
        HealthConnectKey -> NavEntry(key) {
            HealthConnectRoute(viewModel = hiltViewModel(), onBack = navigator::back, onPrivacy = { navigator.navigate(PrivacyPolicyKey) })
        }
        PrivacyPolicyKey -> NavEntry(key) { PrivacyPolicyScreen(onBack = navigator::back) }
        else -> error("No screen for $key")
    }
}

// Transitions as in vynox: a short slide with a fade
private fun forward(): ContentTransform =
    (slideInHorizontally(GtMotion.Page) { it / 5 } + fadeIn(tween(GtMotion.FADE_IN_MILLIS))) togetherWith fadeOut(tween(GtMotion.FADE_OUT_MILLIS))

private fun backward(): ContentTransform =
    fadeIn(tween(GtMotion.FADE_IN_MILLIS)) togetherWith (slideOutHorizontally(GtMotion.Page) { it / 5 } + fadeOut(tween(GtMotion.FADE_OUT_MILLIS)))
