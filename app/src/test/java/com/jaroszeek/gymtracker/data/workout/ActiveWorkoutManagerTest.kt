package com.jaroszeek.gymtracker.data.workout

import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.Routine
import com.jaroszeek.gymtracker.domain.routine.RoutineDraft
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.testing.inMemoryDatabase
import com.jaroszeek.gymtracker.testing.seeder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ActiveWorkoutManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val database = inMemoryDatabase()
    private val exercises = ExerciseRepository(database, seeder(database), Dispatchers.IO)
    private val routines = RoutineRepository(database, exercises, Clock.systemUTC(), Dispatchers.IO)
    private val workouts = WorkoutRepository(database, Dispatchers.IO)
    private val file by lazy { File(folder.root, "active_workout.json") }

    @After
    fun close() = database.close()

    /** Wall clock driven by the test scheduler, so delays and absolute times agree. */
    private class TestClock(private val scheduler: TestCoroutineScheduler, var offsetMillis: Long = START) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun millis(): Long = offsetMillis + scheduler.currentTime

        override fun instant(): Instant = Instant.ofEpochMilli(millis())
    }

    private fun TestScope.manager(clock: Clock, scope: CoroutineScope = backgroundScope) =
        ActiveWorkoutManager(ActiveWorkoutStore(file), workouts, clock, scope, StandardTestDispatcher(testScheduler))

    private suspend fun routine(): Routine {
        val bench = exercises.observeAll().first().single { it.name == "Barbell Bench Press - Medium Grip" }
        var draft = RoutineDraft(name = "Push").addBlock(bench)
        val block = draft.blocks.single()
        draft = draft.updateTargets(block.id, block.variants.single().id, RoutineTargets(2, 8, 12, 60.0, restSeconds = 60))
        return routines.observe(routines.save(null, draft)).first()!!
    }

    @Test
    fun startedWorkoutIsSavedRightAwayAndChangesAfterTheDebounce() = runTest {
        val clock = TestClock(testScheduler)
        val manager = manager(clock)
        assertTrue(manager.start(routine()))
        assertEquals("Push", ActiveWorkoutStore(file).read()!!.name)

        val exercise = manager.workout.value!!.exercises.single()
        manager.updateSet(exercise.id, exercise.sets[0].id) { it.copy(weightKg = 62.5) }
        advanceTimeBy(ActiveWorkoutManager.PERSIST_DEBOUNCE_MILLIS - 100)
        runCurrent()
        assertEquals(60.0, ActiveWorkoutStore(file).read()!!.exercises.single().sets[0].weightKg)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(62.5, ActiveWorkoutStore(file).read()!!.exercises.single().sets[0].weightKg)
    }

    @Test
    fun secondStartIsRefusedWhileAWorkoutRuns() = runTest {
        val manager = manager(TestClock(testScheduler))
        assertTrue(manager.startEmpty("Trening"))
        assertFalse(manager.start(routine()))
        assertEquals("Trening", manager.workout.value!!.name)
    }

    @Test
    fun workoutComesBackAfterProcessDeath() = runTest {
        val clock = TestClock(testScheduler)
        val first = manager(clock, CoroutineScope(StandardTestDispatcher(testScheduler)))
        first.start(routine())
        val exercise = first.workout.value!!.exercises.single()
        first.completeSet(exercise.id, exercise.sets[0].id)
        advanceTimeBy(ActiveWorkoutManager.PERSIST_DEBOUNCE_MILLIS + 1)
        runCurrent()

        // A new process ten seconds later: the set is still done and the 60 s rest keeps counting from its end time
        clock.offsetMillis += 10_000
        val second = manager(clock)
        second.ready.first { it }
        val restored = second.workout.value!!
        assertTrue(restored.exercises.single().sets[0].done)
        assertEquals(first.workout.value!!.rest, restored.rest)
    }

    @Test
    fun restEndsOnItsOwnAtTheEndTime() = runTest {
        val manager = manager(TestClock(testScheduler))
        manager.start(routine())
        val exercise = manager.workout.value!!.exercises.single()
        manager.completeSet(exercise.id, exercise.sets[0].id)
        runCurrent()
        assertNotNull(manager.workout.value!!.rest)

        advanceTimeBy(30_000)
        manager.adjustRest(30)
        advanceTimeBy(59_000)
        runCurrent()
        assertNotNull(manager.workout.value!!.rest)
        advanceTimeBy(1_001)
        runCurrent()
        assertNull(manager.workout.value!!.rest)
    }

    @Test
    fun restAlarmEndsTheRestWhenDeepSleepHeldTheTimerBack() = runTest {
        val clock = TestClock(testScheduler)
        val manager = manager(clock)
        manager.start(routine())
        val exercise = manager.workout.value!!.exercises.single()
        manager.completeSet(exercise.id, exercise.sets[0].id)
        runCurrent()

        // Asleep: the wall clock moves on, the timer does not
        clock.offsetMillis += 59_000
        manager.endRestIfDue()
        assertNotNull(manager.workout.value!!.rest)
        clock.offsetMillis += 1_000
        manager.endRestIfDue()
        assertNull(manager.workout.value!!.rest)
    }

    @Test
    fun workoutAbandonedForADayGoesToHistoryInsteadOfComingBack() = runTest {
        val clock = TestClock(testScheduler)
        val first = manager(clock, CoroutineScope(StandardTestDispatcher(testScheduler)))
        first.start(routine())
        val exercise = first.workout.value!!.exercises.single()
        first.completeSet(exercise.id, exercise.sets[0].id)
        advanceTimeBy(ActiveWorkoutManager.PERSIST_DEBOUNCE_MILLIS + 1)
        runCurrent()

        clock.offsetMillis += ActiveWorkoutManager.STALE_AFTER_MILLIS + 1
        val second = manager(clock)
        second.ready.first { it }
        assertNull(second.workout.value)
        assertFalse(file.exists())
        assertEquals(listOf(PerformedSet(60.0, 8)), workouts.lastSession(exercise.variant.exerciseId))
    }

    @Test
    fun finishSavesAndClears() = runTest {
        val manager = manager(TestClock(testScheduler))
        manager.start(routine())
        val exercise = manager.workout.value!!.exercises.single()
        manager.completeSet(exercise.id, exercise.sets[0].id)
        advanceTimeBy(5 * 60_000)

        val finished = manager.finish()!!
        assertEquals(START + 5 * 60_000, finished.endedAt)
        assertNull(manager.workout.value)
        assertFalse(file.exists())
        assertEquals(1, database.workoutDao().workouts().size)
        assertEquals(listOf(PerformedSet(60.0, 8)), workouts.lastSession(exercise.variant.exerciseId))
    }

    @Test
    fun discardClearsWithoutSaving() = runTest {
        val manager = manager(TestClock(testScheduler))
        manager.start(routine())
        val exercise = manager.workout.value!!.exercises.single()
        manager.completeSet(exercise.id, exercise.sets[0].id)
        manager.discard()
        advanceTimeBy(ActiveWorkoutManager.PERSIST_DEBOUNCE_MILLIS + 1)
        runCurrent()
        assertNull(manager.workout.value)
        assertFalse(file.exists())
        assertEquals(0, database.workoutDao().workouts().size)
    }

    @Test
    fun nextWorkoutStartsFromTheLastSession() = runTest {
        val manager = manager(TestClock(testScheduler))
        val routine = routine()
        manager.start(routine)
        manager.workout.value!!.exercises.single().let { exercise ->
            exercise.sets.forEach { set ->
                manager.updateSet(exercise.id, set.id) { it.copy(reps = 12) }
                manager.completeSet(exercise.id, set.id)
            }
        }
        manager.finish()

        // 2 × 12 at 60 kg reached the top of the range: double progression adds 2.5 kg
        manager.start(routine)
        assertEquals(List(2) { 62.5 to 8 }, manager.workout.value!!.exercises.single().sets.map { it.weightKg to it.reps })
    }

    private companion object {
        const val START = 1_800_000_000_000L
    }
}
