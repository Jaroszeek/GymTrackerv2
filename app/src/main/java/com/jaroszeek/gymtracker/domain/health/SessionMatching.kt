package com.jaroszeek.gymtracker.domain.health

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

/** Sessions to link by hand: started within [MATCH_WINDOW_MILLIS] of the workout, nearest first. */
fun linkCandidates(start: Long, sessions: List<HealthSession>): List<HealthSession> = sessions
    .filter { abs(it.startTime - start) <= MATCH_WINDOW_MILLIS }
    .sortedBy { abs(it.startTime - start) }

const val MATCH_WINDOW_MILLIS = 12 * 3_600_000L

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
