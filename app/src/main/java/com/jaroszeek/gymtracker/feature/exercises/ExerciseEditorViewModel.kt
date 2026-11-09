package com.jaroszeek.gymtracker.feature.exercises

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.data.exercise.ExercisePhotos
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.exercise.ExerciseDraft
import com.jaroszeek.gymtracker.domain.exercise.Level
import com.jaroszeek.gymtracker.domain.exercise.Mechanic
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import com.jaroszeek.gymtracker.domain.exercise.toDraft
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
import kotlinx.coroutines.launch

data class ExerciseEditorUiState(
    val isNew: Boolean,
    val loading: Boolean,
    val draft: ExerciseDraft = ExerciseDraft(),
    /** Validation messages show only after the first save attempt. */
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Picked photos are being copied; saving waits for them. */
    val importingPhotos: Boolean = false,
    val photosFailed: Boolean = false,
    /** Set once saved; the screen leaves. */
    val savedId: Long? = null,
)

@HiltViewModel(assistedFactory = ExerciseEditorViewModel.Factory::class)
class ExerciseEditorViewModel @AssistedInject constructor(
    @Assisted private val id: Long?,
    private val repository: ExerciseRepository,
    private val photos: ExercisePhotos,
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseEditorUiState(isNew = id == null, loading = id != null))
    val state: StateFlow<ExerciseEditorUiState> = _state.asStateFlow()

    /** Imports wait for it, so it never sees a photo of this draft. */
    private val sweep = viewModelScope.launch {
        try {
            photos.sweep()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Cleaning up exercise photos failed", e)
        }
    }

    init {
        if (id != null) {
            viewModelScope.launch {
                val exercise = repository.observe(id).first()
                _state.update { it.copy(loading = false, draft = exercise?.toDraft() ?: ExerciseDraft()) }
            }
        }
    }

    fun onNameChange(name: String) = edit { it.copy(name = name) }

    fun onEquipmentChange(equipment: Equipment?) = edit { it.copy(equipment = equipment) }

    fun onMechanicChange(mechanic: Mechanic?) = edit { it.copy(mechanic = mechanic) }

    fun onLevelChange(level: Level?) = edit { it.copy(level = level) }

    /** Appended in the picked order, which is the order they play in; beyond [ExerciseDraft.MAX_PHOTOS] they are dropped. */
    fun onPhotosPicked(uris: List<Uri>) {
        val picked = uris.take(ExerciseDraft.MAX_PHOTOS - _state.value.draft.images.size)
        if (picked.isEmpty() || _state.value.importingPhotos) return
        _state.update { it.copy(importingPhotos = true, photosFailed = false) }
        viewModelScope.launch {
            sweep.join()
            val imported = picked.mapNotNull { uri ->
                try {
                    photos.import(uri)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Importing a photo failed", e)
                    null
                }
            }
            _state.update {
                it.copy(importingPhotos = false, photosFailed = imported.size < picked.size, draft = it.draft.copy(images = it.draft.images + imported))
            }
        }
    }

    fun onRemovePhoto(index: Int) = edit { draft -> draft.copy(images = draft.images.filterIndexed { i, _ -> i != index }) }

    fun onTogglePrimary(muscle: Muscle) = edit { it.copy(primaryMuscles = it.primaryMuscles.toggle(muscle)) }

    fun onToggleSecondary(muscle: Muscle) = edit { it.copy(secondaryMuscles = it.secondaryMuscles.toggle(muscle)) }

    fun onNotesChange(notes: String) = edit { it.copy(notes = notes) }

    fun onSave() {
        val current = _state.value
        if (current.saving || current.importingPhotos) return
        if (!current.draft.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                val savedId = repository.saveCustom(id, current.draft)
                _state.update { it.copy(saving = false, savedId = savedId) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Saving exercise failed", e)
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    private fun edit(change: (ExerciseDraft) -> ExerciseDraft) = _state.update { it.copy(draft = change(it.draft)) }

    @AssistedFactory
    interface Factory {
        fun create(id: Long?): ExerciseEditorViewModel
    }

    private companion object {
        const val TAG = "Exercises"
    }
}
