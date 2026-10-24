package com.jaroszeek.gymtracker.domain.health

import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.workout.LoggedExercise
import com.jaroszeek.gymtracker.domain.workout.LoggedSet
import com.jaroszeek.gymtracker.domain.workout.LoggedWorkout
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseHeartRateTest {

    /** A workout started at [startedAt] with entries as (entry id, when each of its sets was ticked off). */
    private fun workout(startedAt: Long, vararg entries: Pair<Long, List<Long?>>) = LoggedWorkout(
        id = 1,
        routineId = null,
        name = "Push",
        startedAt = startedAt,
        endedAt = startedAt + 3_600_000,
        exercises = entries.map { (id, times) ->
            LoggedExercise(id, exerciseId = id + 100, routineVariantId = null, sets = times.mapIndexed { index, time -> LoggedSet(id * 10 + index, SetType.Working, 60.0, 8, time) })
        },
    )

    /** A sample every 30 s from 0 to 270 s. */
    private val samples = listOf(100, 110, 120, 130, 140, 90, 95, 100, 105, 150).mapIndexed { index, bpm -> HeartRateSample(index * 30_000L, bpm) }

    /** Entry 1 of a workout started at 0 with sets ticked off at 60 and 120 s. */
    private val first = ExerciseHeartRate(samples.subList(0, 5), listOf(60_000L, 120_000), averageBpm = 115, maxBpm = 140)

    @Test
    fun exercisesInSequenceSplitTheWorkoutAtTheTickOffs() {
        val result = exerciseHeartRate(workout(0, 1L to listOf(60_000L, 120_000L), 2L to listOf(180_000L, 240_000L)), samples)
        assertEquals(setOf(1L, 2L), result.keys)
        assertEquals(first, result[1])
        // From the last tick-off of the first exercise; the sample at 270 s is after the last set
        assertEquals(ExerciseHeartRate(samples.subList(4, 9), listOf(180_000L, 240_000), averageBpm = 106, maxBpm = 140), result[2])
    }

    @Test
    fun theFirstSetsIntervalStartsAtTheWorkoutStart() {
        val samples = listOf(50_000L to 180, 100_000L to 100, 150_000L to 120, 200_000L to 130, 250_000L to 170).map { (time, bpm) -> HeartRateSample(time, bpm) }
        val result = exerciseHeartRate(workout(100_000, 1L to listOf(200_000L)), samples)
        assertEquals(mapOf(1L to ExerciseHeartRate(samples.subList(1, 4), listOf(200_000L), averageBpm = 110, maxBpm = 130)), result)
    }

    @Test
    fun setsWithoutATimeAreLeftOut() {
        val result = exerciseHeartRate(workout(0, 1L to listOf(60_000L, null, 120_000L), 2L to listOf(null, null)), samples)
        assertEquals(mapOf(1L to first), result)
    }

    @Test
    fun anExerciseWithFewerThanTwoSamplesIsLeftOut() {
        val workout = workout(0, 1L to listOf(60_000L), 2L to listOf(600_000L))
        // One sample in the first span, two in the second
        val samples = listOf(HeartRateSample(30_000, 120), HeartRateSample(500_000, 130), HeartRateSample(550_000, 140))
        assertEquals(setOf(2L), exerciseHeartRate(workout, samples).keys)
        assertEquals(emptyMap<Long, ExerciseHeartRate>(), exerciseHeartRate(workout, emptyList()))
    }

    @Test
    fun samplesInAnyOrderGiveTheSameResult() {
        val workout = workout(0, 1L to listOf(60_000L, 120_000L), 2L to listOf(180_000L, 240_000L))
        val inOrder = exerciseHeartRate(workout, samples)
        assertTrue(inOrder.isNotEmpty())
        assertEquals(inOrder, exerciseHeartRate(workout, samples.reversed()))
        assertEquals(inOrder, exerciseHeartRate(workout, samples.shuffled(Random(1))))
    }

    @Test
    fun ticksAreTakenInTimeOrderNotListOrder() {
        // The entry listed first was done last, and the second set of the other was ticked off before its first
        val result = exerciseHeartRate(workout(0, 2L to listOf(180_000L), 1L to listOf(120_000L, 60_000L)), samples)
        assertEquals(first, result[1])
        assertEquals(ExerciseHeartRate(samples.subList(4, 7), listOf(180_000L), averageBpm = 115, maxBpm = 140), result[2])
    }
}
