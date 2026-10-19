package com.jaroszeek.gymtracker.data.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteStatement
import com.jaroszeek.gymtracker.core.database.DailyActivityEntity
import com.jaroszeek.gymtracker.core.database.HealthCounts
import com.jaroszeek.gymtracker.domain.health.HealthMetric
import com.jaroszeek.gymtracker.testing.FakeHealthConnectClient
import com.jaroszeek.gymtracker.testing.inMemoryHealthDatabase
import com.jaroszeek.gymtracker.testing.metadata
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthImporterTest {

    private val zone = ZoneId.of("Europe/Warsaw")
    private val today = LocalDate.of(2026, 10, 19)
    private val yesterday = today.minusDays(1)
    private val database = inMemoryHealthDatabase()
    private val dao = database.healthDao()
    private val client = FakeHealthConnectClient(zone)
    private val importer = HealthImporter(client, database, OWN, Clock.fixed(at(today, 12), zone), zone)

    @After
    fun close() = database.close()

    private fun granted(vararg types: SyncedType) = types.mapTo(mutableSetOf()) { it.permission }

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant = day.atTime(hour, minute).atZone(zone).toInstant()

    private fun exercise(id: String, origin: String, start: Instant, end: Instant, title: String? = null) =
        ExerciseSessionRecord(start, null, end, null, metadata(id, origin), ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, title)

    /** A sample every [every] from [start] until before [end]. */
    private fun heartRate(id: String, origin: String, start: Instant, end: Instant, bpm: Long, every: Duration = Duration.ofSeconds(1)): HeartRateRecord {
        val samples = generateSequence(start) { it + every }.takeWhile { it < end }.map { HeartRateRecord.Sample(it, bpm) }.toList()
        return HeartRateRecord(start, null, end, null, samples, metadata(id, origin))
    }

    private fun activeCalories(id: String, start: Instant, end: Instant, kcal: Double) =
        ActiveCaloriesBurnedRecord(start, null, end, null, Energy.kilocalories(kcal), metadata(id, WATCH))

    private fun totalCalories(id: String, start: Instant, end: Instant, kcal: Double) =
        TotalCaloriesBurnedRecord(start, null, end, null, Energy.kilocalories(kcal), metadata(id, WATCH))

    private fun steps(id: String, day: LocalDate, count: Long) = StepsRecord(at(day, 9), null, at(day, 10), null, count, metadata(id, PHONE))

    private fun distance(id: String, day: LocalDate, meters: Double) = DistanceRecord(at(day, 9), null, at(day, 10), null, Length.meters(meters), metadata(id, PHONE))

    private fun weight(id: String, time: Instant, mass: Mass, origin: String = PHONE) = WeightRecord(time, null, mass, metadata(id, origin))

    private fun bodyFat(id: String, time: Instant, percent: Double) = BodyFatRecord(time, null, Percentage(percent), metadata(id, PHONE))

    private fun night(vararg stages: SleepSessionRecord.Stage) =
        SleepSessionRecord(at(yesterday, 23), null, at(today, 7), null, metadata("night", WATCH), stages = stages.toList())

    private fun stage(start: Instant, end: Instant, type: Int) = SleepSessionRecord.Stage(start, end, type)

    private suspend fun sessions() = dao.observeSessions().first()

    private suspend fun session(id: String) = sessions().single { it.id == id }

    private suspend fun heartRateOf(sessionId: String) = dao.observeHeartRate(sessionId).first()

    private suspend fun measurements(metric: HealthMetric) = dao.observeMeasurements(listOf(metric.key), Long.MIN_VALUE).first()

    private suspend fun tracked() = SyncedType.entries.filter { dao.token(it.name) != null }

    /** The HealthDao reads no daily totals back, so they come from SQL. */
    private suspend fun days(): Map<LocalDate, DailyActivityEntity> = database.useReaderConnection { connection ->
        connection.usePrepared("SELECT day, steps, distance_m, active_kcal, total_kcal FROM daily_activity") { row ->
            buildMap {
                while (row.step()) {
                    val day = row.getLong(0)
                    put(LocalDate.ofEpochDay(day), DailyActivityEntity(day, row.longOrNull(1), row.doubleOrNull(2), row.doubleOrNull(3), row.doubleOrNull(4)))
                }
            }
        }
    }

    private fun SQLiteStatement.longOrNull(index: Int) = if (isNull(index)) null else getLong(index)

    private fun SQLiteStatement.doubleOrNull(index: Int) = if (isNull(index)) null else getDouble(index)

    @Test
    fun theFirstRunImportsWhatIsGrantedButNotTheRecordsOfThisApp() = runTest {
        client.upsert(
            exercise("run", WATCH, at(yesterday, 18), at(yesterday, 19), title = "Evening run"),
            exercise("own", OWN, at(yesterday, 8), at(yesterday, 9)),
            weight("weight", at(today, 7), Mass.pounds(180.0)),
            weight("own weight", at(today, 8), Mass.kilograms(80.0), origin = OWN),
            bodyFat("fat", at(today, 7), 18.5),
            RestingHeartRateRecord(at(today, 6), null, 52, metadata("rhr", WATCH)),
            HeartRateVariabilityRmssdRecord(at(today, 6), null, 48.5, metadata("hrv", WATCH)),
            night(
                stage(at(yesterday, 23), at(yesterday, 23, 20), SleepSessionRecord.STAGE_TYPE_AWAKE),
                stage(at(yesterday, 23, 20), at(today, 3), SleepSessionRecord.STAGE_TYPE_LIGHT),
                stage(at(today, 3), at(today, 4), SleepSessionRecord.STAGE_TYPE_DEEP),
                stage(at(today, 4), at(today, 4, 10), SleepSessionRecord.STAGE_TYPE_AWAKE),
                stage(at(today, 4, 10), at(today, 7), SleepSessionRecord.STAGE_TYPE_REM),
            ),
        )

        importer.run(SyncedType.entries.mapTo(mutableSetOf()) { it.permission })

        val run = sessions().single()
        assertEquals(listOf("run", WATCH, "Evening run"), listOf(run.id, run.origin, run.title))
        assertEquals(listOf(at(yesterday, 18), at(yesterday, 19)).map { it.toEpochMilli() }, listOf(run.startTime, run.endTime))
        assertFalse(run.stale)
        val weight = measurements(HealthMetric.Weight).single()
        // 180 lb
        assertEquals(81.6466266, weight.value, 1e-9)
        assertEquals(listOf("weight", PHONE, at(today, 7).toEpochMilli()), listOf(weight.id, weight.origin, weight.time))
        assertEquals(18.5, measurements(HealthMetric.BodyFat).single().value, 0.0)
        assertEquals(52.0, measurements(HealthMetric.RestingHeartRate).single().value, 0.0)
        assertEquals(48.5, measurements(HealthMetric.Hrv).single().value, 0.0)
        // 8 h less 30 min awake
        assertEquals(450, dao.observeSleep(Long.MIN_VALUE).first().single().asleepMinutes)
        assertEquals(SyncedType.entries, tracked())
    }

    @Test
    fun asleepMinutesComeFromTheSleepStagesOrTheSpanLessAwake() {
        assertEquals(480, night().asleepMinutes())
        val awake = night(
            stage(at(today, 2), at(today, 2, 20), SleepSessionRecord.STAGE_TYPE_AWAKE),
            stage(at(today, 6, 50), at(today, 7), SleepSessionRecord.STAGE_TYPE_OUT_OF_BED),
        )
        assertEquals(450, awake.asleepMinutes())
        val unknown = night(
            stage(at(yesterday, 23), at(today, 2), SleepSessionRecord.STAGE_TYPE_UNKNOWN),
            stage(at(today, 2), at(today, 2, 30), SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED),
        )
        assertEquals(450, unknown.asleepMinutes())
        // With sleep stages only they count, not the time between them
        val stages = night(
            stage(at(today, 1), at(today, 2), SleepSessionRecord.STAGE_TYPE_LIGHT),
            stage(at(today, 5), at(today, 5, 30), SleepSessionRecord.STAGE_TYPE_SLEEPING),
        )
        assertEquals(90, stages.asleepMinutes())
    }

    @Test
    fun typesWithoutPermissionAreNeitherReadNorTracked() = runTest {
        client.upsert(
            exercise("run", WATCH, at(yesterday, 18), at(yesterday, 19)),
            heartRate("hr", WATCH, at(yesterday, 18), at(yesterday, 19), bpm = 140),
            activeCalories("burned", at(yesterday, 18), at(yesterday, 19), 300.0),
            weight("weight", at(today, 7), Mass.kilograms(80.0)),
            bodyFat("fat", at(today, 7), 18.5),
            night(),
            steps("steps", today, 5_000),
        )

        importer.run(granted(SyncedType.Sessions, SyncedType.Weight))

        assertEquals(HealthCounts(sessions = 1, measurements = 1, nights = 0, days = 0), dao.observeCounts().first())
        assertEquals(setOf(ExerciseSessionRecord::class, WeightRecord::class), client.reads.map { it.recordType }.toSet())
        assertEquals(listOf(SyncedType.Sessions, SyncedType.Weight), tracked())
        assertTrue(heartRateOf("run").isEmpty())
        assertNull(session("run").activeKcal)
        assertTrue(client.aggregateRanges.isEmpty())
    }

    @Test
    fun anIncrementalRunAppliesTheChangesAndReadsNothingElse() = runTest {
        client.upsert(
            exercise("run", WATCH, at(yesterday, 18), at(yesterday, 19)),
            weight("monday", at(today.minusDays(7), 7), Mass.kilograms(81.0)),
            weight("tuesday", at(today.minusDays(6), 7), Mass.kilograms(80.5)),
        )
        val granted = granted(SyncedType.Sessions, SyncedType.Weight, SyncedType.Sleep)
        importer.run(granted)

        client.upsert(weight("monday", at(today.minusDays(7), 7), Mass.kilograms(80.8)), weight("today", at(today, 7), Mass.kilograms(80.2)))
        client.delete(WeightRecord::class, "tuesday")
        client.upsert(exercise("walk", PHONE, at(today, 8), at(today, 9)))
        client.reads.clear()
        importer.run(granted)

        assertEquals(mapOf("monday" to 80.8, "today" to 80.2), measurements(HealthMetric.Weight).associate { it.id to it.value })
        assertEquals(setOf("run", "walk"), sessions().map { it.id }.toSet())
        assertEquals(emptyList<Any>(), client.reads)
    }

    @Test
    fun aChangedSessionKeepsItsCaloriesUntilItIsRefreshed() = runTest {
        val start = at(yesterday, 18)
        val end = at(yesterday, 19)
        client.upsert(exercise("run", WATCH, start, end), activeCalories("burned", start, end, 300.0))
        val granted = granted(SyncedType.Sessions, SyncedType.ActiveCalories)
        importer.run(granted)
        assertEquals(300.0, session("run").activeKcal!!, 0.0)

        // Renamed, and the run stops before the refresh, e.g. at the rate limit
        client.upsert(exercise("run", WATCH, start, end, title = "Tempo run"))
        client.aggregateError = IllegalStateException("Rate limited")
        assertTrue(runCatching { importer.run(granted) }.exceptionOrNull() is IllegalStateException)
        val renamed = session("run")
        assertEquals("Tempo run", renamed.title)
        assertEquals(300.0, renamed.activeKcal!!, 0.0)
        assertTrue(renamed.stale)

        client.aggregateError = null
        client.upsert(activeCalories("more", start, end, 50.0))
        importer.run(granted)
        assertEquals(350.0, session("run").activeKcal!!, 0.0)
        assertFalse(session("run").stale)
    }

    @Test
    fun recordsAreReadPageByPage() = runTest {
        val first = at(today.minusDays(10), 0)
        client.upsert(*Array(2_500) { weight("weight $it", first + Duration.ofMinutes(it.toLong()), Mass.kilograms(80.0)) })

        importer.run(granted(SyncedType.Weight))

        assertEquals(2_500, measurements(HealthMetric.Weight).size)
        // Pages of 1 000
        assertEquals(3, client.reads.size)
    }

    @Test
    fun changesAreReadPageByPageWhileThereAreMore() = runTest {
        val granted = granted(SyncedType.Weight)
        importer.run(granted)
        client.upsert(*Array(5) { weight("weight $it", at(today, 7) + Duration.ofMinutes(it.toLong()), Mass.kilograms(80.0)) })

        importer.run(granted)
        assertEquals(5, measurements(HealthMetric.Weight).size)
        // Two changes a page
        assertEquals(3, client.changeReads.size)

        // The token went on to the end: one call with nothing new
        importer.run(granted)
        assertEquals(4, client.changeReads.size)
    }

    @Test
    fun anExpiredTokenReadsTheTypeAgainAndDropsWhatIsGone() = runTest {
        client.upsert(
            weight("kept", at(today.minusDays(2), 7), Mass.kilograms(80.0)),
            weight("gone", at(yesterday, 7), Mass.kilograms(80.5)),
            exercise("run", WATCH, at(yesterday, 18), at(yesterday, 19)),
            exercise("ride", WATCH, at(yesterday, 8), at(yesterday, 9)),
            heartRate("hr", WATCH, at(yesterday, 8), at(yesterday, 9), bpm = 130),
        )
        val granted = granted(SyncedType.Sessions, SyncedType.HeartRate, SyncedType.Weight)
        importer.run(granted)
        assertTrue(heartRateOf("ride").isNotEmpty())
        val oldToken = dao.token(SyncedType.Weight.name)

        // Changes past Health Connect's limit are lost
        client.deleteSilently(WeightRecord::class, "gone")
        client.deleteSilently(ExerciseSessionRecord::class, "ride")
        client.upsert(weight("new", at(today, 7), Mass.kilograms(80.2)))
        client.expireTokens(WeightRecord::class)
        client.expireTokens(ExerciseSessionRecord::class)
        importer.run(granted)

        assertEquals(setOf("kept", "new"), measurements(HealthMetric.Weight).map { it.id }.toSet())
        assertEquals(listOf("run"), sessions().map { it.id })
        // Its heart rate went with it
        assertTrue(heartRateOf("ride").isEmpty())
        assertNotEquals(oldToken, dao.token(SyncedType.Weight.name))
    }

    @Test
    fun aRejectedTokenAlsoReadsTheTypeAgain() = runTest {
        val granted = granted(SyncedType.Weight)
        client.upsert(weight("kept", at(today.minusDays(2), 7), Mass.kilograms(80.0)))
        importer.run(granted)

        // E.g. a token from another phone
        client.forgetTokens()
        client.upsert(weight("new", at(today, 7), Mass.kilograms(80.2)))
        client.reads.clear()
        importer.run(granted)

        assertEquals(setOf("kept", "new"), measurements(HealthMetric.Weight).map { it.id }.toSet())
        assertEquals(listOf(WeightRecord::class), client.reads.map { it.recordType })
        assertNotNull(dao.token(SyncedType.Weight.name))
    }

    @Test
    fun heartRateComesFromTheSessionsAppInsideTheSessionEveryFiveSeconds() = runTest {
        val start = at(yesterday, 18)
        val end = at(yesterday, 18, 10)
        client.upsert(
            exercise("run", WATCH, start, end),
            // A sample a second, from a minute before to a minute after
            heartRate("watch", WATCH, start - Duration.ofMinutes(1), end + Duration.ofMinutes(1), bpm = 150),
            // More samples, from another app
            heartRate("phone", PHONE, start - Duration.ofHours(1), end + Duration.ofHours(1), bpm = 90),
        )

        importer.run(granted(SyncedType.Sessions, SyncedType.HeartRate))

        val samples = heartRateOf("run")
        // From the start to the end, both included
        assertEquals((0..120).map { start.toEpochMilli() + it * 5_000L }, samples.map { it.time })
        assertEquals(setOf(150), samples.map { it.bpm }.toSet())
    }

    @Test
    fun withoutHeartRateFromTheSessionsAppTheAppWithMostSamplesIsUsed() = runTest {
        val start = at(yesterday, 18)
        val end = at(yesterday, 18, 10)
        client.upsert(
            exercise("run", PHONE, start, end),
            // The watch wrote 300 samples in two records, the ring 200 in one; this app's own are left out
            heartRate("watch 1", WATCH, start, at(yesterday, 18, 5), bpm = 140, every = Duration.ofSeconds(2)),
            heartRate("watch 2", WATCH, at(yesterday, 18, 5), end, bpm = 140, every = Duration.ofSeconds(2)),
            heartRate("ring", RING, start, end, bpm = 100, every = Duration.ofSeconds(3)),
            heartRate("own", OWN, start, end, bpm = 70),
        )

        importer.run(granted(SyncedType.Sessions, SyncedType.HeartRate))

        val samples = heartRateOf("run")
        assertTrue(samples.isNotEmpty())
        assertEquals(setOf(140), samples.map { it.bpm }.toSet())
    }

    @Test
    fun sessionCaloriesAreSummedOverTheSession() = runTest {
        val start = at(yesterday, 18)
        val end = at(yesterday, 19)
        client.upsert(
            exercise("run", WATCH, start, end),
            activeCalories("first half", start, at(yesterday, 18, 30), 200.0),
            activeCalories("second half", at(yesterday, 18, 30), end, 150.0),
            activeCalories("later", at(yesterday, 20), at(yesterday, 21), 100.0),
            totalCalories("total", start, end, 500.0),
        )

        importer.run(granted(SyncedType.Sessions, SyncedType.ActiveCalories, SyncedType.TotalCalories))

        val run = session("run")
        assertEquals(350.0, run.activeKcal!!, 1e-9)
        assertEquals(500.0, run.totalKcal!!, 1e-9)
        assertEquals(listOf(start to end), client.aggregateRanges)
    }

    @Test
    fun aHeartRateChangeRefreshesOnlyTheSessionsItOverlaps() = runTest {
        client.upsert(
            exercise("morning", WATCH, at(yesterday, 8), at(yesterday, 9)),
            exercise("evening", WATCH, at(yesterday, 18), at(yesterday, 19)),
        )
        val granted = granted(SyncedType.Sessions, SyncedType.HeartRate)
        importer.run(granted)
        assertTrue(heartRateOf("evening").isEmpty())

        // The watch hands its heart rate over later
        client.upsert(heartRate("hr", WATCH, at(yesterday, 18), at(yesterday, 19), bpm = 150))
        client.reads.clear()
        importer.run(granted)

        assertTrue(heartRateOf("evening").isNotEmpty())
        assertEquals(setOf(at(yesterday, 18)), client.reads.map { it.timeRangeFilter.startTime }.toSet())
    }

    @Test
    fun deletingTheHeartRateASessionUsedRefreshesIt() = runTest {
        val start = at(yesterday, 18)
        val end = at(yesterday, 19)
        client.upsert(
            exercise("run", WATCH, start, end),
            heartRate("watch", WATCH, start, end, bpm = 150),
            heartRate("ring", RING, start, end, bpm = 100),
        )
        val granted = granted(SyncedType.Sessions, SyncedType.HeartRate)
        importer.run(granted)
        assertEquals(setOf(150), heartRateOf("run").map { it.bpm }.toSet())

        client.delete(HeartRateRecord::class, "watch")
        importer.run(granted)

        assertEquals(setOf(100), heartRateOf("run").map { it.bpm }.toSet())
    }

    @Test
    fun theFirstImportSumsTheLast30Days() = runTest {
        client.upsert(steps("today", today, 1_000), steps("oldest", today.minusDays(30), 2_000), steps("older", today.minusDays(31), 3_000))

        importer.run(granted(SyncedType.Steps))

        assertEquals(mapOf(today.minusDays(30) to 2_000L, today to 1_000L), days().mapValues { it.value.steps })
    }

    @Test
    fun withTheHistoryPermissionTheFirstImportSumsAYear() = runTest {
        client.upsert(steps("recent", today.minusDays(31), 3_000), steps("oldest", today.minusDays(365), 2_000), steps("older", today.minusDays(366), 1_000))

        importer.run(granted(SyncedType.Steps) + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)

        assertEquals(mapOf(today.minusDays(365) to 2_000L, today.minusDays(31) to 3_000L), days().mapValues { it.value.steps })
    }

    @Test
    fun changedStepsDistanceAndCaloriesSumTheirDaysAgain() = runTest {
        val day = today.minusDays(2)
        client.upsert(
            steps("steps", day, 1_000),
            distance("distance", day, 800.0),
            activeCalories("active", at(day, 9), at(day, 10), 250.0),
            totalCalories("total", at(day, 0), at(day, 23), 2_000.0),
        )
        val granted = granted(SyncedType.Steps, SyncedType.Distance, SyncedType.ActiveCalories, SyncedType.TotalCalories)
        importer.run(granted)
        assertEquals(DailyActivityEntity(day.toEpochDay(), 1_000, 800.0, 250.0, 2_000.0), days()[day])

        client.upsert(steps("more steps", day, 500), distance("next day", day.plusDays(1), 1_200.0))
        importer.run(granted)

        assertEquals(DailyActivityEntity(day.toEpochDay(), 1_500, 800.0, 250.0, 2_000.0), days()[day])
        assertEquals(DailyActivityEntity(day.plusDays(1).toEpochDay(), null, 1_200.0, null, null), days()[day.plusDays(1)])
        // Only the changed days are summed again
        assertEquals(day.atStartOfDay() to day.plusDays(2).atStartOfDay(), client.dayRanges.last())
    }

    @Test
    fun aDayThatLostItsDataLosesItsRow() = runTest {
        client.upsert(steps("steps", today.minusDays(3), 1_000), steps("other", yesterday, 2_000))
        val granted = granted(SyncedType.Steps)
        importer.run(granted)
        assertEquals(setOf(today.minusDays(3), yesterday), days().keys)

        client.delete(StepsRecord::class, "steps")
        importer.run(granted)

        assertEquals(mapOf(yesterday to 2_000L), days().mapValues { it.value.steps })
    }

    @Test
    fun aSecurityExceptionSkipsOnlyThatType() = runTest {
        client.upsert(weight("weight", at(today, 7), Mass.kilograms(80.0)), bodyFat("fat", at(today, 7), 18.5))
        client.denied += WeightRecord::class
        val granted = granted(SyncedType.Weight, SyncedType.BodyFat)

        importer.run(granted)

        assertTrue(measurements(HealthMetric.Weight).isEmpty())
        assertEquals(1, measurements(HealthMetric.BodyFat).size)
        assertEquals(listOf(SyncedType.BodyFat), tracked())

        // Allowed again, it comes with the next run
        client.denied.clear()
        importer.run(granted)
        assertEquals(1, measurements(HealthMetric.Weight).size)
    }

    @Test
    fun heartRateLostDuringTheRunLeavesTheSessionStaleForTheNextRun() = runTest {
        val start = at(yesterday, 18)
        val end = at(yesterday, 19)
        client.upsert(
            exercise("run", WATCH, start, end),
            heartRate("hr", WATCH, start, end, bpm = 150),
            activeCalories("burned", start, end, 300.0),
            steps("steps", today, 1_000),
        )
        // Granted, yet refused: as when the app leaves the foreground without the background permission
        client.denied += HeartRateRecord::class
        val granted = granted(SyncedType.Sessions, SyncedType.HeartRate, SyncedType.ActiveCalories, SyncedType.Steps)

        importer.run(granted)

        assertTrue(heartRateOf("run").isEmpty())
        assertTrue(session("run").stale)
        assertNull(session("run").activeKcal)
        assertEquals(1_000L, days()[today]?.steps)

        client.denied.clear()
        importer.run(granted)

        assertEquals(setOf(150), heartRateOf("run").map { it.bpm }.toSet())
        assertFalse(session("run").stale)
        assertEquals(300.0, session("run").activeKcal!!, 0.0)
    }

    @Test
    fun withoutTheHeartRatePermissionSessionsStillGetTheirCalories() = runTest {
        val start = at(yesterday, 18)
        val end = at(yesterday, 19)
        client.upsert(exercise("run", WATCH, start, end), heartRate("hr", WATCH, start, end, bpm = 150), activeCalories("burned", start, end, 300.0))

        importer.run(granted(SyncedType.Sessions, SyncedType.ActiveCalories))

        assertTrue(heartRateOf("run").isEmpty())
        assertFalse(session("run").stale)
        assertEquals(300.0, session("run").activeKcal!!, 0.0)
    }

    @Test
    fun aHeartRateRecordMovedToAnotherSessionLeavesTheOldOne() = runTest {
        client.upsert(
            exercise("morning", WATCH, at(yesterday, 8), at(yesterday, 9)),
            exercise("evening", WATCH, at(yesterday, 18), at(yesterday, 19)),
            heartRate("hr", WATCH, at(yesterday, 8), at(yesterday, 9), bpm = 120, every = Duration.ofMinutes(1)),
        )
        val all = granted(SyncedType.Sessions, SyncedType.HeartRate)
        importer.run(all)
        assertTrue(heartRateOf("morning").isNotEmpty())

        client.upsert(heartRate("hr", WATCH, at(yesterday, 18), at(yesterday, 19), bpm = 140, every = Duration.ofMinutes(1)))
        importer.run(all)

        assertTrue(heartRateOf("morning").isEmpty())
        assertEquals(setOf(140), heartRateOf("evening").map { it.bpm }.toSet())
    }

    private companion object {
        const val OWN = "com.jaroszeek.gymtracker"
        const val WATCH = "com.example.watch"
        const val PHONE = "com.example.phone"
        const val RING = "com.example.ring"
    }
}
