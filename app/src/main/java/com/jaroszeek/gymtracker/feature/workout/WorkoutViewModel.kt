package com.jaroszeek.gymtracker.feature.workout

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.di.DefaultDispatcher
import com.jaroszeek.gymtracker.core.notifications.LiveUpdateChecks
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import com.jaroszeek.gymtracker.domain.workout.RecordHit
import com.jaroszeek.gymtracker.domain.workout.TargetChange
import com.jaroszeek.gymtracker.domain.workout.WorkoutSummary
import com.jaroszeek.gymtracker.domain.workout.exerciseWorkingSets
import com.jaroszeek.gymtracker.domain.workout.recordHits
import com.jaroszeek.gymtracker.domain.workout.records
import com.jaroszeek.gymtracker.domain.workout.summary
import com.jaroszeek.gymtracker.domain.workout.targetChanges
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetRef(val exerciseId: Long, val setId: Long)

/** After "Zakończ": the summary and the proposed target changes. */
data class FinishState(
    val name: String,
    val summary: WorkoutSummary,
    val changes: List<TargetChange>,
    val records: List<RecordHit> = emptyList(),
    val exerciseSets: Map<Long, Int> = emptyMap(),
    /** Routine variants whose new targets the user chose to apply. */
    val apply: Set<Long> = emptySet(),
    val changesVisible: Boolean = changes.isNotEmpty(),
)

/** What keeps the workout from running reliably with the screen off; shown above the exercises. */
data class BackgroundIssues(
    val notificationsOff: Boolean = false,
    val exactAlarmsOff: Boolean = false,
    val batteryLimited: Boolean = false,
) {
    val any: Boolean get() = notificationsOff || exactAlarmsOff || batteryLimited

    /** The current issues as stored when the user hides the card. */
    val keys: Set<String> get() = buildSet {
        if (notificationsOff) add(KEY_NOTIFICATIONS)
        if (exactAlarmsOff) add(KEY_EXACT_ALARMS)
        if (batteryLimited) add(KEY_BATTERY)
    }

    /** Hidden stays hidden, also after a restart, until an issue appears that was not there when the user hid the card. */
    fun shown(hidden: Set<String>): Boolean = keys.any { it !in hidden }

    companion object {
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_EXACT_ALARMS = "exact_alarms"
        const val KEY_BATTERY = "battery"
    }
}

data class WorkoutUiState(
    val loading: Boolean = true,
    val workout: ActiveWorkout? = null,
    val exercises: Map<Long, Exercise> = emptyMap(),
    /** Set opened for editing per workout exercise when the user picked one; otherwise the first unticked set is open. */
    val expanded: Map<Long, Long> = emptyMap(),
    /** Per ticked-off set id the records it set (A5.7). */
    val records: Map<Long, Set<RecordType>> = emptyMap(),
    /** A set just ticked off set a record: one haptic pulse, then [WorkoutViewModel.onRecordShown]. */
    val recordHit: Boolean = false,
    val restVisible: Boolean = false,
    val setActions: SetRef? = null,
    val picker: Boolean = false,
    val confirmFinish: Boolean = false,
    val confirmDiscard: Boolean = false,
    val finishing: Boolean = false,
    val finishFailed: Boolean = false,
    val finished: FinishState? = null,
    /** The screen leaves: discarded, finished with nothing to save, or no workout running. */
    val closed: Boolean = false,
    /** Empty while the user hid them; see [BackgroundIssues.shown]. */
    val issues: BackgroundIssues = BackgroundIssues(),
)

