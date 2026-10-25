package com.jaroszeek.gymtracker.feature.routines

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.routine.RoutineDraft
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.toDraft
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/** Where the exercise picked in the picker goes. */
sealed interface PickerTarget {
    data object NewBlock : PickerTarget

    data class Variant(val blockId: Long) : PickerTarget
}

/** Targets being edited in the sheet; applied to the draft on "Gotowe". */
data class TargetsEdit(val blockId: Long, val variantId: Long, val exercise: Exercise, val targets: RoutineTargets)

data class RoutineEditorUiState(
    val routineId: Long?,
    val loading: Boolean,
    val draft: RoutineDraft = RoutineDraft(),
    val dirty: Boolean = false,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val picker: PickerTarget? = null,
    val targetsEdit: TargetsEdit? = null,
    val confirmDiscard: Boolean = false,
    /** Set when the editor should close (saved or discarded). */
    val finished: Boolean = false,
)

@HiltViewModel(assistedFactory = RoutineEditorViewModel.Factory::class)
class RoutineEditorViewModel @AssistedInject constructor(
    @Assisted private val id: Long?,
    private val repository: RoutineRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** The routine as last saved: the draft is compared with it to ask about unsaved changes. */
    private var saved = RoutineDraft()

    /**
     * The draft after the last change, kept in saved state so it survives process death; null until
     * the first change. The open picker or targets sheet is not kept.
     */
    private var restorableDraft: RoutineDraft? by savedStateHandle.saved { null }

    private val _state = MutableStateFlow(RoutineEditorUiState(routineId = id, loading = id != null))
    val state: StateFlow<RoutineEditorUiState> = _state.asStateFlow()

    init {
        val restored = restorableDraft
        if (id != null) {
            viewModelScope.launch {
                // The saved routine is loaded even with a restored draft, to tell whether it has changes
                saved = repository.observe(id).first()?.toDraft() ?: RoutineDraft()
                val draft = restored ?: saved
                _state.update { it.copy(loading = false, draft = draft, dirty = draft != saved) }
            }
        } else if (restored != null) {
            _state.update { it.copy(draft = restored, dirty = restored != saved) }
        }
    }

    fun onNameChange(name: String) = edit { it.rename(name) }

    fun onAddExercise() = _state.update { it.copy(picker = PickerTarget.NewBlock) }

    fun onAddVariant(blockId: Long) = _state.update { it.copy(picker = PickerTarget.Variant(blockId)) }

    fun onPickerDismiss() = _state.update { it.copy(picker = null) }

    fun onPicked(exercise: Exercise) {
        val target = _state.value.picker ?: return
        _state.update { it.copy(picker = null) }
        edit { draft ->
            when (target) {
                PickerTarget.NewBlock -> draft.addBlock(exercise)
                is PickerTarget.Variant -> draft.addVariant(target.blockId, exercise)
            }
        }
    }

    fun onRemoveVariant(blockId: Long, variantId: Long) = edit { it.removeVariant(blockId, variantId) }

    /** Reorders by list keys (block ids), which stay stable while dragging. */
    fun onMoveBlock(fromId: Any, toId: Any) = edit { draft ->
        draft.moveBlock(draft.blocks.indexOfFirst { it.id == fromId }, draft.blocks.indexOfFirst { it.id == toId })
    }

    fun onEditTargets(blockId: Long, variantId: Long) {
        val variant = _state.value.draft.blocks.firstOrNull { it.id == blockId }?.variants?.firstOrNull { it.id == variantId } ?: return
        _state.update { it.copy(targetsEdit = TargetsEdit(blockId, variantId, variant.exercise, variant.targets)) }
    }

    fun onTargetsChange(targets: RoutineTargets) = _state.update { state -> state.copy(targetsEdit = state.targetsEdit?.copy(targets = targets)) }

    fun onTargetsDone() {
        val edit = _state.value.targetsEdit ?: return
        _state.update { it.copy(targetsEdit = null) }
        edit { it.updateTargets(edit.blockId, edit.variantId, edit.targets) }
    }

    /** Back: leave right away when nothing changed, otherwise ask. */
    fun onBack() = _state.update { if (it.dirty) it.copy(confirmDiscard = true) else it.copy(finished = true) }

    fun onDiscardDismiss() = _state.update { it.copy(confirmDiscard = false) }

    fun onDiscardConfirm() = _state.update { it.copy(confirmDiscard = false, finished = true) }

    fun onSave() {
        val current = _state.value
        if (current.saving) return
        if (!current.draft.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                repository.save(id, current.draft)
                _state.update { it.copy(saving = false, finished = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Saving routine failed", e)
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    private fun edit(change: (RoutineDraft) -> RoutineDraft) {
        restorableDraft = _state.updateAndGet { state ->
            val draft = change(state.draft)
            state.copy(draft = draft, dirty = draft != saved)
        }.draft
    }

    @AssistedFactory
    interface Factory {
        fun create(id: Long?): RoutineEditorViewModel
    }

    private companion object {
        const val TAG = "Routines"
    }
}
