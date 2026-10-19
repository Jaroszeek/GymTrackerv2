package com.jaroszeek.gymtracker.domain.workout

import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveWorkoutTest {

    private val empty = ActiveWorkout(routineId = 1, name = "Push", startedAt = 0, updatedAt = 0)
    private val bench = WorkoutVariant(exerciseId = 10, routineVariantId = 100, targets = RoutineTargets(3, 8, 12, 60.0, 120))
    private val dips = WorkoutVariant(exerciseId = 20, routineVariantId = 200, targets = RoutineTargets(2, 10, 10))

    private fun ActiveWorkout.exercise(index: Int = 0) = exercises[index]

    @Test
    fun routineTargetsPlanTheSets() {
        val workout = empty.addExercise(listOf(bench))
        assertEquals(List(3) { 60.0 to 8 }, workout.exercise().sets.map { it.weightKg to it.reps })
        assertTrue(workout.exercise().sets.all { it.type == SetType.Working && !it.done })
    }

    @Test
    fun lastSessionPlansTheSetsWhenNoProgressionIsDue() {
        val previous = listOf(PerformedSet(40.0, 12, SetType.Warmup), PerformedSet(62.5, 10), PerformedSet(62.5, 9), PerformedSet(60.0, 8))
        val sets = empty.addExercise(listOf(bench.copy(previous = previous))).exercise().sets
        // Warm-ups are left out of the plan
        assertEquals(listOf(62.5 to 10, 62.5 to 9, 60.0 to 8), sets.map { it.weightKg to it.reps })
    }

    @Test
    fun dueProgressionRaisesTheWeightAndStartsFromTheBottomOfTheRange() {
        val previous = List(3) { PerformedSet(60.0, 12) }
        val workout = empty.addExercise(listOf(bench.copy(previous = previous)))
        assertEquals(62.5, workout.exercise().variant.progressionKg)
        assertEquals(List(3) { 62.5 to 8 }, workout.exercise().sets.map { it.weightKg to it.reps })
    }

    @Test
    fun exerciseAddedWithoutRoutineCopiesTheLastSessionOrStartsEmpty() {
        val adHoc = empty.addExercise(listOf(WorkoutVariant(exerciseId = 30, previous = listOf(PerformedSet(20.0, 15), PerformedSet(20.0, 12)))))
        assertEquals(listOf(20.0 to 15, 20.0 to 12), adHoc.exercise().sets.map { it.weightKg to it.reps })

        val fresh = empty.addExercise(listOf(WorkoutVariant(exerciseId = 30)))
        assertEquals(RoutineTargets.DEFAULT_SETS, fresh.exercise().sets.size)
        assertTrue(fresh.exercise().sets.none { it.canComplete })
    }

    @Test
    fun idsStayUniqueAcrossExercisesAndSets() {
        val workout = empty.addExercise(listOf(bench)).addExercise(listOf(dips)).addSet(1)
        val ids = workout.exercises.flatMap { exercise -> exercise.sets.map { it.id } + exercise.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun newSetCopiesTheLastOne() {
        var workout = empty.addExercise(listOf(bench))
        val exercise = workout.exercise()
        workout = workout.updateSet(exercise.id, exercise.sets.last().id) { it.copy(type = SetType.Failure, weightKg = 65.0, reps = 6, done = true) }
            .addSet(exercise.id)
        val added = workout.exercise().sets.last()
        assertEquals(4, workout.exercise().sets.size)
        assertEquals(WorkoutSet(added.id, SetType.Failure, 65.0, 6, done = false), added)
    }

    @Test
    fun newWeightFillsDownToLaterSetsThatHadTheSameWeight() {
        var workout = empty.addExercise(listOf(bench))
        val exercise = workout.exercise()
        val (first, second, third) = exercise.sets
        workout = workout.complete(exercise.id, first.id, 0, 90)
            .updateSet(exercise.id, third.id) { it.copy(type = SetType.DropSet) }
            .addSet(exercise.id)
            .setWeight(exercise.id, second.id, 62.5)
        // The done set before it and the drop sets (another kind) keep their weight
        assertEquals(listOf(60.0, 62.5, 60.0, 60.0), workout.exercise().sets.map { it.weightKg })

        workout = workout.setWeight(exercise.id, first.id, 65.0)
        assertEquals(listOf(65.0, 62.5, 60.0, 60.0), workout.exercise().sets.map { it.weightKg })
    }

    @Test
    fun emptyWeightFillsDownToo() {
        var workout = empty.addExercise(listOf(dips))
        val exercise = workout.exercise()
        workout = workout.setWeight(exercise.id, exercise.sets[0].id, 10.0)
        assertEquals(listOf(10.0, 10.0), workout.exercise().sets.map { it.weightKg })
    }

    @Test
    fun removingTheLastSetRemovesTheExercise() {
        var workout = empty.addExercise(listOf(dips))
        val exercise = workout.exercise()
        workout = workout.removeSet(exercise.id, exercise.sets[0].id)
        assertEquals(1, workout.exercise().sets.size)
        workout = workout.removeSet(exercise.id, exercise.sets[1].id)
        assertTrue(workout.exercises.isEmpty())
    }

    @Test
    fun completingStartsTheRestFromTheTargetOrTheDefault() {
        var workout = empty.addExercise(listOf(bench)).addExercise(listOf(dips))
        val benchSet = workout.exercise(0).sets[0]
        workout = workout.complete(workout.exercise(0).id, benchSet.id, now = 1_000, defaultRestSeconds = 90)
        assertTrue(workout.exercise(0).sets[0].done)
        assertEquals(RestTimer(1_000, 121_000, workout.exercise(0).id, benchSet.id), workout.rest)

        // Dips have no target rest
        workout = workout.complete(workout.exercise(1).id, workout.exercise(1).sets[0].id, now = 5_000, defaultRestSeconds = 90)
        assertEquals(95_000L, workout.rest?.endsAt)
    }

    @Test
    fun setWithoutRepsCannotBeCompleted() {
        val workout = empty.addExercise(listOf(WorkoutVariant(exerciseId = 30)))
        val exercise = workout.exercise()
        assertSame(workout, workout.complete(exercise.id, exercise.sets[0].id, now = 0, defaultRestSeconds = 90))
    }

    @Test
    fun uncompletingCancelsTheRest() {
        var workout = empty.addExercise(listOf(bench))
        val exercise = workout.exercise()
        workout = workout.complete(exercise.id, exercise.sets[0].id, 0, 90).uncomplete(exercise.id, exercise.sets[0].id)
        assertFalse(workout.exercise().sets[0].done)
        assertNull(workout.rest)
    }

    @Test
    fun restCanBeShortenedExtendedAndEnds() {
        var workout = empty.addExercise(listOf(dips))
        val exercise = workout.exercise()
        workout = workout.complete(exercise.id, exercise.sets[0].id, now = 0, defaultRestSeconds = 60)
        workout = workout.adjustRest(30, now = 10_000)
        assertEquals(90_000L, workout.rest?.endsAt)
        assertEquals(0.5f, workout.rest!!.fraction(now = 45_000), 0.0001f)
        workout = workout.adjustRest(-15, now = 10_000)
        assertEquals(75_000L, workout.rest?.endsAt)

        assertEquals(workout, workout.endRestIfDue(now = 74_999))
        assertNull(workout.endRestIfDue(now = 75_000).rest)
        // Shortened past now: over
        assertNull(workout.adjustRest(-15, now = 70_000).rest)
    }

    @Test
    fun nextSetFollowsTheSetThatStartedTheRestAndWraps() {
        var workout = empty.addExercise(listOf(bench)).addExercise(listOf(dips))
        val first = workout.exercise(0)
        workout = workout.complete(first.id, first.sets[0].id, 0, 90)
        assertEquals(first.id to 2, workout.nextSet()!!.let { it.exercise.id to it.number })

        // After the last bench set come the dips
        workout = workout.complete(first.id, first.sets[1].id, 0, 90).complete(first.id, first.sets[2].id, 0, 90)
        assertEquals(workout.exercise(1).id to 1, workout.nextSet()!!.let { it.exercise.id to it.number })

        val second = workout.exercise(1)
        workout = workout.complete(second.id, second.sets[0].id, 0, 90).complete(second.id, second.sets[1].id, 0, 90)
        assertNull(workout.nextSet())
    }

    @Test
    fun withoutARestTheNextSetIsTheFirstUntickedOne() {
        var workout = empty.addExercise(listOf(bench)).addExercise(listOf(dips))
        val first = workout.exercise(0)
        // Done out of order, rest skipped: the notification offers the set left behind
        workout = workout.complete(first.id, first.sets[1].id, 0, 90).skipRest()
        assertEquals(first.id to 1, workout.nextSet()!!.let { it.exercise.id to it.number })
    }

    @Test
    fun choosingAnotherVariantReplansUntilASetIsDone() {
        val hammer = WorkoutVariant(exerciseId = 11, routineVariantId = 101, targets = RoutineTargets(4, 6, 8, 80.0))
        var workout = empty.addExercise(listOf(bench, hammer))
        val id = workout.exercise().id
        workout = workout.selectVariant(id, 1)
        assertEquals(11L, workout.exercise().variant.exerciseId)
        assertEquals(List(4) { 80.0 to 6 }, workout.exercise().sets.map { it.weightKg to it.reps })

        workout = workout.complete(id, workout.exercise().sets[0].id, 0, 90).selectVariant(id, 0)
        assertEquals(10L, workout.exercise().variant.exerciseId)
        assertEquals(4, workout.exercise().sets.size)
        assertTrue(workout.exercise().sets[0].done)
    }

    @Test
    fun hintsMatchSetsOfTheSameKind() {
        val previous = listOf(PerformedSet(40.0, 10, SetType.Warmup), PerformedSet(60.0, 10), PerformedSet(60.0, 9))
        var workout = empty.addExercise(listOf(dips.copy(previous = previous)))
        val exercise = workout.exercise()
        // A warm-up added in front does not shift the hints of the working sets
        workout = workout.updateSet(exercise.id, exercise.sets[0].id) { it.copy(type = SetType.Warmup) }.addSet(exercise.id)
        val sets = workout.exercise().sets
        assertEquals(PerformedSet(40.0, 10, SetType.Warmup), workout.exercise().previousFor(sets[0]))
        assertEquals(PerformedSet(60.0, 10), workout.exercise().previousFor(sets[1]))
    }

    @Test
    fun progressCountsDoneSets() {
        var workout = empty.addExercise(listOf(bench)).addExercise(listOf(dips))
        workout = workout.complete(workout.exercise(1).id, workout.exercise(1).sets[0].id, 0, 90)
        assertEquals(1 to 5, workout.doneSets to workout.totalSets)
        assertTrue(workout.uses(20))
        assertFalse(workout.uses(99))
    }
}
