package com.jaroszeek.gymtracker.domain.health

import kotlin.math.roundToInt

/** Time-weighted average and maximum, and with a maximum heart rate the time in each of the [ZONES]. */
data class HeartRateSummary(val averageBpm: Int, val maxBpm: Int, val zoneMillis: List<Long>?)

/** Lower bounds of the five zones as shares of the maximum heart rate: 50–60, 60–70, 70–80, 80–90 and 90–100 %. */
val ZONES = listOf(0.5, 0.6, 0.7, 0.8, 0.9)

/** A gap longer than this between samples is missing data, not time at the last value. */
const val MAX_SAMPLE_GAP_MILLIS = 120_000L

/** Tanaka's estimate, 208 − 0.7 × age; better than 220 − age for adults. */
fun estimatedMaxHeartRate(age: Int): Int = (208 - 0.7 * age).roundToInt()

/**
 * Each sample lasts until the next one, unless the gap is over [MAX_SAMPLE_GAP_MILLIS]; time under
 * half of [maxHeartRate] is in no zone. Null without samples.
 */
fun summarize(samples: List<HeartRateSample>, maxHeartRate: Int?): HeartRateSummary? {
    if (samples.isEmpty()) return null
    val sorted = samples.sortedBy { it.time }
    val durations = sorted.zipWithNext { a, b -> (b.time - a.time).takeIf { it <= MAX_SAMPLE_GAP_MILLIS } ?: 0L } + 0L
    val total = durations.sum()
    val average = if (total > 0) sorted.indices.sumOf { sorted[it].bpm * durations[it].toDouble() } / total else sorted.map { it.bpm }.average()
    val zones = maxHeartRate?.takeIf { it > 0 }?.let { max ->
        val millis = LongArray(ZONES.size)
        sorted.forEachIndexed { index, sample ->
            val zone = ZONES.indexOfLast { sample.bpm >= it * max }
            if (zone >= 0) millis[zone] += durations[index]
        }
        millis.toList()
    }
    return HeartRateSummary(average.roundToInt(), sorted.maxOf { it.bpm }, zones)
}

/** In time order, a sample at most every [stepMillis], so a session recorded every second stays small. */
fun <T> List<T>.downsample(stepMillis: Long = DOWNSAMPLE_STEP_MILLIS, time: (T) -> Long): List<T> {
    var next = Long.MIN_VALUE
    return sortedBy(time).filter { sample ->
        (time(sample) >= next).also { kept -> if (kept) next = time(sample) + stepMillis }
    }
}

const val DOWNSAMPLE_STEP_MILLIS = 5_000L
