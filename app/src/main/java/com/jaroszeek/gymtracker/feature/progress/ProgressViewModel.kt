package com.jaroszeek.gymtracker.feature.progress

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.data.progress.ProgressRepository
import com.jaroszeek.gymtracker.data.workout.WorkoutRepository
import com.jaroszeek.gymtracker.domain.exercise.BodyPart
import com.jaroszeek.gymtracker.domain.progress.ExerciseHistory
import com.jaroszeek.gymtracker.domain.progress.RoutineHistory
import com.jaroszeek.gymtracker.domain.progress.Week
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

data class ProgressUiState(
    val tab: ProgressTab = ProgressTab.History,
    val loading: Boolean = true,
    val error: Boolean = false,
    val history: List<WorkoutListItem> = emptyList(),
    val exercises: List<ExerciseHistory> = emptyList(),
    val routines: List<RoutineHistory> = emptyList(),
    val week: Week = Week.of(LocalDate.now()),
    val weekSets: Map<BodyPart, Int> = emptyMap(),
    /** No week after the current one. */
    val isCurrentWeek: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    workouts: WorkoutRepository,
    progress: ProgressRepository,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val currentWeek = Week.of(LocalDate.now(zone))
    private val tab = MutableStateFlow(ProgressTab.History)
    private val week = MutableStateFlow(currentWeek)

    private val weekSets = week.flatMapLatest { week -> progress.observeWeek(week, zone).map { week to it } }

    val state: StateFlow<ProgressUiState> =
        combine(tab, workouts.observeHistory(), progress.observeExercises(), progress.observeRoutines(), weekSets) { tab, history, exercises, routines, (week, sets) ->
            ProgressUiState(
                tab = tab,
                loading = false,
                history = history,
                exercises = exercises,
                routines = routines,
                week = week,
                weekSets = sets,
                isCurrentWeek = week == currentWeek,
            )
        }
            .catch { e ->
                Log.e(TAG, "Loading progress failed", e)
                emit(ProgressUiState(tab = tab.value, loading = false, error = true))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProgressUiState())

    fun onTab(tab: ProgressTab) = this.tab.update { tab }

    fun onPreviousWeek() = week.update { it.previous() }

    fun onNextWeek() = week.update { if (it == currentWeek) it else it.next() }

    private companion object {
        const val TAG = "Progress"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
