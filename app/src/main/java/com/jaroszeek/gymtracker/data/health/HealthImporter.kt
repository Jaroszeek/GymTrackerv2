package com.jaroszeek.gymtracker.data.health

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.room3.withWriteTransaction
import com.jaroszeek.gymtracker.core.database.DailyActivityEntity
import com.jaroszeek.gymtracker.core.database.DirtyDayEntity
import com.jaroszeek.gymtracker.core.database.HealthDatabase
import com.jaroszeek.gymtracker.core.database.HealthSessionEntity
import com.jaroszeek.gymtracker.core.database.HeartRateSampleEntity
import com.jaroszeek.gymtracker.core.database.MeasurementEntity
import com.jaroszeek.gymtracker.core.database.SleepEntity
import com.jaroszeek.gymtracker.core.database.SyncTokenEntity
import com.jaroszeek.gymtracker.domain.health.HealthMetric
import com.jaroszeek.gymtracker.domain.health.downsample
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.math.min
import kotlin.reflect.KClass

/** The record types copied from Health Connect; [name] keys the changes token. */
internal enum class SyncedType(val record: KClass<out Record>) {
    Sessions(ExerciseSessionRecord::class),
    HeartRate(HeartRateRecord::class),
    ActiveCalories(ActiveCaloriesBurnedRecord::class),
    TotalCalories(TotalCaloriesBurnedRecord::class),
    Steps(StepsRecord::class),
    Distance(DistanceRecord::class),
    Weight(WeightRecord::class),
    Height(HeightRecord::class),
    BodyFat(BodyFatRecord::class),
    LeanMass(LeanBodyMassRecord::class),
    BoneMass(BoneMassRecord::class),
    WaterMass(BodyWaterMassRecord::class),
    RestingHeartRate(RestingHeartRateRecord::class),
    Hrv(HeartRateVariabilityRmssdRecord::class),
    Sleep(SleepSessionRecord::class),
    ;

    val permission: String get() = HealthPermission.getReadPermission(record)

    /** Kept as rows of their own; the other types only make sessions or days be read again. */
    val stored: Boolean get() = this == Sessions || this == Sleep || metric != null

    val metric: HealthMetric?
        get() = when (this) {
            Weight -> HealthMetric.Weight
            Height -> HealthMetric.Height
            BodyFat -> HealthMetric.BodyFat
            LeanMass -> HealthMetric.LeanMass
            BoneMass -> HealthMetric.BoneMass
            WaterMass -> HealthMetric.WaterMass
            RestingHeartRate -> HealthMetric.RestingHeartRate
            Hrv -> HealthMetric.Hrv
            else -> null
        }
}

/**
 * One sync of everything [granted] allows (A6): each type goes on from its changes token, page by page,
 * so an interrupted run loses nothing; without a token (first run, token expired, history just allowed)
 * the type is read again whole and rows no longer in Health Connect are dropped. Records of this app
 * ([ownPackage]) are skipped. Sessions changed on the way get their heart rate and calories read again,
 * and days whose steps, distance or calories changed are summed again by the Aggregate API.
 * Throws what the client throws, except a missing permission, which skips the type.
 */
