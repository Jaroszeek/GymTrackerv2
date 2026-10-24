package com.jaroszeek.gymtracker.domain.health

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaloriesTest {

    private val today = LocalDate.of(2026, 10, 20)

    private fun day(back: Long, active: Double?, total: Double?) = DailyActivity(today.minusDays(back), null, active, total)

    @Test
    fun todayIsShownButLeftOutOfTheAverage() {
        val calories = calories(listOf(day(0, 100.0, 700.0), day(1, 500.0, 2_400.0), day(2, 300.0, 2_200.0)), today)
        assertEquals(100.0, calories.activeToday!!, 0.0)
        assertEquals(700.0, calories.totalToday!!, 0.0)
        assertEquals(400.0, calories.activeAverage!!, 1e-9)
        assertEquals(2_300.0, calories.totalAverage!!, 1e-9)
    }

    @Test
    fun daysWithoutAValueDoNotLowerTheAverage() {
        val calories = calories(listOf(day(1, 600.0, null), day(2, null, null), day(3, null, 2_000.0)), today)
        assertNull(calories.activeToday)
        assertEquals(600.0, calories.activeAverage!!, 0.0)
        assertEquals(2_000.0, calories.totalAverage!!, 0.0)
    }

    @Test
    fun withoutValuesItIsEmpty() {
        assertTrue(calories(emptyList(), today).isEmpty)
        assertTrue(calories(listOf(day(0, null, null)), today).isEmpty)
        assertEquals(false, calories(listOf(day(0, 1.0, null)), today).isEmpty)
    }
}
