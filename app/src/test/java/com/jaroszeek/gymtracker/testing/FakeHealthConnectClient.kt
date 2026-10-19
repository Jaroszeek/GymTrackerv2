package com.jaroszeek.gymtracker.testing

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
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
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Health Connect in memory, for the importer: records per type, a change log that changes tokens
 * point into, paged reads and changes, and the totals the Aggregate API gives. Days are local days
 * of [zone]. What the importer does not call throws.
 */
class FakeHealthConnectClient(private val zone: ZoneId) : HealthConnectClient by unsupported() {
    private val stored = mutableMapOf<KClass<out Record>, MutableMap<String, Record>>()
    private val log = mutableListOf<Pair<KClass<out Record>, Change>>()
    private val tokens = mutableMapOf<String, Token>()
    private var tokenCount = 0

    /** Changes per page of [getChanges]; small, so paging happens. */
    var changesPageSize = 2

    /** Types whose every call throws a [SecurityException], as after a revoked permission. */
    val denied = mutableSetOf<KClass<out Record>>()

    /** Thrown by [aggregate] while set, e.g. to stop a run before its sessions are refreshed. */
    var aggregateError: Exception? = null

    val reads = mutableListOf<ReadRecordsRequest<*>>()

    /** The type of each [getChanges] call. */
    val changeReads = mutableListOf<KClass<out Record>>()
    val aggregateRanges = mutableListOf<Pair<Instant, Instant>>()
    val dayRanges = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()

    /** Inserts, or replaces by id, and logs the change. */
    fun upsert(vararg records: Record) = records.forEach { record ->
        stored.getOrPut(record::class) { linkedMapOf() }[record.metadata.id] = record
        log += record::class to UpsertionChange(record)
    }

    fun delete(type: KClass<out Record>, id: String) {
        deleteSilently(type, id)
        log += type to DeletionChange(id)
    }

    /** Without a change in the log, as when the changes are past Health Connect's limit: only a whole read sees it. */
    fun deleteSilently(type: KClass<out Record>, id: String) {
        checkNotNull(stored[type]?.remove(id)) { "No ${type.simpleName} $id" }
    }

    fun expireTokens(type: KClass<out Record>) = tokens.values.filter { it.type == type }.forEach { it.expired = true }

    /** Every token given so far becomes one Health Connect does not know. */
    fun forgetTokens() = tokens.clear()

    override suspend fun getChangesToken(request: ChangesTokenRequest): String {
        val type = request.recordTypes.single()
        checkAllowed(type)
        return token(type, log.size)
    }

    override suspend fun getChanges(changesToken: String): ChangesResponse {
        val token = requireNotNull(tokens[changesToken]) { "Unknown changes token" }
        checkAllowed(token.type)
        changeReads += token.type
        if (token.expired) return ChangesResponse(emptyList(), changesToken, false, true)
        val pending = log.withIndex().filter { it.index >= token.position && it.value.first == token.type }
        val page = pending.take(changesPageSize)
        val hasMore = pending.size > page.size
        return ChangesResponse(page.map { it.value.second }, token(token.type, if (hasMore) page.last().index + 1 else log.size), hasMore, false)
    }

    override suspend fun <T : Record> readRecords(request: ReadRecordsRequest<T>): ReadRecordsResponse<T> {
        checkAllowed(request.recordType)
        reads += request
        val (from, to) = request.timeRangeFilter.bounds()
        val origins = request.dataOriginFilter.map { it.packageName }
        val matching = stored[request.recordType].orEmpty().values
            .filter { origins.isEmpty() || it.metadata.dataOrigin.packageName in origins }
            .filter { it.span().within(from, to) }
            .sortedBy { it.span().first }
            .let { if (request.ascendingOrder) it else it.reversed() }
        val offset = request.pageToken?.toInt() ?: 0
        val end = offset + request.pageSize
        @Suppress("UNCHECKED_CAST")
        val page = matching.subList(offset, minOf(end, matching.size)) as List<T>
        return ReadRecordsResponse(page, end.takeIf { it < matching.size }?.toString())
    }

    /** Totals of the records overlapping the range, whole. */
    override suspend fun aggregate(request: AggregateRequest): AggregationResult {
        aggregateError?.let { throw it }
        val (from, to) = request.field<TimeRangeFilter>("timeRangeFilter").bounds()
        aggregateRanges += from to to
        return total(request.field("metrics")) { it.span().within(from, to) }
    }

