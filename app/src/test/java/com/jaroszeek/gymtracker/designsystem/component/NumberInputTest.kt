package com.jaroszeek.gymtracker.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberInputTest {

    @Test
    fun acceptsCommaAndDot() {
        assertEquals(82.5, NumberInput.parse("82,5", decimals = 2)!!, 0.0)
        assertEquals(82.5, NumberInput.parse("82.5", decimals = 2)!!, 0.0)
        assertNull(NumberInput.parse("", decimals = 2))
    }

    @Test
    fun validatesWhileTyping() {
        assertTrue(NumberInput.isValidInput("", 2))
        assertTrue(NumberInput.isValidInput("82,", 2))
        assertTrue(NumberInput.isValidInput("82,25", 2))
        assertFalse(NumberInput.isValidInput("82,255", 2))
        assertFalse(NumberInput.isValidInput("8,5", 0))
        assertFalse(NumberInput.isValidInput("-5", 2))
        assertFalse(NumberInput.isValidInput("123456", 0))
    }

    @Test
    fun formatsWithoutTrailingZeros() {
        assertEquals("80", NumberInput.format(80.0, 2, ','))
        assertEquals("82,5", NumberInput.format(82.5, 2, ','))
        assertEquals("82.25", NumberInput.format(82.25, 2, '.'))
        assertEquals("", NumberInput.format(null, 2, ','))
    }

    @Test
    fun padsWholeNumbersToMinDigits() {
        assertEquals("07", NumberInput.format(7.0, 0, ',', minDigits = 2))
        assertEquals("45", NumberInput.format(45.0, 0, ',', minDigits = 2))
        assertEquals(7.0, NumberInput.parse("07", 0)!!, 0.0)
    }

    @Test
    fun stepsWithoutFloatingPointNoise() {
        var value: Double? = 0.0
        repeat(3) { value = NumberInput.step(value, 0.1, 2, 0.0..100.0) }
        assertEquals(0.3, value!!, 0.0)
        assertEquals(2.5, NumberInput.step(null, 2.5, 2, 0.0..100.0), 0.0)
    }

    @Test
    fun stepsStayInRange() {
        assertEquals(0.0, NumberInput.step(1.0, -2.5, 2, 0.0..100.0), 0.0)
        assertEquals(100.0, NumberInput.step(99.0, 2.5, 2, 0.0..100.0), 0.0)
    }
}
