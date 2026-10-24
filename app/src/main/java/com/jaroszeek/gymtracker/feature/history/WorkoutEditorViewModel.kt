package com.jaroszeek.gymtracker.feature.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.workout.WorkoutRepository
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.workout.LoggedWorkout
import com.jaroszeek.gymtracker.feature.workout.SetRef
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutEditorUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val draft: LoggedWorkout? = null,
    val dirty: Boolean = false,
    val exercises: Map<Long, Exercise> = emptyMap(),
    /** The set whose fields are open; the others show their values. */
    val expanded: SetRef? = null,
    val setActions: SetRef? = null,
    val picker: Boolean = false,
    val confirmDiscard: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Saved or discarded: the screen leaves. */
    val finished: Boolean = false,
)

/** Edits a finished workout: sets, exercises, name and duration (A5.6); saving replaces its sets. */
@HiltViewModel(assistedFactory = WorkoutEditorViewModel.Factory::class)
class WorkoutEditorViewModel @AssistedInject constructor(
    @Assisted private val id: Long,
    private val workouts: WorkoutRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    private var saved: LoggedWorkout? = null
    private val local = MutableStateFlow(WorkoutEditorUiState())

    private val exercisesById = exercises.observeAll()
        .map { all -> all.associateBy { it.id } }
        .catch { e ->
            // Names and photos are missing, but editing still works
            Log.e(TAG, "Loading exercises failed", e)
            emit(emptyMap())
        }

    val state: StateFlow<WorkoutEditorUiState> = combine(local, exercisesById) { state, byId -> state.copy(exercises = byId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorkoutEditorUiState())

    init {
        viewModelScope.launch {
            try {
                saved = workouts.observeWorkout(id).first()
                local.update { it.copy(loading = false, draft = saved) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Loading workout $id failed", e)
                local.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    fun onNameChange(name: String) = edit { it.copy(name = name) }

    fun onDurationChange(minutes: Double?) {
        if (minutes != null) edit { it.withDuration(minutes.toInt()) }
    }

    fun onWeightChange(exerciseId: Long, setId: Long, weightKg: Double?) = edit { it.updateSet(exerciseId, setId) { set -> set.copy(weightKg = weightKg) } }

    fun onRepsChange(exerciseId: Long, setId: Long, reps: Int?) = edit { it.updateSet(exerciseId, setId) { set -> set.copy(reps = reps ?: 0) } }

    fun onExpand(exerciseId: Long, setId: Long) = local.update { it.copy(expanded = SetRef(exerciseId, setId)) }

    /** The new set opens, ready to type in. */
    fun onAddSet(exerciseId: Long) {
        edit { it.addSet(exerciseId) }
        expandLastSet(exerciseId)
    }

    fun onRemoveExercise(exerciseId: Long) = edit { it.removeExercise(exerciseId) }

    fun onSetActions(exerciseId: Long, setId: Long) = local.update { it.copy(setActions = SetRef(exerciseId, setId)) }

    fun onSetActionsDismiss() = local.update { it.copy(setActions = null) }

    fun onSetType(type: SetType) {
        val ref = local.value.setActions ?: return
        local.update { it.copy(setActions = null) }
        edit { it.updateSet(ref.exerciseId, ref.setId) { set -> set.copy(type = type) } }
    }

    fun onDeleteSet() {
        val ref = local.value.setActions ?: return
        local.update { it.copy(setActions = null) }
        edit { it.removeSet(ref.exerciseId, ref.setId) }
    }

    fun onAddExercise() = local.update { it.copy(picker = true) }

    fun onPickerDismiss() = local.update { it.copy(picker = false) }

    fun onPicked(exercise: Exercise) {
        local.update { it.copy(picker = false) }
        edit { it.addExercise(exercise.id) }
        local.value.draft?.exercises?.lastOrNull()?.let { expandLastSet(it.id) }
    }

    /** Back: leave right away when nothing changed, otherwise ask. */
    fun onBack() = local.update { if (it.dirty) it.copy(confirmDiscard = true) else it.copy(finished = true) }

    fun onDiscardDismiss() = local.update { it.copy(confirmDiscard = false) }

    fun onDiscardConfirm() = local.update { it.copy(confirmDiscard = false, finished = true) }

    fun onSave() {
        val current = local.value
        val draft = current.draft ?: return
        if (current.saving || !draft.canSave) return
        local.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                val updated = workouts.update(draft)
                local.update { it.copy(saving = false, saveFailed = !updated, finished = updated) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Saving workout $id failed", e)
                local.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    private fun expandLastSet(exerciseId: Long) {
        val set = local.value.draft?.exercises?.firstOrNull { it.id == exerciseId }?.sets?.lastOrNull() ?: return
        local.update { it.copy(expanded = SetRef(exerciseId, set.id)) }
    }

    private fun edit(change: (LoggedWorkout) -> LoggedWorkout) = local.update { state ->
        val draft = state.draft?.let(change) ?: return@update state
        state.copy(draft = draft, dirty = draft != saved, saveFailed = false)
    }

    @AssistedFactory
    interface Factory {
        fun create(id: Long): WorkoutEditorViewModel
    }

    private companion object {
        const val TAG = "History"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
