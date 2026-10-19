package com.jaroszeek.gymtracker.domain.workout

import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.routine.normalized

/** Only ticked-off sets count; volume and the set count leave warm-ups out. */
data class WorkoutSummary(val durationMillis: Long, val volumeKg: Double, val sets: Int, val exercises: Int) {
    /** Nothing was ticked off, so nothing is saved. */
    val isEmpty: Boolean get() = exercises == 0
}

fun ActiveWorkout.summary(endedAt: Long): WorkoutSummary {
    val done = exercises.map { exercise -> exercise.sets.filter { it.done } }.filter { it.isNotEmpty() }
    val work = done.flatten().filter { it.type != SetType.Warmup }
    return WorkoutSummary(
        durationMillis = (endedAt - startedAt).coerceAtLeast(0),
        volumeKg = work.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
        sets = work.size,
        exercises = done.size,
    )
}

/** What the workout did differently from the routine targets of one variant. */
data class TargetChange(val exerciseId: Long, val routineVariantId: Long, val old: RoutineTargets, val new: RoutineTargets)

/**
 * Proposed new targets per routine exercise: the number of ticked-off working sets and the heaviest
 * working weight. The rep range and rest stay. Exercises without a working set propose nothing.
 */
fun ActiveWorkout.targetChanges(): List<TargetChange> = exercises.mapNotNull { exercise ->
    val variant = exercise.variant
    val variantId = variant.routineVariantId ?: return@mapNotNull null
    val old = variant.targets ?: return@mapNotNull null
    val working = exercise.sets.filter { it.done && it.type.isWork }
    if (working.isEmpty()) return@mapNotNull null
    val new = old.copy(sets = working.size, weightKg = working.mapNotNull { it.weightKg }.maxOrNull() ?: old.weightKg).normalized()
    TargetChange(variant.exerciseId, variantId, old, new).takeIf { new != old }
}
