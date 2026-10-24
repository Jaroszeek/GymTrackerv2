package com.jaroszeek.gymtracker.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

    @Test
    fun kilogramsStayAsTheyAre() {
        assertEquals(62.5, WeightUnit.Kg.fromKg(62.5), 0.0)
        assertEquals(62.5, WeightUnit.Kg.toKg(62.5), 0.0)
        assertEquals(2.5, WeightUnit.Kg.stepKg, 0.0)
    }

    @Test
    fun poundsConvertBothWays() {
        assertEquals(220.462, WeightUnit.Lb.fromKg(100.0), 0.001)
        assertEquals(100.0, WeightUnit.Lb.toKg(WeightUnit.Lb.fromKg(100.0)), 1e-9)
        assertEquals(2.26796, WeightUnit.Lb.stepKg, 1e-5)
    }

    @Test
    fun anUnknownStoredUnitIsKilograms() {
        assertEquals(WeightUnit.Lb, WeightUnit.fromKey("lb"))
        assertEquals(WeightUnit.Kg, WeightUnit.fromKey(null))
        assertEquals(WeightUnit.Kg, WeightUnit.fromKey("stone"))
    }

    @Test
    fun restStaysWithinItsRange() {
        assertEquals(RestDefaults.MIN_SECONDS, RestDefaults.coerce(0))
        assertEquals(120, RestDefaults.coerce(120))
        assertEquals(RestDefaults.MAX_SECONDS, RestDefaults.coerce(3_600))
    }
}
