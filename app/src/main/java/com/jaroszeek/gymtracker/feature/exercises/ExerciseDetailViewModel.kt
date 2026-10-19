package com.jaroszeek.gymtracker.feature.exercises

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.data.exercise.DeleteResult
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.progress.ProgressRepository
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.designsystem.component.ChartRange
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.progress.ExerciseProgress
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the exercise's chart shows. */
enum class ExerciseMetric { Weight, E1rm, Reps }

sealed interface ExerciseDetailUiState {
    data object Loading : ExerciseDetailUiState

    data object NotFound : ExerciseDetailUiState

    data object Error : ExerciseDetailUiState

    data class Content(
        val exercise: Exercise,
        val confirmDelete: Boolean = false,
        /** Delete was refused because a routine uses the exercise. */
        val inUse: Boolean = false,
        val progress: ExerciseProgress = ExerciseProgress(emptyList(), emptyList()),
        /** Null until the user picks one: e1RM, or the weight or reps when there is no estimate. */
        val metric: ExerciseMetric? = null,
        val range: ChartRange = ChartRange.ThreeMonths,
    ) : ExerciseDetailUiState

    /** The exercise was deleted from this screen; the screen leaves. */
    data object Deleted : ExerciseDetailUiState
}

private data class Local(
    val confirmDelete: Boolean = false,
    val deleting: Boolean = false,
    val inUse: Boolean = false,
    val metric: ExerciseMetric? = null,
    val range: ChartRange = ChartRange.ThreeMonths,
)

@HiltViewModel(assistedFactory = ExerciseDetailViewModel.Factory::class)
class ExerciseDetailViewModel @AssistedInject constructor(
    @Assisted private val id: Long,
    private val repository: ExerciseRepository,
    progress: ProgressRepository,
    private val workouts: ActiveWorkoutManager,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val state: StateFlow<ExerciseDetailUiState> = combine(repository.observe(id), progress.observeExercise(id), local) { exercise, history, local ->
        when {
            // Leave only once the row is really gone, so the screen never flashes "not found"
            exercise == null && local.deleting -> ExerciseDetailUiState.Deleted
            exercise == null -> ExerciseDetailUiState.NotFound
            else -> ExerciseDetailUiState.Content(exercise, local.confirmDelete, local.inUse, history, local.metric, local.range)
        }
    }
        .catch { e ->
            Log.e(TAG, "Loading exercise $id failed", e)
            emit(ExerciseDetailUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ExerciseDetailUiState.Loading)

    fun onMetric(metric: ExerciseMetric) = local.update { it.copy(metric = metric) }

    fun onRange(range: ChartRange) = local.update { it.copy(range = range) }

    fun onDeleteRequest() = local.update { it.copy(confirmDelete = true) }

    fun onDeleteDismiss() = local.update { it.copy(confirmDelete = false) }

    fun onDeleteConfirm() {
        local.update { it.copy(confirmDelete = false, deleting = true) }
        viewModelScope.launch {
            // The running workout is not in the database yet, so it is asked separately
            if (workouts.uses(id) || repository.deleteCustom(id) == DeleteResult.InUse) {
                local.update { it.copy(deleting = false, inUse = true) }
            }
        }
    }

    fun onInUseDismiss() = local.update { it.copy(inUse = false) }

    @AssistedFactory
    interface Factory {
        fun create(id: Long): ExerciseDetailViewModel
    }

    private companion object {
        const val TAG = "Exercises"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
