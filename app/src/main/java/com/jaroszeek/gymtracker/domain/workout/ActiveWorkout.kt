package com.jaroszeek.gymtracker.domain.workout

import com.jaroszeek.gymtracker.domain.routine.DoubleProgression
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.SetType
import kotlinx.serialization.Serializable

@Serializable
data class WorkoutSet(
    val id: Long,
    val type: SetType = SetType.Working,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val done: Boolean = false,
) {
    /** A set without reps cannot be ticked off; no weight means bodyweight. */
    val canComplete: Boolean get() = (reps ?: 0) > 0
}

/** One exercise option; [routineVariantId] and [targets] are set when it comes from a routine. */
@Serializable
data class WorkoutVariant(
    val exerciseId: Long,
    val routineVariantId: Long? = null,
    val targets: RoutineTargets? = null,
    /** Sets of the last finished session with this exercise, captured when the workout starts. */
    val previous: List<PerformedSet> = emptyList(),
) {
    /** Double progression suggestion (D-036) for this session. */
    val progressionKg: Double? get() = targets?.let { DoubleProgression.nextWeight(it, previous) }
}

/** An exercise of the workout; a routine block with alternatives keeps all of them and one is [selected]. */
@Serializable
data class WorkoutExercise(
    val id: Long,
    val variants: List<WorkoutVariant>,
    val selected: Int = 0,
    val sets: List<WorkoutSet>,
) {
    val variant: WorkoutVariant get() = variants[selected]

    /** The set at the same place in the last session: warm-ups match warm-ups, other sets match other sets. */
    fun previousFor(set: WorkoutSet): PerformedSet? {
        val warmup = set.type == SetType.Warmup
        val index = sets.filter { (it.type == SetType.Warmup) == warmup }.indexOfFirst { it.id == set.id }
        return variant.previous.filter { (it.type == SetType.Warmup) == warmup }.getOrNull(index)
    }
}

/** Rest after ticking off a set; based on absolute times, so it survives process death. Epoch millis. */
@Serializable
data class RestTimer(val startsAt: Long, val endsAt: Long, val exerciseId: Long, val setId: Long) {
    fun remainingMillis(now: Long): Long = (endsAt - now).coerceAtLeast(0)

    /** 1 at the start, 0 at the end. */
    fun fraction(now: Long): Float = if (endsAt <= startsAt) 0f else remainingMillis(now).toFloat() / (endsAt - startsAt)
}

/** Where the next set is: the exercise and the 1-based set number. */
data class NextSet(val exercise: WorkoutExercise, val number: Int)

/**
 * The workout in progress. Pure operations; the manager stamps [updatedAt] and persists it.
 * Ids of exercises and sets are unique within the workout. Times are epoch millis UTC.
 */
