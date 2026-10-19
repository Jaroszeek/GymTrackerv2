package com.jaroszeek.gymtracker.core.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.core.di.DefaultDispatcher
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service of the running workout (A7): shows the workout notification, keeps the process
 * alive and wakes the device at the end of the rest. Started only from the foreground (see [start]);
 * stops itself, removing the notification, as soon as no workout runs.
 */
@AndroidEntryPoint
class WorkoutService : Service() {

    @Inject lateinit var manager: ActiveWorkoutManager

    @Inject lateinit var exercises: ExerciseRepository

    @Inject lateinit var notifications: WorkoutNotifications

    @Inject lateinit var restAlarm: RestAlarm

    @Inject @DefaultDispatcher lateinit var dispatcher: CoroutineDispatcher

    private lateinit var scope: CoroutineScope
    private var collecting = false

    // Written by the collector on a background thread too
    @Volatile private var foreground = false

    /** The user swiped the notification away: it comes back only when the app is opened again. */
    @Volatile private var dismissed = false

    private val names = MutableStateFlow<Map<Long, String>>(emptyMap())

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fromApp = intent?.action == null
        // startForeground right away (A7). Notification actions skip it once foreground, so a dismissed notification stays away
        if ((fromApp || !foreground) && !enterForeground()) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_COMPLETE_SET -> manager.completeSet(intent.getLongExtra(EXTRA_EXERCISE_ID, -1), intent.getLongExtra(EXTRA_SET_ID, -1))
            ACTION_SKIP_REST -> manager.skipRest()
            ACTION_DISMISSED -> dismissed = true
            else -> dismissed = false
        }
        if (!collecting) collect()
        // Not restarted by the system after process death: that start would come from the background
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        restAlarm.schedule(null)
        super.onDestroy()
    }

    private fun collect() {
        collecting = true
        scope.launch {
            exercises.observeAll()
                .catch { e -> Log.e(TAG, "Loading exercise names failed", e) }
                .collect { all -> names.value = all.associate { it.id to it.name } }
        }
        scope.launch {
            combine(manager.workout, names) { workout, byId -> workout to byId }.collect { (workout, byId) ->
                if (workout == null) stop() else if (!dismissed) post(workout, byId)
            }
        }
        scope.launch {
            manager.workout.map { it?.rest?.endsAt }.distinctUntilChanged().collect { restAlarm.schedule(it) }
        }
    }

    private fun enterForeground(): Boolean {
        notifications.createChannel()
        val workout = manager.workout.value ?: ActiveWorkout(null, getString(R.string.workout_default_name), 0, 0)
        return try {
            ServiceCompat.startForeground(
                this,
                WorkoutNotifications.NOTIFICATION_ID,
                notifications.build(workout, names.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            foreground = true
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: started from the background after all
            Log.e(TAG, "Promoting the workout service to the foreground failed", e)
            stopSelf()
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Promoting the workout service to the foreground failed", e)
            stopSelf()
            false
        }
    }

    private fun post(workout: ActiveWorkout, names: Map<Long, String>) {
        // Without the permission the notification shows only in the task manager; the service still runs
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        getSystemService(NotificationManager::class.java).notify(WorkoutNotifications.NOTIFICATION_ID, notifications.build(workout, names))
    }

    private fun stop() {
        Log.i(TAG, "No workout running, stopping")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    companion object {
        const val ACTION_COMPLETE_SET = "com.jaroszeek.gymtracker.action.COMPLETE_SET"
        const val ACTION_SKIP_REST = "com.jaroszeek.gymtracker.action.SKIP_REST"
        const val ACTION_DISMISSED = "com.jaroszeek.gymtracker.action.NOTIFICATION_DISMISSED"
        const val EXTRA_EXERCISE_ID = "exercise_id"
        const val EXTRA_SET_ID = "set_id"
        private const val TAG = "WorkoutService"

        /** Call only while the app is in the foreground (A7): a start from the background is refused on Android 12+. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Starting the workout service failed", e)
            }
        }
    }
}
