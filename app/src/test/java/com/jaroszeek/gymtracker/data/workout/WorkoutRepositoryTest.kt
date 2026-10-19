package com.jaroszeek.gymtracker.data.workout

import com.jaroszeek.gymtracker.data.exercise.DeleteResult
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.exercise.ExerciseDraft
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import com.jaroszeek.gymtracker.domain.progress.PersonalBests
import com.jaroszeek.gymtracker.domain.progress.volumeKg
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.RoutineDraft
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import com.jaroszeek.gymtracker.domain.workout.HealthLink
import com.jaroszeek.gymtracker.domain.workout.LoggedSet
import com.jaroszeek.gymtracker.domain.workout.WorkoutExercise
import com.jaroszeek.gymtracker.domain.workout.WorkoutSet
import com.jaroszeek.gymtracker.domain.workout.WorkoutSummary
import com.jaroszeek.gymtracker.domain.workout.WorkoutVariant
import kotlin.random.Random
import com.jaroszeek.gymtracker.testing.inMemoryDatabase
import com.jaroszeek.gymtracker.testing.seeder
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRepositoryTest {

    private val database = inMemoryDatabase()
    private val exercises = ExerciseRepository(database, seeder(database), Dispatchers.IO)
    private val routines = RoutineRepository(database, exercises, Clock.systemUTC(), Dispatchers.IO)
    private val workouts = WorkoutRepository(database, Dispatchers.IO)

    @After
    fun close() = database.close()

    private suspend fun exercise(name: String): Exercise = exercises.observeAll().first().single { it.name == name }

    /** A workout with the given exercises, every set ticked off with the weights and reps given. */
    private fun workout(startedAt: Long, vararg done: Pair<WorkoutVariant, List<Pair<Double?, Int>>>): ActiveWorkout {
        var workout = ActiveWorkout(routineId = null, name = "Push", startedAt = startedAt, updatedAt = startedAt)
        done.forEach { (variant, sets) ->
            workout = workout.addExercise(listOf(variant))
            val exercise = workout.exercises.last()
            exercise.sets.drop(sets.size).forEach { workout = workout.removeSet(exercise.id, it.id) }
            while (workout.exercises.last().sets.size < sets.size) workout = workout.addSet(exercise.id)
            workout.exercises.last().sets.zip(sets).forEach { (set, values) ->
                workout = workout.updateSet(exercise.id, set.id) { it.copy(weightKg = values.first, reps = values.second, done = true) }
            }
        }
        return workout
    }

    @Test
    fun onlyTickedOffSetsAreSaved() = runTest {
        val bench = exercise("Barbell Bench Press - Medium Grip")
        var workout = workout(1_000, WorkoutVariant(bench.id) to listOf(60.0 to 10, 60.0 to 9))
        val exercise = workout.exercises.single()
        workout = workout.addSet(exercise.id).addExercise(listOf(WorkoutVariant(exercise("Butterfly").id)))

        workouts.save(workout, endedAt = 2_000)

        val saved = database.workoutDao().workouts().single()
        assertEquals(listOf("Push", 1_000L, 2_000L), listOf(saved.name, saved.startedAt, saved.endedAt))
        assertEquals(listOf(bench.id), database.workoutDao().exercises(saved.id).map { it.exerciseId })
        assertEquals(listOf(PerformedSet(60.0, 10), PerformedSet(60.0, 9)), workouts.lastSession(bench.id))
    }

    @Test
    fun workoutWithoutTickedOffSetsIsNotSaved() = runTest {
        val workout = ActiveWorkout(null, "Push", 0, 0).addExercise(listOf(WorkoutVariant(exercise("Butterfly").id)))
        assertNull(workouts.save(workout, endedAt = 1))
        assertEquals(0, database.workoutDao().workouts().size)
    }

    @Test
    fun lastSessionIsTheMostRecentWorkout() = runTest {
        val squat = exercise("Barbell Squat").id
        workouts.save(workout(1_000, WorkoutVariant(squat) to listOf(100.0 to 5)), endedAt = 2_000)
        workouts.save(workout(5_000, WorkoutVariant(squat) to listOf(102.5 to 5, 102.5 to 4)), endedAt = 6_000)
        workouts.save(workout(3_000, WorkoutVariant(squat) to listOf(90.0 to 8)), endedAt = 4_000)

        assertEquals(listOf(PerformedSet(102.5, 5), PerformedSet(102.5, 4)), workouts.lastSession(squat))
        assertEquals(emptyList<PerformedSet>(), workouts.lastSession(exercise("Leg Press").id))
    }

    @Test
    fun setTypesRoundTrip() = runTest {
        val dips = exercise("Dips - Chest Version").id
        var workout = workout(1_000, WorkoutVariant(dips) to listOf(null to 12, 10.0 to 8))
        val exercise = workout.exercises.single()
        workout = workout.updateSet(exercise.id, exercise.sets[1].id) { it.copy(type = SetType.Failure) }
        workouts.save(workout, endedAt = 2_000)
        assertEquals(listOf(PerformedSet(null, 12), PerformedSet(10.0, 8, SetType.Failure)), workouts.lastSession(dips))
    }

    @Test
    fun routineAndVariantDeletedDuringTheWorkoutAreUnlinked() = runTest {
        val press = exercise("Leg Press")
        val routineId = routines.save(null, RoutineDraft(name = "Legs").addBlock(press))
        val routine = routines.observe(routineId).first()!!
        val variant = routine.blocks.single().variants.single()
        val workout = workout(1_000, WorkoutVariant(press.id, variant.id, variant.targets) to listOf(150.0 to 10)).copy(routineId = routineId)

        routines.delete(routineId)
        workouts.save(workout, endedAt = 2_000)

        val saved = database.workoutDao().workouts().single()
        assertNull(saved.routineId)
        assertNull(database.workoutDao().exercises(saved.id).single().routineVariantId)
    }

    @Test
    fun deletingTheRoutineLaterKeepsTheHistory() = runTest {
        val press = exercise("Leg Press")
        val routineId = routines.save(null, RoutineDraft(name = "Legs").addBlock(press))
        val variant = routines.observe(routineId).first()!!.blocks.single().variants.single()
        workouts.save(workout(1_000, WorkoutVariant(press.id, variant.id, variant.targets) to listOf(150.0 to 10)).copy(routineId = routineId), endedAt = 2_000)
        assertEquals(variant.id, database.workoutDao().exercises(database.workoutDao().workouts().single().id).single().routineVariantId)

        routines.delete(routineId)

        val saved = database.workoutDao().workouts().single()
        assertNull(saved.routineId)
        assertNull(database.workoutDao().exercises(saved.id).single().routineVariantId)
        assertEquals(listOf(PerformedSet(150.0, 10)), workouts.lastSession(press.id))
    }

    @Test
    fun exerciseWithHistoryCannotBeDeleted() = runTest {
        val id = exercises.saveCustom(null, ExerciseDraft(name = "Landmine Press", primaryMuscles = setOf(Muscle.Shoulders)))
        workouts.save(workout(1_000, WorkoutVariant(id) to listOf(30.0 to 10)), endedAt = 2_000)
        assertEquals(DeleteResult.InUse, exercises.deleteCustom(id))
    }

    /** Saves one finished workout of an hour with [sets] of [exerciseId]. */
    private suspend fun saveSets(startedAt: Long, exerciseId: Long, sets: List<PerformedSet>): Long {
        val exercise = WorkoutExercise(1, listOf(WorkoutVariant(exerciseId)), sets = sets.mapIndexed { i, s -> WorkoutSet(i + 2L, s.type, s.weightKg, s.reps, done = true) })
        return workouts.save(ActiveWorkout(null, "Push", startedAt, startedAt, listOf(exercise)), endedAt = startedAt + HOUR)!!
    }

    @Test
    fun historyListsSessionsNewestFirstWithoutWarmupsInTheTotals() = runTest {
        val bench = exercise("Barbell Bench Press - Medium Grip").id
        saveSets(1_000, bench, listOf(PerformedSet(40.0, 10, SetType.Warmup), PerformedSet(60.0, 10), PerformedSet(null, 12)))
        val second = workout(10 * HOUR, WorkoutVariant(bench) to listOf(62.5 to 8), WorkoutVariant(exercise("Butterfly").id) to listOf(30.0 to 12, 30.0 to 10))
        workouts.save(second, endedAt = 11 * HOUR)

        val history = workouts.observeHistory().first()
        assertEquals(listOf(10 * HOUR, 1_000L), history.map { it.startedAt })
        assertEquals(WorkoutSummary(HOUR, 62.5 * 8 + 30.0 * 22, sets = 3, exercises = 2), history[0].summary)
        assertEquals(WorkoutSummary(HOUR, 600.0, sets = 2, exercises = 1), history[1].summary)
    }

    @Test
    fun editingReplacesTheSetsAndKeepsTheWorkout() = runTest {
        val bench = exercise("Barbell Bench Press - Medium Grip").id
        val butterfly = exercise("Butterfly").id
        val id = saveSets(1_000, bench, listOf(PerformedSet(60.0, 10), PerformedSet(60.0, 9), PerformedSet(60.0, 8)))
        val saved = workouts.observeWorkout(id).first()!!
        val entry = saved.exercises.single()
        val edited = saved
            .removeSet(entry.id, entry.sets[1].id)
            .updateSet(entry.id, entry.sets[2].id) { it.copy(type = SetType.Failure, reps = 7) }
            .addExercise(butterfly)
            .let { w -> w.updateSet(w.exercises.last().id, w.exercises.last().sets.single().id) { it.copy(weightKg = 30.0, reps = 12) } }
            .withDuration(45)
            .copy(name = " Push A ")

        assertTrue(workouts.update(edited))

        val reloaded = workouts.observeWorkout(id).first()!!
        assertEquals(listOf("Push A", 1_000L, 1_000L + 45 * 60_000), listOf(reloaded.name, reloaded.startedAt, reloaded.endedAt))
        assertEquals(listOf(bench, butterfly), reloaded.exercises.map { it.exerciseId })
        assertEquals(
            listOf(listOf(PerformedSet(60.0, 10), PerformedSet(60.0, 7, SetType.Failure)), listOf(PerformedSet(30.0, 12))),
            reloaded.exercises.map { e -> e.sets.map(LoggedSet::performed) },
        )
        assertEquals(listOf(PerformedSet(30.0, 12)), workouts.lastSession(butterfly))
    }

    @Test
    fun anEditWithoutExistingExercisesChangesNothing() = runTest {
        val id = saveSets(1_000, exercise("Butterfly").id, listOf(PerformedSet(30.0, 12)))
        val saved = workouts.observeWorkout(id).first()!!
        assertFalse(workouts.update(saved.removeExercise(saved.exercises.single().id).addExercise(999_999).copy(name = "X")))
        assertEquals(saved, workouts.observeWorkout(id).first())
    }

    @Test
    fun deletingAWorkoutRemovesItsSets() = runTest {
        val butterfly = exercise("Butterfly").id
        saveSets(1_000, butterfly, listOf(PerformedSet(25.0, 12)))
        val id = saveSets(HOUR, butterfly, listOf(PerformedSet(30.0, 12)))
        workouts.delete(id)
        assertNull(workouts.observeWorkout(id).first())
        assertEquals(listOf(PerformedSet(25.0, 12)), workouts.lastSession(butterfly))
        assertEquals(25.0, workouts.personalBests(butterfly).weightKg!!, 0.0)
    }

    @Test
    fun bestsFromRoomMatchTheDomainRules() = runTest {
        val bench = exercise("Barbell Bench Press - Medium Grip").id
        val random = Random(42)
        val types = SetType.entries
        // Weights in 2.5 kg steps, some bodyweight or 0, reps up to 15 and every set type
        val sessions = List(30) {
            List(random.nextInt(1, 7)) {
                val weight = when (random.nextInt(10)) {
                    0 -> null
                    1 -> 0.0
                    else -> 40 + 2.5 * random.nextInt(30)
                }
                PerformedSet(weight, random.nextInt(1, 16), types[random.nextInt(types.size)])
            }
        }
        val ids = sessions.mapIndexed { index, sets -> saveSets(index * HOUR, bench, sets) }

        fun expected(upTo: Int) = sessions.take(upTo).fold(PersonalBests()) { bests, sets ->
            val session = sets.filter { it.type != SetType.Warmup }.sumOf { it.volumeKg }
            sets.fold(bests) { b, set -> b.plus(set) }.copy(sessionVolumeKg = listOfNotNull(bests.sessionVolumeKg, session.takeIf { it > 0 }).maxOrNull())
        }
        assertBests(expected(sessions.size), workouts.personalBests(bench))
        val middle = workouts.observeWorkout(ids[17]).first()!!
        assertBests(expected(17), workouts.bestsBefore(middle).getValue(bench))
        assertEquals(PersonalBests(), workouts.personalBests(exercise("Butterfly").id))
    }

    @Test
    fun aWorkoutIsLinkedToAHealthSessionUnlinkedAndItsMatchDismissed() = runTest {
        val id = saveSets(1_000, exercise("Butterfly").id, listOf(PerformedSet(30.0, 12)))
        assertEquals(HealthLink(1_000, 1_000 + HOUR, sessionId = null, dismissed = false), workouts.observeHealthLink(id).first())

        workouts.linkHealthSession(id, "hc-1")
        assertEquals("hc-1", workouts.observeHealthLink(id).first()!!.sessionId)
        assertEquals("hc-1", workouts.forHealth(id)!!.hcSessionId)

        workouts.linkHealthSession(id, null)
        workouts.dismissHealthMatch(id)
        assertEquals(HealthLink(1_000, 1_000 + HOUR, sessionId = null, dismissed = true), workouts.observeHealthLink(id).first())
        assertNull(workouts.observeHealthLink(id + 1).first())
        assertNull(workouts.forHealth(id + 1))
    }

    @Test
    fun linkedSessionsAreListedAndCarriedByTheHistory() = runTest {
        val butterfly = exercise("Butterfly").id
        val first = saveSets(1_000, butterfly, listOf(PerformedSet(30.0, 12)))
        val second = saveSets(HOUR * 2, butterfly, listOf(PerformedSet(30.0, 10)))
        val third = saveSets(HOUR * 4, butterfly, listOf(PerformedSet(30.0, 8)))
        workouts.linkHealthSession(first, "hc-1")
        workouts.linkHealthSession(second, "hc-2")
        workouts.linkHealthSession(second, null)
        workouts.linkHealthSession(third, "hc-3")

        assertEquals(setOf("hc-1", "hc-3"), workouts.observeLinkedSessions().first())
        assertEquals(listOf(third to "hc-3", second to null, first to "hc-1"), workouts.observeHistory().first().map { it.id to it.healthSessionId })
    }

    @Test
    fun changedEmitsTheWorkoutsHealthConnectGetsACopyOf() = runTest {
        val emitted = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { workouts.changed.toList(emitted) }
        val butterfly = exercise("Butterfly").id

        val first = saveSets(1_000, butterfly, listOf(PerformedSet(30.0, 12)))
        val second = saveSets(HOUR, butterfly, listOf(PerformedSet(30.0, 10)))
        val saved = workouts.observeWorkout(first).first()!!
        workouts.update(saved.copy(name = "Pull"))
        workouts.linkHealthSession(second, "hc-1")
        // Not changes: a dismissed match, a save without sets and an edit without existing exercises
        workouts.dismissHealthMatch(first)
        assertNull(workouts.save(ActiveWorkout(null, "Push", 0, 0), endedAt = 1))
        assertFalse(workouts.update(saved.removeExercise(saved.exercises.single().id).addExercise(999_999)))
        workouts.delete(second)
        advanceUntilIdle()

        assertEquals(listOf(first, second, first, second, second), emitted)
    }

    private fun assertBests(expected: PersonalBests, actual: PersonalBests) {
        assertEquals(expected.copy(reps = emptyList(), e1rmKg = null), actual.copy(reps = emptyList(), e1rmKg = null))
        assertEquals(expected.e1rmKg!!, actual.e1rmKg!!, 1e-9)
        assertEquals(expected.reps.toSet(), actual.reps.toSet())
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
