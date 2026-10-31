package com.jaroszeek.gymtracker.domain.home

import com.jaroszeek.gymtracker.domain.health.HealthSession
import com.jaroszeek.gymtracker.domain.progress.Week
import com.jaroszeek.gymtracker.domain.workout.WorkoutListItem
import com.jaroszeek.gymtracker.domain.workout.WorkoutSummary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeTest {

    // A Wednesday
    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun streakCountsWeeksInARowEndingThisWeek() {
        val days = listOf(today, today.minusWeeks(1), today.minusWeeks(2).minusDays(2), today.minusWeeks(4))
        assertEquals(3, weeklyStreak(days, today))
    }

    @Test
    fun aWeekWithoutAWorkoutYetDoesNotBreakTheStreak() {
        // Monday of this week, nothing yet; the last two weeks had workouts
        val monday = LocalDate.of(2026, 10, 19)
        assertEquals(2, weeklyStreak(listOf(monday.minusDays(1), monday.minusDays(10)), monday))
    }

    @Test
    fun aMissedWeekEndsTheStreak() {
        assertEquals(0, weeklyStreak(listOf(today.minusWeeks(2)), today))
        assertEquals(0, weeklyStreak(emptyList(), today))
    }

    @Test
    fun severalWorkoutsInAWeekCountOnceAndWeeksStartOnMonday() {
        // Sunday and the next Monday are two weeks
        val sunday = LocalDate.of(2026, 10, 18)
        assertEquals(2, weeklyStreak(listOf(sunday, sunday, sunday.plusDays(1), sunday.plusDays(1)), sunday.plusDays(1)))
    }

    @Test
    fun weekStatsSumTheWorkoutsOfThatWeekOnly() {
        val zone = ZoneOffset.UTC
        val week = Week.of(today)
        fun workout(day: LocalDate, minutes: Long, volume: Double, sets: Int) = WorkoutListItem(
            id = day.toEpochDay(),
            name = "Push",
            startedAt = day.atTime(18, 0).toInstant(zone).toEpochMilli(),
            summary = WorkoutSummary(minutes * 60_000, volume, sets, 5),
        )
        val workouts = listOf(
            workout(today, 60, 5_000.0, 15),
            workout(week.monday, 45, 3_000.0, 12),
            workout(week.monday.minusDays(1), 50, 9_999.0, 20),
            workout(week.monday.plusWeeks(1), 50, 9_999.0, 20),
        )
        assertEquals(WeekStats(2, 105 * 60_000, 8_000.0, 27), weekStats(workouts, week, zone))
        assertEquals(WeekStats(), weekStats(emptyList(), week, zone))
    }

    @Test
    fun weekStatsFollowTheLocalWeekAcrossTheClockChange() {
        // The week of 25 October 2026, whose Sunday has 25 hours in Warsaw
        val zone = ZoneId.of("Europe/Warsaw")
        val week = Week.of(LocalDate.of(2026, 10, 25))
        fun workout(at: LocalDateTime) = WorkoutListItem(
            id = at.toEpochSecond(ZoneOffset.UTC),
            name = "Push",
            startedAt = at.atZone(zone).toInstant().toEpochMilli(),
            summary = WorkoutSummary(60_000, 100.0, 1, 1),
        )
        val workouts = listOf(
            workout(LocalDateTime.of(2026, 10, 19, 0, 0)),
            workout(LocalDateTime.of(2026, 10, 25, 23, 30)),
            workout(LocalDateTime.of(2026, 10, 18, 23, 59)),
            workout(LocalDateTime.of(2026, 10, 26, 0, 0)),
        )
        assertEquals(2, weekStats(workouts, week, zone).workouts)
    }

    @Test
    fun sessionDaysAreTheDaysWithAnUnlinkedSessionAndNoWorkout() {
        val zone = ZoneId.of("Europe/Warsaw")
        fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        fun session(id: String, start: Long) = HealthSession(id, "com.watch", exerciseType = 56, title = null, startTime = start, endTime = start + 3_600_000)
        val workouts = listOf(
            WorkoutListItem(1, "Push", at(today, 18), WorkoutSummary(3_600_000, 5_000.0, 15, 5), healthSessionId = "linked"),
            WorkoutListItem(2, "Pull", at(today.minusDays(2), 18), WorkoutSummary(3_600_000, 5_000.0, 15, 5)),
        )
        val sessions = listOf(
            session("linked", at(today, 18)),
            // A run on a day with a workout: the workout shows
            session("run", at(today.minusDays(2), 7)),
            session("walk", at(today.minusDays(3), 12)),
            // 23:30 UTC is already the next day in Warsaw
            session("late", today.minusDays(6).atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()),
        )
        assertEquals(setOf(today.minusDays(3), today.minusDays(5)), sessionDays(workouts, sessions, zone))
        // A linked session alone is still its workout's
        assertEquals(emptySet<LocalDate>(), sessionDays(workouts.take(1), sessions.take(1), zone))
        assertEquals(emptySet<LocalDate>(), sessionDays(workouts, emptyList(), zone))
        // Hidden types add no day, so the walk is gone
        val walking = 79
        val withWalk = sessions + HealthSession("walk2", "com.phone", walking, null, at(today.minusDays(4), 9), at(today.minusDays(4), 10))
        assertEquals(setOf(today.minusDays(3), today.minusDays(5)), sessionDays(workouts, withWalk, zone, hiddenTypes = setOf(walking)))
    }

    @Test
    fun theProposedRoutineIsTheOneAfterTheLastDone() {
        val push = 1L
        val pull = 2L
        val legs = 3L
        val routines = listOf(push, pull, legs)
        assertEquals(pull, proposedRoutine(routines, listOf(push, legs)))
        // After the last one the first again
        assertEquals(push, proposedRoutine(routines, listOf(legs)))
        // Empty workouts and deleted routines are skipped
        assertEquals(legs, proposedRoutine(routines, listOf(null, 99L, pull)))
    }

    @Test
    fun withoutAWorkoutFromARoutineTheFirstOneIsProposed() {
        assertEquals(5L, proposedRoutine(listOf(5L, 6L), emptyList()))
        assertEquals(5L, proposedRoutine(listOf(5L, 6L), listOf(null)))
        assertEquals(5L, proposedRoutine(listOf(5L), listOf(5L)))
        assertNull(proposedRoutine(emptyList(), listOf(1L)))
    }

    @Test
    fun layoutRoundTripsThroughItsStoredForm() {
        val layout = HomeLayout().move(from = 0, to = 3).toggle(HomeWidget.Steps).toggle(HomeWidget.Readiness)
        assertEquals(layout, HomeLayout.decode(layout.encode()))
        assertEquals(HomeLayout(), HomeLayout.decode(null))
        assertEquals(HomeLayout(), HomeLayout.decode(""))
    }

    @Test
    fun movingAndHidingChangeTheVisibleWidgets() {
        val layout = HomeLayout().move(from = HomeWidget.entries.indexOf(HomeWidget.Body), to = 0).toggle(HomeWidget.Hero)
        assertEquals(HomeWidget.Body, layout.visible.first())
        assertEquals(HomeWidget.entries.size - 1, layout.visible.size)
        assertEquals(HomeLayout().copy(order = layout.order), layout.toggle(HomeWidget.Hero))
    }

    @Test
    fun quickStartMovesAndHidesLikeOtherWidgets() {
        val layout = HomeLayout().move(from = 0, to = 2).toggle(HomeWidget.QuickStart)
        assertEquals(HomeWidget.QuickStart, layout.order[2])
        assertEquals(setOf(HomeWidget.QuickStart), layout.hidden)
        val decoded = HomeLayout.decode("body,-quick_start")
        assertEquals(HomeWidget.Body, decoded.order[0])
        assertEquals(setOf(HomeWidget.QuickStart), decoded.hidden)
    }

    @Test
    fun decodingDropsUnknownAndRepeatedKeysAndAppendsNewWidgets() {
        // Layouts saved before quick start became a widget keep it on top
        val layout = HomeLayout.decode("steps,-hero,widget_from_v2,steps,-records")
        assertEquals(HomeWidget.QuickStart, layout.order[0])
        assertEquals(HomeWidget.Steps, layout.order[1])
        assertEquals(HomeWidget.Hero, layout.order[2])
        assertEquals(HomeWidget.Records, layout.order[3])
        assertEquals(HomeWidget.LastWorkout, layout.order[4])
        assertEquals(HomeWidget.entries.toSet(), layout.order.toSet())
        assertEquals(HomeWidget.entries.size, layout.order.size)
        assertEquals(setOf(HomeWidget.Hero, HomeWidget.Records), layout.hidden)
    }
}
