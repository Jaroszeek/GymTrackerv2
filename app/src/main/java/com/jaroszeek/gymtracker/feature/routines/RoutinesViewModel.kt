package com.jaroszeek.gymtracker.feature.routines

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.domain.routine.Routine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    /** Starting a workout arrives in Etap 4. */
    val startStub: Boolean = false,
)

private data class Overlays(val actionsFor: Routine? = null, val confirmDeleteFor: Routine? = null, val startStub: Boolean = false)

@HiltViewModel
class RoutinesViewModel @Inject constructor(private val repository: RoutineRepository) : ViewModel() {

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
            startStub = overlays.startStub,
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

    fun onStart() = overlays.update { it.copy(startStub = true) }

    fun onStartStubDismiss() = overlays.update { it.copy(startStub = false) }

    private companion object {
        const val TAG = "Routines"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
