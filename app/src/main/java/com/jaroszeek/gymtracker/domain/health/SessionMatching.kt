package com.jaroszeek.gymtracker.domain.health

import com.jaroszeek.gymtracker.domain.workout.WorkoutListItem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** How long [aStart, aEnd) and [bStart, bEnd) overlap, in millis. */
fun overlapMillis(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long = (min(aEnd, bEnd) - max(aStart, bStart)).coerceAtLeast(0)

/**
 * The session recorded alongside a workout from [start] to [end]: the one overlapping most, if the
 * overlap covers at least half of the longer of the two, so a short walk to the gym is no match.
 */
fun bestMatch(start: Long, end: Long, sessions: List<HealthSession>): HealthSession? = sessions
    .map { it to overlapMillis(start, end, it.startTime, it.endTime) }
    .filter { (session, overlap) -> overlap > 0 && overlap * 2 >= max(end - start, session.durationMillis) }
    .maxByOrNull { it.second }
    ?.first

/** A finished workout without a session that was not unlinked by hand. Times are epoch millis UTC. */
data class UnlinkedWorkout(val id: Long, val startedAt: Long, val endedAt: Long)

/**
 * The links made by themselves, workout id to session id: each workout gets its [bestMatch] among the
 * sessions not [linked] yet, and a session goes to one workout only. Oldest workouts go first, so of two
 * competing for a session the earlier gets it and the later its next best match, if there is one.
 */
fun autoLinks(workouts: List<UnlinkedWorkout>, sessions: List<HealthSession>, linked: Set<String>): Map<Long, String> {
    val free = sessions.filterTo(mutableListOf()) { it.id !in linked }
    return buildMap {
        workouts.sortedBy { it.startedAt }.forEach { workout ->
            val session = bestMatch(workout.startedAt, workout.endedAt, free) ?: return@forEach
            free.remove(session)
            put(workout.id, session.id)
        }
    }
}

/** Sessions to link by hand: started within [MATCH_WINDOW_MILLIS] of the workout, nearest first. */
fun linkCandidates(start: Long, sessions: List<HealthSession>): List<HealthSession> = sessions
    .filter { abs(it.startTime - start) <= MATCH_WINDOW_MILLIS }
    .sortedBy { abs(it.startTime - start) }

const val MATCH_WINDOW_MILLIS = 12 * 3_600_000L

/** Sessions no workout is linked to: a linked one is that workout, not an activity of its own (D-062). */
fun List<HealthSession>.notLinkedTo(workouts: List<WorkoutListItem>): List<HealthSession> {
    val linked = workouts.mapNotNullTo(HashSet()) { it.healthSessionId }
    return filter { it.id !in linked }
}

/**
 * The same activity written by two apps (e.g. a watch app and an app it shares with): of sessions of
 * one type from different apps overlapping at least half of the shorter one, the longest stays.
 */
fun List<HealthSession>.withoutDuplicates(): List<HealthSession> =
    keepLongest(this, { it.startTime }, { it.endTime }, { it.origin }) { a, b -> a.exerciseType == b.exerciseType }

/** Sleep written by two apps: of overlapping nights from different apps, the longest stays. */
fun List<SleepSession>.withoutDuplicateSleep(): List<SleepSession> = keepLongest(this, { it.startTime }, { it.endTime }, { it.origin }) { _, _ -> true }

// ponytail: O(n²) over all items, fine for hundreds; sweep by start time if histories reach thousands
private inline fun <T> keepLongest(
    items: List<T>,
    crossinline start: (T) -> Long,
    crossinline end: (T) -> Long,
    crossinline origin: (T) -> String,
    crossinline sameKind: (T, T) -> Boolean,
): List<T> {
    val kept = mutableListOf<T>()
    items.sortedByDescending { end(it) - start(it) }.forEach { item ->
        val duplicate = kept.any { other ->
            origin(other) != origin(item) && sameKind(other, item) &&
                overlapMillis(start(other), end(other), start(item), end(item)) * 2 >= end(item) - start(item)
        }
        if (!duplicate) kept += item
    }
    return items.filter { it in kept }
}
