package com.jaroszeek.gymtracker.domain.routine

import com.jaroszeek.gymtracker.domain.exercise.Exercise
import java.time.Instant

/** Targets of one exercise in a routine; [repsMin] == [repsMax] means a fixed rep count. */
data class RoutineTargets(
    val sets: Int = DEFAULT_SETS,
    val repsMin: Int = DEFAULT_REPS_MIN,
    val repsMax: Int = DEFAULT_REPS_MAX,
    val weightKg: Double? = null,
    /** Null = the global default rest. */
    val restSeconds: Int? = null,
) {
    companion object {
        const val DEFAULT_SETS = 3
        const val DEFAULT_REPS_MIN = 8
        const val DEFAULT_REPS_MAX = 12
    }
}

data class RoutineVariant(val id: Long, val exercise: Exercise, val targets: RoutineTargets)

/** A single exercise, or several alternatives of which one is picked during the workout. */
data class RoutineBlock(val id: Long, val variants: List<RoutineVariant>)

data class Routine(
    val id: Long,
    val name: String,
    val blocks: List<RoutineBlock>,
    val createdAt: Instant,
) {
    val totalSets: Int get() = blocks.sumOf { it.variants.first().targets.sets }
}
