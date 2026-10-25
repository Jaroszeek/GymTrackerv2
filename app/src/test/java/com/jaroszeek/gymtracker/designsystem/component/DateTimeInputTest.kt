package com.jaroszeek.gymtracker.designsystem.component

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateTimeInputTest {

    private val warsaw = ZoneId.of("Europe/Warsaw")

    @Test
    fun aDateAndTimeAreReadInTheZone() {
        assertEquals(Instant.parse("2026-10-24T16:30:00Z").toEpochMilli(), DateTimeInput.millis(LocalDate.of(2026, 10, 24), 18, 30, warsaw))
        // Winter time from 25 October: one hour less ahead of UTC
        assertEquals(Instant.parse("2026-10-26T17:30:00Z").toEpochMilli(), DateTimeInput.millis(LocalDate.of(2026, 10, 26), 18, 30, warsaw))
    }

    @Test
    fun aTimeSkippedByTheClockMovesForward() {
        // 29 March 2026 the clock jumps from 2:00 to 3:00
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(), DateTimeInput.millis(LocalDate.of(2026, 3, 29), 2, 30, warsaw))
    }

    @Test
    fun weeksStartOnMondayAndAlwaysSixOfThem() {
        // February 2026 starts on a Sunday
        val weeks = DateTimeInput.weeks(YearMonth.of(2026, 2))
        assertEquals(6, weeks.size)
        assertEquals(List(6) { 7 }, weeks.map { it.size })
        assertEquals(List(6) { null } + LocalDate.of(2026, 2, 1), weeks.first())
        assertEquals(LocalDate.of(2026, 2, 28), weeks[4][5])
        assertNull(weeks[4][6])
        assertEquals(List(7) { null }, weeks.last())
        assertEquals(28, weeks.flatten().count { it != null })
    }
}
