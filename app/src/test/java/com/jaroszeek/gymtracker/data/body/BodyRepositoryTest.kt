package com.jaroszeek.gymtracker.data.body

import com.jaroszeek.gymtracker.core.database.MeasurementEntity
import com.jaroszeek.gymtracker.domain.body.BodyMeasurement
import com.jaroszeek.gymtracker.domain.body.BodyMetric
import com.jaroszeek.gymtracker.domain.body.LeanMass
import com.jaroszeek.gymtracker.domain.health.HealthMetric
import com.jaroszeek.gymtracker.testing.inMemoryDatabase
import com.jaroszeek.gymtracker.testing.inMemoryHealthDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyRepositoryTest {

    private val database = inMemoryDatabase()
    private val health = inMemoryHealthDatabase()
    private val repository = BodyRepository(database, health, Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC), Dispatchers.IO)

    @After
    fun close() {
        database.close()
        health.close()
    }

    @Test
    fun valuesEnteredHereAndFromHealthConnectAreMergedWithoutDuplicates() = runTest {
        assertTrue(repository.add(BodyMetric.Weight, 80.0))
        health.healthDao().upsertMeasurements(
            listOf(
                // The same weighing, synced by the scale's app a minute later
                MeasurementEntity("scale-1", HealthMetric.Weight.key, NOW + 60_000, 80.2, SCALE),
                MeasurementEntity("scale-2", HealthMetric.Weight.key, NOW - DAY, 81.0, SCALE),
                MeasurementEntity("fat", HealthMetric.BodyFat.key, NOW - DAY + 1, 18.0, SCALE),
                // Not a body value of the profile
                MeasurementEntity("hrv", HealthMetric.Hrv.key, NOW, 40.0, SCALE),
            ),
        )

        assertEquals(
            listOf(
                BodyMeasurement(BodyMetric.Weight, NOW - DAY, 81.0, origin = SCALE),
                BodyMeasurement(BodyMetric.BodyFat, NOW - DAY + 1, 18.0, origin = SCALE),
                BodyMeasurement(BodyMetric.Weight, NOW, 80.0, manualId = 1),
            ),
            repository.observeMeasurements().first(),
        )
    }

    @Test
    fun valuesOutsideTheRangeAreNotSaved() = runTest {
        assertFalse(repository.add(BodyMetric.Weight, 19.9))
        // Heights are metres
        assertFalse(repository.add(BodyMetric.Height, 180.0))
        assertTrue(repository.add(BodyMetric.Height, 1.8))

        assertEquals(listOf(BodyMeasurement(BodyMetric.Height, NOW, 1.8, manualId = 1)), repository.observeMeasurements().first())
    }

    @Test
    fun deletingRemovesOnlyThatValue() = runTest {
        repository.add(BodyMetric.Weight, 80.0)
        repository.add(BodyMetric.SkeletalMuscle, 35.0)
        val weight = repository.observeMeasurements().first().first { it.metric == BodyMetric.Weight }

        repository.delete(weight.manualId!!)

        assertEquals(listOf(BodyMetric.SkeletalMuscle), repository.observeMeasurements().first().map { it.metric })
    }

    @Test
    fun leanMassIsTheLatestPlausibleOneFromHealthConnect() = runTest {
        assertNull(repository.observeLeanMass().first())
        health.healthDao().upsertMeasurements(
            listOf(
                MeasurementEntity("lean-1", HealthMetric.LeanMass.key, NOW - DAY, 64.0, SCALE),
                MeasurementEntity("lean-0", HealthMetric.LeanMass.key, NOW - 2 * DAY, 65.0, SCALE),
                // Another app's 0 kg
                MeasurementEntity("lean-2", HealthMetric.LeanMass.key, NOW, 0.0, "com.example"),
            ),
        )

        assertEquals(LeanMass(NOW - DAY, 64.0, SCALE), repository.observeLeanMass().first())
    }

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val DAY = 86_400_000L
        const val SCALE = "com.withings.wiscale2"
    }
}
