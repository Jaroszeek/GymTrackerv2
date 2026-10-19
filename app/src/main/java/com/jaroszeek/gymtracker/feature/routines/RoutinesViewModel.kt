package com.jaroszeek.gymtracker.feature.routines

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.domain.routine.Routine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutinesUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val routines: List<Routine> = emptyList(),
    /** Routine whose action sheet (edit, duplicate, delete) is open. */
    val actionsFor: Routine? = null,
    val confirmDeleteFor: Routine? = null,
    /** "Start" was refused: another workout is running. */
    val workoutRunning: Boolean = false,
    /** The workout started (or the running one was asked for): the screen opens it. */
    val openWorkout: Boolean = false,
)

private data class Overlays(
    val actionsFor: Routine? = null,
    val confirmDeleteFor: Routine? = null,
    val workoutRunning: Boolean = false,
    val openWorkout: Boolean = false,
)

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val workouts: ActiveWorkoutManager,
) : ViewModel() {

    private val overlays = MutableStateFlow(Overlays())

    val state: StateFlow<RoutinesUiState> = combine(
        repository.observeAll().map { Result.success(it) }.catch { e ->
            Log.e(TAG, "Loading routines failed", e)
            emit(Result.failure(e))
        },
        overlays,
    ) { routines, overlays ->
        RoutinesUiState(
            loading = false,
            error = routines.isFailure,
            routines = routines.getOrDefault(emptyList()),
            actionsFor = overlays.actionsFor,
            confirmDeleteFor = overlays.confirmDeleteFor,
            workoutRunning = overlays.workoutRunning,
            openWorkout = overlays.openWorkout,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RoutinesUiState())

    fun onMore(routine: Routine) = overlays.update { it.copy(actionsFor = routine) }

    fun onActionsDismiss() = overlays.update { it.copy(actionsFor = null) }

    fun onDuplicate(routine: Routine, name: String) {
        overlays.update { it.copy(actionsFor = null) }
        viewModelScope.launch { repository.duplicate(routine.id, name) }
    }

    fun onDeleteRequest(routine: Routine) = overlays.update { it.copy(actionsFor = null, confirmDeleteFor = routine) }

    fun onDeleteDismiss() = overlays.update { it.copy(confirmDeleteFor = null) }

    fun onDeleteConfirm() {
        val routine = overlays.value.confirmDeleteFor ?: return
        overlays.update { it.copy(confirmDeleteFor = null) }
        viewModelScope.launch { repository.delete(routine.id) }
    }

    fun onStart(routine: Routine) = start { workouts.start(routine) }

    /** [name] is the localized default name of a workout without a routine. */
    fun onStartEmpty(name: String) = start { workouts.startEmpty(name) }

    fun onRunningDismiss() = overlays.update { it.copy(workoutRunning = false) }

    fun onOpenRunning() = overlays.update { it.copy(workoutRunning = false, openWorkout = true) }

    fun onWorkoutOpened() = overlays.update { it.copy(openWorkout = false) }

    private fun start(start: suspend () -> Boolean) {
        viewModelScope.launch {
            val started = try {
                start()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Starting a workout failed", e)
                return@launch
            }
            overlays.update { if (started) it.copy(openWorkout = true) else it.copy(workoutRunning = true) }
        }
    }

    private companion object {
        const val TAG = "Routines"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
