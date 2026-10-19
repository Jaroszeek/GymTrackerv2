package com.jaroszeek.gymtracker.data.progress

import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.data.workout.WorkoutRepository
import com.jaroszeek.gymtracker.domain.exercise.BodyPart
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.progress.Dated
import com.jaroszeek.gymtracker.domain.progress.ExerciseSession
import com.jaroszeek.gymtracker.domain.progress.RepBest
import com.jaroszeek.gymtracker.domain.progress.RoutineHistory
import com.jaroszeek.gymtracker.domain.progress.RoutineSession
import com.jaroszeek.gymtracker.domain.progress.Week
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.RoutineDraft
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import com.jaroszeek.gymtracker.domain.workout.WorkoutExercise
import com.jaroszeek.gymtracker.domain.workout.WorkoutSet
import com.jaroszeek.gymtracker.domain.workout.WorkoutVariant
import com.jaroszeek.gymtracker.testing.inMemoryDatabase
import com.jaroszeek.gymtracker.testing.seeder
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressRepositoryTest {

    private val database = inMemoryDatabase()
    private val exercises = ExerciseRepository(database, seeder(database), Dispatchers.IO)
    private val routines = RoutineRepository(database, exercises, Clock.systemUTC(), Dispatchers.IO)
    private val workouts = WorkoutRepository(database, Dispatchers.IO)
    private val progress = ProgressRepository(database, Dispatchers.IO)

    @After
    fun close() = database.close()

    private suspend fun exercise(name: String): Exercise = exercises.observeAll().first().single { it.name == name }

    /** Saves a finished workout of an hour; every pair is an exercise and its sets. */
    private suspend fun save(startedAt: Long, vararg done: Pair<Long, List<PerformedSet>>, routineId: Long? = null): Long {
        val entries = done.mapIndexed { index, (exerciseId, sets) ->
            WorkoutExercise(index * 100L + 1, listOf(WorkoutVariant(exerciseId)), sets = sets.mapIndexed { i, s -> WorkoutSet(index * 100L + i + 2, s.type, s.weightKg, s.reps, done = true) })
        }
        return workouts.save(ActiveWorkout(routineId, "Push", startedAt, startedAt, entries), endedAt = startedAt + HOUR)!!
    }

    @Test
    fun exerciseSessionsAreAggregatedPerWorkout() = runTest {
        val bench = exercise("Barbell Bench Press - Medium Grip").id
        val first = save(1_000, bench to listOf(PerformedSet(40.0, 10, SetType.Warmup), PerformedSet(60.0, 10), PerformedSet(65.0, 6)))
        // The exercise twice in one workout is still one session; 14 reps give no e1RM
        val second = save(
            HOUR * 2,
            bench to listOf(PerformedSet(70.0, 14)),
            exercise("Butterfly").id to listOf(PerformedSet(30.0, 12)),
            bench to listOf(PerformedSet(null, 20)),
        )

        val sessions = progress.observeExercise(bench).first().sessions
        assertEquals(
            listOf(
                ExerciseSession(first, 1_000, 65.0, 60.0 * (1 + 10 / 30.0), 600.0, 600.0 + 390.0, 10),
                ExerciseSession(second, HOUR * 2, 70.0, null, 980.0, 980.0, 20),
            ),
            sessions,
        )
    }

    @Test
    fun recordsCarryTheDateTheyWereFirstReached() = runTest {
        val squat = exercise("Barbell Squat").id
        save(1_000, squat to listOf(PerformedSet(100.0, 5), PerformedSet(90.0, 8)))
        save(HOUR, squat to listOf(PerformedSet(100.0, 5), PerformedSet(80.0, 8), PerformedSet(null, 15)))
        save(HOUR * 2, squat to listOf(PerformedSet(102.5, 3)))

        val records = progress.observeExercise(squat).first().records()
        assertEquals(Dated(102.5, HOUR * 2), records.weight)
        assertEquals(Dated(100.0 * (1 + 5 / 30.0), 1_000L), records.e1rm)
        assertEquals(RepBest(100.0, 5), records.e1rmFrom)
        assertEquals(Dated(720.0, 1_000L), records.setVolume)
        assertEquals(Dated(1_220.0, 1_000L), records.sessionVolume)
        assertEquals(listOf(Dated(RepBest(102.5, 3), HOUR * 2), Dated(RepBest(100.0, 5), 1_000L), Dated(RepBest(90.0, 8), 1_000L), Dated(RepBest(null, 15), HOUR)), records.reps)
    }

    @Test
    fun listsShowExercisesAndRoutinesWithHistory() = runTest {
        val press = exercise("Leg Press")
        val dips = exercise("Dips - Chest Version").id
        val routineId = routines.save(null, RoutineDraft(name = "Legs").addBlock(press))
        save(1_000, press.id to listOf(PerformedSet(150.0, 10)), routineId = routineId)
        val second = save(HOUR, press.id to listOf(PerformedSet(160.0, 8)), routineId = routineId)
        save(HOUR * 2, dips to listOf(PerformedSet(null, 12), PerformedSet(null, 10)))

        val listed = progress.observeExercises().first()
        assertEquals(listOf(dips, press.id), listed.map { it.exerciseId })
        assertEquals(listOf(1, 2), listed.map { it.sessions })
        assertEquals(listOf(null, 160.0), listed.map { it.maxWeightKg })
        assertEquals(12, listed[0].maxReps)
        assertEquals(listOf(RoutineHistory(routineId, "Legs", 2, HOUR)), progress.observeRoutines().first())
        assertEquals(RoutineSession(second, HOUR, 1_280.0, 160.0), progress.observeRoutineSessions(routineId).first().last())
    }

    @Test
    fun weeklySetsCountWorkingSetsOfTheWeekPerBodyPart() = runTest {
        val zone = ZoneId.of("Europe/Warsaw")
        val week = Week(LocalDate.of(2026, 10, 19))
        val bench = exercise("Barbell Bench Press - Medium Grip")
        val row = exercise("Bent Over Barbell Row")
        val start = week.startMillis(zone)
        save(start + HOUR, bench.id to listOf(PerformedSet(40.0, 10, SetType.Warmup), PerformedSet(60.0, 10), PerformedSet(60.0, 9)))
        save(start + HOUR * 30, row.id to listOf(PerformedSet(60.0, 10)))
        // The week before and the week after do not count
        save(start - HOUR, bench.id to listOf(PerformedSet(60.0, 10)))
        save(week.endMillis(zone), bench.id to listOf(PerformedSet(60.0, 10)))

        val sets = progress.observeWeek(week, zone).first()
        val expected = BodyPart.entries.associateWith { part ->
            listOf(bench, bench, row).count { exercise -> exercise.primaryMuscles.any { it in part.muscles } }
        }
        assertEquals(expected, sets.perPart)
        assertEquals(2, sets.perPart[BodyPart.Chest])
        assertEquals(3, sets.total)
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
