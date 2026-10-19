package com.jaroszeek.gymtracker.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Transaction
    @Query("SELECT * FROM exercises")
    fun observeAll(): Flow<List<ExerciseWithMuscles>>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: Long): Flow<ExerciseWithMuscles?>

    @Query("SELECT COUNT(*) FROM exercises WHERE source != 'custom'")
    suspend fun countSeeded(): Int

    @Insert
    suspend fun insert(exercise: ExerciseEntity): Long

    @Insert
    suspend fun insertMuscles(muscles: List<ExerciseMuscleEntity>)

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("DELETE FROM exercise_muscles WHERE exercise_id = :exerciseId")
    suspend fun deleteMuscles(exerciseId: Long)

    /** Only custom exercises can be deleted; returns the number of deleted rows. */
    @Query("DELETE FROM exercises WHERE id = :id AND source = 'custom'")
    suspend fun deleteCustom(id: Long): Int
}
