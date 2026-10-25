package com.jaroszeek.gymtracker.feature.progress

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.data.health.HealthRepository
import com.jaroszeek.gymtracker.data.progress.ProgressRepository
import com.jaroszeek.gymtracker.data.workout.WorkoutRepository
import com.jaroszeek.gymtracker.domain.health.HealthSession
import com.jaroszeek.gymtracker.domain.health.notLinkedTo
import com.jaroszeek.gymtracker.domain.progress.ExerciseHistory
import com.jaroszeek.gymtracker.domain.progress.RoutineHistory
import com.jaroszeek.gymtracker.domain.progress.Week
import com.jaroszeek.gymtracker.domain.progress.WeekSets
import com.jaroszeek.gymtracker.domain.workout.WorkoutListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class ProgressTab { History, Exercises, Routines, Muscles }

/** A row of the history: a workout, or a session another app wrote to Health Connect (A6). */
sealed interface HistoryEntry {
    val startedAt: Long

    data class Workout(val item: WorkoutListItem) : HistoryEntry {
        override val startedAt: Long get() = item.startedAt
    }

    data class Session(val session: HealthSession) : HistoryEntry {
        override val startedAt: Long get() = session.startTime
    }
}

data class ProgressUiState(
    val tab: ProgressTab = ProgressTab.History,
    val loading: Boolean = true,
    val error: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val exercises: List<ExerciseHistory> = emptyList(),
    val routines: List<RoutineHistory> = emptyList(),
    val week: Week = Week.of(LocalDate.now()),
    val weekSets: WeekSets = WeekSets(),
    /** No week after the current one. */
    val isCurrentWeek: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    workouts: WorkoutRepository,
    progress: ProgressRepository,
    health: HealthRepository,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val tab = MutableStateFlow(ProgressTab.History)

    /** The week picked with the arrows; null follows the current one. */
    private val week = MutableStateFlow<Week?>(null)

    // The current week is looked up on every subscription, so a screen kept overnight moves on to a new week
    private val weekSets = week.flatMapLatest { picked ->
        val current = currentWeek()
        val shown = picked ?: current
        progress.observeWeek(shown, zone).map { Triple(shown, it, shown == current) }
    }

    // A session linked to a workout shows inside it, not as a row of its own. A link to a session no longer
    // imported (deleted in its app, or after disconnecting) counts as none, as in the workout's details
    private val history = combine(workouts.observeHistory(), health.observeSessions()) { workouts, sessions ->
        val imported = sessions.mapTo(HashSet()) { it.id }
        val rows = workouts.map { HistoryEntry.Workout(it.copy(healthSessionId = it.healthSessionId?.takeIf(imported::contains))) }
        (rows + sessions.notLinkedTo(workouts).map(HistoryEntry::Session)).sortedByDescending { it.startedAt }
    }

    val state: StateFlow<ProgressUiState> =
        combine(tab, history, progress.observeExercises(), progress.observeRoutines(), weekSets) { tab, history, exercises, routines, (week, sets, isCurrent) ->
            ProgressUiState(
                tab = tab,
                loading = false,
                history = history,
                exercises = exercises,
                routines = routines,
                week = week,
                weekSets = sets,
                isCurrentWeek = isCurrent,
            )
        }
            .catch { e ->
                Log.e(TAG, "Loading progress failed", e)
                emit(ProgressUiState(tab = tab.value, loading = false, error = true))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProgressUiState())

    fun onTab(tab: ProgressTab) = this.tab.update { tab }

    fun onPreviousWeek() = week.update { (it ?: currentWeek()).previous() }

    fun onNextWeek() = week.update { it?.next()?.takeIf { next -> next.monday < currentWeek().monday } }

    private fun currentWeek() = Week.of(LocalDate.now(zone))

    private companion object {
        const val TAG = "Progress"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
