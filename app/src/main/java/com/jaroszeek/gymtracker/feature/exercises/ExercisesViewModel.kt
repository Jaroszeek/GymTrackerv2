package com.jaroszeek.gymtracker.feature.exercises

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.core.di.DefaultDispatcher
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.domain.exercise.BodyPart
import com.jaroszeek.gymtracker.domain.exercise.EquipmentGroup
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.exercise.ExerciseFilter
import com.jaroszeek.gymtracker.domain.exercise.ExerciseIndex
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface ExercisesUiState {
    data object Loading : ExercisesUiState

    data object Error : ExercisesUiState

    data class Content(val exercises: List<Exercise>, val filter: ExerciseFilter) : ExercisesUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExercisesViewModel @Inject constructor(
    repository: ExerciseRepository,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /**
     * The search text lives in snapshot state, not in [state]: a text field fed from an
     * asynchronous flow loses keystrokes when the user types faster than the list updates.
     */
    var query by mutableStateOf("")
        private set

    private val chips = MutableStateFlow(ExerciseFilter())
    private val attempt = MutableStateFlow(0)

    val state: StateFlow<ExercisesUiState> = combine(
        attempt.flatMapLatest { repository.observeAll() }.map { ExerciseIndex(it) },
        snapshotFlow { query },
        chips,
    ) { index, query, chips ->
        val filter = chips.copy(query = query)
        ExercisesUiState.Content(index.search(filter), filter) as ExercisesUiState
    }
        .flowOn(defaultDispatcher)
        .catch { e ->
            Log.e(TAG, "Loading exercises failed", e)
            emit(ExercisesUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ExercisesUiState.Loading)

    fun onQueryChange(query: String) {
        this.query = query
    }

    fun onToggleBodyPart(bodyPart: BodyPart) = chips.update { it.copy(bodyParts = it.bodyParts.toggle(bodyPart)) }

    fun onToggleEquipment(group: EquipmentGroup) = chips.update { it.copy(equipment = it.equipment.toggle(group)) }

    fun onClearFilters() {
        query = ""
        chips.update { ExerciseFilter() }
    }

    fun onRetry() = attempt.update { it + 1 }

    private companion object {
        const val TAG = "Exercises"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
