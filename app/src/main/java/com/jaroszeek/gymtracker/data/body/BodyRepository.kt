package com.jaroszeek.gymtracker.data.body

import com.jaroszeek.gymtracker.core.database.BodyMeasurementEntity
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.database.HealthDatabase
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.domain.body.BodyMeasurement
import com.jaroszeek.gymtracker.domain.body.BodyMetric
import com.jaroszeek.gymtracker.domain.body.withoutDuplicates
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Body values of the profile (A5.8): the ones entered here live in the app database (and its backup),
 * the ones from Health Connect in the health database, which disconnecting clears.
 */
@Singleton
class BodyRepository @Inject constructor(
    database: GtDatabase,
    health: HealthDatabase,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao = database.bodyDao()
    private val healthDao = health.healthDao()

    /** Both sources, oldest first, without duplicates. */
    fun observeMeasurements(): Flow<List<BodyMeasurement>> =
        combine(dao.observeAll(), healthDao.observeMeasurements(BodyMetric.entries.map { it.key }, from = 0)) { manual, imported ->
            val entered = manual.mapNotNull { row -> BodyMetric.fromKey(row.type)?.let { BodyMeasurement(it, row.time, row.value, manualId = row.id) } }
            // Values that cannot be entered here stay out: a height of 0 m from another app gives an infinite BMI
            val synced = imported.mapNotNull { row ->
                BodyMetric.fromKey(row.type)?.takeIf { row.value in it.range }?.let { BodyMeasurement(it, row.time, row.value, origin = row.origin) }
            }
            (entered + synced).withoutDuplicates()
        }.flowOn(io)

    /** Saved as measured now; false, and nothing saved, when [value] is outside the metric's range. */
    suspend fun add(metric: BodyMetric, value: Double): Boolean {
        if (value !in metric.range) return false
        withContext(io) { dao.insert(BodyMeasurementEntity(type = metric.key, time = clock.millis(), value = value)) }
        return true
    }

    /** Only values entered here can be deleted; the others are changed in their app. */
    suspend fun delete(manualId: Long) = withContext(io) { dao.delete(manualId) }
}
