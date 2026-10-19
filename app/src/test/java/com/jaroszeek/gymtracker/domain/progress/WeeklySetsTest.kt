package com.jaroszeek.gymtracker.domain.progress

import com.jaroszeek.gymtracker.domain.exercise.BodyPart
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklySetsTest {

    private val warsaw = ZoneId.of("Europe/Warsaw")

    @Test
    fun weeksStartOnMonday() {
        val monday = LocalDate.of(2026, 10, 19)
        assertEquals(Week(monday), Week.of(monday))
        assertEquals(Week(monday), Week.of(LocalDate.of(2026, 10, 25)))
        assertEquals(Week(LocalDate.of(2026, 10, 26)), Week.of(LocalDate.of(2026, 10, 26)))
        assertEquals(Week(LocalDate.of(2026, 10, 12)), Week(monday).previous())
    }

    @Test
    fun weekBoundsFollowTheZoneAcrossTheClockChange() {
        // Summer time ends on 2026-10-25, so this week is an hour longer
        val week = Week(LocalDate.of(2026, 10, 19))
        assertEquals(1_792_360_800_000L, week.startMillis(warsaw))
        assertEquals(7 * 24 + 1, (week.endMillis(warsaw) - week.startMillis(warsaw)) / 3_600_000L)
        assertEquals(week.next().startMillis(warsaw), week.endMillis(warsaw))
    }

    @Test
    fun aSetCountsOncePerBodyPart() {
        val sets = listOf(
            setOf(Muscle.Lats, Muscle.MiddleBack),
            setOf(Muscle.Chest),
            setOf(Muscle.Chest, Muscle.Shoulders, Muscle.Triceps),
            setOf(Muscle.Quadriceps, Muscle.Glutes),
            emptySet(),
        )
        val week = weekSets(sets)
        // Each set once in the total, though the press and the squat count for more than one body part
        assertEquals(4, week.total)
        val counts = week.perPart
        assertEquals(1, counts[BodyPart.Back])
        assertEquals(2, counts[BodyPart.Chest])
        assertEquals(1, counts[BodyPart.Shoulders])
        assertEquals(1, counts[BodyPart.Arms])
        assertEquals(1, counts[BodyPart.Legs])
        assertEquals(1, counts[BodyPart.Glutes])
        assertEquals(0, counts[BodyPart.Abs])
        assertEquals(BodyPart.entries.toSet(), counts.keys)
    }
}
