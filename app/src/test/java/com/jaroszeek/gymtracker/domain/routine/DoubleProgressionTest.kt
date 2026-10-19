package com.jaroszeek.gymtracker.domain.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoubleProgressionTest {

    private val targets = RoutineTargets(sets = 3, repsMin = 8, repsMax = 12, weightKg = 60.0)

    private fun sets(vararg reps: Int, weight: Double? = 60.0) = reps.map { PerformedSet(weight, it) }

    @Test
    fun allSetsAtTopOfRangeAddWeight() {
        assertEquals(62.5, DoubleProgression.nextWeight(targets, sets(12, 12, 12))!!, 0.0)
    }

    @Test
    fun oneSetBelowTopKeepsWeight() {
        assertNull(DoubleProgression.nextWeight(targets, sets(12, 12, 11)))
    }

    @Test
    fun fewerSetsThanTargetKeepsWeight() {
        assertNull(DoubleProgression.nextWeight(targets, sets(12, 12)))
    }

    @Test
    fun warmupAndDropSetsAreIgnored() {
        val session = listOf(PerformedSet(40.0, 5, SetType.Warmup)) +
            sets(12, 12, 12) +
            PerformedSet(45.0, 6, SetType.DropSet)
        assertEquals(62.5, DoubleProgression.nextWeight(targets, session)!!, 0.0)
    }

    @Test
    fun setsToFailureCountAsWorking() {
        val session = sets(12, 12) + PerformedSet(60.0, 12, SetType.Failure)
        assertEquals(62.5, DoubleProgression.nextWeight(targets, session)!!, 0.0)
    }

    @Test
    fun fixedRepCountWorks() {
        val fixed = RoutineTargets(sets = 3, repsMin = 5, repsMax = 5, weightKg = 100.0)
        assertEquals(102.5, DoubleProgression.nextWeight(fixed, sets(5, 5, 5, weight = 100.0))!!, 0.0)
    }

    @Test
    fun lighterThanTargetDoesNotCount() {
        assertNull(DoubleProgression.nextWeight(targets, sets(12, 12, 12, weight = 57.5)))
    }

    @Test
    fun heavierThanTargetProgressesFromTheWeightActuallyUsed() {
        val session = listOf(PerformedSet(65.0, 12), PerformedSet(65.0, 12), PerformedSet(62.5, 12))
        assertEquals(65.0, DoubleProgression.nextWeight(targets, session)!!, 0.0)
    }

    @Test
    fun noTargetWeightUsesTheSessionWeight() {
        assertEquals(22.5, DoubleProgression.nextWeight(targets.copy(weightKg = null), sets(12, 12, 12, weight = 20.0))!!, 0.0)
    }

    @Test
    fun bodyweightAndEmptyHistoryGiveNoSuggestion() {
        assertNull(DoubleProgression.nextWeight(targets.copy(weightKg = null), sets(12, 12, 12, weight = null)))
        assertNull(DoubleProgression.nextWeight(targets, emptyList()))
    }

    @Test
    fun customIncrementIsRounded() {
        assertEquals(61.25, DoubleProgression.nextWeight(targets, sets(12, 12, 12), incrementKg = 1.25)!!, 0.0)
    }
}
