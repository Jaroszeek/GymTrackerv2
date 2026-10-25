package com.jaroszeek.gymtracker.data.workout

import androidx.room3.withWriteTransaction
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.database.WorkoutEntity
import com.jaroszeek.gymtracker.core.database.WorkoutExerciseEntity
import com.jaroszeek.gymtracker.core.database.WorkoutSetEntity
import com.jaroszeek.gymtracker.core.database.WorkoutWithExercises
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.data.progress.toDomain
import com.jaroszeek.gymtracker.domain.health.UnlinkedWorkout
import com.jaroszeek.gymtracker.domain.progress.ExerciseProgress
import com.jaroszeek.gymtracker.domain.progress.PersonalBests
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import com.jaroszeek.gymtracker.domain.workout.ExerciseRecords
import com.jaroszeek.gymtracker.domain.workout.HealthLink
import com.jaroszeek.gymtracker.domain.workout.LoggedExercise
import com.jaroszeek.gymtracker.domain.workout.LoggedSet
import com.jaroszeek.gymtracker.domain.workout.LoggedWorkout
import com.jaroszeek.gymtracker.domain.workout.WorkoutListItem
import com.jaroszeek.gymtracker.domain.workout.WorkoutSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class WorkoutRepository @Inject constructor(
    private val database: GtDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao = database.workoutDao()
    private val _changed = MutableSharedFlow<Long>(extraBufferCapacity = CHANGE_BUFFER)

    /** Ids of workouts saved, edited, linked or unlinked, and deleted: what Health Connect gets a copy of. */
    val changed: SharedFlow<Long> = _changed.asSharedFlow()

    /** Sets of the last finished session with [exerciseId]: the "last time" hints and the progression input. */
    suspend fun lastSession(exerciseId: Long): List<PerformedSet> = withContext(io) {
        dao.lastSession(exerciseId).map { PerformedSet(it.weightKg, it.reps, SetType.fromKey(it.type)) }
    }

    /** Bests of [exerciseId] so far, which the sets of a new workout are compared with. */
    suspend fun personalBests(exerciseId: Long): PersonalBests = withContext(io) { progress(exerciseId).bestsBefore(Long.MAX_VALUE) }

    /** Each exercise's bests from before [workout], for its record badges. */
    suspend fun bestsBefore(workout: LoggedWorkout): Map<Long, PersonalBests> = withContext(io) {
        workout.exercises.map { it.exerciseId }.distinct().associateWith { progress(it).bestsBefore(workout.startedAt) }
    }

    /**
     * Saves the ticked-off sets as a finished workout; returns its id, or null when nothing was ticked off.
     * A routine, variant or exercise deleted while the workout ran is dropped instead of failing the save.
     */
    suspend fun save(workout: ActiveWorkout, endedAt: Long): Long? = database.withWriteTransaction {
        val done = workout.exercises.mapNotNull { exercise ->
            val sets = exercise.sets.filter { it.done }.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            LoggedExercise(0, exercise.variant.exerciseId, exercise.variant.routineVariantId, sets.map { LoggedSet(0, it.type, it.weightKg, it.reps ?: 0, it.doneAt) })
        }
        val kept = existing(done)
        if (kept.isEmpty()) return@withWriteTransaction null
        val workoutId = dao.insertWorkout(
            WorkoutEntity(
                routineId = workout.routineId?.takeIf { dao.routineExists(it) },
                name = workout.name,
                startedAt = workout.startedAt,
                endedAt = endedAt,
            ),
        )
        insertExercises(workoutId, kept)
        workoutId
    }.also { id -> if (id != null) _changed.tryEmit(id) }

    fun observeHistory(): Flow<List<WorkoutListItem>> = dao.observeHistory()
        .map { rows ->
            rows.map {
                WorkoutListItem(it.id, it.name, it.startedAt, WorkoutSummary(it.endedAt - it.startedAt, it.volumeKg, it.sets, it.exercises), it.hcSessionId, it.routineId)
            }
        }
        .flowOn(io)

    fun observeWorkout(id: Long): Flow<LoggedWorkout?> = dao.observeWorkout(id).map { it?.toDomain() }.flowOn(io)

    /** Records set by the workouts started since [since], per workout (newest first) and exercise. */
    // Names come from their own query, so renaming an exercise shows up too
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeRecordsSince(since: Long): Flow<List<ExerciseRecords>> = dao.observeWorkoutsSince(since)
        .map { rows ->
            rows.map { it.toDomain() }.flatMap { workout ->
                workout.recordHits(bestsBefore(workout)).groupBy { it.exerciseId }.map { (exerciseId, hits) ->
                    ExerciseRecords(workout.id, workout.startedAt, exerciseId, exerciseName = "", hits)
                }
            }
        }
        .flatMapLatest { records ->
            dao.observeExerciseNames(records.mapTo(HashSet()) { it.exerciseId }).map { rows ->
                val names = rows.associate { it.id to it.name }
                records.mapNotNull { record -> names[record.exerciseId]?.let { record.copy(exerciseName = it) } }
            }
        }
        .flowOn(io)

    /**
     * Replaces the workout's name, start, end and sets with the edited ones; false when none of its exercises
     * exists any more, in which case nothing changes. A new start re-sorts the history, and records, being
     * worked out on reading (D-054), follow by themselves.
     */
    suspend fun update(workout: LoggedWorkout): Boolean = database.withWriteTransaction {
        val kept = existing(workout.exercises)
        if (kept.isEmpty()) return@withWriteTransaction false
        dao.updateWorkout(workout.id, workout.name.trim(), workout.startedAt, workout.endedAt)
        dao.deleteExercises(workout.id)
        insertExercises(workout.id, kept)
        true
    }.also { if (it) _changed.tryEmit(workout.id) }

    suspend fun delete(id: Long) {
        withContext(io) { dao.deleteWorkout(id) }
        _changed.tryEmit(id)
    }

    fun observeHealthLink(id: Long): Flow<HealthLink?> =
        dao.observeHealthLink(id).map { row -> row?.let { HealthLink(it.startedAt, it.endedAt, it.hcSessionId, it.hcMatchDismissed) } }.flowOn(io)

    /** Linked session ids, which the history shows only as part of their workout. */
    fun observeLinkedSessions(): Flow<Set<String>> = dao.observeLinkedSessions().map { it.toSet() }.flowOn(io)

    /** [sessionId] null unlinks. */
    suspend fun linkHealthSession(id: Long, sessionId: String?) {
        withContext(io) { dao.setHealthSession(id, sessionId) }
        _changed.tryEmit(id)
    }

    suspend fun dismissHealthMatch(id: Long) = withContext(io) { dao.dismissHealthMatch(id) }

    /** Workouts without a session that were not unlinked by hand: what [autoLinkHealthSession] links. */
    fun observeUnlinked(): Flow<List<UnlinkedWorkout>> =
        dao.observeUnlinked().map { rows -> rows.map { UnlinkedWorkout(it.id, it.startedAt, it.endedAt) } }.flowOn(io)

    /**
     * A link made by itself; false, and nothing changes, when the workout or the session got linked
     * meanwhile or the workout was unlinked by hand.
     */
    suspend fun autoLinkHealthSession(id: Long, sessionId: String): Boolean =
        (withContext(io) { dao.linkIfFree(id, sessionId) } > 0).also { if (it) _changed.tryEmit(id) }

    /** Unlinked on purpose: dismissed in the same step, so the session is neither proposed nor linked again. */
    suspend fun unlinkHealthSession(id: Long) {
        withContext(io) { dao.unlinkHealthSession(id) }
        _changed.tryEmit(id)
    }

    /** The workout as Health Connect gets it: name, span and whether a watch session already stands for it. */
    suspend fun forHealth(id: Long): WorkoutEntity? = withContext(io) { dao.workout(id) }

    private suspend fun progress(exerciseId: Long) =
        ExerciseProgress(dao.exerciseSessions(exerciseId).map { it.toDomain() }, dao.repsDone(exerciseId).map { it.toDomain() })

    /** Exercises that still exist, linked only to routine variants that still exist. */
    private suspend fun existing(exercises: List<LoggedExercise>): List<LoggedExercise> {
        val exerciseIds = dao.existingExercises(exercises.map { it.exerciseId }).toSet()
        val variantIds = dao.existingVariants(exercises.mapNotNull { it.routineVariantId }).toSet()
        return exercises
            .filter { it.exerciseId in exerciseIds && it.sets.isNotEmpty() }
            .map { exercise -> exercise.copy(routineVariantId = exercise.routineVariantId?.takeIf { it in variantIds }) }
    }

    private suspend fun insertExercises(workoutId: Long, exercises: List<LoggedExercise>) {
        exercises.forEachIndexed { position, exercise ->
            val rowId = dao.insertExercise(
                WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exercise.exerciseId, position = position, routineVariantId = exercise.routineVariantId),
            )
            dao.insertSets(exercise.sets.mapIndexed { index, set -> WorkoutSetEntity(0, rowId, index, set.type.key, set.weightKg, set.reps, set.doneAt) })
        }
    }
}

private const val CHANGE_BUFFER = 64

private fun WorkoutWithExercises.toDomain() = LoggedWorkout(
    id = workout.id,
    routineId = workout.routineId,
    name = workout.name,
    startedAt = workout.startedAt,
    endedAt = workout.endedAt,
    exercises = exercises.sortedBy { it.exercise.position }.map { row ->
        LoggedExercise(
            id = row.exercise.id,
            exerciseId = row.exercise.exerciseId,
            routineVariantId = row.exercise.routineVariantId,
            sets = row.sets.sortedBy { it.position }.map { LoggedSet(it.id, SetType.fromKey(it.type), it.weightKg, it.reps, it.doneAt) },
        )
    },
)
