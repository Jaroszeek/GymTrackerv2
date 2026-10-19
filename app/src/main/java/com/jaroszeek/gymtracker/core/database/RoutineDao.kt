package com.jaroszeek.gymtracker.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY created_at, id")
    fun observeAll(): Flow<List<RoutineWithBlocks>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    fun observe(id: Long): Flow<RoutineWithBlocks?>

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Query("UPDATE routines SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateRoutine(id: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("SELECT id FROM routine_blocks WHERE routine_id = :routineId")
    suspend fun blockIds(routineId: Long): List<Long>

    @Insert
    suspend fun insertBlock(block: RoutineBlockEntity): Long

    @Query("UPDATE routine_blocks SET position = :position WHERE id = :id")
    suspend fun updateBlockPosition(id: Long, position: Int)

    @Query("DELETE FROM routine_blocks WHERE id IN (:ids)")
    suspend fun deleteBlocks(ids: Collection<Long>)

    @Query("SELECT id FROM routine_variants WHERE block_id = :blockId")
    suspend fun variantIds(blockId: Long): List<Long>

    @Insert
    suspend fun insertVariant(variant: RoutineVariantEntity): Long

    @Update
    suspend fun updateVariant(variant: RoutineVariantEntity)

    @Query("DELETE FROM routine_variants WHERE id IN (:ids)")
    suspend fun deleteVariants(ids: Collection<Long>)

    @Query(
        """UPDATE routine_variants SET target_sets = :sets, reps_min = :repsMin, reps_max = :repsMax,
               target_weight_kg = :weightKg, rest_seconds = :restSeconds WHERE id = :id""",
    )
    suspend fun updateTargets(id: Long, sets: Int, repsMin: Int, repsMax: Int, weightKg: Double?, restSeconds: Int?)
}
