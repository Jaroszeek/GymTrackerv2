package com.jaroszeek.gymtracker.data.progress

import com.jaroszeek.gymtracker.core.database.ExerciseSessionRow
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.database.RepsRow
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import com.jaroszeek.gymtracker.domain.progress.ExerciseHistory
import com.jaroszeek.gymtracker.domain.progress.ExerciseProgress
import com.jaroszeek.gymtracker.domain.progress.ExerciseSession
import com.jaroszeek.gymtracker.domain.progress.RepBest
import com.jaroszeek.gymtracker.domain.progress.RepsDone
import com.jaroszeek.gymtracker.domain.progress.RoutineHistory
import com.jaroszeek.gymtracker.domain.progress.RoutineSession
import com.jaroszeek.gymtracker.domain.progress.Week
import com.jaroszeek.gymtracker.domain.progress.WeekSets
import com.jaroszeek.gymtracker.domain.progress.weekSets
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Charts, records and lists of the Postępy tab, aggregated by Room per session (A5.7). */
@Singleton
class ProgressRepository @Inject constructor(
    database: GtDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao = database.workoutDao()

    fun observeExercise(exerciseId: Long): Flow<ExerciseProgress> =
        combine(dao.observeExerciseSessions(exerciseId), dao.observeRepsDone(exerciseId)) { sessions, reps ->
            ExerciseProgress(sessions.map { it.toDomain() }, reps.map { it.toDomain() })
        }.flowOn(io)

    fun observeExercises(): Flow<List<ExerciseHistory>> = dao.observeExercisesWithHistory()
        .map { rows -> rows.map { ExerciseHistory(it.exerciseId, it.name, it.sessions, it.lastAt, it.e1rmKg, it.maxWeightKg, it.maxReps) } }
        .flowOn(io)

    fun observeRoutines(): Flow<List<RoutineHistory>> = dao.observeRoutinesWithHistory()
        .map { rows -> rows.map { RoutineHistory(it.routineId, it.name, it.sessions, it.lastAt) } }
        .flowOn(io)

    fun observeRoutineSessions(routineId: Long): Flow<List<RoutineSession>> = dao.observeRoutineSessions(routineId)
        .map { rows -> rows.map { RoutineSession(it.workoutId, it.startedAt, it.volumeKg, it.maxWeightKg) } }
        .flowOn(io)

    /** Working sets of the workouts started in [week] in [zone]. */
    fun observeWeek(week: Week, zone: ZoneId): Flow<WeekSets> = dao.observeMuscleSets(week.startMillis(zone), week.endMillis(zone))
        .map { rows -> weekSets(rows.groupBy { it.setId }.values.map { set -> set.map { Muscle.fromKey(it.muscle) }.toSet() }) }
        .flowOn(io)
}

internal fun ExerciseSessionRow.toDomain() = ExerciseSession(workoutId, startedAt, maxWeightKg, e1rmKg, bestSetVolumeKg, volumeKg, maxReps)

internal fun RepsRow.toDomain() = RepsDone(RepBest(weightKg, reps), firstAt)
