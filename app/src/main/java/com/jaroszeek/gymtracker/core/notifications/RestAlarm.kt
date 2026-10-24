package com.jaroszeek.gymtracker.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.di.ApplicationScope
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Wakes the device at the end of the rest, also with the screen off (A5.4): an exact alarm when the
 * user allows them, otherwise a partial wake lock held for the rest. At the end it vibrates and plays a
 * sound as set (A5.9) and ends the rest in the manager, whose own timer stops while the device sleeps.
 */
@Singleton
class RestAlarm @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: ActiveWorkoutManager,
    private val preferences: AppPreferences,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private var scheduledAt: Long? = null
    private var timer: Job? = null

    /** Follows the rest of the running workout; [endsAt] is null when there is none. */
    @Synchronized
    fun schedule(endsAt: Long?) {
        if (endsAt == scheduledAt) return
        val now = clock.millis()
        // A rest that ended on its own keeps its wake-up, so its end still vibrates; a skipped or shortened one loses it
        if (scheduledAt?.let { now < it } == true) cancel()
        scheduledAt = endsAt
        if (endsAt == null || endsAt <= now) return
        if (alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, alarmIntent())
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm refused, holding a wake lock instead", e)
            }
        }
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply { acquire(endsAt - now + WAKE_GRACE_MILLIS) }
        timer = scope.launch {
            try {
                delay(endsAt - now)
                ring()
            } finally {
                if (lock.isHeld) lock.release()
            }
        }
    }

    /** The rest is over: vibrate and sound as set (screen off too) and let the workout move on. */
    fun ring() {
        Log.i(TAG, "Rest over")
        manager.endRestIfDue()
        // The receiver's wake lock covers this short read of the settings
        scope.launch {
            val alert = preferences.restAlert.first()
            if (alert.vibration) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(VIBRATION_PATTERN, -1),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
            }
            if (alert.sound) playSound()
        }
    }

    /** The default notification sound; silent and Do Not Disturb modes keep it quiet. */
    private fun playSound() {
        val ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)) ?: run {
            Log.w(TAG, "No notification sound to play")
            return
        }
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone.play()
    }

    private fun cancel() {
        alarms.cancel(alarmIntent())
        timer?.cancel()
        timer = null
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RestAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val TAG = "RestAlarm"
        const val WAKE_LOCK_TAG = "GymTracker:rest"
        const val WAKE_GRACE_MILLIS = 5_000L
        val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 200, 700)
    }
}

/** Delivers the exact rest alarm; works in a fresh process too, if the system killed the app meanwhile. */
class RestAlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun restAlarm(): RestAlarm
    }

    override fun onReceive(context: Context, intent: Intent) {
        // The alarm's own wake lock ends with onReceive; this one lets the notification update land
        context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GymTracker:restEnd")
            .acquire(UPDATE_WAKE_MILLIS)
        EntryPointAccessors.fromApplication(context, Dependencies::class.java).restAlarm().ring()
    }

    private companion object {
        const val UPDATE_WAKE_MILLIS = 3_000L
    }
}
