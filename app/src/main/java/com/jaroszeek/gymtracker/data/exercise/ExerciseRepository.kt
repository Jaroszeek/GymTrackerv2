package com.jaroszeek.gymtracker.data.exercise

import androidx.room3.withWriteTransaction
import com.jaroszeek.gymtracker.core.database.ExerciseEntity
import com.jaroszeek.gymtracker.core.database.ExerciseMuscleEntity
import com.jaroszeek.gymtracker.core.database.ExerciseWithMuscles
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.exercise.ExerciseCategory
import com.jaroszeek.gymtracker.domain.exercise.ExerciseDraft
import com.jaroszeek.gymtracker.domain.exercise.ExerciseSource
import com.jaroszeek.gymtracker.domain.exercise.Level
import com.jaroszeek.gymtracker.domain.exercise.Mechanic
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Singleton
class ExerciseRepository @Inject constructor(
    private val database: GtDatabase,
    private val seeder: ExerciseSeeder,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao = database.exerciseDao()

    /** Seeds on first use, so the list shows a loading state instead of an empty one. */
    fun observeAll(): Flow<List<Exercise>> = flow {
        seeder.ensureSeeded()
        emitAll(dao.observeAll().map { rows -> rows.map { it.toDomain() } })
    }.flowOn(io)

    /**
     * Ids of the exercises with one of [externalIds] (the templates'), in a routine or in a saved
     * workout; the search ranks them first. Distinct, so saving a workout with the same exercises does not re-emit.
     */
    fun observeCommonIds(externalIds: Set<String>): Flow<Set<Long>> =
        dao.observeCommonIds(externalIds).map { it.toSet() }.distinctUntilChanged().flowOn(io)

    fun observe(id: Long): Flow<Exercise?> = flow {
        seeder.ensureSeeded()
        emitAll(dao.observe(id).map { it?.toDomain() })
    }.flowOn(io)

    /** Inserts a new custom exercise ([id] null) or updates an existing one; returns its id. */
    suspend fun saveCustom(id: Long?, draft: ExerciseDraft): Long = database.withWriteTransaction {
        val entity = ExerciseEntity(
            id = id ?: 0,
            externalId = null,
            name = draft.name.trim(),
            aliasesPl = emptyList(),
            category = ExerciseCategory.Strength.key,
            equipment = draft.equipment?.key,
            level = null,
            mechanic = null,
            force = null,
            instructions = draft.notes.lines().map { it.trim() }.filter { it.isNotEmpty() },
            images = emptyList(),
            source = ExerciseSource.Custom.key,
        )
        val exerciseId = if (id == null) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            dao.deleteMuscles(id)
            id
        }
        dao.insertMuscles(
            draft.primaryMuscles.map { ExerciseMuscleEntity(exerciseId, it.key, isPrimary = true) } +
                (draft.secondaryMuscles - draft.primaryMuscles).map { ExerciseMuscleEntity(exerciseId, it.key, isPrimary = false) },
        )
        exerciseId
    }

    /** Built-in exercises are never deleted, and neither is one a routine uses. */
    suspend fun deleteCustom(id: Long): DeleteResult = database.withWriteTransaction {
        if (dao.isUsed(id)) {
            DeleteResult.InUse
        } else {
            dao.deleteCustom(id)
            DeleteResult.Deleted
        }
    }
}

enum class DeleteResult { Deleted, InUse }

internal fun ExerciseWithMuscles.toDomain(): Exercise = Exercise(
    id = exercise.id,
    name = exercise.name,
    aliases = exercise.aliasesPl,
    category = ExerciseCategory.fromKey(exercise.category),
    equipment = exercise.equipment?.let(Equipment::fromKey),
    level = exercise.level?.let(Level::fromKey),
    mechanic = exercise.mechanic?.let(Mechanic::fromKey),
    primaryMuscles = muscles.filter { it.isPrimary }.map { Muscle.fromKey(it.muscle) }.toSet(),
    secondaryMuscles = muscles.filterNot { it.isPrimary }.map { Muscle.fromKey(it.muscle) }.toSet(),
    instructions = exercise.instructions,
    images = exercise.images.map { ExerciseImages.url(it) },
    source = ExerciseSource.fromKey(exercise.source),
)

/** free-exercise-db photos, pinned to the commit the bundled JSON comes from. */
internal object ExerciseImages {
    private const val BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/"

    fun url(path: String) = BASE + path
}