internal class HealthImporter(
    private val client: HealthConnectClient,
    private val database: HealthDatabase,
    private val ownPackage: String,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    private val dao = database.healthDao()

    suspend fun run(granted: Set<String>) {
        val history = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
        SyncedType.entries.filter { it.permission in granted }.forEach { type ->
            try {
                sync(type, history)
            } catch (e: SecurityException) {
                // Revoked meanwhile, or read in the background without the permission: the next run tries again
                Log.w(TAG, "No access to ${type.name}", e)
            }
        }
        refreshSessions(granted)
        refreshDays(granted)
    }

    private suspend fun sync(type: SyncedType, history: Boolean) {
        var token = dao.token(type.name) ?: return import(type, history)
        while (true) {
            val response = try {
                client.getChanges(token)
            } catch (e: IllegalArgumentException) {
                // A token Health Connect does not know, e.g. restored from another phone
                Log.w(TAG, "Changes token of ${type.name} rejected", e)
                return import(type, history)
            }
            if (response.changesTokenExpired) return import(type, history)
            database.withWriteTransaction {
                val changes = response.changes
                apply(type, changes.filterIsInstance<UpsertionChange>().map { it.record }, changes.filterIsInstance<DeletionChange>().map { it.recordId })
                dao.saveToken(SyncTokenEntity(type.name, response.nextChangesToken))
            }
            if (!response.hasMore) return
            token = response.nextChangesToken
        }
    }

    /** Reads the type whole; the token is taken first, so changes made meanwhile come with the next run. */
    private suspend fun import(type: SyncedType, history: Boolean) {
        val token = client.getChangesToken(ChangesTokenRequest(setOf(type.record)))
        if (type.stored) {
            val seen = mutableSetOf<String>()
            readAll(type.record) { records ->
                database.withWriteTransaction { apply(type, records, deleted = emptyList()) }
                records.mapTo(seen) { it.metadata.id }
            }
            val gone = localIds(type) - seen
            if (gone.isNotEmpty()) database.withWriteTransaction { apply(type, upserted = emptyList(), deleted = gone.toList()) }
        } else {
            database.withWriteTransaction {
                if (type != SyncedType.Steps && type != SyncedType.Distance) dao.markSessionsStaleSince(Long.MIN_VALUE)
                if (type != SyncedType.HeartRate) markDays(today().minusDays(if (history) HISTORY_DAYS else RECENT_DAYS), today())
            }
        }
        dao.saveToken(SyncTokenEntity(type.name, token))
    }

    /** [deleted] are record ids. */
    private suspend fun apply(type: SyncedType, upserted: List<Record>, deleted: List<String>) {
        val records = upserted.filter { it.metadata.dataOrigin.packageName != ownPackage }
        when (type) {
            SyncedType.Sessions -> {
                val sessions = records.filterIsInstance<ExerciseSessionRecord>()
                val old = sessions.map { it.metadata.id }.chunked(CHUNK).flatMap { dao.sessions(it) }.associateBy { it.id }
                dao.upsertSessions(sessions.map { it.toEntity(old[it.metadata.id]) })
                deleted.chunked(CHUNK).forEach { dao.deleteSessions(it) }
            }
            SyncedType.HeartRate -> {
                records.mapNotNull { it.span() }.forEach { (start, end) -> dao.markSessionsStale(start.toEpochMilli(), end.toEpochMilli()) }
                // A record moved in time leaves the session that held its samples too
                (records.map { it.metadata.id } + deleted).chunked(CHUNK).forEach { dao.markStaleByHeartRate(it) }
            }
            SyncedType.ActiveCalories, SyncedType.TotalCalories, SyncedType.Steps, SyncedType.Distance -> {
                val sessions = type == SyncedType.ActiveCalories || type == SyncedType.TotalCalories
                records.mapNotNull { it.span() }.forEach { (start, end) ->
                    if (sessions) dao.markSessionsStale(start.toEpochMilli(), end.toEpochMilli())
                    markDays(day(start), day(end))
                }
                // ponytail: a deletion names no time, so the last 30 days are summed again; older ones wait for a full import.
                // A record moved in time marks only its new days: keeping every record's span would cover the old ones
                if (deleted.isNotEmpty()) {
                    val since = today().minusDays(RECENT_DAYS)
                    if (sessions) dao.markSessionsStaleSince(since.atStartOfDay(zone).toInstant().toEpochMilli())
                    markDays(since, today())
                }
            }
            SyncedType.Sleep -> {
                dao.upsertSleep(records.filterIsInstance<SleepSessionRecord>().map { it.toEntity() })
                deleted.chunked(CHUNK).forEach { dao.deleteSleep(it) }
            }
            else -> {
                val metric = checkNotNull(type.metric)
                dao.upsertMeasurements(records.mapNotNull { it.toMeasurement(metric) })
                deleted.chunked(CHUNK).forEach { dao.deleteMeasurements(it) }
            }
        }
    }

    private suspend fun localIds(type: SyncedType): Set<String> = when (type) {
        SyncedType.Sessions -> dao.sessionIds()
        SyncedType.Sleep -> dao.sleepIds()
        else -> dao.measurementIds(checkNotNull(type.metric).key)
    }.toSet()

    /** Heart rate and calories of the sessions marked stale, newest first, a limited number per run. */
    private suspend fun refreshSessions(granted: Set<String>) {
        var heartRate = SyncedType.HeartRate.permission in granted
        var metrics = buildSet<AggregateMetric<*>> {
            if (SyncedType.ActiveCalories.permission in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (SyncedType.TotalCalories.permission in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        }
        dao.staleSessions(MAX_SESSION_REFRESHES).forEach { session ->
            val range = TimeRangeFilter.between(Instant.ofEpochMilli(session.startTime), Instant.ofEpochMilli(session.endTime))
            // Without access, revoked even during this run, a session keeps the heart rate it has
            val samples = if (heartRate) readOrNull("heart rate") { heartRate(session, range) } else null
            if (samples == null) heartRate = false
            val calories = if (metrics.isEmpty()) null else readOrNull("calories") { client.aggregate(AggregateRequest(metrics, range)) }
            if (calories == null) metrics = emptySet()
            database.withWriteTransaction {
                if (samples != null) {
                    dao.deleteHeartRate(session.id)
                    dao.insertHeartRate(samples)
                }
                dao.setRefreshed(
                    session.id,
                    calories?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
                    calories?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories,
                )
            }
        }
    }

    /** Null when the access is gone, e.g. revoked during the run; the next run reads it again. */
    private suspend fun <T> readOrNull(what: String, read: suspend () -> T): T? = try {
        read()
    } catch (e: SecurityException) {
        Log.w(TAG, "No access to $what", e)
        null
    }

    /** The app that recorded the session first; another app's heart rate only when that one wrote none. */
    private suspend fun heartRate(session: HealthSessionEntity, range: TimeRangeFilter): List<HeartRateSampleEntity> {
        val records = mutableListOf<HeartRateRecord>()
        readAll(HeartRateRecord::class, range, setOf(DataOrigin(session.origin))) { records += it }
        if (records.isEmpty()) {
            val others = mutableListOf<HeartRateRecord>()
            readAll(HeartRateRecord::class, range) { page -> others += page.filter { it.metadata.dataOrigin.packageName != ownPackage } }
            others.groupBy { it.metadata.dataOrigin.packageName }.maxByOrNull { (_, byApp) -> byApp.sumOf { it.samples.size } }?.let { records += it.value }
        }
        return records
            .flatMap { record -> record.samples.map { HeartRateSampleEntity(session.id, it.time.toEpochMilli(), it.beatsPerMinute.toInt(), record.metadata.id) } }
            .filter { it.time in session.startTime..session.endTime }
            .downsample { it.time }
    }

    /** Sums the dirty days again, a year per call at most; a day without data loses its row. */
    private suspend fun refreshDays(granted: Set<String>) {
        val dirty = dao.dirtyDays()
        if (dirty.isEmpty()) return
        val metrics = buildSet<AggregateMetric<*>> {
            if (SyncedType.Steps.permission in granted) add(StepsRecord.COUNT_TOTAL)
            if (SyncedType.Distance.permission in granted) add(DistanceRecord.DISTANCE_TOTAL)
            if (SyncedType.ActiveCalories.permission in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (SyncedType.TotalCalories.permission in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        }
        var from = dirty.first()
        while (from <= dirty.last()) {
            val to = min(from + MAX_DAYS_PER_CALL - 1, dirty.last())
            val days = if (metrics.isEmpty()) {
                emptyList()
            } else {
                val range = TimeRangeFilter.between(LocalDate.ofEpochDay(from).atStartOfDay(), LocalDate.ofEpochDay(to + 1).atStartOfDay())
                // The days stay dirty for the next run
                val buckets = readOrNull("daily totals") { client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(metrics, range, Period.ofDays(1))) } ?: return
                buckets.mapNotNull { bucket ->
                    val result = bucket.result
                    DailyActivityEntity(
                        day = bucket.startTime.toLocalDate().toEpochDay(),
                        steps = result[StepsRecord.COUNT_TOTAL],
                        distanceMeters = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
                        activeKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
                        totalKcal = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
                    ).takeIf { it.steps != null || it.distanceMeters != null || it.activeKcal != null || it.totalKcal != null }
                }
            }
            database.withWriteTransaction {
                dao.deleteDays(from, to)
                dao.upsertDays(days)
                dao.clearDirty(from, to)
            }
            from = to + 1
        }
    }

    private suspend fun markDays(from: LocalDate, to: LocalDate) {
        dao.markDirty((from.toEpochDay()..to.toEpochDay()).map(::DirtyDayEntity))
    }

    private suspend fun <T : Record> readAll(
        type: KClass<T>,
        range: TimeRangeFilter = TimeRangeFilter.after(Instant.EPOCH),
        origins: Set<DataOrigin> = emptySet(),
        onPage: suspend (List<T>) -> Unit,
    ) {
        var pageToken: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, range, origins, ascendingOrder = true, pageSize = PAGE_SIZE, pageToken = pageToken))
            onPage(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(zone))

    private fun day(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    private fun ExerciseSessionRecord.toEntity(old: HealthSessionEntity?) = HealthSessionEntity(
        id = metadata.id,
        origin = metadata.dataOrigin.packageName,
        exerciseType = exerciseType,
        title = title,
        startTime = startTime.toEpochMilli(),
        endTime = endTime.toEpochMilli(),
        // Kept until the refresh, so the screen does not blink
        activeKcal = old?.activeKcal,
        totalKcal = old?.totalKcal,
        stale = true,
    )

    private fun SleepSessionRecord.toEntity() = SleepEntity(
        id = metadata.id,
        origin = metadata.dataOrigin.packageName,
        startTime = startTime.toEpochMilli(),
        endTime = endTime.toEpochMilli(),
        asleepMinutes = asleepMinutes(),
    )

    private fun Record.toMeasurement(metric: HealthMetric): MeasurementEntity? {
        val (time, value) = when (this) {
            is WeightRecord -> time to weight.inKilograms
            is HeightRecord -> time to height.inMeters
            is BodyFatRecord -> time to percentage.value
            is LeanBodyMassRecord -> time to mass.inKilograms
            is BoneMassRecord -> time to mass.inKilograms
            is BodyWaterMassRecord -> time to mass.inKilograms
            is RestingHeartRateRecord -> time to beatsPerMinute.toDouble()
            is HeartRateVariabilityRmssdRecord -> time to heartRateVariabilityMillis
            else -> return null
        }
        return MeasurementEntity(metadata.id, metric.key, time.toEpochMilli(), value, metadata.dataOrigin.packageName)
    }

    companion object {
        private const val TAG = "HealthSync"
        private const val PAGE_SIZE = 1000

        /** Room binds each id of an IN list as a variable; SQLite allows a limited number. */
        private const val CHUNK = 500

        /** Calls per run stay well under the limits (A6): two or three per session. */
        const val MAX_SESSION_REFRESHES = 100

        const val RECENT_DAYS = 30L

        /** ponytail: daily totals go back a year with the history permission; records have no limit */
        const val HISTORY_DAYS = 365L

        private const val MAX_DAYS_PER_CALL = 366L
    }
}

/** Start and end of the interval records whose changes mark sessions or days. */
private fun Record.span(): Pair<Instant, Instant>? = when (this) {
    is HeartRateRecord -> startTime to endTime
    is ActiveCaloriesBurnedRecord -> startTime to endTime
    is TotalCaloriesBurnedRecord -> startTime to endTime
    is StepsRecord -> startTime to endTime
    is DistanceRecord -> startTime to endTime
    else -> null
}

private val ASLEEP = setOf(
    SleepSessionRecord.STAGE_TYPE_SLEEPING,
    SleepSessionRecord.STAGE_TYPE_LIGHT,
    SleepSessionRecord.STAGE_TYPE_DEEP,
    SleepSessionRecord.STAGE_TYPE_REM,
)
private val AWAKE = setOf(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED)

/** Time in sleep stages; without them the span less any awake stages. */
internal fun SleepSessionRecord.asleepMinutes(): Int {
    fun Iterable<SleepSessionRecord.Stage>.millis() = sumOf { Duration.between(it.startTime, it.endTime).toMillis() }
    val millis = if (stages.any { it.stage in ASLEEP }) {
        stages.filter { it.stage in ASLEEP }.millis()
    } else {
        Duration.between(startTime, endTime).toMillis() - stages.filter { it.stage in AWAKE }.millis()
    }
    return (millis / MILLIS_PER_MINUTE).toInt().coerceAtLeast(0)
}

private const val MILLIS_PER_MINUTE = 60_000L
