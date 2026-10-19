package com.jaroszeek.gymtracker.domain.workout

import com.jaroszeek.gymtracker.domain.progress.PersonalBests
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.progress.RepBest
import com.jaroszeek.gymtracker.domain.routine.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggedWorkoutTest {

    private val workout = LoggedWorkout(
        id = 7, routineId = 1, name = "Push", startedAt = 1_000, endedAt = 3_601_000,
        exercises = listOf(
            LoggedExercise(10, 100, 5, listOf(LoggedSet(11, SetType.Warmup, 40.0, 10), LoggedSet(12, SetType.Working, 60.0, 10), LoggedSet(13, SetType.Working, 62.5, 8))),
            LoggedExercise(20, 200, null, listOf(LoggedSet(21, SetType.Working, null, 12))),
        ),
    )

    @Test
    fun summaryLeavesWarmupsOut() {
        assertEquals(WorkoutSummary(3_600_000, 60.0 * 10 + 62.5 * 8, sets = 3, exercises = 2), workout.summary())
    }

    @Test
    fun aNewSetCopiesTheLastOneWithANewId() {
        val edited = workout.addSet(10)
        assertEquals(LoggedSet(22, SetType.Working, 62.5, 8), edited.exercises[0].sets.last())
    }

    @Test
    fun removingTheLastSetRemovesTheExercise() {
        assertEquals(listOf(10L), workout.removeSet(20, 21).exercises.map { it.id })
        assertEquals(listOf(11L, 13L), workout.removeSet(10, 12).exercises[0].sets.map { it.id })
        assertEquals(listOf(20L), workout.removeExercise(10).exercises.map { it.id })
    }

    @Test
    fun anAddedExerciseNeedsRepsBeforeSaving() {
        val added = workout.addExercise(300)
        val exercise = added.exercises.last()
        assertEquals(300L, exercise.exerciseId)
        assertEquals(listOf(LoggedSet(23, SetType.Working, null, 0)), exercise.sets)
        assertFalse(added.canSave)
        assertTrue(added.updateSet(exercise.id, 23) { it.copy(reps = 10) }.canSave)
    }

    @Test
    fun noExercisesOrNoNameCannotBeSaved() {
        assertTrue(workout.canSave)
        assertFalse(workout.removeExercise(10).removeExercise(20).canSave)
        assertFalse(workout.copy(name = " ").canSave)
    }

    @Test
    fun durationIsSetInWholeMinutes() {
        assertEquals(1_000L + 45 * 60_000, workout.withDuration(45).endedAt)
        assertEquals(1_000L + 60_000, workout.withDuration(0).endedAt)
    }

    @Test
    fun recordsAreMatchedToSetIdsWithEachExercisesBests() {
        val bests = mapOf(100L to PersonalBests(weightKg = 60.0, e1rmKg = 80.0, setVolumeKg = 600.0, sessionVolumeKg = 2_000.0, reps = listOf(RepBest(60.0, 10))))
        // 62.5 kg is a weight record; exercise 200 has no bests, so no records
        assertEquals(mapOf(13L to setOf(RecordType.Weight)), workout.records(bests))
    }
}
