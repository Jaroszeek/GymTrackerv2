package com.jaroszeek.gymtracker.domain.progress

import com.jaroszeek.gymtracker.domain.exercise.BodyPart
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A Monday-to-Sunday week, identified by its Monday. */
data class Week(val monday: LocalDate) {
    fun startMillis(zone: ZoneId): Long = monday.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endMillis(zone: ZoneId): Long = monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun previous() = Week(monday.minusWeeks(1))

    fun next() = Week(monday.plusWeeks(1))

    companion object {
        fun of(date: LocalDate) = Week(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
}

/** Working sets of a week: [total] counts each set once, [perPart] once for every body part it works. */
data class WeekSets(val total: Int = 0, val perPart: Map<BodyPart, Int> = emptyMap())

/**
 * Working sets per body part (A5.7). Each element is one non-warm-up set, given as the primary muscles
 * of its exercise; a set counts once for every body part those muscles belong to, so a row (lats and
 * middle back) is one set for the back. Secondary muscles do not count.
 */
fun weekSets(sets: List<Set<Muscle>>) = WeekSets(
    total = sets.count { it.isNotEmpty() },
    perPart = BodyPart.entries.associateWith { part -> sets.count { muscles -> muscles.any { it in part.muscles } } },
)
