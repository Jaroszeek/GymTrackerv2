package com.jaroszeek.gymtracker.domain.progress

import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneRepMaxTest {

    @Test
    fun epleyAddsAThirtiethPerRep() {
        assertEquals(116.667, OneRepMax.epley(100.0, 5)!!, 0.001)
        assertEquals(140.0, OneRepMax.epley(100.0, 12)!!, 0.001)
    }

    @Test
    fun brzyckiForComparison() {
        assertEquals(112.5, OneRepMax.brzycki(100.0, 5)!!, 0.001)
        assertEquals(144.0, OneRepMax.brzycki(100.0, 12)!!, 0.001)
    }

    @Test
    fun aSingleIsItsOwnMax() {
        assertEquals(100.0, OneRepMax.epley(100.0, 1)!!, 0.0)
        assertEquals(100.0, OneRepMax.brzycki(100.0, 1)!!, 0.0)
    }

    @Test
    fun noEstimateOverTwelveRepsWithoutRepsOrWithoutWeight() {
        assertNull(OneRepMax.epley(100.0, 13))
        assertNull(OneRepMax.brzycki(100.0, 13))
        assertNull(OneRepMax.epley(100.0, 0))
        assertNull(OneRepMax.epley(0.0, 5))
        assertNull(OneRepMax.epley(-10.0, 5))
    }

    @Test
    fun setsLeaveWarmupsAndBodyweightOut() {
        assertNull(PerformedSet(100.0, 5, SetType.Warmup).e1rmKg)
        assertNull(PerformedSet(null, 5).e1rmKg)
        assertEquals(116.667, PerformedSet(100.0, 5, SetType.DropSet).e1rmKg!!, 0.001)
        assertEquals(116.667, PerformedSet(100.0, 5, SetType.Failure).e1rmKg!!, 0.001)
    }
}
