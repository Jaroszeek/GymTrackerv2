package com.jaroszeek.gymtracker.domain.routine

import com.jaroszeek.gymtracker.domain.settings.WeightUnit
import kotlinx.serialization.Serializable

/** [key] is how the type is stored. */
enum class SetType(val key: String) {
    Warmup("warmup"),
    Working("working"),
    DropSet("drop"),
    Failure("failure"),
    ;

    /** Counts toward the target sets and progression (warm-ups and drop sets do not). */
    val isWork: Boolean get() = this == Working || this == Failure

    companion object {
        fun fromKey(key: String): SetType = entries.first { it.key == key }
    }
}

@Serializable
data class PerformedSet(val weightKg: Double?, val reps: Int, val type: SetType = SetType.Working)

/**
 * Double progression: work within the rep range at a fixed weight; once every working set reaches the
 * top of the range, add weight and start again from the bottom of the range.
 */
object DoubleProgression {
    /**
     * Weight to use next time, or null when the lifter should stay at the current weight (or there is
     * no weight to progress, e.g. bodyweight work or no history). Warm-up and drop sets do not count.
     * The weight is rounded in [unit], the one masses are shown in, so 135 lb + 5 lb is 140 lb.
     */
    fun nextWeight(targets: RoutineTargets, lastSession: List<PerformedSet>, unit: WeightUnit = WeightUnit.Kg, incrementKg: Double = unit.stepKg): Double? {
        val working = lastSession.filter { it.type.isWork }
        if (working.size < targets.sets) return null
        val weights = working.map { it.weightKg ?: return null }
        // The weight every working set was done with at least; below the target weight means not there yet
        val base = weights.min()
        if (targets.weightKg != null && base < targets.weightKg) return null
        if (working.any { it.reps < targets.repsMax }) return null
        return unit.roundKg(base + incrementKg)
    }
}
