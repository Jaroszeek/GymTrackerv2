package com.jaroszeek.gymtracker.data.workout

import com.jaroszeek.gymtracker.data.exercise.DeleteResult
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.exercise.ExerciseDraft
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.RoutineDraft
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import com.jaroszeek.gymtracker.domain.workout.WorkoutVariant
import com.jaroszeek.gymtracker.testing.inMemoryDatabase
import com.jaroszeek.gymtracker.testing.seeder
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
