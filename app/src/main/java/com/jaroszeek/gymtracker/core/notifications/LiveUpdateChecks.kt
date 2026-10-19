package com.jaroszeek.gymtracker.core.notifications

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import com.jaroszeek.gymtracker.domain.workout.WorkoutVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Everything that decides whether the workout shows as a Live Update (Now Bar) and runs reliably in the background. */
data class LiveUpdateStatus(
    /** E.g. "16 (36.1)". */
    val android: String,
    val device: String,
    val samsung: Boolean,
    val notificationsEnabled: Boolean,
    /** Importance of the workout channel; null until the first workout creates it. */
    val channelImportance: Int?,
    /** Null below Android 16, which has no promotion. */
    val canPostPromoted: Boolean?,
    /** Of the workout notification built now; ignores the user's Live Update setting. */
    val promotable: Boolean?,
    /** Of the posted workout notification; null when none is posted. */
    val promoted: Boolean?,
    val exactAlarms: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val backgroundRestricted: Boolean,
)

enum class SystemSettings { Notifications, LiveUpdates, Battery, ExactAlarms, Developer }

@Singleton
class LiveUpdateChecks @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: ActiveWorkoutManager,
    private val notifications: WorkoutNotifications,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /** Reads every state and logs it, so `adb logcat -s LiveUpdate` shows the same as the screen. */
    fun status(): LiveUpdateStatus {
        val promotion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
        val power = context.getSystemService(PowerManager::class.java)
        val status = LiveUpdateStatus(
            android = "${Build.VERSION.RELEASE} (${if (promotion) sdkFull() else Build.VERSION.SDK_INT.toString()})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            samsung = Build.MANUFACTURER.equals(SAMSUNG, ignoreCase = true),
            notificationsEnabled = notificationManager.areNotificationsEnabled(),
            channelImportance = notificationManager.getNotificationChannel(WorkoutNotifications.CHANNEL_ID)?.importance,
            canPostPromoted = if (promotion) notificationManager.canPostPromotedNotifications() else null,
            promotable = if (promotion) notifications.build(manager.workout.value ?: SAMPLE, emptyMap()).hasPromotableCharacteristics() else null,
            promoted = if (promotion) posted()?.let { it.flags and Notification.FLAG_PROMOTED_ONGOING != 0 } else null,
            exactAlarms = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            ignoringBatteryOptimizations = power.isIgnoringBatteryOptimizations(context.packageName),
            backgroundRestricted = context.getSystemService(ActivityManager::class.java).isBackgroundRestricted,
        )
        Log.i(TAG, status.toString())
        return status
    }

    private fun posted(): Notification? =
        notificationManager.activeNotifications.firstOrNull { it.id == WorkoutNotifications.NOTIFICATION_ID }?.notification

    private fun sdkFull(): String = Build.VERSION.SDK_INT_FULL.let { "${it / SDK_FULL_FACTOR}.${it % SDK_FULL_FACTOR}" }

    private companion object {
        const val TAG = "LiveUpdate"
        const val SAMSUNG = "samsung"

        // SDK_INT_FULL = major * 100 000 + minor
        const val SDK_FULL_FACTOR = 100_000

        /** Stands in for the workout when none runs, so the notification can still be checked. */
        val SAMPLE = ActiveWorkout(null, "Push", 0, 0).addExercise(listOf(WorkoutVariant(exerciseId = 0)))
    }
}

/** Opens the system screen; a build without it (API 36.0 has no promotion screen) gets the app's notification settings. */
fun Context.openSettings(screen: SystemSettings) {
    val app = "package:$packageName".toUri()
    val intent = when (screen) {
        SystemSettings.Notifications -> notificationSettings()
        SystemSettings.LiveUpdates -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            notificationSettings()
        }
        SystemSettings.Battery -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
        SystemSettings.ExactAlarms -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, app)
        SystemSettings.Developer -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
    }
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("LiveUpdate", "No settings screen for ${intent.action}", e)
        startActivity(notificationSettings())
    }
}

private fun Context.notificationSettings() = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
