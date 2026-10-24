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

    @Query("SELECT id, external_id AS externalId FROM exercises WHERE external_id IS NOT NULL")
    suspend fun externalIds(): List<ExternalIdRow>

    /** Exercises with one of [externalIds], in a routine or in a saved workout. */
    @Query(
        """SELECT id FROM exercises WHERE external_id IN (:externalIds)
               UNION SELECT exercise_id FROM routine_variants
               UNION SELECT exercise_id FROM workout_exercises""",
    )
    fun observeCommonIds(externalIds: Collection<String>): Flow<List<Long>>

    @Query("SELECT id, external_id AS externalId, name FROM exercises WHERE source != 'custom'")
    suspend fun bundled(): List<BundledRow>

    @Query("SELECT COUNT(*) FROM exercises WHERE source != 'custom'")
    suspend fun countSeeded(): Int

    @Insert
    suspend fun insert(exercise: ExerciseEntity): Long

    /** Returns the new ids in the order of [exercises]. */
    @Insert
    suspend fun insertAll(exercises: List<ExerciseEntity>): List<Long>

    @Insert
    suspend fun insertMuscles(muscles: List<ExerciseMuscleEntity>)

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("DELETE FROM exercise_muscles WHERE exercise_id = :exerciseId")
    suspend fun deleteMuscles(exerciseId: Long)

    /** Whether a routine or a finished workout uses the exercise; such an exercise cannot be deleted (FK RESTRICT is the safety net). */
    @Query(
        """SELECT EXISTS(SELECT 1 FROM routine_variants WHERE exercise_id = :id)
               OR EXISTS(SELECT 1 FROM workout_exercises WHERE exercise_id = :id)""",
    )
    suspend fun isUsed(id: Long): Boolean

    /** Only custom exercises can be deleted; returns the number of deleted rows. */
    @Query("DELETE FROM exercises WHERE id = :id AND source = 'custom'")
    suspend fun deleteCustom(id: Long): Int
}

data class ExternalIdRow(val id: Long, val externalId: String)

data class BundledRow(val id: Long, val externalId: String, val name: String)
