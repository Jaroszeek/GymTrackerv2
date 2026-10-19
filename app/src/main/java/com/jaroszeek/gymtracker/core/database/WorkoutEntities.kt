package com.jaroszeek.gymtracker.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A finished workout. The workout in progress is not here but in a file (D-038); only ticked-off sets
 * are saved. [name] is a copy, so history keeps it after the routine is renamed or deleted.
 */
@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routine_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["routine_id"]), Index(value = ["started_at"])],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "routine_id") val routineId: Long?,
    val name: String,
    /** Epoch millis UTC. */
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
)

/** The exercise done (the chosen variant) and the routine variant whose targets it followed. */
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workout_id"], onDelete = ForeignKey.CASCADE),
        // An exercise with history cannot be deleted
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exercise_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = RoutineVariantEntity::class, parentColumns = ["id"], childColumns = ["routine_variant_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["workout_id"]), Index(value = ["exercise_id"]), Index(value = ["routine_variant_id"])],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    val position: Int,
    @ColumnInfo(name = "routine_variant_id") val routineVariantId: Long?,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(entity = WorkoutExerciseEntity::class, parentColumns = ["id"], childColumns = ["workout_exercise_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["workout_exercise_id"])],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "workout_exercise_id") val workoutExerciseId: Long,
    val position: Int,
    /** [com.jaroszeek.gymtracker.domain.routine.SetType.key] */
    val type: String,
    /** Null = bodyweight. */
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    val reps: Int,
)
