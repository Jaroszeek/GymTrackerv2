package com.jaroszeek.gymtracker.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.core.health.HealthConnect
import com.jaroszeek.gymtracker.core.health.HealthFeature
import com.jaroszeek.gymtracker.data.body.BodyRepository
import com.jaroszeek.gymtracker.data.health.HealthRepository
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.data.workout.WorkoutRepository
import com.jaroszeek.gymtracker.domain.body.BodyMeasurement
import com.jaroszeek.gymtracker.domain.body.BodyMetric
import com.jaroszeek.gymtracker.domain.body.BodySummary
import com.jaroszeek.gymtracker.domain.body.bodySummary
import com.jaroszeek.gymtracker.domain.health.HealthSession
import com.jaroszeek.gymtracker.domain.home.HomeLayout
import com.jaroszeek.gymtracker.domain.home.HomeWidget
import com.jaroszeek.gymtracker.domain.home.WeekStats
import com.jaroszeek.gymtracker.domain.home.proposedRoutine
import com.jaroszeek.gymtracker.domain.home.sessionDays
import com.jaroszeek.gymtracker.domain.home.weekStats
import com.jaroszeek.gymtracker.domain.home.weeklyStreak
import com.jaroszeek.gymtracker.domain.progress.Week
import com.jaroszeek.gymtracker.domain.routine.Routine
import com.jaroszeek.gymtracker.domain.workout.HeadlineRecord
import com.jaroszeek.gymtracker.domain.workout.WorkoutListItem
import com.jaroszeek.gymtracker.domain.workout.headlineRecords
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the quick start card offers (A5.1). */
sealed interface QuickStart {
    /** No routine yet: an empty workout, or making one. */
    data object NoRoutines : QuickStart

    data class Proposed(val routine: Routine) : QuickStart

    data class Running(val name: String, val startedAt: Long) : QuickStart
}

data class HomeUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val evening: Boolean = false,
    val layout: HomeLayout = HomeLayout(),
    val editing: Boolean = false,
    val quickStart: QuickStart = QuickStart.NoRoutines,
    val streak: Int = 0,
    val week: WeekStats = WeekStats(),
    val lastWorkout: WorkoutListItem? = null,
    val workoutDays: Set<LocalDate> = emptySet(),
    /** Days with only a session from Health Connect; none without it. */
    val sessionDays: Set<LocalDate> = emptySet(),
    val records: List<HeadlineRecord> = emptyList(),
    /** Steps of the last 7 days, today last; null while Health Connect gives none (the card offers to connect). */
    val steps: List<Long>? = null,
    val body: BodySummary = BodySummary(),
    /** Weights of the last 90 days, for the trend. */
    val weights: List<BodyMeasurement> = emptyList(),
    /** "Start" was refused: another workout is running. */
    val workoutRunning: Boolean = false,
    /** The workout started: the screen opens it. */
    val openWorkout: Boolean = false,
)

private data class Training(
    val history: List<WorkoutListItem>,
    val routines: List<Routine>,
    val running: QuickStart.Running?,
    val sessions: List<HealthSession>,
)

