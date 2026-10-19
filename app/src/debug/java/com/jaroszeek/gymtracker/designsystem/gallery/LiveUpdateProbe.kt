package com.jaroszeek.gymtracker.designsystem.gallery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.jaroszeek.gymtracker.R

/** What the probe found; logged under [TAG] too, so `adb logcat -s LiveUpdateProbe` shows it. */
data class ProbeState(
    val notificationsEnabled: Boolean,
    val canPostPromoted: Boolean?,
    val hasPromotableCharacteristics: Boolean?,
    val promoted: Boolean?,
    val ignoringBatteryOptimizations: Boolean,
)

/**
 * Debug-only Live Update probe (D-014): posts a ProgressStyle notification that requests
 * promotion and reports every state that decides whether One UI shows it in the Now Bar.
 */
object LiveUpdateProbe {
    private const val TAG = "LiveUpdateProbe"
    private const val CHANNEL_ID = "live_update_probe"
    private const val NOTIFICATION_ID = 1001
    private const val TOTAL_SETS = 8
    private const val DONE_SETS = 3

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

    fun state(context: Context): ProbeState {
        val manager = context.getSystemService(NotificationManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val state = if (supported) {
            ProbeState(
                notificationsEnabled = manager.areNotificationsEnabled(),
                canPostPromoted = manager.canPostPromotedNotifications(),
                hasPromotableCharacteristics = build(context).hasPromotableCharacteristics(),
                promoted = manager.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID }
                    ?.let { it.notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0 },
                ignoringBatteryOptimizations = power.isIgnoringBatteryOptimizations(context.packageName),
            )
        } else {
            ProbeState(manager.areNotificationsEnabled(), null, null, null, power.isIgnoringBatteryOptimizations(context.packageName))
        }
        Log.i(TAG, state.toString())
        return state
    }

    fun post(context: Context) {
        if (!supported) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.probe_channel), NotificationManager.IMPORTANCE_LOW),
        )
        try {
            manager.notify(NOTIFICATION_ID, build(context))
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted", e)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun promotedSettingsIntent(context: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun notificationSettingsIntent(context: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun batterySettingsIntent(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData("package:${context.packageName}".toUri())

    // NotificationCompat: setRequestPromotedOngoing exists only from API 36.1; the compat builder sets
    // the "android.requestPromotedOngoing" extra on any API level
    private fun build(context: Context): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_fitness_center)
        .setContentTitle(context.getString(R.string.probe_notification_title))
        .setContentText(context.getString(R.string.probe_notification_text))
        .setOngoing(true)
        .setRequestPromotedOngoing(true)
        .setShortCriticalText(context.getString(R.string.probe_short_text, DONE_SETS, TOTAL_SETS))
        .setUsesChronometer(true)
        .setWhen(System.currentTimeMillis())
        .setStyle(
            NotificationCompat.ProgressStyle()
                .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(TOTAL_SETS)))
                .setProgress(DONE_SETS),
        )
        .build()
}
