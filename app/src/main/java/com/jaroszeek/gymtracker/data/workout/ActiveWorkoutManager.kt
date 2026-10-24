package com.jaroszeek.gymtracker.data.workout

import android.util.Log
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.di.ApplicationScope
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.domain.routine.Routine
import com.jaroszeek.gymtracker.domain.settings.RestDefaults
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import com.jaroszeek.gymtracker.domain.workout.WorkoutSet
import com.jaroszeek.gymtracker.domain.workout.WorkoutVariant
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class FinishedWorkout(val workout: ActiveWorkout, val endedAt: Long)

/**
 * Owns the workout in progress: the single source of truth for the UI and the workout notification.
 * Every change is saved to [ActiveWorkoutStore] with a debounce and restored after process death;
 * a workout untouched for over 24 h is not brought back (D-038).
 */
@Singleton
class ActiveWorkoutManager @Inject constructor(
    private val store: ActiveWorkoutStore,
    private val workouts: WorkoutRepository,
    private val preferences: AppPreferences,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _workout = MutableStateFlow<ActiveWorkout?>(null)
    val workout: StateFlow<ActiveWorkout?> = _workout.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** True once the workout saved before the process died is back (or turned out to be absent or stale). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val storeLock = Mutex()

    /** The rest after a set whose routine sets none (A5.9). */
    private val defaultRest = preferences.defaultRestSeconds.stateIn(scope, SharingStarted.Eagerly, RestDefaults.SECONDS)

    init {
        scope.launch {
            restore()
            @OptIn(FlowPreview::class)
            _workout
                .onSubscription { _ready.value = true }
                .drop(1)
                .debounce(PERSIST_DEBOUNCE_MILLIS)
                .collect { persist() }
        }
        scope.launch {
            // Ends the rest at its absolute end time; a new or adjusted rest restarts the wait
            _workout.map { it?.rest?.endsAt }.distinctUntilChanged().collectLatest { endsAt ->
                if (endsAt == null) return@collectLatest
                // The delay runs on the monotonic clock, which stops in deep sleep; the rest alarm (Etap 5) ends it on time then
                while (clock.millis() < endsAt) delay(endsAt - clock.millis())
                endRestIfDue()
            }
        }
    }

    /** Starts a workout with the blocks of [routine]; false when a workout is already running. */
    suspend fun start(routine: Routine): Boolean {
        val incrementKg = incrementKg()
        return start(
            routineId = routine.id,
            name = routine.name,
            blocks = routine.blocks.map { block ->
                block.variants.map {
                    WorkoutVariant(it.exercise.id, it.id, it.targets, workouts.lastSession(it.exercise.id), workouts.personalBests(it.exercise.id), incrementKg)
                }
            },
        )
    }

    suspend fun startEmpty(name: String): Boolean = start(routineId = null, name = name, blocks = emptyList())

    suspend fun addExercise(exerciseId: Long) {
        val previous = workouts.lastSession(exerciseId)
        val bests = workouts.personalBests(exerciseId)
        val incrementKg = incrementKg()
        mutate { it.addExercise(listOf(WorkoutVariant(exerciseId, previous = previous, bests = bests, incrementKg = incrementKg))) }
    }

    /** The progression adds 2.5 kg, or 5 lb when masses are shown in pounds. */
    private suspend fun incrementKg(): Double = preferences.weightUnit.first().stepKg

    fun selectVariant(exerciseId: Long, index: Int) = mutate { it.selectVariant(exerciseId, index) }

    fun updateSet(exerciseId: Long, setId: Long, change: (WorkoutSet) -> WorkoutSet) = mutate { it.updateSet(exerciseId, setId, change) }

    fun setWeight(exerciseId: Long, setId: Long, weightKg: Double?) = mutate { it.setWeight(exerciseId, setId, weightKg) }

    fun addSet(exerciseId: Long) = mutate { it.addSet(exerciseId) }

    fun removeSet(exerciseId: Long, setId: Long) = mutate { it.removeSet(exerciseId, setId) }

    fun completeSet(exerciseId: Long, setId: Long) =
        mutate { it.complete(exerciseId, setId, clock.millis(), defaultRest.value) }

    fun uncompleteSet(exerciseId: Long, setId: Long) = mutate { it.uncomplete(exerciseId, setId) }

    fun adjustRest(deltaSeconds: Int) = mutate { it.adjustRest(deltaSeconds, clock.millis()) }

    fun skipRest() = mutate { it.skipRest() }

    /** Ends the rest once its time is up; the rest alarm calls it when the device wakes up for it. */
    fun endRestIfDue() = _workout.update { it?.endRestIfDue(clock.millis()) }

    /**
     * Saves the ticked-off sets to history and ends the workout. Throws when saving fails; the workout
     * then keeps running, so nothing is lost.
     */
    suspend fun finish(): FinishedWorkout? {
        val workout = _workout.value ?: return null
        val endedAt = clock.millis()
        // ponytail: process death between the insert and clearing the file brings the workout back once; add an idempotency key if that ever bites
        workouts.save(workout, endedAt)
        end()
        return FinishedWorkout(workout, endedAt)
    }

    suspend fun discard() = end()

    /** Whether the running workout uses the exercise, which therefore cannot be deleted. */
    fun uses(exerciseId: Long): Boolean = _workout.value?.uses(exerciseId) == true

    private suspend fun start(routineId: Long?, name: String, blocks: List<List<WorkoutVariant>>): Boolean {
        ready.first { it }
        val now = clock.millis()
        val workout = blocks.fold(ActiveWorkout(routineId, name, startedAt = now, updatedAt = now)) { w, variants -> w.addExercise(variants) }
        if (!_workout.compareAndSet(null, workout)) return false
        // Saved right away: a kill a moment after "Start" must not lose the workout
        persist()
        return true
    }

    private suspend fun end() {
        _workout.value = null
        persist()
    }

    private suspend fun restore() {
        val saved = withContext(io) { store.read() } ?: return
        val now = clock.millis()
        if (now - saved.updatedAt < STALE_AFTER_MILLIS) {
            _workout.value = saved.endRestIfDue(now)
            return
        }
        // Abandoned: not brought back, but its ticked-off sets go to history, ending at the last change
        try {
            workouts.save(saved, endedAt = saved.updatedAt)
            storeLock.withLock { withContext(io) { store.write(null) } }
            Log.i(TAG, "Saved a workout abandoned at ${saved.updatedAt} to history")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Saving an abandoned workout failed", e)
        }
    }

    private fun mutate(change: (ActiveWorkout) -> ActiveWorkout) =
        _workout.update { current -> current?.let { change(it).copy(updatedAt = clock.millis()) } }

    // Writes the current value, not a passed one, so a late write can never put an older state back
    private suspend fun persist() = storeLock.withLock {
        try {
            withContext(io) { store.write(_workout.value) }
        } catch (e: IOException) {
            Log.e(TAG, "Saving the active workout failed", e)
        }
    }

    companion object {
        const val PERSIST_DEBOUNCE_MILLIS = 500L
        const val STALE_AFTER_MILLIS = 24 * 60 * 60 * 1_000L
        private const val TAG = "Workout"
    }
}
