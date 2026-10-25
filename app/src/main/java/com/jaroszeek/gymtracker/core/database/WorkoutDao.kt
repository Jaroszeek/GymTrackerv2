package com.jaroszeek.gymtracker.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.jaroszeek.gymtracker.domain.progress.OneRepMax
import kotlinx.coroutines.flow.Flow

// Building blocks of the progress queries: 'warmup' is SetType.Warmup.key, a weight of 0 is bodyweight,
// and the e1RM is OneRepMax.epley (WorkoutRepositoryTest compares the two)
private const val SETS = "workout_sets s JOIN workout_exercises we ON we.id = s.workout_exercise_id JOIN workouts w ON w.id = we.workout_id"
private const val WORK = "s.type != 'warmup'"
private const val WEIGHT = "CASE WHEN s.weight_kg > 0 THEN s.weight_kg END"
private const val VOLUME = "CASE WHEN s.weight_kg > 0 THEN s.weight_kg * s.reps END"
private const val E1RM = "CASE WHEN s.weight_kg > 0 AND s.reps = 1 THEN s.weight_kg " +
    "WHEN s.weight_kg > 0 AND s.reps BETWEEN 2 AND ${OneRepMax.MAX_REPS} THEN s.weight_kg * (1 + s.reps / ${OneRepMax.EPLEY_DIVISOR}) END"
private const val EXERCISE_SESSIONS = "SELECT w.id AS workoutId, w.started_at AS startedAt, MAX($WEIGHT) AS maxWeightKg, MAX($E1RM) AS e1rmKg, " +
    "MAX($VOLUME) AS bestSetVolumeKg, COALESCE(SUM($VOLUME), 0) AS volumeKg, MAX(s.reps) AS maxReps " +
    "FROM $SETS WHERE we.exercise_id = :exerciseId AND $WORK GROUP BY w.id ORDER BY w.started_at, w.id"
