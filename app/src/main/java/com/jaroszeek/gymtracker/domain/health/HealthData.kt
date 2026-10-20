package com.jaroszeek.gymtracker.domain.health

import java.time.LocalDate

/** Body and recovery values read from Health Connect; [key] is stored. Units: kg, m, %, bpm and ms. */
enum class HealthMetric(val key: String) {
    Weight("weight"),
    Height("height"),
    BodyFat("body_fat"),
    LeanMass("lean_mass"),
    BoneMass("bone_mass"),
    WaterMass("water_mass"),
    RestingHeartRate("resting_hr"),
    Hrv("hrv"),
    ;

    companion object {
        fun fromKey(key: String): HealthMetric = entries.first { it.key == key }
    }
}

/**
 * An exercise session another app wrote to Health Connect, e.g. a walk or a workout recorded by a watch.
 * [exerciseType] is Health Connect's constant, [origin] the package of the app. Times are epoch millis UTC.
 */
data class HealthSession(
    val id: String,
    val origin: String,
    val exerciseType: Int,
    val title: String?,
    val startTime: Long,
    val endTime: Long,
    val activeKcal: Double? = null,
    val totalKcal: Double? = null,
) {
    val durationMillis: Long get() = (endTime - startTime).coerceAtLeast(0)
}

data class HeartRateSample(val time: Long, val bpm: Int)

data class SleepSession(val id: String, val origin: String, val startTime: Long, val endTime: Long, val asleepMinutes: Int)

/** Totals of a local day, summed by Health Connect across apps. */
data class DailyActivity(val day: LocalDate, val steps: Long?, val activeKcal: Double?, val totalKcal: Double?)

/** Active and total kcal of today and on average over the earlier days that have them; null where there are none. */
data class Calories(val activeToday: Double?, val totalToday: Double?, val activeAverage: Double?, val totalAverage: Double?) {
    val isEmpty: Boolean get() = listOf(activeToday, totalToday, activeAverage, totalAverage).all { it == null }
}

/** Today is left out of the averages, as it is not over yet; a day without a value does not lower them either. */
fun calories(days: List<DailyActivity>, today: LocalDate): Calories {
    val todays = days.firstOrNull { it.day == today }
    val before = days.filter { it.day < today }
    return Calories(
        activeToday = todays?.activeKcal,
        totalToday = todays?.totalKcal,
        activeAverage = before.mapNotNull { it.activeKcal }.takeIf { it.isNotEmpty() }?.average(),
        totalAverage = before.mapNotNull { it.totalKcal }.takeIf { it.isNotEmpty() }?.average(),
    )
}

/** How much is imported, for the Health Connect screen. */
data class ImportSummary(val sessions: Int = 0, val measurements: Int = 0, val nights: Int = 0, val days: Int = 0)
