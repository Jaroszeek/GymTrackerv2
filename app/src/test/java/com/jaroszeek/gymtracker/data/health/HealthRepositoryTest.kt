package com.jaroszeek.gymtracker.data.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.jaroszeek.gymtracker.core.database.DailyActivityEntity
import com.jaroszeek.gymtracker.core.database.HealthSessionEntity
import com.jaroszeek.gymtracker.core.database.MeasurementEntity
import com.jaroszeek.gymtracker.core.database.SleepEntity
import com.jaroszeek.gymtracker.domain.health.DailyRecovery
import com.jaroszeek.gymtracker.domain.health.HealthMetric
import com.jaroszeek.gymtracker.domain.health.ImportSummary
import com.jaroszeek.gymtracker.testing.inMemoryHealthDatabase
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthRepositoryTest {

    private val zone = ZoneId.of("Europe/Warsaw")
    private val today = LocalDate.of(2026, 10, 19)
    private val database = inMemoryHealthDatabase()
    private val dao = database.healthDao()
    private val repository = HealthRepository(database, Dispatchers.IO)

    @After
    fun close() = database.close()

    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun measurement(id: String, metric: HealthMetric, time: Long, value: Double, origin: String = WATCH) = MeasurementEntity(id, metric.key, time, value, origin)

    private fun session(id: String, origin: String, type: Int, start: Long, end: Long) = HealthSessionEntity(id, origin, type, null, start, end)

    private suspend fun recovery() = repository.observeRecovery(today, zone).first()

    @Test
    fun sleepCountsOnTheLocalDayItEndedWithoutTheCopyOfASecondApp() = runTest {
        dao.upsertSleep(
            listOf(
                SleepEntity("night", WATCH, at(today.minusDays(1), 22), at(today, 6), 420),
                // The same night from the phone, shorter but with more sleep: the longer one stays
                SleepEntity("copy", PHONE, at(today.minusDays(1), 22, 30), at(today, 6), 440),
                SleepEntity("nap", WATCH, at(today, 14), at(today, 14, 45), 40),
                // Ends at 01:30 here, still on the 16th in UTC
                SleepEntity("late", RING, at(today.minusDays(3), 23), at(today.minusDays(2), 1, 30), 150),
            ),
        )

        assertEquals(listOf(DailyRecovery(today.minusDays(2), sleepMinutes = 150), DailyRecovery(today, sleepMinutes = 460)), recovery())
    }

    @Test
    fun severalValuesOfADayAreAveraged() = runTest {
        dao.upsertMeasurements(
            listOf(
                measurement("watch rhr", HealthMetric.RestingHeartRate, at(today, 6), 50.0),
                measurement("ring rhr", HealthMetric.RestingHeartRate, at(today, 7), 54.0, RING),
                // 00:30 here, still the day before in UTC
                measurement("night hrv", HealthMetric.Hrv, at(today, 0, 30), 40.0),
                measurement("morning hrv", HealthMetric.Hrv, at(today, 7), 50.0),
                measurement("yesterday hrv", HealthMetric.Hrv, at(today.minusDays(1), 7), 60.0),
                measurement("weight", HealthMetric.Weight, at(today, 7), 80.0, PHONE),
            ),
        )

        assertEquals(
            listOf(DailyRecovery(today.minusDays(1), hrvMs = 60.0), DailyRecovery(today, restingHeartRate = 52.0, hrvMs = 45.0)),
            recovery(),
        )
    }

    @Test
    fun recoveryCoversTodayAndTheSevenDaysBefore() = runTest {
        dao.upsertMeasurements(
            listOf(
                measurement("too old", HealthMetric.RestingHeartRate, at(today.minusDays(8), 23, 59), 60.0),
                measurement("first", HealthMetric.RestingHeartRate, at(today.minusDays(7), 0), 51.0),
                measurement("today", HealthMetric.RestingHeartRate, at(today, 6), 50.0),
            ),
        )
        dao.upsertSleep(
            listOf(
                // A night counts by its end
                SleepEntity("ended in the week", WATCH, at(today.minusDays(8), 22), at(today.minusDays(7), 6), 420),
                SleepEntity("ended before", WATCH, at(today.minusDays(9), 22), at(today.minusDays(8), 6), 400),
            ),
        )

        assertEquals(
            listOf(DailyRecovery(today.minusDays(7), sleepMinutes = 420, restingHeartRate = 51.0), DailyRecovery(today, restingHeartRate = 50.0)),
            recovery(),
        )
    }

    @Test
    fun sessionsComeNewestFirstWithoutTheCopyOfASecondApp() = runTest {
        dao.upsertSessions(
            listOf(
                session("run", WATCH, RUNNING, at(today, 10), at(today, 11)),
                session("copy", PHONE, RUNNING, at(today, 10, 5), at(today, 10, 55)),
                // Overlapping, but a walk
                session("walk", PHONE, WALKING, at(today, 10, 10), at(today, 10, 40)),
                session("evening", PHONE, RUNNING, at(today, 18), at(today, 18, 30)),
            ),
        )

        assertEquals(listOf("evening", "walk", "run"), repository.observeSessions().first().map { it.id })
    }

    @Test
    fun theSummaryCountsWhatWasImported() = runTest {
        assertEquals(ImportSummary(), repository.observeSummary().first())

        dao.upsertSessions(listOf(session("run", WATCH, RUNNING, at(today, 10), at(today, 11)), session("walk", PHONE, WALKING, at(today, 18), at(today, 19))))
        dao.upsertMeasurements(
            listOf(
                measurement("weight", HealthMetric.Weight, at(today, 7), 80.0, PHONE),
                measurement("fat", HealthMetric.BodyFat, at(today, 7), 18.0, PHONE),
                measurement("rhr", HealthMetric.RestingHeartRate, at(today, 6), 50.0),
            ),
        )
        dao.upsertSleep(listOf(SleepEntity("night", WATCH, at(today.minusDays(1), 22), at(today, 6), 420)))
        dao.upsertDays(listOf(DailyActivityEntity(today.toEpochDay(), 5_000, null, null, null), DailyActivityEntity(today.minusDays(1).toEpochDay(), 8_000, 6_000.0, null, null)))

        assertEquals(ImportSummary(sessions = 2, measurements = 3, nights = 1, days = 2), repository.observeSummary().first())
    }

    private companion object {
        const val RUNNING = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        const val WALKING = ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        const val WATCH = "com.example.watch"
        const val PHONE = "com.example.phone"
        const val RING = "com.example.ring"
    }
}