    /** Daily totals, a record counting on the day it started; days without data have no bucket. */
    override suspend fun aggregateGroupByPeriod(request: AggregateGroupByPeriodRequest): List<AggregationResultGroupedByPeriod> {
        check(request.field<Period>("timeRangeSlicer") == Period.ofDays(1))
        val range = request.field<TimeRangeFilter>("timeRangeFilter")
        val start = checkNotNull(range.localStartTime)
        val end = checkNotNull(range.localEndTime)
        dayRanges += start to end
        val metrics = request.field<Set<AggregateMetric<*>>>("metrics")
        return generateSequence(start.toLocalDate()) { it.plusDays(1) }
            .takeWhile { it.atStartOfDay() < end }
            .mapNotNull { day ->
                val result = total(metrics) { it.span().first.atZone(zone).toLocalDate() == day }
                if (result.longValues.isEmpty() && result.doubleValues.isEmpty()) null else AggregationResultGroupedByPeriod(result, day.atStartOfDay(), day.plusDays(1).atStartOfDay())
            }
            .toList()
    }

    /** A metric without records is left out, as Health Connect does. */
    private fun total(metrics: Set<AggregateMetric<*>>, included: (Record) -> Boolean): AggregationResult {
        val longs = mutableMapOf<String, Long>()
        val doubles = mutableMapOf<String, Double>()
        metrics.forEach { metric ->
            val total = TOTALS.getValue(metric.metricKey)
            checkAllowed(total.type)
            val values = stored[total.type].orEmpty().values.filter(included).map(total.value)
            if (values.isEmpty()) return@forEach
            if (total.type == StepsRecord::class) longs[metric.metricKey] = values.sum().toLong() else doubles[metric.metricKey] = values.sum()
        }
        return AggregationResult(longs, doubles, emptySet())
    }

    private fun token(type: KClass<out Record>, position: Int): String = "token-${tokenCount++}".also { tokens[it] = Token(type, position) }

    private fun checkAllowed(type: KClass<out Record>) {
        if (type in denied) throw SecurityException("No permission for ${type.simpleName}")
    }

    private fun TimeRangeFilter.bounds(): Pair<Instant, Instant> = Pair(
        startTime ?: localStartTime?.atZone(zone)?.toInstant() ?: Instant.MIN,
        endTime ?: localEndTime?.atZone(zone)?.toInstant() ?: Instant.MAX,
    )

    /** [position] is where in the log the changes of [type] go on from. */
    private class Token(val type: KClass<out Record>, val position: Int, var expired: Boolean = false)
}

/** Metadata with an id and the app that wrote the record, as Health Connect returns it; its constructor is internal. */
fun metadata(id: String, origin: String): Metadata =
    METADATA.newInstance(Metadata.RECORDING_METHOD_UNKNOWN, id, DataOrigin(origin), Instant.EPOCH, null, 0L, null)

private val METADATA = Metadata::class.java.getDeclaredConstructor(
    Int::class.javaPrimitiveType,
    String::class.java,
    DataOrigin::class.java,
    Instant::class.java,
    String::class.java,
    Long::class.javaPrimitiveType,
    Device::class.java,
).apply { isAccessible = true }

private fun unsupported(): HealthConnectClient = Proxy.newProxyInstance(
    HealthConnectClient::class.java.classLoader,
    arrayOf(HealthConnectClient::class.java),
) { _, method, _ -> throw UnsupportedOperationException("${method.name} is not faked") } as HealthConnectClient

/** The requests keep these internal. */
@Suppress("UNCHECKED_CAST")
private fun <T> Any.field(name: String): T = javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T

private class Total(val type: KClass<out Record>, val value: (Record) -> Double)

private val TOTALS = mapOf(
    StepsRecord.COUNT_TOTAL.metricKey to Total(StepsRecord::class) { (it as StepsRecord).count.toDouble() },
    DistanceRecord.DISTANCE_TOTAL.metricKey to Total(DistanceRecord::class) { (it as DistanceRecord).distance.inMeters },
    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL.metricKey to Total(ActiveCaloriesBurnedRecord::class) {
        (it as ActiveCaloriesBurnedRecord).energy.inKilocalories
    },
    TotalCaloriesBurnedRecord.ENERGY_TOTAL.metricKey to Total(TotalCaloriesBurnedRecord::class) { (it as TotalCaloriesBurnedRecord).energy.inKilocalories },
)

private fun Record.span(): Pair<Instant, Instant> = when (this) {
    is ExerciseSessionRecord -> startTime to endTime
    is HeartRateRecord -> startTime to endTime
    is ActiveCaloriesBurnedRecord -> startTime to endTime
    is TotalCaloriesBurnedRecord -> startTime to endTime
    is StepsRecord -> startTime to endTime
    is DistanceRecord -> startTime to endTime
    is SleepSessionRecord -> startTime to endTime
    is WeightRecord -> time to time
    is HeightRecord -> time to time
    is BodyFatRecord -> time to time
    is LeanBodyMassRecord -> time to time
    is BoneMassRecord -> time to time
    is BodyWaterMassRecord -> time to time
    is RestingHeartRateRecord -> time to time
    is HeartRateVariabilityRmssdRecord -> time to time
    else -> error("No times for ${this::class.simpleName}")
}

/** Instantaneous records in [from, to), interval records overlapping it. */
private fun Pair<Instant, Instant>.within(from: Instant, to: Instant): Boolean =
    if (first == second) first >= from && first < to else first < to && second > from