private data class Overlays(val workoutRunning: Boolean = false, val openWorkout: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
    routines: RoutineRepository,
    private val manager: ActiveWorkoutManager,
    private val health: HealthRepository,
    body: BodyRepository,
    private val preferences: AppPreferences,
    private val healthConnect: HealthConnect,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val now = MutableStateFlow(now())
    private val activityPermitted = MutableStateFlow(false)

    /** The layout being edited; null outside edit mode. */
    private val draft = MutableStateFlow<HomeLayout?>(null)
    private val overlays = MutableStateFlow(Overlays())

    private val training = combine(
        workouts.observeHistory(),
        routines.observeAll(),
        manager.workout.map { workout -> workout?.let { QuickStart.Running(it.name, it.startedAt) } }.distinctUntilChanged(),
        health.observeSessions(),
        ::Training,
    )
    private val layout = combine(preferences.homeLayout, draft, overlays, ::Triple)

    // Everything that depends on the date is looked up again when a new day comes
    val state: StateFlow<HomeUiState> = now.map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { today ->
        combine(
            training,
            workouts.observeRecordsSince(startOf(today.minusDays(RECORD_DAYS - 1))),
            combine(health.observeActivity(today.minusDays(STEP_DAYS - 1), today), activityPermitted, ::Pair),
            body.observeMeasurements(),
            combine(layout, now.map { it.hour !in DAY_HOURS }.distinctUntilChanged(), ::Pair),
        ) { training, records, (activity, permitted), measurements, (layout, evening) ->
            val (stored, edited, overlay) = layout
            val days = training.history.mapTo(HashSet()) { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            val weightsFrom = startOf(today.minusDays(TREND_DAYS - 1))
            HomeUiState(
                loading = false,
                today = today,
                evening = evening,
                layout = edited ?: HomeLayout.decode(stored),
                editing = edited != null,
                quickStart = quickStart(training),
                streak = weeklyStreak(days, today),
                week = weekStats(training.history, Week.of(today), zone),
                lastWorkout = training.history.firstOrNull(),
                workoutDays = days,
                sessionDays = sessionDays(training.history, training.sessions, zone),
                records = headlineRecords(records),
                steps = if (permitted || activity.any { it.steps != null }) {
                    (STEP_DAYS - 1 downTo 0).map { back -> activity.firstOrNull { it.day == today.minusDays(back) }?.steps ?: 0 }
                } else {
                    null
                },
                body = bodySummary(measurements),
                weights = measurements.filter { it.metric == BodyMetric.Weight && it.time >= weightsFrom },
                workoutRunning = overlay.workoutRunning,
                openWorkout = overlay.openWorkout,
            )
        }
    }
        .catch { e ->
            Log.e(TAG, "Loading Start failed", e)
            emit(HomeUiState(loading = false, error = true))
        }
        // The header is right from the first frame, so an evening start does not flash "Dzień dobry"
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            HomeUiState(today = now.value.toLocalDate(), evening = now.value.hour !in DAY_HOURS),
        )

    /** On every return to the screen: a new day or hour, or permissions changed in Health Connect. */
    fun onRefresh() {
        now.value = now()
        viewModelScope.launch {
            val granted = withContext(io) { healthConnect.grantedOrNone() }
            activityPermitted.value = HealthFeature.Activity.permissions.any { it in granted }
        }
    }

    // Edit mode: every change is saved at once, so leaving the app keeps it

    /** Only once the saved layout is loaded, or the first change would save the default over it. */
    fun onEdit() {
        val current = state.value
        if (!current.loading && !current.error) draft.update { current.layout }
    }

    fun onEditDone() = draft.update { null }

    fun onMove(from: HomeWidget, to: HomeWidget) = change { it.move(it.order.indexOf(from), it.order.indexOf(to)) }

    fun onToggle(widget: HomeWidget) = change { it.toggle(widget) }

    fun onResetLayout() = change { HomeLayout() }

    // Quick start, as on the Trening tab

    fun onStart(routine: Routine) = start { manager.start(routine) }

    /** [name] is the localized default name of a workout without a routine. */
    fun onStartEmpty(name: String) = start { manager.startEmpty(name) }

    fun onRunningDismiss() = overlays.update { it.copy(workoutRunning = false) }

    fun onOpenRunning() = overlays.update { it.copy(workoutRunning = false, openWorkout = true) }

    fun onWorkoutOpened() = overlays.update { it.copy(openWorkout = false) }

    private fun change(edit: (HomeLayout) -> HomeLayout) {
        val layout = edit(draft.value ?: return)
        draft.value = layout
        viewModelScope.launch { preferences.setHomeLayout(layout.encode()) }
    }

    private fun start(start: suspend () -> Boolean) {
        viewModelScope.launch {
            val started = try {
                start()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Starting a workout failed", e)
                return@launch
            }
            overlays.update { if (started) it.copy(openWorkout = true) else it.copy(workoutRunning = true) }
        }
    }

    private fun quickStart(training: Training): QuickStart {
        training.running?.let { return it }
        val id = proposedRoutine(training.routines.map { it.id }, training.history.map { it.routineId }) ?: return QuickStart.NoRoutines
        return QuickStart.Proposed(training.routines.first { it.id == id })
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock.withZone(zone))

    private fun startOf(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    private companion object {
        const val TAG = "Home"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val RECORD_DAYS = 7L
        const val STEP_DAYS = 7L
        const val TREND_DAYS = 90L

        /** "Dzień dobry" from 5:00, "Dobry wieczór" from 18:00. */
        val DAY_HOURS = 5..17
    }
}
