package com.jaroszeek.gymtracker.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Insert
    suspend fun insertExercise(exercise: WorkoutExerciseEntity): Long

    @Insert
    suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Query("SELECT * FROM workouts ORDER BY started_at DESC")
    suspend fun workouts(): List<WorkoutEntity>

    /** Sets of the most recent workout with [exerciseId], in order. */
    @Query(
        """SELECT * FROM workout_sets WHERE workout_exercise_id = (
               SELECT we.id FROM workout_exercises we JOIN workouts w ON w.id = we.workout_id
               WHERE we.exercise_id = :exerciseId ORDER BY w.started_at DESC, we.position DESC LIMIT 1
           ) ORDER BY position""",
    )
    suspend fun lastSession(exerciseId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_exercises WHERE workout_id = :workoutId ORDER BY position")
    suspend fun exercises(workoutId: Long): List<WorkoutExerciseEntity>

    // Rows the workout points at may be deleted while it runs; saving keeps only the links that still exist

    @Query("SELECT id FROM exercises WHERE id IN (:ids)")
    suspend fun existingExercises(ids: Collection<Long>): List<Long>

    @Query("SELECT id FROM routine_variants WHERE id IN (:ids)")
    suspend fun existingVariants(ids: Collection<Long>): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM routines WHERE id = :id)")
    suspend fun routineExists(id: Long): Boolean
}
