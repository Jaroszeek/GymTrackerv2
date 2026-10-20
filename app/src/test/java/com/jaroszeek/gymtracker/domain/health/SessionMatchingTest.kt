package com.jaroszeek.gymtracker.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionMatchingTest {

    /** A session from minute [start] to minute [end]. */
    private fun session(id: String, start: Int, end: Int, origin: String = WATCH, type: Int = RUN) =
        HealthSession(id, origin, type, null, start * MINUTE, end * MINUTE)

    private fun sleep(id: String, origin: String, start: Int, end: Int) = SleepSession(id, origin, start * MINUTE, end * MINUTE, end - start)

    /** The workout of these tests: minute 60 to 120. */
    private fun matchFor(vararg sessions: HealthSession) = bestMatch(60 * MINUTE, 120 * MINUTE, sessions.toList())

    @Test
    fun overlapOfTwoSpans() {
        assertEquals(0L, overlapMillis(0, 10, 20, 30))
        assertEquals(0L, overlapMillis(20, 30, 0, 10))
        // Touching
        assertEquals(0L, overlapMillis(0, 10, 10, 20))
        assertEquals(5L, overlapMillis(0, 10, 5, 20))
        // Nested, either way round
        assertEquals(30L, overlapMillis(0, 100, 20, 50))
        assertEquals(30L, overlapMillis(20, 50, 0, 100))
    }

    @Test
    fun theBestMatchOverlapsMost() {
        // 40 of 70 min against 55 of 60
        val earlier = session("earlier", 30, 100)
        val later = session("later", 65, 125)
        assertEquals(later, matchFor(earlier, later))
        assertEquals(earlier, matchFor(earlier))
    }

    @Test
    fun theOverlapMustCoverHalfOfTheLongerOfTheTwo() {
        // A 10-min walk inside the workout, a 3-hour hike around it
        assertNull(matchFor(session("walk", 80, 90), session("hike", 30, 210)))
        // Exactly half matches, a minute less does not
        val half = session("half", 90, 150)
        assertEquals(half, matchFor(half))
        assertNull(matchFor(session("less", 91, 151)))
    }

    @Test
    fun withoutAnOverlapThereIsNoMatch() {
        assertNull(matchFor())
        assertNull(matchFor(session("before", 0, 60), session("after", 120, 180)))
    }

    @Test
    fun linkCandidatesStartWithinTwelveHoursNearestFirst() {
        val start = 24 * 60 * MINUTE
        fun startingAt(id: String, time: Long) = HealthSession(id, WATCH, RUN, null, time, time + 30 * MINUTE)
        val sessions = listOf(
            startingAt("twelve hours after", start + MATCH_WINDOW_MILLIS),
            startingAt("three hours before", start - 3 * 60 * MINUTE),
            startingAt("too early", start - MATCH_WINDOW_MILLIS - 1),
            startingAt("an hour after", start + 60 * MINUTE),
            startingAt("at the start", start),
            startingAt("too late", start + MATCH_WINDOW_MILLIS + 1),
        )
        assertEquals(
            listOf("at the start", "an hour after", "three hours before", "twelve hours after"),
            linkCandidates(start, sessions).map { it.id },
        )
    }

    @Test
    fun theCopyFromAnotherAppIsDroppedAndTheLongestStays() {
        val watch = session("watch", 0, 60, WATCH)
        val phone = session("phone", 10, 50, PHONE)
        assertEquals(listOf(watch), listOf(phone, watch).withoutDuplicates())
    }

    @Test
    fun aCopyOverlapsAtLeastHalfOfTheShorterSession() {
        // 40-min sessions overlapping the watch's by 20 and by 19 min
        val watch = session("watch", 0, 60, WATCH)
        assertEquals(listOf(watch), listOf(watch, session("half", 40, 80, PHONE)).withoutDuplicates())
        val less = session("less", 41, 81, PHONE)
        assertEquals(listOf(watch, less), listOf(watch, less).withoutDuplicates())
    }

    @Test
    fun overlapsFromTheSameAppOrOfAnotherTypeStay() {
        val run = session("run", 0, 60, WATCH)
        val intervals = session("intervals", 10, 50, WATCH)
        val walk = session("walk", 10, 50, PHONE, type = WALK)
        assertEquals(listOf(run, intervals, walk), listOf(run, intervals, walk).withoutDuplicates())
    }

    @Test
    fun duplicatesAreDroppedInPlaceKeepingTheOrder() {
        val a = session("a", 100, 130, PHONE)
        val b = session("b", 0, 60, WATCH)
        val copyOfB = session("copy of b", 5, 55, PHONE)
        val c = session("c", 200, 290, RING)
        assertEquals(listOf(a, b, c), listOf(a, copyOfB, b, c).withoutDuplicates())
    }

    @Test
    fun sleepFromASecondAppIsDropped() {
        val night = sleep("night", WATCH, 0, 480)
        val copy = sleep("copy", PHONE, 30, 480)
        val sameApp = sleep("same app", WATCH, 60, 300)
        // Overlapping the night by 80 of its 480 min
        val next = sleep("next", PHONE, 400, 880)
        val nap = sleep("nap", PHONE, 900, 960)
        assertEquals(listOf(night, sameApp, next, nap), listOf(night, copy, sameApp, next, nap).withoutDuplicateSleep())
    }

    private companion object {
        const val MINUTE = 60_000L
        const val RUN = 56
        const val WALK = 79
        const val WATCH = "com.example.watch"
        const val PHONE = "com.example.phone"
        const val RING = "com.example.ring"
    }
}
