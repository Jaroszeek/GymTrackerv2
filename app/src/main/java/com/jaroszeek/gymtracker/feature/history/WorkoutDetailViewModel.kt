package com.jaroszeek.gymtracker.feature.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.workout.WorkoutRepository
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.workout.LoggedWorkout
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
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

sealed interface WorkoutDetailUiState {
    data object Loading : WorkoutDetailUiState

    data object NotFound : WorkoutDetailUiState

    data object Error : WorkoutDetailUiState

    data class Content(
        val workout: LoggedWorkout,
        val exercises: Map<Long, Exercise>,
        /** Per set id the records it set when it was done. */
        val records: Map<Long, Set<RecordType>>,
        val confirmDelete: Boolean = false,
        val deleteFailed: Boolean = false,
    ) : WorkoutDetailUiState

    /** Deleted from this screen; the screen leaves. */
    data object Deleted : WorkoutDetailUiState
}

@HiltViewModel(assistedFactory = WorkoutDetailViewModel.Factory::class)
class WorkoutDetailViewModel @AssistedInject constructor(
    @Assisted private val id: Long,
    private val workouts: WorkoutRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    private val confirmDelete = MutableStateFlow(false)
    private val deleting = MutableStateFlow(false)
    private val deleteFailed = MutableStateFlow(false)

    // Records are worked out against the history before the workout, so an edit of an older one shows here too
    private val detail = workouts.observeWorkout(id).map { workout -> workout?.let { it to it.records(workouts.bestsBefore(it)) } }

    val state: StateFlow<WorkoutDetailUiState> =
        combine(detail, exercises.observeAll(), confirmDelete, deleting, deleteFailed) { detail, exercises, confirm, deleting, failed ->
            when {
                detail == null && deleting -> WorkoutDetailUiState.Deleted
                detail == null -> WorkoutDetailUiState.NotFound
                else -> WorkoutDetailUiState.Content(detail.first, exercises.associateBy { it.id }, detail.second, confirm, failed)
            }
        }
            .catch { e ->
                Log.e(TAG, "Loading workout $id failed", e)
                emit(WorkoutDetailUiState.Error)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorkoutDetailUiState.Loading)

    fun onDeleteRequest() = confirmDelete.update { true }

    fun onDeleteDismiss() = confirmDelete.update { false }

    fun onDeleteConfirm() {
        confirmDelete.update { false }
        deleteFailed.update { false }
        deleting.update { true }
        viewModelScope.launch {
            try {
                workouts.delete(id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Deleting workout $id failed", e)
                deleting.update { false }
                deleteFailed.update { true }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(id: Long): WorkoutDetailViewModel
    }

    private companion object {
        const val TAG = "History"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
