package com.jaroszeek.gymtracker.domain.workout

import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutResultTest {

    private fun workout(vararg exercises: WorkoutExercise) =
        ActiveWorkout(routineId = 1, name = "Push", startedAt = 1_000, updatedAt = 1_000, exercises = exercises.toList())

    private fun exercise(id: Long, targets: RoutineTargets?, vararg sets: WorkoutSet, variantId: Long? = id * 10) =
        WorkoutExercise(id, listOf(WorkoutVariant(exerciseId = id * 100, routineVariantId = variantId, targets = targets)), sets = sets.toList())

    private fun set(id: Long, weight: Double?, reps: Int, done: Boolean = true, type: SetType = SetType.Working) = WorkoutSet(id, type, weight, reps, done)

    @Test
    fun summaryCountsTickedOffWorkWithoutWarmups() {
        val workout = workout(
            exercise(1, null, set(11, 40.0, 10, type = SetType.Warmup), set(12, 60.0, 10), set(13, 60.0, 8), set(14, 60.0, 8, done = false)),
            exercise(2, null, set(21, null, 12), set(22, 20.0, 15, type = SetType.DropSet)),
            exercise(3, null, set(31, 50.0, 10, done = false)),
        )
        val summary = workout.summary(endedAt = 3_601_000)
        assertEquals(3_600_000L, summary.durationMillis)
        assertEquals(60.0 * 10 + 60.0 * 8 + 20.0 * 15, summary.volumeKg, 0.001)
        assertEquals(4, summary.sets)
        assertEquals(2, summary.exercises)
    }

    @Test
    fun nothingTickedOffIsAnEmptySummary() {
        assertTrue(workout(exercise(1, null, set(11, 60.0, 10, done = false))).summary(2_000).isEmpty)
    }

    @Test
    fun changesProposeTheDoneWorkingSetsAndTheHeaviestWeight() {
        val targets = RoutineTargets(3, 8, 12, 60.0, 120)
        val workout = workout(
            // Four working sets, top set 65 kg; the warm-up and the drop set do not count
            exercise(1, targets, set(11, 40.0, 10, type = SetType.Warmup), set(12, 62.5, 10), set(13, 65.0, 8), set(14, 62.5, 9), set(15, 60.0, 8, type = SetType.Failure), set(16, 40.0, 12, type = SetType.DropSet)),
            // Done as planned: no change
            exercise(2, RoutineTargets(2, 10, 10, 20.0), set(21, 20.0, 10), set(22, 20.0, 10)),
            // Nothing done: no change proposed
            exercise(3, targets, set(31, 60.0, 10, done = false)),
            // Not from the routine
            exercise(4, null, set(41, 30.0, 10), variantId = null),
        )
        assertEquals(listOf(TargetChange(100, 10, targets, targets.copy(sets = 4, weightKg = 65.0))), workout.targetChanges())
    }

    @Test
    fun bodyweightWorkKeepsTheTargetWeight() {
        val targets = RoutineTargets(3, 8, 12, weightKg = 10.0)
        val workout = workout(exercise(1, targets, set(11, null, 12), set(12, null, 10)))
        assertEquals(targets.copy(sets = 2), workout.targetChanges().single().new)
    }
}
