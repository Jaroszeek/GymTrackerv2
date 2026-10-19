package com.jaroszeek.gymtracker.feature.exercises

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.data.exercise.DeleteResult
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.domain.exercise.Exercise
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

sealed interface ExerciseDetailUiState {
    data object Loading : ExerciseDetailUiState

    data object NotFound : ExerciseDetailUiState

    data object Error : ExerciseDetailUiState

    data class Content(
        val exercise: Exercise,
        val confirmDelete: Boolean = false,
        /** Delete was refused because a routine uses the exercise. */
        val inUse: Boolean = false,
    ) : ExerciseDetailUiState

    /** The exercise was deleted from this screen; the screen leaves. */
    data object Deleted : ExerciseDetailUiState
}

@HiltViewModel(assistedFactory = ExerciseDetailViewModel.Factory::class)
class ExerciseDetailViewModel @AssistedInject constructor(
    @Assisted private val id: Long,
    private val repository: ExerciseRepository,
    private val workouts: ActiveWorkoutManager,
) : ViewModel() {

    private val confirmDelete = MutableStateFlow(false)
    private val deleting = MutableStateFlow(false)
    private val inUse = MutableStateFlow(false)

    val state: StateFlow<ExerciseDetailUiState> = combine(repository.observe(id), confirmDelete, deleting, inUse) { exercise, confirm, deleting, inUse ->
        when {
            // Leave only once the row is really gone, so the screen never flashes "not found"
            exercise == null && deleting -> ExerciseDetailUiState.Deleted
            exercise == null -> ExerciseDetailUiState.NotFound
            else -> ExerciseDetailUiState.Content(exercise, confirm, inUse)
        }
    }
        .catch { e ->
            Log.e(TAG, "Loading exercise $id failed", e)
            emit(ExerciseDetailUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ExerciseDetailUiState.Loading)

    fun onDeleteRequest() = confirmDelete.update { true }

    fun onDeleteDismiss() = confirmDelete.update { false }

    fun onDeleteConfirm() {
        confirmDelete.update { false }
        deleting.update { true }
        viewModelScope.launch {
            // The running workout is not in the database yet, so it is asked separately
            if (workouts.uses(id) || repository.deleteCustom(id) == DeleteResult.InUse) {
                deleting.update { false }
                inUse.update { true }
            }
        }
    }

    fun onInUseDismiss() = inUse.update { false }

    @AssistedFactory
    interface Factory {
        fun create(id: Long): ExerciseDetailViewModel
    }

    private companion object {
        const val TAG = "Exercises"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
