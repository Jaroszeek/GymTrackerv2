package com.jaroszeek.gymtracker.domain.workout

import com.jaroszeek.gymtracker.domain.exercise.BodyPart
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import com.jaroszeek.gymtracker.domain.routine.SetType

/** Relative engagement tier of a muscle in a workout. */
enum class MuscleIntensity {
    None,
    Low,
    Medium,
    High,
}

/**
 * Working sets hitting [muscle] as primary (weight 1.0) and as secondary (weight [SECONDARY_WEIGHT]).
 * A muscle trained only as a secondary mover stays at [MuscleIntensity.Low].
 */
data class MuscleLoad(
    val muscle: Muscle,
    val primarySets: Int,
    val secondarySets: Int,
) {
    val effectiveSets: Double get() = primarySets + secondarySets * SECONDARY_WEIGHT

    val intensity: MuscleIntensity
        get() = when {
            effectiveSets <= 0.0 -> MuscleIntensity.None
            primarySets == 0 || effectiveSets < MEDIUM_SETS -> MuscleIntensity.Low
            effectiveSets < HIGH_SETS -> MuscleIntensity.Medium
            else -> MuscleIntensity.High
        }

    companion object {
        const val SECONDARY_WEIGHT = 0.5
        const val MEDIUM_SETS = 2.0
        const val HIGH_SETS = 4.0
    }
}

/** Ticked-off working sets per exercise id; warm-ups are excluded. */
fun ActiveWorkout.exerciseWorkingSets(): Map<Long, Int> = buildMap {
    for (exercise in exercises) {
        val count = exercise.sets.count { it.done && it.type != SetType.Warmup }
        if (count > 0) {
            val id = exercise.variant.exerciseId
            put(id, (get(id) ?: 0) + count)
        }
    }
}

/** Working sets per exercise id in a finished workout; warm-ups and 0-rep drafts are excluded. */
fun LoggedWorkout.exerciseWorkingSets(): Map<Long, Int> = buildMap {
    for (exercise in exercises) {
        val count = exercise.sets.count { it.type != SetType.Warmup && it.reps > 0 }
        if (count > 0) {
            val id = exercise.exerciseId
            put(id, (get(id) ?: 0) + count)
        }
    }
}

/**
 * Muscle workload sorted from highest intensity and effective set count down, in anatomical [BodyPart.orderedMuscles]
 * tie-break order. Unworked muscles are omitted.
 */
fun muscleWorkload(
    exerciseSets: Map<Long, Int>,
    exercises: Map<Long, Exercise>,
): List<MuscleLoad> {
    if (exerciseSets.isEmpty()) return emptyList()
    return BodyPart.orderedMuscles
        .mapNotNull { muscle ->
            var primary = 0
            var secondary = 0
            for ((exerciseId, sets) in exerciseSets) {
                if (sets <= 0) continue
                val exercise = exercises[exerciseId] ?: continue
                when {
                    muscle in exercise.primaryMuscles -> primary += sets
                    muscle in exercise.secondaryMuscles -> secondary += sets
                }
            }
            MuscleLoad(muscle, primary, secondary).takeIf { it.effectiveSets > 0.0 }
        }
        .sortedWith(
            compareByDescending<MuscleLoad> { it.intensity.ordinal }
                .thenByDescending { it.effectiveSets }
                .thenByDescending { it.primarySets },
        )
}
