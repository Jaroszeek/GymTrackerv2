package com.jaroszeek.gymtracker.feature.progress

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.data.progress.ProgressRepository
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.designsystem.component.ChartRange
import com.jaroszeek.gymtracker.domain.progress.RoutineSession
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

data class RoutineProgressUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    /** Null once the routine is deleted; its workouts are no longer linked to it then. */
    val name: String? = null,
    val sessions: List<RoutineSession> = emptyList(),
    val range: ChartRange = ChartRange.ThreeMonths,
)

@HiltViewModel(assistedFactory = RoutineProgressViewModel.Factory::class)
class RoutineProgressViewModel @AssistedInject constructor(
    @Assisted private val id: Long,
    routines: RoutineRepository,
    progress: ProgressRepository,
) : ViewModel() {

    private val range = MutableStateFlow(ChartRange.ThreeMonths)

    val state: StateFlow<RoutineProgressUiState> =
        combine(routines.observe(id), progress.observeRoutineSessions(id), range) { routine, sessions, range ->
            RoutineProgressUiState(loading = false, name = routine?.name, sessions = sessions, range = range)
        }
            .catch { e ->
                Log.e(TAG, "Loading routine progress $id failed", e)
                emit(RoutineProgressUiState(loading = false, error = true))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RoutineProgressUiState())

    fun onRange(range: ChartRange) = this.range.update { range }

    @AssistedFactory
    interface Factory {
        fun create(id: Long): RoutineProgressViewModel
    }

    private companion object {
        const val TAG = "Progress"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