private data class Local(
    val expanded: Map<Long, Long> = emptyMap(),
    /** Start of the rest whose overlay the user closed; a new rest shows again. */
    val hiddenRest: Long? = null,
    val setActions: SetRef? = null,
    val picker: Boolean = false,
    val confirmFinish: Boolean = false,
    val confirmDiscard: Boolean = false,
    val finishing: Boolean = false,
    val finishFailed: Boolean = false,
    val finished: FinishState? = null,
    val issues: BackgroundIssues = BackgroundIssues(),
    val recordHit: Boolean = false,
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val manager: ActiveWorkoutManager,
    exercises: ExerciseRepository,
    private val routines: RoutineRepository,
    private val checks: LiveUpdateChecks,
    private val preferences: AppPreferences,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    private val exercisesById = exercises.observeAll()
        .map<List<Exercise>, Map<Long, Exercise>?> { all -> all.associateBy { it.id } }
        .catch { e ->
            // Names and photos are missing, but the workout itself keeps working
            Log.e(TAG, "Loading exercises failed", e)
            emit(emptyMap())
        }

    val state: StateFlow<WorkoutUiState> = combine(
        manager.ready,
        manager.workout,
        exercisesById,
        local,
        preferences.hiddenBackgroundIssues,
    ) { ready, workout, byId, local, hiddenIssues ->
        val loading = !ready || byId == null
        WorkoutUiState(
            loading = loading,
            workout = workout,
            exercises = byId.orEmpty(),
            expanded = local.expanded,
            records = workout?.records().orEmpty(),
            recordHit = local.recordHit,
            restVisible = workout?.rest != null && workout.rest.startsAt != local.hiddenRest,
            setActions = local.setActions,
            picker = local.picker,
            confirmFinish = local.confirmFinish,
            confirmDiscard = local.confirmDiscard,
            finishing = local.finishing,
            finishFailed = local.finishFailed,
            finished = local.finished,
            closed = !loading && workout == null && local.finished == null && !local.finishing,
            issues = local.issues.takeIf { it.shown(hiddenIssues) } ?: BackgroundIssues(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorkoutUiState())

    /** Checks the background issues again, e.g. after coming back from the system settings. */
    fun onResume() {
        viewModelScope.launch {
            val status = withContext(dispatcher) { checks.status() }
            val issues = BackgroundIssues(
                notificationsOff = !status.notificationsEnabled,
                exactAlarmsOff = !status.exactAlarms,
                batteryLimited = status.backgroundRestricted || !status.ignoringBatteryOptimizations,
            )
            local.update { it.copy(issues = issues) }
        }
    }

    /** Remembers the issues shown now; a new one brings the card back. */
    fun onHideIssues() {
        val keys = local.value.issues.keys
        viewModelScope.launch { preferences.setHiddenBackgroundIssues(keys) }
    }

    fun onWeightChange(exerciseId: Long, setId: Long, weightKg: Double?) = manager.setWeight(exerciseId, setId, weightKg)

    fun onRepsChange(exerciseId: Long, setId: Long, reps: Int?) = manager.updateSet(exerciseId, setId) { it.copy(reps = reps) }

    /** Ticks the set off (which starts the rest) or unticks it (which cancels the rest). */
    fun onToggleSet(exerciseId: Long, setId: Long) {
        val set = manager.workout.value?.exercises?.firstOrNull { it.id == exerciseId }?.sets?.firstOrNull { it.id == setId } ?: return
        if (set.done) manager.uncompleteSet(exerciseId, setId) else manager.completeSet(exerciseId, setId)
        val record = !set.done && manager.workout.value?.records()?.containsKey(setId) == true
        // The next unticked set opens
        local.update { it.copy(expanded = it.expanded - exerciseId, recordHit = it.recordHit || record) }
    }

    fun onRecordShown() = local.update { it.copy(recordHit = false) }

    fun onExpandSet(exerciseId: Long, setId: Long) = local.update { it.copy(expanded = it.expanded + (exerciseId to setId)) }

    fun onSetActions(exerciseId: Long, setId: Long) = local.update { it.copy(setActions = SetRef(exerciseId, setId)) }

    fun onSetActionsDismiss() = local.update { it.copy(setActions = null) }

    fun onSetType(type: SetType) {
        val ref = local.value.setActions ?: return
        manager.updateSet(ref.exerciseId, ref.setId) { it.copy(type = type) }
        local.update { it.copy(setActions = null) }
    }

    fun onDeleteSet() {
        val ref = local.value.setActions ?: return
        manager.removeSet(ref.exerciseId, ref.setId)
        local.update { it.copy(setActions = null) }
    }

    fun onAddSet(exerciseId: Long) = manager.addSet(exerciseId)

    fun onSelectVariant(exerciseId: Long, index: Int) = manager.selectVariant(exerciseId, index)

    fun onAddExercise() = local.update { it.copy(picker = true) }

    fun onPickerDismiss() = local.update { it.copy(picker = false) }

    fun onPicked(exercise: Exercise) {
        local.update { it.copy(picker = false) }
        viewModelScope.launch { manager.addExercise(exercise.id) }
    }

    fun onRestShow() = local.update { it.copy(hiddenRest = null) }

    fun onRestHide() = local.update { it.copy(hiddenRest = manager.workout.value?.rest?.startsAt) }

    fun onRestAdjust(deltaSeconds: Int) = manager.adjustRest(deltaSeconds)

    fun onRestSkip() = manager.skipRest()

    fun onFinishRequest() = local.update { it.copy(confirmFinish = true) }

    fun onFinishDismiss() = local.update { it.copy(confirmFinish = false) }

    fun onFinishConfirm() {
        if (local.value.finishing) return
        local.update { it.copy(confirmFinish = false, finishing = true, finishFailed = false) }
        viewModelScope.launch {
            try {
                val finished = manager.finish()
                val state = finished?.let { (workout, endedAt) ->
                    workout.summary(endedAt).takeUnless { it.isEmpty }?.let { FinishState(workout.name, it, workout.targetChanges(), workout.recordHits(), exerciseSets = workout.exerciseWorkingSets()) }
                }
                local.update { it.copy(finishing = false, finished = state) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Saving the workout failed", e)
                local.update { it.copy(finishing = false, finishFailed = true) }
            }
        }
    }

    fun onDiscardRequest() = local.update { it.copy(confirmDiscard = true) }

    fun onDiscardDismiss() = local.update { it.copy(confirmDiscard = false) }

    fun onDiscardConfirm() {
        local.update { it.copy(confirmDiscard = false) }
        viewModelScope.launch { manager.discard() }
    }

    fun onChangeChoice(routineVariantId: Long, apply: Boolean) = local.update { state ->
        val finished = state.finished ?: return@update state
        state.copy(finished = finished.copy(apply = if (apply) finished.apply + routineVariantId else finished.apply - routineVariantId))
    }

    /** Writes the chosen targets to the routine; the rest stay as they were. */
    fun onChangesDone() {
        val finished = local.value.finished ?: return
        local.update { it.copy(finished = finished.copy(changesVisible = false)) }
        viewModelScope.launch {
            try {
                finished.changes.filter { it.routineVariantId in finished.apply }.forEach { routines.updateTargets(it.routineVariantId, it.new) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Updating routine targets failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "Workout"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
