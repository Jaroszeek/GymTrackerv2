package com.jaroszeek.gymtracker.core.notifications

import android.app.Notification
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import com.jaroszeek.gymtracker.domain.workout.WorkoutVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The workout notification meets the Live Update requirements and shows what A7 asks for. */
@RunWith(AndroidJUnit4::class)
class WorkoutNotificationsTest {

    private val notifications = WorkoutNotifications(InstrumentationRegistry.getInstrumentation().targetContext)
    private val workout = ActiveWorkout(1, "Push", startedAt = 1_000, updatedAt = 1_000)
        .addExercise(listOf(WorkoutVariant(10, 100, RoutineTargets(3, 8, 12, 60.0, restSeconds = 120))))
        .addExercise(listOf(WorkoutVariant(20, 200, RoutineTargets(2, 10, 10))))
    private val names = mapOf(10L to "Bench Press", 20L to "Dips")

    private val Notification.text get() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()
    private val Notification.shortText get() = extras.getCharSequence(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT)?.toString()

    private fun Notification.assertPromotable() {
        assertTrue(flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Push", extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(NotificationCompat.isRequestPromotedOngoing(this))
        // 36.0 has the method but promotes nothing (D-022); promotion starts with 36.1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
            assertTrue(hasPromotableCharacteristics())
        }
    }

    @Test
    fun setInProgressShowsTheProgressAndCompletesTheSet() {
        val notification = notifications.build(workout, names)
        notification.assertPromotable()
        assertEquals("Bench Press • seria 1", notification.text)
        // No short text: the status bar chip shows the session clock
        assertNull(notification.shortText)
        assertEquals(1_000L, notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(listOf("Ukończ serię"), notification.actions.map { it.title.toString() })
    }

    @Test
    fun restCountsDownInTheChipAndCanBeSkipped() {
        val bench = workout.exercises[0]
        val resting = workout.complete(bench.id, bench.sets[0].id, now = 10_000, defaultRestSeconds = 90)
        val notification = notifications.build(resting, names)
        notification.assertPromotable()
        assertEquals("Przerwa • następne: Bench Press • seria 2", notification.text)
        // The chip counts the rest down
        assertNull(notification.shortText)
        assertEquals(130_000L, notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(listOf("Pomiń przerwę"), notification.actions.map { it.title.toString() })
    }

    @Test
    fun emptyWorkoutIsStillPromotable() {
        val notification = notifications.build(ActiveWorkout(null, "Push", 0, 0), emptyMap())
        notification.assertPromotable()
        assertEquals("Dodaj pierwsze ćwiczenie, żeby zacząć.", notification.text)
        assertNull(notification.shortText)
        assertEquals(0, notification.actions?.size ?: 0)
    }
}
