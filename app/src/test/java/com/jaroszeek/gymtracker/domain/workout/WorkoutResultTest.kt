package com.jaroszeek.gymtracker.domain.workout

import com.jaroszeek.gymtracker.domain.progress.PersonalBests
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.progress.RepBest
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutResultTest {

    private fun workout(vararg exercises: WorkoutExercise) =
        ActiveWorkout(routineId = 1, name = "Push", startedAt = 1_000, updatedAt = 1_000, exercises = exercises.toList())

    private fun exercise(id: Long, targets: RoutineTargets?, vararg sets: WorkoutSet, variantId: Long? = id * 10) =
        WorkoutExercise(id, listOf(WorkoutVariant(exerciseId = id * 100, routineVariantId = variantId, targets = targets)), sets = sets.toList())

    private fun set(id: Long, weight: Double?, reps: Int, done: Boolean = true, type: SetType = SetType.Working) = WorkoutSet(id, type, weight, reps, done)

    @Test
    fun summaryCountsTickedOffWorkWithoutWarmups() {
        val workout = workout(
            exercise(1, null, set(11, 40.0, 10, type = SetType.Warmup), set(12, 60.0, 10), set(13, 60.0, 8), set(14, 60.0, 8, done = false)),
            exercise(2, null, set(21, null, 12), set(22, 20.0, 15, type = SetType.DropSet)),
            exercise(3, null, set(31, 50.0, 10, done = false)),
        )
        val summary = workout.summary(endedAt = 3_601_000)
        assertEquals(3_600_000L, summary.durationMillis)
        assertEquals(60.0 * 10 + 60.0 * 8 + 20.0 * 15, summary.volumeKg, 0.001)
        assertEquals(4, summary.sets)
        assertEquals(2, summary.exercises)
    }

    @Test
    fun nothingTickedOffIsAnEmptySummary() {
        assertTrue(workout(exercise(1, null, set(11, 60.0, 10, done = false))).summary(2_000).isEmpty)
    }

    @Test
    fun changesProposeTheDoneWorkingSetsAndTheHeaviestWeight() {
        val targets = RoutineTargets(3, 8, 12, 60.0, 120)
        val workout = workout(
            // Four working sets, top set 65 kg; the warm-up and the drop set do not count
            exercise(1, targets, set(11, 40.0, 10, type = SetType.Warmup), set(12, 62.5, 10), set(13, 65.0, 8), set(14, 62.5, 9), set(15, 60.0, 8, type = SetType.Failure), set(16, 40.0, 12, type = SetType.DropSet)),
            // Done as planned: no change
            exercise(2, RoutineTargets(2, 10, 10, 20.0), set(21, 20.0, 10), set(22, 20.0, 10)),
            // Nothing done: no change proposed
            exercise(3, targets, set(31, 60.0, 10, done = false)),
            // Not from the routine
            exercise(4, null, set(41, 30.0, 10), variantId = null),
        )
        assertEquals(listOf(TargetChange(100, 10, targets, targets.copy(sets = 4, weightKg = 65.0))), workout.targetChanges())
    }

    @Test
    fun bodyweightWorkKeepsTheTargetWeight() {
        val targets = RoutineTargets(3, 8, 12, weightKg = 10.0)
        val workout = workout(exercise(1, targets, set(11, null, 12), set(12, null, 10)))
        assertEquals(targets.copy(sets = 2), workout.targetChanges().single().new)
    }

    private val bests = PersonalBests(weightKg = 60.0, e1rmKg = 80.0, setVolumeKg = 600.0, sessionVolumeKg = 1_000.0, reps = listOf(RepBest(60.0, 10)))

    private fun withBests(id: Long, bests: PersonalBests, vararg sets: WorkoutSet, exerciseId: Long = id * 100) =
        WorkoutExercise(id, listOf(WorkoutVariant(exerciseId = exerciseId, bests = bests)), sets = sets.toList())

    @Test
    fun tickedOffSetsAreComparedWithTheBestsFromBeforeTheWorkout() {
        val workout = workout(
            withBests(1, bests, set(11, 60.0, 10), set(12, 62.5, 10), set(13, 70.0, 8, done = false)),
            // No history: the first session sets no records
            withBests(2, PersonalBests(), set(21, 100.0, 5)),
        )
        val all = setOf(RecordType.Weight, RecordType.E1rm, RecordType.SetVolume, RecordType.SessionVolume)
        assertEquals(mapOf(12L to all), workout.records())
        assertEquals(
            listOf(
                RecordHit(100, RecordType.Weight, PerformedSet(62.5, 10), 62.5),
                RecordHit(100, RecordType.E1rm, PerformedSet(62.5, 10), 62.5 * (1 + 10 / 30.0)),
                RecordHit(100, RecordType.SetVolume, PerformedSet(62.5, 10), 625.0),
                RecordHit(100, RecordType.SessionVolume, PerformedSet(62.5, 10), 1_225.0),
            ),
            workout.recordHits(),
        )
    }

    @Test
    fun theSameExerciseTwiceIsOneSession() {
        val workout = workout(
            withBests(1, bests, set(11, 60.0, 10)),
            withBests(2, bests, set(21, 60.0, 10), exerciseId = 100),
        )
        // 600 + 600 kg passes the best session of 1 000 kg on the second entry
        assertEquals(mapOf(21L to setOf(RecordType.SessionVolume)), workout.records())
    }

    @Test
    fun theBestHitOfEachTypeIsTheLastOne() {
        val workout = workout(withBests(1, bests, set(11, 62.5, 5), set(12, 65.0, 3)))
        assertEquals(65.0, workout.recordHits().single { it.type == RecordType.Weight }.valueKg!!, 0.0)
    }

    private fun hit(exerciseId: Long, type: RecordType, valueKg: Double?) = RecordHit(exerciseId, type, PerformedSet(valueKg, 5), valueKg)

    @Test
    fun startShowsTheMostImportantRecordPerExercise() {
        val records = listOf(
            // Newest workout first
            ExerciseRecords(3, 3_000, 100, "Bench", listOf(hit(100, RecordType.Reps, null), hit(100, RecordType.E1rm, 118.0))),
            ExerciseRecords(2, 2_000, 200, "Squat", listOf(hit(200, RecordType.SessionVolume, 4_000.0), hit(200, RecordType.SetVolume, 900.0))),
            ExerciseRecords(1, 1_000, 100, "Bench", listOf(hit(100, RecordType.E1rm, 115.0), hit(100, RecordType.Weight, 100.0))),
        )
        assertEquals(
            listOf(
                // The newer e1RM; the older one is the same kind, so the others are reps and weight
                HeadlineRecord(3, 3_000, "Bench", hit(100, RecordType.E1rm, 118.0), others = 2),
                HeadlineRecord(2, 2_000, "Squat", hit(200, RecordType.SetVolume, 900.0), others = 1),
            ),
            headlineRecords(records),
        )
    }

    @Test
    fun aHeadlineFromAnOlderWorkoutSortsByItsDate() {
        val records = listOf(
            ExerciseRecords(3, 3_000, 100, "Bench", listOf(hit(100, RecordType.SessionVolume, 3_000.0))),
            ExerciseRecords(2, 2_000, 200, "Squat", listOf(hit(200, RecordType.Weight, 140.0))),
            ExerciseRecords(1, 1_000, 100, "Bench", listOf(hit(100, RecordType.Weight, 100.0))),
        )
        assertEquals(listOf(2L to 0, 1L to 1), headlineRecords(records).map { it.workoutId to it.others })
        assertEquals(emptyList<HeadlineRecord>(), headlineRecords(emptyList()))
    }
}