@Serializable
data class ActiveWorkout(
    val routineId: Long?,
    val name: String,
    val startedAt: Long,
    val updatedAt: Long,
    val exercises: List<WorkoutExercise> = emptyList(),
    val rest: RestTimer? = null,
) {
    val doneSets: Int get() = exercises.sumOf { e -> e.sets.count { it.done } }
    val totalSets: Int get() = exercises.sumOf { it.sets.size }

    fun addExercise(variants: List<WorkoutVariant>): ActiveWorkout {
        val id = nextId()
        return copy(exercises = exercises + WorkoutExercise(id, variants, sets = prefill(variants.first(), id + 1)))
    }

    /** Before any set is ticked off the sets are planned again for the new variant; afterwards they stay. */
    fun selectVariant(exerciseId: Long, index: Int): ActiveWorkout = updateExercise(exerciseId) { exercise ->
        if (index !in exercise.variants.indices || index == exercise.selected) return@updateExercise exercise
        val sets = if (exercise.sets.none { it.done }) prefill(exercise.variants[index], nextId()) else exercise.sets
        exercise.copy(selected = index, sets = sets)
    }

    fun updateSet(exerciseId: Long, setId: Long, change: (WorkoutSet) -> WorkoutSet): ActiveWorkout = updateExercise(exerciseId) { exercise ->
        exercise.copy(sets = exercise.sets.map { if (it.id == setId) change(it) else it })
    }

    /** A new set copies the type, weight and reps of the last one. */
    fun addSet(exerciseId: Long): ActiveWorkout {
        val id = nextId()
        return updateExercise(exerciseId) { exercise ->
            val last = exercise.sets.lastOrNull()
            exercise.copy(sets = exercise.sets + WorkoutSet(id, last?.type ?: SetType.Working, last?.weightKg, last?.reps))
        }
    }

    /** Removing the last set removes the exercise from the workout. */
    fun removeSet(exerciseId: Long, setId: Long): ActiveWorkout = copy(
        exercises = exercises.mapNotNull { exercise ->
            if (exercise.id != exerciseId) return@mapNotNull exercise
            exercise.copy(sets = exercise.sets.filterNot { it.id == setId }).takeIf { it.sets.isNotEmpty() }
        },
        rest = rest?.takeUnless { it.setId == setId },
    )

    /** Ticks the set off and starts the rest: the exercise's target rest, otherwise [defaultRestSeconds]. */
    fun complete(exerciseId: Long, setId: Long, now: Long, defaultRestSeconds: Int): ActiveWorkout {
        val exercise = exercises.firstOrNull { it.id == exerciseId } ?: return this
        val set = exercise.sets.firstOrNull { it.id == setId }?.takeIf { it.canComplete && !it.done } ?: return this
        val restSeconds = exercise.variant.targets?.restSeconds ?: defaultRestSeconds
        return updateSet(exerciseId, set.id) { it.copy(done = true) }
            .copy(rest = RestTimer(now, now + restSeconds * MILLIS_PER_SECOND, exerciseId, setId))
    }

    /** Unticking a set cancels the rest. */
    fun uncomplete(exerciseId: Long, setId: Long): ActiveWorkout = updateSet(exerciseId, setId) { it.copy(done = false) }.copy(rest = null)

    /** Shortens or extends the rest; a rest pushed to the past ends. */
    fun adjustRest(deltaSeconds: Int, now: Long): ActiveWorkout {
        val timer = rest ?: return this
        val endsAt = timer.endsAt + deltaSeconds * MILLIS_PER_SECOND
        return copy(rest = timer.copy(endsAt = endsAt).takeIf { endsAt > now })
    }

    fun skipRest(): ActiveWorkout = copy(rest = null)

    fun endRestIfDue(now: Long): ActiveWorkout = if (rest != null && rest.endsAt <= now) copy(rest = null) else this

    /** The first unticked set after the one that started the rest, wrapping to the start of the workout. */
    fun nextSet(): NextSet? {
        val order = exercises.flatMap { exercise -> exercise.sets.mapIndexed { index, set -> Triple(exercise, index + 1, set) } }
        val from = rest?.let { timer -> order.indexOfFirst { it.first.id == timer.exerciseId && it.third.id == timer.setId } } ?: -1
        val next = (order.drop(from + 1) + order.take(from + 1)).firstOrNull { !it.third.done } ?: return null
        return NextSet(next.first, next.second)
    }

    fun uses(exerciseId: Long): Boolean = exercises.any { exercise -> exercise.variants.any { it.exerciseId == exerciseId } }

    private fun updateExercise(exerciseId: Long, change: (WorkoutExercise) -> WorkoutExercise) =
        copy(exercises = exercises.map { if (it.id == exerciseId) change(it) else it })

    private fun nextId(): Long = (exercises.flatMap { exercise -> exercise.sets.map { it.id } + exercise.id }.maxOrNull() ?: 0L) + 1

    companion object {
        /** Global default until the setting arrives (Etap 9). */
        const val DEFAULT_REST_SECONDS = 90
        private const val MILLIS_PER_SECOND = 1_000L
    }
}

/**
 * Sets planned for a variant: the routine's set count (or as many as last time), each starting from the
 * progression weight, otherwise last session's set at the same place, otherwise the routine targets.
 */
internal fun prefill(variant: WorkoutVariant, firstId: Long): List<WorkoutSet> {
    val targets = variant.targets
    val previous = variant.previous.filter { it.type != SetType.Warmup }
    val count = targets?.sets ?: previous.size.takeIf { it > 0 } ?: RoutineTargets.DEFAULT_SETS
    val progression = variant.progressionKg
    return List(count) { index ->
        val last = previous.getOrNull(index) ?: previous.lastOrNull()
        WorkoutSet(
            id = firstId + index,
            weightKg = progression ?: last?.weightKg ?: targets?.weightKg,
            // A heavier weight starts again from the bottom of the range
            reps = if (progression != null) targets?.repsMin else last?.reps ?: targets?.repsMin,
        )
    }
}
