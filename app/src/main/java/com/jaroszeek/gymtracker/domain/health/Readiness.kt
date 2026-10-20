package com.jaroszeek.gymtracker.domain.health

import java.time.LocalDate

/** Recovery values of one local day: sleep that ended that day, and the day's average resting heart rate and HRV. */
data class DailyRecovery(val day: LocalDate, val sleepMinutes: Int? = null, val restingHeartRate: Double? = null, val hrvMs: Double? = null)

enum class ReadinessLevel { Good, Moderate, Low }

/**
 * Today's value against the mean of the 7 days before; [points] are 2 (as usual), 1 (a bit off)
 * or 0 (well off), and null while fewer than [MIN_BASELINE_DAYS] of them have a value.
 */
data class ReadinessMetric(val today: Double, val baseline: Double?, val points: Int?)

/** [level] is null while no metric has both today's value and a baseline: still collecting data. */
data class Readiness(
    val level: ReadinessLevel?,
    val sleep: ReadinessMetric?,
    val restingHeartRate: ReadinessMetric?,
    val hrv: ReadinessMetric?,
)

const val BASELINE_DAYS = 7
const val MIN_BASELINE_DAYS = 3

/**
 * A rough guide, not a medical one (A6): less sleep, a higher resting heart rate and a lower HRV than
 * usual each lower the score. Days with several values (e.g. from two apps) should come in averaged.
 */
fun readiness(today: LocalDate, days: List<DailyRecovery>): Readiness {
    val byDay = days.associateBy { it.day }
    val before = (1..BASELINE_DAYS).mapNotNull { byDay[today.minusDays(it.toLong())] }
    fun metric(value: (DailyRecovery) -> Double?, score: (today: Double, baseline: Double) -> Int): ReadinessMetric? {
        val now = byDay[today]?.let(value) ?: return null
        val baseline = before.mapNotNull(value).takeIf { it.size >= MIN_BASELINE_DAYS }?.average()
        return ReadinessMetric(now, baseline, baseline?.let { score(now, it) })
    }
    val sleep = metric({ it.sleepMinutes?.toDouble() }) { now, usual ->
        when {
            now >= usual * SLEEP_GOOD -> 2
            now >= usual * SLEEP_FAIR -> 1
            else -> 0
        }
    }
    val restingHeartRate = metric({ it.restingHeartRate }) { now, usual ->
        when {
            now - usual <= RHR_GOOD_BPM -> 2
            now - usual <= RHR_FAIR_BPM -> 1
            else -> 0
        }
    }
    val hrv = metric({ it.hrvMs }) { now, usual ->
        when {
            now >= usual * HRV_GOOD -> 2
            now >= usual * HRV_FAIR -> 1
            else -> 0
        }
    }
    val points = listOfNotNull(sleep?.points, restingHeartRate?.points, hrv?.points)
    val level = if (points.isEmpty()) {
        null
    } else {
        val average = points.average()
        when {
            average >= GOOD_AVERAGE -> ReadinessLevel.Good
            average >= MODERATE_AVERAGE -> ReadinessLevel.Moderate
            else -> ReadinessLevel.Low
        }
    }
    return Readiness(level, sleep, restingHeartRate, hrv)
}

private const val SLEEP_GOOD = 0.9
private const val SLEEP_FAIR = 0.75
private const val RHR_GOOD_BPM = 2.0
private const val RHR_FAIR_BPM = 5.0
private const val HRV_GOOD = 0.95
private const val HRV_FAIR = 0.85
private const val GOOD_AVERAGE = 1.5
private const val MODERATE_AVERAGE = 0.75
