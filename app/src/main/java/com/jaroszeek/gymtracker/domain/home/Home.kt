package com.jaroszeek.gymtracker.domain.home

import com.jaroszeek.gymtracker.domain.health.HealthSession
import com.jaroszeek.gymtracker.domain.health.notLinkedTo
import com.jaroszeek.gymtracker.domain.progress.Week
import com.jaroszeek.gymtracker.domain.workout.WorkoutListItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Widgets of Start (A5.1) in their default order; [key] is stored. */
enum class HomeWidget(val key: String) {
    Hero("hero"),
    LastWorkout("last_workout"),
    Readiness("readiness"),
    Records("records"),
    Calendar("calendar"),
    Steps("steps"),
    Body("body"),
}

/** Order of the widgets on Start and the hidden ones; [order] has every widget once. */
data class HomeLayout(val order: List<HomeWidget> = HomeWidget.entries, val hidden: Set<HomeWidget> = emptySet()) {
    val visible: List<HomeWidget> get() = order.filter { it !in hidden }

    fun move(from: Int, to: Int): HomeLayout = copy(order = order.toMutableList().apply { add(to, removeAt(from)) })

    fun toggle(widget: HomeWidget): HomeLayout = copy(hidden = if (widget in hidden) hidden - widget else hidden + widget)

    /** "hero,-steps,…": a hidden widget starts with a minus. */
    fun encode(): String = order.joinToString(",") { if (it in hidden) "-${it.key}" else it.key }

    companion object {
        /** Unknown keys (from a newer version) are dropped; widgets missing from [value] (added later) come last, shown. */
        fun decode(value: String?): HomeLayout {
            if (value.isNullOrBlank()) return HomeLayout()
            val stored = value.split(",").mapNotNull { token ->
                val key = token.removePrefix("-")
                HomeWidget.entries.firstOrNull { it.key == key }?.let { it to token.startsWith("-") }
            }.distinctBy { it.first }
            val order = stored.map { it.first }
            return HomeLayout(order + HomeWidget.entries.filter { it !in order }, stored.filter { it.second }.mapTo(HashSet()) { it.first })
        }
    }
}

/**
 * Weeks in a row with at least one workout (A5.1). This week counts once it has one; until then the
 * streak runs to last week, so a new week does not break it.
 */
fun weeklyStreak(workoutDays: Collection<LocalDate>, today: LocalDate): Int {
    val weeks = workoutDays.mapTo(HashSet()) { Week.of(it) }
    var week = Week.of(today).takeIf { it in weeks } ?: Week.of(today).previous()
    var streak = 0
    while (week in weeks) {
        streak++
        week = week.previous()
    }
    return streak
}

/**
 * Days of the calendar with a session imported from Health Connect, e.g. a run recorded by a watch, and no
 * workout here: a day with both shows the workout. A linked session is its workout and adds no day.
 */
fun sessionDays(workouts: List<WorkoutListItem>, sessions: List<HealthSession>, zone: ZoneId): Set<LocalDate> {
    fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    return sessions.notLinkedTo(workouts).mapTo(HashSet()) { day(it.startTime) } - workouts.mapTo(HashSet()) { day(it.startedAt) }
}

/** Totals of a week's workouts; volume and sets leave warm-ups out, as everywhere. */
data class WeekStats(val workouts: Int = 0, val durationMillis: Long = 0, val volumeKg: Double = 0.0, val sets: Int = 0)

fun weekStats(workouts: List<WorkoutListItem>, week: Week, zone: ZoneId): WeekStats {
    val range = week.startMillis(zone) until week.endMillis(zone)
    val summaries = workouts.filter { it.startedAt in range }.map { it.summary }
    return WeekStats(summaries.size, summaries.sumOf { it.durationMillis }, summaries.sumOf { it.volumeKg }, summaries.sumOf { it.sets })
}

/**
 * The routine to start next: the one after the last workout's routine in the order of [routines]
 * (the first again after the last one), e.g. Pull after Push. [history] holds the routine of each
 * workout, newest first; deleted routines are skipped. The first routine when none was done yet.
 */
fun proposedRoutine(routines: List<Long>, history: List<Long?>): Long? {
    if (routines.isEmpty()) return null
    val last = history.firstOrNull { it in routines } ?: return routines.first()
    return routines[(routines.indexOf(last) + 1) % routines.size]
}
