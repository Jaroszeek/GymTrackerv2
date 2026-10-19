package com.jaroszeek.gymtracker.core.database

import androidx.room3.Embedded
import androidx.room3.Relation

/** Results of the history and progress queries in [WorkoutDao]; volumes and counts leave warm-ups out. */
data class WorkoutListRow(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val endedAt: Long,
    val exercises: Int,
    val sets: Int,
    val volumeKg: Double,
)

data class WorkoutExerciseWithSets(
    @Embedded val exercise: WorkoutExerciseEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["workout_exercise_id"]) val sets: List<WorkoutSetEntity>,
)

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumns = ["id"], entityColumns = ["workout_id"]) val exercises: List<WorkoutExerciseWithSets>,
)

data class ExerciseSessionRow(
    val workoutId: Long,
    val startedAt: Long,
    val maxWeightKg: Double?,
    val e1rmKg: Double?,
    val bestSetVolumeKg: Double?,
    val volumeKg: Double,
    val maxReps: Int,
)

/** A weight (null = bodyweight) and reps pair, and when it was first done. */
data class RepsRow(val weightKg: Double?, val reps: Int, val firstAt: Long)

data class ExerciseHistoryRow(
    val exerciseId: Long,
    val name: String,
    val sessions: Int,
    val lastAt: Long,
    val e1rmKg: Double?,
    val maxWeightKg: Double?,
    val maxReps: Int,
)

data class RoutineHistoryRow(val routineId: Long, val name: String, val sessions: Int, val lastAt: Long)

data class RoutineSessionRow(val workoutId: Long, val startedAt: Long, val volumeKg: Double, val maxWeightKg: Double?)

/** A working set and one primary muscle of its exercise. */
data class MuscleSetRow(val setId: Long, val muscle: String)
