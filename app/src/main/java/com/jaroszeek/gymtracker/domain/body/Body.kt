package com.jaroszeek.gymtracker.domain.body

import com.jaroszeek.gymtracker.domain.health.HealthMetric
import kotlin.math.abs
import kotlin.math.max

/**
 * Body values of the profile (A5.8) in kg, m and %; [key] is stored, the same as Health Connect's
 * where it has the type. [range] is what can be entered.
 */
enum class BodyMetric(val key: String, val range: ClosedFloatingPointRange<Double>) {
    Weight(HealthMetric.Weight.key, 20.0..400.0),
    Height(HealthMetric.Height.key, 1.0..2.5),
    BodyFat(HealthMetric.BodyFat.key, 2.0..75.0),

    /** Health Connect has no such type: entered by hand only. */
    SkeletalMuscle("skeletal_muscle", 5.0..100.0),
    ;

    companion object {
        fun fromKey(key: String): BodyMetric? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A body value at [time] (epoch millis UTC). [manualId] is set for values entered in the app; the
 * others come from Health Connect, written by the app [origin].
 */
data class BodyMeasurement(val metric: BodyMetric, val time: Long, val value: Double, val origin: String? = null, val manualId: Long? = null)

/**
 * Values entered here and read from Health Connect as one list, oldest first. A value is a duplicate of
 * one from another source with the same metric, at most [DUPLICATE_WINDOW_MILLIS] apart and differing by
 * at most 1 %, e.g. a weighing entered here and also synced by the scale's app. The one entered here
 * stays, otherwise the earlier one.
 */
// ponytail: compares each value with all kept ones, fine for years of daily weighings; sort by time and compare neighbours if it gets slow
fun List<BodyMeasurement>.withoutDuplicates(): List<BodyMeasurement> {
    val kept = mutableListOf<BodyMeasurement>()
    sortedWith(compareBy({ it.manualId == null }, { it.time })).forEach { measurement ->
        if (kept.none { it.duplicates(measurement) }) kept += measurement
    }
    return kept.sortedBy { it.time }
}

private fun BodyMeasurement.duplicates(other: BodyMeasurement) = metric == other.metric && origin != other.origin &&
    abs(time - other.time) <= DUPLICATE_WINDOW_MILLIS && abs(value - other.value) <= DUPLICATE_TOLERANCE * max(abs(value), abs(other.value))

const val DUPLICATE_WINDOW_MILLIS = 30 * 60_000L
private const val DUPLICATE_TOLERANCE = 0.01

fun bmi(weightKg: Double, heightM: Double): Double = weightKg / (heightM * heightM)

/** BMI at each weighing (time to BMI) with the latest height, which barely changes in adults; empty without a height. */
fun List<BodyMeasurement>.bmiSeries(): List<Pair<Long, Double>> {
    val height = filter { it.metric == BodyMetric.Height }.maxByOrNull { it.time } ?: return emptyList()
    return filter { it.metric == BodyMetric.Weight }.map { it.time to bmi(it.value, height.value) }
}

/** WHO categories for adults. */
enum class BmiCategory { Underweight, Normal, Overweight, Obese }

fun bmiCategory(bmi: Double): BmiCategory = when {
    bmi < BMI_NORMAL -> BmiCategory.Underweight
    bmi < BMI_OVERWEIGHT -> BmiCategory.Normal
    bmi < BMI_OBESE -> BmiCategory.Overweight
    else -> BmiCategory.Obese
}

private const val BMI_NORMAL = 18.5
private const val BMI_OVERWEIGHT = 25.0
private const val BMI_OBESE = 30.0

/** The latest value of each metric; fat and lean mass come from the latest weight and body fat. */
data class BodySummary(
    val weight: BodyMeasurement? = null,
    val height: BodyMeasurement? = null,
    val bodyFat: BodyMeasurement? = null,
    val skeletalMuscle: BodyMeasurement? = null,
) {
    val bmi: Double? get() = if (weight != null && height != null) bmi(weight.value, height.value) else null

    val fatMassKg: Double? get() = if (weight != null && bodyFat != null) weight.value * bodyFat.value / PERCENT else null

    val leanMassKg: Double? get() = fatMassKg?.let { fat -> weight?.let { it.value - fat } }

    operator fun get(metric: BodyMetric): BodyMeasurement? = when (metric) {
        BodyMetric.Weight -> weight
        BodyMetric.Height -> height
        BodyMetric.BodyFat -> bodyFat
        BodyMetric.SkeletalMuscle -> skeletalMuscle
    }
}

fun bodySummary(measurements: List<BodyMeasurement>): BodySummary {
    fun latest(metric: BodyMetric) = measurements.filter { it.metric == metric }.maxByOrNull { it.time }
    return BodySummary(latest(BodyMetric.Weight), latest(BodyMetric.Height), latest(BodyMetric.BodyFat), latest(BodyMetric.SkeletalMuscle))
}

private const val PERCENT = 100.0
