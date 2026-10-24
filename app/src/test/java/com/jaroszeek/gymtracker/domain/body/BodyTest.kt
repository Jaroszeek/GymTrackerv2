package com.jaroszeek.gymtracker.domain.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyTest {

    private val minute = 60_000L
    private val day = 24 * 60 * minute

    @Test
    fun bmiIsWeightOverHeightSquared() {
        assertEquals(24.69, bmi(80.0, 1.8), 0.01)
        assertEquals(22.0, bmi(63.58, 1.7), 0.01)
    }

    @Test
    fun theBmiSeriesUsesTheLatestHeightForEveryWeighing() {
        val values = listOf(
            BodyMeasurement(BodyMetric.Height, 0, 1.7, manualId = 1),
            BodyMeasurement(BodyMetric.Weight, day, 81.0, manualId = 2),
            BodyMeasurement(BodyMetric.Height, 2 * day, 1.8, origin = "scale"),
            BodyMeasurement(BodyMetric.Weight, 3 * day, 77.76, origin = "scale"),
        )
        val series = values.bmiSeries()
        assertEquals(listOf(day, 3 * day), series.map { it.first })
        assertEquals(25.0, series[0].second, 1e-9)
        assertEquals(24.0, series[1].second, 1e-9)
        assertEquals(emptyList<Pair<Long, Double>>(), values.filter { it.metric == BodyMetric.Weight }.bmiSeries())
    }

    @Test
    fun bmiCategoriesStartAtTheirLowerBound() {
        assertEquals(BmiCategory.Underweight, bmiCategory(18.49))
        assertEquals(BmiCategory.Normal, bmiCategory(18.5))
        assertEquals(BmiCategory.Normal, bmiCategory(24.99))
        assertEquals(BmiCategory.Overweight, bmiCategory(25.0))
        assertEquals(BmiCategory.Obese, bmiCategory(30.0))
    }

    @Test
    fun aValueEnteredHereReplacesTheSameOneFromHealthConnect() {
        val manual = BodyMeasurement(BodyMetric.Weight, 1_000 * minute, 80.0, manualId = 1)
        val synced = BodyMeasurement(BodyMetric.Weight, 1_010 * minute, 80.3, origin = "com.sec.android.app.shealth")
        assertEquals(listOf(manual), listOf(synced, manual).withoutDuplicates())
    }

    @Test
    fun differentValuesTimesOrMetricsAreNoDuplicates() {
        val manual = BodyMeasurement(BodyMetric.Weight, 1_000 * minute, 80.0, manualId = 1)
        val values = listOf(
            manual,
            // More than 1 % apart
            BodyMeasurement(BodyMetric.Weight, 1_005 * minute, 81.0, origin = "scale"),
            // More than 30 min apart
            BodyMeasurement(BodyMetric.Weight, 1_031 * minute, 80.0, origin = "scale"),
            // Another metric with the same number
            BodyMeasurement(BodyMetric.SkeletalMuscle, 1_000 * minute, 80.0, manualId = 2),
        )
        assertEquals(values.sortedBy { it.time }, values.withoutDuplicates())
    }

    @Test
    fun valuesOfOneSourceAreNeverDuplicates() {
        val values = listOf(
            BodyMeasurement(BodyMetric.Weight, 0, 80.0, manualId = 1),
            BodyMeasurement(BodyMetric.Weight, minute, 80.0, manualId = 2),
            BodyMeasurement(BodyMetric.Weight, 2 * minute, 80.0, origin = "scale"),
            BodyMeasurement(BodyMetric.Weight, 3 * minute, 80.0, origin = "scale"),
        )
        // Both scale values are copies of an entry made here
        assertEquals(values.take(2), values.withoutDuplicates())
        assertEquals(values.drop(2), values.drop(2).withoutDuplicates())
    }

    @Test
    fun ofTwoAppsTheEarlierValueStaysAndTheResultIsOldestFirst() {
        val scale = BodyMeasurement(BodyMetric.BodyFat, day + 2 * minute, 18.0, origin = "scale")
        val watch = BodyMeasurement(BodyMetric.BodyFat, day + 5 * minute, 18.1, origin = "watch")
        val older = BodyMeasurement(BodyMetric.BodyFat, 0, 19.0, origin = "watch")
        assertEquals(listOf(older, scale), listOf(watch, scale, older).withoutDuplicates())
    }

    @Test
    fun theSummaryHasTheLatestValuesAndTheMassesFromThem() {
        val summary = bodySummary(
            listOf(
                BodyMeasurement(BodyMetric.Weight, 0, 85.0, manualId = 1),
                BodyMeasurement(BodyMetric.Weight, day, 80.0, origin = "scale"),
                BodyMeasurement(BodyMetric.Height, 0, 1.8, manualId = 2),
                BodyMeasurement(BodyMetric.BodyFat, day, 20.0, origin = "scale"),
            ),
        )
        assertEquals(80.0, summary.weight!!.value, 0.0)
        assertEquals(24.69, summary.bmi!!, 0.01)
        assertEquals(16.0, summary.fatMassKg!!, 1e-9)
        assertEquals(64.0, summary.leanMassKg!!, 1e-9)
        assertNull(summary.skeletalMuscle)
        assertEquals(summary.height, summary[BodyMetric.Height])
    }

    @Test
    fun withoutHeightOrBodyFatTheirResultsAreMissing() {
        val summary = bodySummary(listOf(BodyMeasurement(BodyMetric.Weight, 0, 80.0, manualId = 1)))
        assertNull(summary.bmi)
        assertNull(summary.fatMassKg)
        assertNull(summary.leanMassKg)
        assertNull(bodySummary(emptyList()).weight)
    }

    @Test
    fun rangesTakeTheEdgesAndKeysMatchHealthConnect() {
        assertEquals(true, 20.0 in BodyMetric.Weight.range)
        assertEquals(false, 19.9 in BodyMetric.Weight.range)
        assertEquals(true, 2.5 in BodyMetric.Height.range)
        assertEquals(BodyMetric.BodyFat, BodyMetric.fromKey("body_fat"))
        assertNull(BodyMetric.fromKey("lean_mass"))
    }
}
