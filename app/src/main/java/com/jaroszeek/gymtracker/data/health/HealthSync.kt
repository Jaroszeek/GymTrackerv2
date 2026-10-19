package com.jaroszeek.gymtracker.data.health

import android.content.Context
import android.health.connect.HealthConnectException
import android.os.RemoteException
import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.room3.withWriteTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jaroszeek.gymtracker.core.database.HealthDatabase
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.di.ApplicationScope
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.core.health.HealthConnect
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SyncProblem { RateLimited, Failed }

/** [problem] is of the last run, which may have imported part of the data. */
data class SyncStatus(val running: Boolean = false, val problem: SyncProblem? = null)

/**
 * Runs the import (A6): when the app opens (if turned on), on request, and periodically in the
 * background with the permission. One run at a time; it never blocks the UI.
 */
@Singleton
class HealthSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val health: HealthConnect,
    private val database: HealthDatabase,
    private val preferences: AppPreferences,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun syncNow() {
        scope.launch { sync(background = false) }
    }

    /** From the activity's start: syncs unless done in the last few minutes, and keeps the background work in step. */
    fun onAppStart() {
        scope.launch {
            val granted = granted() ?: return@launch
            val background = preferences.healthBackgroundSync.first()
            if (background && HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND !in granted) preferences.setHealthBackgroundSync(false)
            schedule(background && HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted)
            val last = preferences.healthLastSync.first() ?: 0L
            if (preferences.healthSyncOnOpen.first() && clock.millis() - last >= OPEN_SYNC_INTERVAL_MILLIS) sync(background = false)
        }
    }

    fun schedule(enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(BACKGROUND_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Skipped while another run is on; a background run needs the background permission to read anything. */
    suspend fun sync(background: Boolean) {
        if (!mutex.tryLock()) return
        try {
            withContext(io) { run(background) }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun run(background: Boolean) {
        val client = health.client() ?: return
        _status.update { it.copy(running = true) }
        val problem = try {
            val granted = health.granted()
            if (background && HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND !in granted) return
            if (SyncedType.entries.none { it.permission in granted }) return
            resetForHistory(granted)
            HealthImporter(client, database, context.packageName, clock, ZoneId.systemDefault()).run(granted)
            preferences.setHealthLastSync(clock.millis())
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // What was imported stays: tokens move on page by page
            if (e is IllegalStateException && (e.cause as? HealthConnectException)?.errorCode == HealthConnectException.ERROR_RATE_LIMIT_EXCEEDED) {
                Log.w(TAG, "Rate limited; the next run goes on", e)
                SyncProblem.RateLimited
            } else {
                Log.e(TAG, "Sync failed", e)
                SyncProblem.Failed
            }
        } finally {
            _status.update { it.copy(running = false) }
        }
        _status.update { it.copy(problem = problem) }
    }

    /**
     * Revokes every permission and deletes everything imported; workouts and their links to sessions
     * stay, so reconnecting brings the heart rate back. Waits for a running sync.
     */
    suspend fun disconnect() {
        mutex.withLock {
            withContext(io) {
                health.revokeAll()
                val dao = database.healthDao()
                database.withWriteTransaction {
                    dao.deleteAllSessions()
                    dao.deleteAllMeasurements()
                    dao.deleteAllSleep()
                    dao.deleteAllDays()
                    dao.deleteAllDirtyDays()
                    dao.deleteTokens()
                }
                preferences.setHealthLastSync(null)
                preferences.setHealthHistoryImported(false)
                preferences.setHealthBackgroundSync(false)
                preferences.setHealthWriteWorkouts(false)
                schedule(false)
                _status.update { SyncStatus() }
            }
        }
    }

    /** Health Connect allows older data with the history permission: then every type is read again whole. */
    private suspend fun resetForHistory(granted: Set<String>) {
        val history = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
        if (history == preferences.healthHistoryImported.first()) return
        if (history) database.healthDao().deleteTokens()
        preferences.setHealthHistoryImported(history)
    }

    /** Null without Health Connect or when it fails. */
    private suspend fun granted(): Set<String>? {
        if (health.client() == null) return null
        return try {
            health.granted()
        } catch (e: IOException) {
            Log.e(TAG, "Reading permissions failed", e)
            null
        } catch (e: RemoteException) {
            Log.e(TAG, "Reading permissions failed", e)
            null
        }
    }

    private companion object {
        const val TAG = "HealthSync"
        const val WORK_NAME = "health-sync"
        const val OPEN_SYNC_INTERVAL_MILLIS = 5 * 60_000L
        const val BACKGROUND_INTERVAL_HOURS = 1L
    }
}

/** Periodic background sync; Hilt gives the sync through an entry point, so no hilt-work is needed (D-059). */
class HealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, HealthSyncEntryPoint::class.java).healthSync().sync(background = true)
        // A failed run is retried on the next period, not sooner: the limits are per 15 minutes
        return Result.success()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HealthSyncEntryPoint {
    fun healthSync(): HealthSync
}
