package com.jaroszeek.gymtracker.data.routine

import androidx.room3.withWriteTransaction
import com.jaroszeek.gymtracker.core.database.RoutineBlockEntity
import com.jaroszeek.gymtracker.core.database.RoutineEntity
import com.jaroszeek.gymtracker.core.database.RoutineVariantEntity
import com.jaroszeek.gymtracker.core.database.RoutineWithBlocks
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.routine.Routine
import com.jaroszeek.gymtracker.domain.routine.RoutineBlock
import com.jaroszeek.gymtracker.domain.routine.RoutineDraft
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.RoutineVariant
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn

@Singleton
class RoutineRepository @Inject constructor(
    private val database: GtDatabase,
    private val exercises: ExerciseRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao = database.routineDao()

    fun observeAll(): Flow<List<Routine>> =
        combine(dao.observeAll(), exercises.observeAll()) { rows, all ->
            val byId = all.associateBy { it.id }
            rows.map { it.toDomain(byId) }
        }.flowOn(io)

    fun observe(id: Long): Flow<Routine?> =
        combine(dao.observe(id), exercises.observeAll()) { row, all -> row?.toDomain(all.associateBy { it.id }) }.flowOn(io)

    /**
     * Inserts ([id] null) or updates a routine from the editor draft. The update is a diff: saved blocks
     * and variants keep their ids, because finished workouts (Etap 4) point at them.
     */
    suspend fun save(id: Long?, draft: RoutineDraft): Long = database.withWriteTransaction {
        val now = clock.millis()
        val routineId = if (id == null) {
            dao.insertRoutine(RoutineEntity(name = draft.name.trim(), createdAt = now, updatedAt = now))
        } else {
            dao.updateRoutine(id, draft.name.trim(), now)
            id
        }
        val keptBlocks = draft.blocks.filter { it.id > 0 }.map { it.id }.toSet()
        dao.deleteBlocks(dao.blockIds(routineId) - keptBlocks)
        draft.blocks.forEachIndexed { position, block ->
            val blockId = if (block.id > 0) {
                dao.updateBlockPosition(block.id, position)
                block.id
            } else {
                dao.insertBlock(RoutineBlockEntity(routineId = routineId, position = position))
            }
            val keptVariants = block.variants.filter { it.id > 0 }.map { it.id }.toSet()
            dao.deleteVariants(dao.variantIds(blockId) - keptVariants)
            block.variants.forEachIndexed { variantPosition, variant ->
                val entity = variant.toEntity(blockId, variantPosition)
                if (variant.id > 0) dao.updateVariant(entity) else dao.insertVariant(entity)
            }
        }
        routineId
    }

    /** Copies the routine with all blocks and targets under [name]; returns the new id. */
    suspend fun duplicate(id: Long, name: String): Long {
        val routine = observe(id).first() ?: return id
        return save(null, routine.asNewDraft(name))
    }

    suspend fun delete(id: Long) = dao.deleteRoutine(id)
}

/** The same routine as a draft of new rows (all ids temporary). */
internal fun Routine.asNewDraft(name: String): RoutineDraft {
    var nextId = -1L
    return RoutineDraft(
        name = name,
        blocks = blocks.map { block ->
            RoutineBlock(nextId--, block.variants.map { it.copy(id = nextId--) })
        },
    )
}

private fun RoutineVariant.toEntity(blockId: Long, position: Int) = RoutineVariantEntity(
    id = id.coerceAtLeast(0),
    blockId = blockId,
    exerciseId = exercise.id,
    position = position,
    targetSets = targets.sets,
    repsMin = targets.repsMin,
    repsMax = targets.repsMax,
    targetWeightKg = targets.weightKg,
    restSeconds = targets.restSeconds,
)

private fun RoutineWithBlocks.toDomain(exercises: Map<Long, Exercise>) = Routine(
    id = routine.id,
    name = routine.name,
    createdAt = Instant.ofEpochMilli(routine.createdAt),
    blocks = blocks.sortedBy { it.block.position }.mapNotNull { row ->
        // FK RESTRICT keeps exercises alive, so a missing one only happens while the seed is running
        val variants = row.variants.sortedBy { it.position }.mapNotNull { variant ->
            val exercise = exercises[variant.exerciseId] ?: return@mapNotNull null
            RoutineVariant(
                id = variant.id,
                exercise = exercise,
                targets = RoutineTargets(variant.targetSets, variant.repsMin, variant.repsMax, variant.targetWeightKg, variant.restSeconds),
            )
        }
        variants.takeIf { it.isNotEmpty() }?.let { RoutineBlock(row.block.id, it) }
    },
)
