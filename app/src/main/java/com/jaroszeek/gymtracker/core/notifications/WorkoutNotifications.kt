package com.jaroszeek.gymtracker.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jaroszeek.gymtracker.MainActivity
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.NotificationRestArgb
import com.jaroszeek.gymtracker.designsystem.theme.NotificationWorkoutArgb
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import com.jaroszeek.gymtracker.domain.workout.NextSet
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/** Everything the workout notification shows; [completeSet] is the (exercise, set) the action ticks off. */
data class WorkoutNotificationContent(
    val title: String,
    val text: String,
    val segments: List<Int>,
    val progress: Int,
    /** Session start, or the end of the rest while [countDown]. */
    val clockBase: Long,
    val countDown: Boolean,
    val completeSet: Pair<Long, Long>?,
)

/**
 * The workout notification (A7), built to qualify as a Live Update (Samsung Now Bar): ongoing, with a
 * title, ProgressStyle, no colorize and no custom views, on a channel above IMPORTANCE_MIN.
 */
@Singleton
class WorkoutNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Default importance without sound, vibration or badge: a low one counts as silent, and Android hides
     * silent notifications from the lock screen by default, where the rest countdown belongs (D-044).
     */
    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_workout), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.notification_channel_workout_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * What the notification shows: during a set, the routine name, "exercise • set", segmented set
     * progress and the session clock; while resting, a distinct rest title ("Rest • <name>"), timer icon,
     * Aqua progress bar draining with the remaining rest time, countdown clock and rest adjustment actions.
     * No short critical text, so the status bar chip ("fasolka") shows the clock too (D-044).
     */
    fun content(workout: ActiveWorkout, names: Map<Long, String>, now: Long = workout.updatedAt): WorkoutNotificationContent {
        val rest = workout.rest
        val next = workout.nextSet()
        val set = next?.let { it.exercise.sets[it.number - 1] }
        val segments: List<Int>
        val progress: Int
        if (rest != null) {
            val totalSec = ((rest.endsAt - rest.startsAt) / MILLIS_PER_SECOND).toInt().coerceAtLeast(1)
            val remainingSec = ceil(rest.remainingMillis(now) / MILLIS_PER_SECOND.toDouble()).toInt().coerceIn(0, totalSec)
            segments = listOf(totalSec)
            progress = remainingSec
        } else {
            segments = workout.exercises.map { it.sets.size }.ifEmpty { listOf(1) }
            progress = workout.doneSets
        }
        return WorkoutNotificationContent(
            title = if (rest != null) context.getString(R.string.notification_rest_title, workout.name) else workout.name,
            text = text(workout, next, names),
            segments = segments,
            progress = progress,
            clockBase = rest?.endsAt ?: workout.startedAt,
            countDown = rest != null,
            completeSet = if (rest == null && next != null && set != null && set.canComplete) next.exercise.id to set.id else null,
        )
    }

    fun build(workout: ActiveWorkout, names: Map<Long, String>, now: Long = workout.updatedAt): Notification =
        build(content(workout, names, now))

    fun build(content: WorkoutNotificationContent): Notification {
        val accentArgb = if (content.countDown) NotificationRestArgb else NotificationWorkoutArgb
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (content.countDown) R.drawable.ic_timer else R.drawable.ic_fitness_center)
            .setColor(accentArgb)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // The compat builder sets the extra on every API level; the platform setter exists only from 36.1 (D-022)
            .setRequestPromotedOngoing(true)
            .setContentIntent(openWorkoutIntent())
            .setDeleteIntent(serviceIntent(WorkoutService.ACTION_DISMISSED))
            .setShowWhen(true)
            .setWhen(content.clockBase)
            .setUsesChronometer(true)
            .setChronometerCountDown(content.countDown)
            .setStyle(
                NotificationCompat.ProgressStyle()
                    .setProgressSegments(content.segments.map { NotificationCompat.ProgressStyle.Segment(it).setColor(accentArgb) })
                    .setProgress(content.progress),
            )
        if (content.countDown) {
            builder.addAction(R.drawable.ic_remove, context.getString(R.string.rest_minus), serviceIntent(WorkoutService.ACTION_REST_MINUS))
            builder.addAction(R.drawable.ic_add, context.getString(R.string.rest_plus), serviceIntent(WorkoutService.ACTION_REST_PLUS))
            builder.addAction(R.drawable.ic_timer, context.getString(R.string.notification_skip_rest), serviceIntent(WorkoutService.ACTION_SKIP_REST))
        }
        content.completeSet?.let { (exerciseId, setId) ->
            val intent = Intent(context, WorkoutService::class.java)
                .setAction(WorkoutService.ACTION_COMPLETE_SET)
                .putExtra(WorkoutService.EXTRA_EXERCISE_ID, exerciseId)
                .putExtra(WorkoutService.EXTRA_SET_ID, setId)
            builder.addAction(R.drawable.ic_check, context.getString(R.string.notification_complete_set), servicePendingIntent(intent))
        }
        return builder.build()
    }

    private fun text(workout: ActiveWorkout, next: NextSet?, names: Map<Long, String>): String {
        val nextLabel = next?.let { set ->
            names[set.exercise.variant.exerciseId]?.let { context.getString(R.string.notification_set, it, set.number) }
                ?: context.getString(R.string.workout_set_number, set.number)
        }
        return when {
            workout.rest != null -> nextLabel?.let { context.getString(R.string.notification_rest_next, it) } ?: context.getString(R.string.notification_rest_last)
            nextLabel != null -> nextLabel
            workout.totalSets > 0 -> context.getString(R.string.notification_all_done)
            else -> context.getString(R.string.workout_empty)
        }
    }

    private fun openWorkoutIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_WORKOUT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceIntent(action: String) = servicePendingIntent(Intent(context, WorkoutService::class.java).setAction(action))

    // One request code per action, so the actions do not overwrite each other's intents
    private fun servicePendingIntent(intent: Intent): PendingIntent =
        PendingIntent.getService(context, intent.action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        const val CHANNEL_ID = "workout"
        const val NOTIFICATION_ID = 1
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
