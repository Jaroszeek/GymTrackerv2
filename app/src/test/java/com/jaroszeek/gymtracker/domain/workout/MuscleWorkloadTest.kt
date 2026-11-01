package com.jaroszeek.gymtracker.domain.workout

import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.exercise.ExerciseCategory
import com.jaroszeek.gymtracker.domain.exercise.ExerciseSource
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import com.jaroszeek.gymtracker.domain.routine.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleWorkloadTest {

    private fun exercise(
        id: Long,
        primary: Set<Muscle>,
        secondary: Set<Muscle> = emptySet(),
    ) = Exercise(
        id = id,
        name = "Exercise $id",
        aliases = emptyList(),
        category = ExerciseCategory.Strength,
        equipment = Equipment.Barbell,
        level = null,
        mechanic = null,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        instructions = emptyList(),
        images = emptyList(),
        source = ExerciseSource.Builtin,
    )

    @Test
    fun activeWorkoutCountsOnlyTickedNonWarmupSetsPerExercise() {
        val workout = ActiveWorkout(
            routineId = null,
            name = "Push",
            startedAt = 1_000L,
            updatedAt = 2_000L,
            exercises = listOf(
                WorkoutExercise(
                    id = 1,
                    variants = listOf(WorkoutVariant(exerciseId = 10)),
                    sets = listOf(
                        WorkoutSet(id = 2, type = SetType.Warmup, weightKg = 40.0, reps = 10, done = true),
                        WorkoutSet(id = 3, type = SetType.Working, weightKg = 80.0, reps = 8, done = true),
                        WorkoutSet(id = 4, type = SetType.Failure, weightKg = 80.0, reps = 6, done = true),
                        WorkoutSet(id = 5, type = SetType.Working, weightKg = 80.0, reps = 8, done = false),
                    ),
                ),
                WorkoutExercise(
                    id = 6,
                    variants = listOf(WorkoutVariant(exerciseId = 10)),
                    sets = listOf(
                        WorkoutSet(id = 7, type = SetType.DropSet, weightKg = 60.0, reps = 10, done = true),
                    ),
                ),
            ),
        )

        assertEquals(mapOf(10L to 3), workout.exerciseWorkingSets())
    }

    @Test
    fun loggedWorkoutCountsOnlyPositiveRepNonWarmupSets() {
        val workout = LoggedWorkout(
            id = 1,
            routineId = null,
            name = "Pull",
            startedAt = 1_000L,
            endedAt = 2_000L,
            exercises = listOf(
                LoggedExercise(
                    id = 1,
                    exerciseId = 20,
                    routineVariantId = null,
                    sets = listOf(
                        LoggedSet(id = 2, type = SetType.Warmup, weightKg = 30.0, reps = 12),
                        LoggedSet(id = 3, type = SetType.Working, weightKg = 70.0, reps = 8),
                        LoggedSet(id = 4, type = SetType.Working, weightKg = 70.0, reps = 0),
                    ),
                ),
            ),
        )

        assertEquals(mapOf(20L to 1), workout.exerciseWorkingSets())
    }

    @Test
    fun muscleWorkloadWeightsPrimaryAndSecondaryAndOrdersByIntensity() {
        val bench = exercise(1, primary = setOf(Muscle.Chest), secondary = setOf(Muscle.Shoulders, Muscle.Triceps))
        val squat = exercise(2, primary = setOf(Muscle.Quadriceps), secondary = setOf(Muscle.Glutes, Muscle.Abdominals))
        val exercises = mapOf(1L to bench, 2L to squat)

        val loads = muscleWorkload(mapOf(1L to 4, 2L to 2), exercises)

        assertEquals(
            listOf(
                MuscleLoad(Muscle.Chest, primarySets = 4, secondarySets = 0),
                MuscleLoad(Muscle.Quadriceps, primarySets = 2, secondarySets = 0),
                MuscleLoad(Muscle.Shoulders, primarySets = 0, secondarySets = 4),
                MuscleLoad(Muscle.Triceps, primarySets = 0, secondarySets = 4),
                MuscleLoad(Muscle.Abdominals, primarySets = 0, secondarySets = 2),
                MuscleLoad(Muscle.Glutes, primarySets = 0, secondarySets = 2),
            ),
            loads,
        )
        assertEquals(MuscleIntensity.High, loads.first { it.muscle == Muscle.Chest }.intensity)
        assertEquals(MuscleIntensity.Medium, loads.first { it.muscle == Muscle.Quadriceps }.intensity)
        // Secondary-only muscles stay Low even when effectiveSets >= 2.0
        assertEquals(MuscleIntensity.Low, loads.first { it.muscle == Muscle.Shoulders }.intensity)
        assertTrue(loads.none { it.muscle == Muscle.Biceps })
    }
}