private const val REPS_DONE = "SELECT weightKg, reps, MIN(startedAt) AS firstAt FROM (" +
    "SELECT $WEIGHT AS weightKg, s.reps AS reps, w.started_at AS startedAt FROM $SETS WHERE we.exercise_id = :exerciseId AND $WORK" +
    ") GROUP BY weightKg, reps"

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

    // History

    @Query(
        "SELECT w.id, w.name, w.started_at AS startedAt, w.ended_at AS endedAt, w.hc_session_id AS hcSessionId, w.routine_id AS routineId, " +
            "COUNT(DISTINCT we.id) AS exercises, " +
            "COUNT(CASE WHEN $WORK THEN 1 END) AS sets, COALESCE(SUM(CASE WHEN $WORK THEN $VOLUME END), 0) AS volumeKg " +
            "FROM $SETS GROUP BY w.id ORDER BY w.started_at DESC, w.id DESC",
    )
    fun observeHistory(): Flow<List<WorkoutListRow>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeWorkout(id: Long): Flow<WorkoutWithExercises?>

    /** Newest first. */
    @Transaction
    @Query("SELECT * FROM workouts WHERE started_at >= :since ORDER BY started_at DESC, id DESC")
    fun observeWorkoutsSince(since: Long): Flow<List<WorkoutWithExercises>>

    @Query("SELECT id, name FROM exercises WHERE id IN (:ids)")
    fun observeExerciseNames(ids: Collection<Long>): Flow<List<ExerciseNameRow>>

    @Query("UPDATE workouts SET name = :name, started_at = :startedAt, ended_at = :endedAt WHERE id = :id")
    suspend fun updateWorkout(id: Long, name: String, startedAt: Long, endedAt: Long)

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun workout(id: Long): WorkoutEntity?

    // The link to a watch session (Etap 7)

    @Query("SELECT started_at AS startedAt, ended_at AS endedAt, hc_session_id AS hcSessionId, hc_match_dismissed AS hcMatchDismissed FROM workouts WHERE id = :id")
    fun observeHealthLink(id: Long): Flow<WorkoutHealthRow?>

    @Query("UPDATE workouts SET hc_session_id = :sessionId WHERE id = :id")
    suspend fun setHealthSession(id: Long, sessionId: String?)

    @Query("UPDATE workouts SET hc_match_dismissed = 1 WHERE id = :id")
    suspend fun dismissHealthMatch(id: Long)

    @Query("SELECT hc_session_id FROM workouts WHERE hc_session_id IS NOT NULL")
    fun observeLinkedSessions(): Flow<List<String>>

    /** Workouts without a session that were not unlinked by hand: the ones linked by themselves. */
    @Query("SELECT id, started_at AS startedAt, ended_at AS endedAt FROM workouts WHERE hc_session_id IS NULL AND hc_match_dismissed = 0")
    fun observeUnlinked(): Flow<List<WorkoutSpanRow>>

    /**
     * Links only while the workout has no session, was not unlinked by hand and [sessionId] is no other
     * workout's, so a link worked out from an outdated read changes nothing. Returns the rows changed.
     */
    @Query(
        "UPDATE workouts SET hc_session_id = :sessionId WHERE id = :id AND hc_session_id IS NULL AND hc_match_dismissed = 0 " +
            "AND NOT EXISTS (SELECT 1 FROM workouts WHERE hc_session_id = :sessionId)",
    )
    suspend fun linkIfFree(id: Long, sessionId: String): Int

    /** One statement, so automatic linking cannot link the workout again between the two changes. */
    @Query("UPDATE workouts SET hc_session_id = NULL, hc_match_dismissed = 1 WHERE id = :id")
    suspend fun unlinkHealthSession(id: Long)

    /** Sets go with them (cascade). */
    @Query("DELETE FROM workout_exercises WHERE workout_id = :workoutId")
    suspend fun deleteExercises(workoutId: Long)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    // Progress, aggregated per session

    @Query(EXERCISE_SESSIONS)
    fun observeExerciseSessions(exerciseId: Long): Flow<List<ExerciseSessionRow>>

    @Query(EXERCISE_SESSIONS)
    suspend fun exerciseSessions(exerciseId: Long): List<ExerciseSessionRow>

    @Query(REPS_DONE)
    fun observeRepsDone(exerciseId: Long): Flow<List<RepsRow>>

    @Query(REPS_DONE)
    suspend fun repsDone(exerciseId: Long): List<RepsRow>

    @Query(
        "SELECT we.exercise_id AS exerciseId, e.name AS name, COUNT(DISTINCT w.id) AS sessions, MAX(w.started_at) AS lastAt, " +
            "MAX($E1RM) AS e1rmKg, MAX($WEIGHT) AS maxWeightKg, MAX(s.reps) AS maxReps " +
            "FROM $SETS JOIN exercises e ON e.id = we.exercise_id WHERE $WORK GROUP BY we.exercise_id ORDER BY lastAt DESC, e.name",
    )
    fun observeExercisesWithHistory(): Flow<List<ExerciseHistoryRow>>

    @Query(
        "SELECT r.id AS routineId, r.name AS name, COUNT(*) AS sessions, MAX(w.started_at) AS lastAt " +
            "FROM workouts w JOIN routines r ON r.id = w.routine_id GROUP BY r.id ORDER BY lastAt DESC, r.name",
    )
    fun observeRoutinesWithHistory(): Flow<List<RoutineHistoryRow>>

    @Query(
        "SELECT w.id AS workoutId, w.started_at AS startedAt, COALESCE(SUM(CASE WHEN $WORK THEN $VOLUME END), 0) AS volumeKg, " +
            "MAX(CASE WHEN $WORK THEN $WEIGHT END) AS maxWeightKg " +
            "FROM $SETS WHERE w.routine_id = :routineId GROUP BY w.id ORDER BY w.started_at, w.id",
    )
    fun observeRoutineSessions(routineId: Long): Flow<List<RoutineSessionRow>>

    @Query(
        "SELECT s.id AS setId, m.muscle AS muscle FROM $SETS " +
            "JOIN exercise_muscles m ON m.exercise_id = we.exercise_id AND m.is_primary = 1 " +
            "WHERE $WORK AND w.started_at >= :from AND w.started_at < :to",
    )
    fun observeMuscleSets(from: Long, to: Long): Flow<List<MuscleSetRow>>
}
