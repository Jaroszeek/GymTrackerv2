package com.jaroszeek.gymtracker.domain.health

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadinessTest {

    private val today = LocalDate.of(2026, 10, 19)

    /** Today's values, and the usual 400 min of sleep, 50 bpm and 100 ms on the [daysBefore] days before. */
    private fun readinessWith(sleep: Int?, restingHeartRate: Double?, hrv: Double?, daysBefore: Int = 3): Readiness {
        val before = (1..daysBefore).map { DailyRecovery(today.minusDays(it.toLong()), 400, 50.0, 100.0) }
        return readiness(today, before + DailyRecovery(today, sleep, restingHeartRate, hrv))
    }

    @Test
    fun withFewerThanThreeDaysBeforeItIsStillCollectingData() {
        val readiness = readinessWith(sleep = 380, restingHeartRate = 52.0, hrv = 90.0, daysBefore = 2)
        assertNull(readiness.level)
        assertEquals(ReadinessMetric(380.0, null, null), readiness.sleep)
        assertEquals(ReadinessMetric(52.0, null, null), readiness.restingHeartRate)
        assertEquals(ReadinessMetric(90.0, null, null), readiness.hrv)
    }

    @Test
    fun anyThreeOfTheSevenDaysBeforeMakeTheBaseline() {
        val days = listOf(
            DailyRecovery(today, sleepMinutes = 400),
            DailyRecovery(today.minusDays(1), sleepMinutes = 420),
            DailyRecovery(today.minusDays(4), sleepMinutes = 450),
            DailyRecovery(today.minusDays(7), sleepMinutes = 480),
        )
        // 400 is under 90 % of 450 but not under 75 %
        assertEquals(ReadinessMetric(400.0, 450.0, 1), readiness(today, days).sleep)
    }

    @Test
    fun sleepScoresAgainstNinetyAndSeventyFivePercentOfUsual() {
        // 360 and 300 are 90 and 75 % of 400
        assertEquals(listOf(2, 2, 1, 1, 0), listOf(450, 360, 359, 300, 299).map { readinessWith(it, null, null).sleep!!.points })
    }

    @Test
    fun restingHeartRateScoresUpToTwoAndFiveBpmAboveUsual() {
        assertEquals(listOf(2, 2, 1, 1, 0), listOf(45.0, 52.0, 52.5, 55.0, 55.5).map { readinessWith(null, it, null).restingHeartRate!!.points })
    }

    @Test
    fun hrvScoresAgainstNinetyFiveAndEightyFivePercentOfUsual() {
        assertEquals(listOf(2, 2, 1, 1, 0), listOf(120.0, 95.0, 94.9, 85.0, 84.9).map { readinessWith(null, null, it).hrv!!.points })
    }

    @Test
    fun theLevelFollowsTheAverageOfThePoints() {
        // Good from an average of 1.5, moderate from 0.75
        assertEquals(ReadinessLevel.Good, readinessWith(400, 50.0, 100.0).level)
        assertEquals(ReadinessLevel.Good, readinessWith(400, 53.0, null).level)
        assertEquals(ReadinessLevel.Moderate, readinessWith(350, 53.0, 100.0).level)
        assertEquals(ReadinessLevel.Moderate, readinessWith(400, 60.0, null).level)
        assertEquals(ReadinessLevel.Low, readinessWith(350, 53.0, 80.0).level)
        assertEquals(ReadinessLevel.Low, readinessWith(200, null, null).level)
    }

    @Test
    fun aMetricWithoutTodaysValueIsLeftOut() {
        // Only sleep, well under usual: the usual heart rate and HRV do not lift the level
        val readiness = readinessWith(sleep = 250, restingHeartRate = null, hrv = null)
        assertNull(readiness.restingHeartRate)
        assertNull(readiness.hrv)
        assertEquals(ReadinessLevel.Low, readiness.level)
    }

    @Test
    fun eachMetricNeedsThreeDaysOfItsOwn() {
        val before = (1..3).map { DailyRecovery(today.minusDays(it.toLong()), sleepMinutes = 400, hrvMs = if (it < 3) 100.0 else null) }
        val readiness = readiness(today, before + DailyRecovery(today, sleepMinutes = 400, hrvMs = 50.0))
        assertEquals(ReadinessMetric(50.0, null, null), readiness.hrv)
        assertEquals(ReadinessLevel.Good, readiness.level)
    }

    @Test
    fun onlyTheSevenDaysBeforeTodayMakeTheBaseline() {
        val outside = listOf(8L, 9L, 10L).map { DailyRecovery(today.minusDays(it), sleepMinutes = 600) } + DailyRecovery(today.plusDays(1), sleepMinutes = 600)
        val days = outside + DailyRecovery(today, sleepMinutes = 300)
        assertEquals(ReadinessMetric(300.0, null, null), readiness(today, days).sleep)
        val week = listOf(5L, 6L, 7L).map { DailyRecovery(today.minusDays(it), sleepMinutes = 320) }
        assertEquals(ReadinessMetric(300.0, 320.0, 2), readiness(today, days + week).sleep)
    }
}
