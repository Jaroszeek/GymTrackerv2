package com.jaroszeek.gymtracker.data.health

import android.util.Log
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.jaroszeek.gymtracker.core.database.WorkoutEntity
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.di.ApplicationScope
import com.jaroszeek.gymtracker.core.health.HealthConnect
import com.jaroszeek.gymtracker.core.health.HealthFeature
import com.jaroszeek.gymtracker.data.workout.WorkoutRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Optional copy of finished workouts in Health Connect as strength sessions (A6), off by default. A
 * workout linked to a watch session gets no copy, so the session is not there twice; deleting or
 * linking the workout removes the copy. The copy is keyed by the workout id, so an edit replaces it.
 */
@Singleton
class HealthWriter @Inject constructor(
    private val health: HealthConnect,
    private val workouts: WorkoutRepository,
    private val preferences: AppPreferences,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Called once by the application; from then on every change of a workout is written. */
    fun start() {
        scope.launch { workouts.changed.collect { write(it) } }
    }

    private suspend fun write(id: Long) {
        val client = health.client() ?: return
        try {
            if (!health.granted().containsAll(HealthFeature.WriteWorkouts.permissions)) return
            val workout = workouts.forHealth(id)
            val clientId = "$CLIENT_ID_PREFIX$id"
            if (workout != null && workout.hcSessionId == null && workout.endedAt > workout.startedAt && preferences.healthWriteWorkouts.first()) {
                client.insertRecords(listOf(workout.toRecord(clientId)))
            } else {
                // Deleting a copy that was never written does nothing
                client.deleteRecords(ExerciseSessionRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(clientId))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ponytail: not retried, the next change of the workout writes it again; a retry queue if writes fail in practice
            Log.e(TAG, "Writing workout $id to Health Connect failed", e)
        }
    }

    private fun WorkoutEntity.toRecord(clientId: String): ExerciseSessionRecord {
        val start = Instant.ofEpochMilli(startedAt)
        val end = Instant.ofEpochMilli(endedAt)
        val rules = ZoneId.systemDefault().rules
        return ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = rules.getOffset(start),
            endTime = end,
            endZoneOffset = rules.getOffset(end),
            // A newer version replaces the copy with the same client id
            metadata = Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE), clientId, clock.millis()),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = name,
        )
    }

    private companion object {
        const val TAG = "HealthWriter"
        const val CLIENT_ID_PREFIX = "workout-"
    }
}
