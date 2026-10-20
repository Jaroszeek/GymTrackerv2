package com.jaroszeek.gymtracker.data.health

import com.jaroszeek.gymtracker.core.database.HealthDatabase
import com.jaroszeek.gymtracker.core.database.HealthSessionEntity
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.domain.health.BASELINE_DAYS
import com.jaroszeek.gymtracker.domain.health.DailyRecovery
import com.jaroszeek.gymtracker.domain.health.HealthMetric
import com.jaroszeek.gymtracker.domain.health.HealthSession
import com.jaroszeek.gymtracker.domain.health.HeartRateSample
import com.jaroszeek.gymtracker.domain.health.ImportSummary
import com.jaroszeek.gymtracker.domain.health.MATCH_WINDOW_MILLIS
import com.jaroszeek.gymtracker.domain.health.SleepSession
import com.jaroszeek.gymtracker.domain.health.resolveMaxHeartRate
import com.jaroszeek.gymtracker.domain.health.withoutDuplicateSleep
import com.jaroszeek.gymtracker.domain.health.withoutDuplicates
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** What was imported from Health Connect, for the screens; the import itself is [HealthSync]. */
@Singleton
class HealthRepository @Inject constructor(
    database: HealthDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao = database.healthDao()

    /** Newest first, without the copies a second app wrote. */
    fun observeSessions(): Flow<List<HealthSession>> =
        dao.observeSessions().map { rows -> rows.map { it.toDomain() }.withoutDuplicates() }.flowOn(io)

    /** Sessions started up to 12 h around a workout: the ones it can be linked with. */
    fun observeSessionsAround(start: Long, end: Long): Flow<List<HealthSession>> =
        dao.observeSessionsStartedBetween(start - MATCH_WINDOW_MILLIS, end + MATCH_WINDOW_MILLIS)
            .map { rows -> rows.map { it.toDomain() }.withoutDuplicates() }
            .flowOn(io)

    fun observeSession(id: String): Flow<HealthSession?> = dao.observeSession(id).map { it?.toDomain() }.flowOn(io)

    fun observeHeartRate(sessionId: String): Flow<List<HeartRateSample>> =
        dao.observeHeartRate(sessionId).map { rows -> rows.map { HeartRateSample(it.time, it.bpm) } }.flowOn(io)

    fun observeSummary(): Flow<ImportSummary> =
        dao.observeCounts().map { ImportSummary(it.sessions, it.measurements, it.nights, it.days) }.flowOn(io)

    /**
     * Today and the days of the readiness baseline: sleep counts on the day it ended, several values
     * of a day (e.g. from two apps) are averaged.
     */
    fun observeRecovery(today: LocalDate, zone: ZoneId): Flow<List<DailyRecovery>> {
        val from = today.minusDays(BASELINE_DAYS.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val types = listOf(HealthMetric.RestingHeartRate.key, HealthMetric.Hrv.key)
        return combine(dao.observeSleep(from), dao.observeMeasurements(types, from)) { sleep, measurements ->
            val nights = sleep.map { SleepSession(it.id, it.origin, it.startTime, it.endTime, it.asleepMinutes) }
                .withoutDuplicateSleep()
                .groupBy { day(it.endTime) }
                .mapValues { (_, nights) -> nights.sumOf { it.asleepMinutes } }
            fun average(metric: HealthMetric) = measurements.filter { it.type == metric.key }
                .groupBy { day(it.time) }
                .mapValues { (_, values) -> values.map { it.value }.average() }
            val restingHeartRate = average(HealthMetric.RestingHeartRate)
            val hrv = average(HealthMetric.Hrv)
            (nights.keys + restingHeartRate.keys + hrv.keys).sorted().map { DailyRecovery(it, nights[it], restingHeartRate[it], hrv[it]) }
        }.flowOn(io)
    }
}

internal fun HealthSessionEntity.toDomain() = HealthSession(id, origin, exerciseType, title, startTime, endTime, activeKcal, totalKcal)

/** Age from the stored birth year, in the device zone. */
fun AppPreferences.observeAge(clock: Clock): Flow<Int?> = birthYear.map { year -> year?.let { Year.now(clock.withZone(ZoneId.systemDefault())).value - it } }

/** The maximum heart rate the zones use (A6). */
fun AppPreferences.observeMaxHeartRate(clock: Clock): Flow<Int?> = combine(maxHeartRate, observeAge(clock), ::resolveMaxHeartRate)
