package com.jaroszeek.gymtracker.data.workout

import androidx.room3.withWriteTransaction
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.database.WorkoutEntity
import com.jaroszeek.gymtracker.core.database.WorkoutExerciseEntity
import com.jaroszeek.gymtracker.core.database.WorkoutSetEntity
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class WorkoutRepository @Inject constructor(
    private val database: GtDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao = database.workoutDao()

    /** Sets of the last finished session with [exerciseId]: the "last time" hints and the progression input. */
    suspend fun lastSession(exerciseId: Long): List<PerformedSet> = withContext(io) {
        dao.lastSession(exerciseId).map { PerformedSet(it.weightKg, it.reps, SetType.fromKey(it.type)) }
    }

    /**
     * Saves the ticked-off sets as a finished workout; returns its id, or null when nothing was ticked off.
     * A routine, variant or exercise deleted while the workout ran is dropped instead of failing the save.
     */
    suspend fun save(workout: ActiveWorkout, endedAt: Long): Long? = database.withWriteTransaction {
        val done = workout.exercises.mapNotNull { exercise -> exercise.sets.filter { it.done }.takeIf { it.isNotEmpty() }?.let { exercise to it } }
        val exerciseIds = dao.existingExercises(done.map { it.first.variant.exerciseId }).toSet()
        val kept = done.filter { it.first.variant.exerciseId in exerciseIds }
        if (kept.isEmpty()) return@withWriteTransaction null
        val variantIds = dao.existingVariants(kept.mapNotNull { it.first.variant.routineVariantId }).toSet()
        val workoutId = dao.insertWorkout(
            WorkoutEntity(
                routineId = workout.routineId?.takeIf { dao.routineExists(it) },
                name = workout.name,
                startedAt = workout.startedAt,
                endedAt = endedAt,
            ),
        )
        kept.forEachIndexed { position, (exercise, sets) ->
            val variant = exercise.variant
            val rowId = dao.insertExercise(
                WorkoutExerciseEntity(
                    workoutId = workoutId,
                    exerciseId = variant.exerciseId,
                    position = position,
                    routineVariantId = variant.routineVariantId?.takeIf { it in variantIds },
                ),
            )
            dao.insertSets(sets.mapIndexed { index, set -> WorkoutSetEntity(0, rowId, index, set.type.key, set.weightKg, set.reps ?: 0) })
        }
        workoutId
    }
}
