package com.jaroszeek.gymtracker.feature.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.health.HealthRepository
import com.jaroszeek.gymtracker.data.health.observeMaxHeartRate
import com.jaroszeek.gymtracker.data.workout.WorkoutRepository
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.health.ExerciseHeartRate
import com.jaroszeek.gymtracker.domain.health.HealthSession
import com.jaroszeek.gymtracker.domain.health.HeartRateSample
import com.jaroszeek.gymtracker.domain.health.HeartRateSummary
import com.jaroszeek.gymtracker.domain.health.exerciseHeartRate
import com.jaroszeek.gymtracker.domain.health.linkCandidates
import com.jaroszeek.gymtracker.domain.health.summarize
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.workout.LoggedWorkout
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
        val health: WorkoutHealth = WorkoutHealth(),
        /** Per entry id the heart rate during that exercise, from the linked session. */
        val exerciseHeartRate: Map<Long, ExerciseHeartRate> = emptyMap(),
        /** The sheet to link a session by hand. */
        val choosingSession: Boolean = false,
    ) : WorkoutDetailUiState

    /** Deleted from this screen; the screen leaves. */
    data object Deleted : WorkoutDetailUiState
}

/** The workout's watch session (A6, A5.6). */
data class WorkoutHealth(
    /** Linked and imported; a link to a session that is no longer imported counts as none. */
    val session: HealthSession? = null,
    val heartRate: List<HeartRateSample> = emptyList(),
    val heartRateSummary: HeartRateSummary? = null,
    /** To link by hand: started within 12 h and not linked to another workout. */
    val candidates: List<HealthSession> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = WorkoutDetailViewModel.Factory::class)
class WorkoutDetailViewModel @AssistedInject constructor(
    @Assisted private val id: Long,
    private val workouts: WorkoutRepository,
    exercises: ExerciseRepository,
    health: HealthRepository,
    preferences: AppPreferences,
    clock: Clock,
) : ViewModel() {

    private val confirmDelete = MutableStateFlow(false)
    private val deleting = MutableStateFlow(false)
    private val deleteFailed = MutableStateFlow(false)
    private val choosingSession = MutableStateFlow(false)

    // Records are worked out against the history before the workout, so an edit of an older one shows here too
    private val detail = workouts.observeWorkout(id).map { workout -> workout?.let { it to it.records(workouts.bestsBefore(it)) } }

    private val link = workouts.observeHealthLink(id)

    private val linked = link.map { it?.sessionId }.distinctUntilChanged().flatMapLatest { sessionId ->
        if (sessionId == null) flowOf(null to emptyList()) else combine(health.observeSession(sessionId), health.observeHeartRate(sessionId), ::Pair)
    }

    private val around = link.map { it?.let { span -> span.startedAt to span.endedAt } }.distinctUntilChanged().flatMapLatest { span ->
        if (span == null) flowOf(emptyList()) else health.observeSessionsAround(span.first, span.second)
    }

    private val watch = combine(link, linked, around, workouts.observeLinkedSessions(), preferences.observeMaxHeartRate(clock)) { link, (session, heartRate), around, taken, maxHeartRate ->
        val free = around.filter { it.id !in taken }
        WorkoutHealth(
            session = session,
            heartRate = heartRate,
            heartRateSummary = summarize(heartRate, maxHeartRate),
            // ponytail: the sheet does not scroll; a scrolling one if 12 h ever hold more sessions
            candidates = if (link != null && session == null) linkCandidates(link.startedAt, free).take(MAX_CANDIDATES) else emptyList(),
        )
    }

    private data class Flags(val confirmDelete: Boolean, val deleting: Boolean, val deleteFailed: Boolean, val choosingSession: Boolean)

    private val flags = combine(confirmDelete, deleting, deleteFailed, choosingSession, ::Flags)

    val state: StateFlow<WorkoutDetailUiState> =
        combine(detail, exercises.observeAll(), flags, watch) { detail, exercises, flags, watch ->
            when {
                detail == null && flags.deleting -> WorkoutDetailUiState.Deleted
                detail == null -> WorkoutDetailUiState.NotFound
                else -> WorkoutDetailUiState.Content(
                    workout = detail.first,
                    exercises = exercises.associateBy { it.id },
                    records = detail.second,
                    confirmDelete = flags.confirmDelete,
                    deleteFailed = flags.deleteFailed,
                    health = watch,
                    exerciseHeartRate = exerciseHeartRate(detail.first, watch.heartRate),
                    choosingSession = flags.choosingSession,
                )
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

    fun onLinkSession(sessionId: String) {
        choosingSession.update { false }
        viewModelScope.launch { workouts.linkHealthSession(id, sessionId) }
    }

    /** Unlinked on purpose: the workout is not linked automatically again, only by hand. */
    fun onUnlinkSession() {
        viewModelScope.launch { workouts.unlinkHealthSession(id) }
    }

    fun onChooseSession() = choosingSession.update { true }

    fun onChooseDismiss() = choosingSession.update { false }

    @AssistedFactory
    interface Factory {
        fun create(id: Long): WorkoutDetailViewModel
    }

    private companion object {
        const val TAG = "History"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_CANDIDATES = 6
    }
}
