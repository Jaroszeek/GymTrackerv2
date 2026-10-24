package com.jaroszeek.gymtracker.domain.health

import com.jaroszeek.gymtracker.domain.workout.LoggedWorkout

/** Heart rate during one exercise: the [samples] of its span, when its sets were ticked off, and the time-weighted average and maximum. */
data class ExerciseHeartRate(val samples: List<HeartRateSample>, val setTimes: List<Long>, val averageBpm: Int, val maxBpm: Int)

/**
 * Per exercise entry ([com.jaroszeek.gymtracker.domain.workout.LoggedExercise.id]) the heart rate while it was
 * done. A set's interval runs from the tick-off before it, of any set (the workout's start for the first), to its
 * own, so it holds the rest before the set and the set; an exercise spans its sets' intervals. Sets without a time
 * are left out, and so is an exercise with fewer than two samples in its span.
 */
// ponytail: interleaved exercises (supersets) get overlapping spans that hold each other's sets; chart per set interval if that matters
fun exerciseHeartRate(workout: LoggedWorkout, samples: List<HeartRateSample>): Map<Long, ExerciseHeartRate> {
    val ticks = workout.exercises.flatMap { exercise -> exercise.sets.mapNotNull { set -> set.doneAt?.let { exercise.id to it } } }.sortedBy { it.second }
    val sorted = samples.sortedBy { it.time }
    return ticks.withIndex()
        .groupBy({ it.value.first }) { (index, tick) -> (ticks.getOrNull(index - 1)?.second ?: workout.startedAt) to tick.second }
        .mapNotNull { (entryId, intervals) ->
            val from = intervals.minOf { it.first }
            val to = intervals.maxOf { it.second }
            val span = sorted.filter { it.time in from..to }.takeIf { it.size >= 2 } ?: return@mapNotNull null
            val summary = summarize(span, maxHeartRate = null) ?: return@mapNotNull null
            entryId to ExerciseHeartRate(span, intervals.map { it.second }, summary.averageBpm, summary.maxBpm)
        }
        .toMap()
}
