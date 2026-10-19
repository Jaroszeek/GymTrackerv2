package com.jaroszeek.gymtracker.designsystem.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideOnScrollStateTest {

    @Test
    fun hidesOnlyAfterThreshold() {
        val state = HideOnScrollState(threshold = 100f)
        state.onScroll(-60f)
        assertTrue(state.visible)
        state.onScroll(-60f)
        assertFalse(state.visible)
    }

    @Test
    fun directionChangeResetsAccumulation() {
        val state = HideOnScrollState(threshold = 100f)
        state.onScroll(-90f)
        state.onScroll(20f)
        state.onScroll(-90f)
        assertTrue(state.visible)
    }

    @Test
    fun showsAgainWhenScrollingUp() {
        val state = HideOnScrollState(threshold = 100f)
        state.onScroll(-150f)
        state.onScroll(150f)
        assertTrue(state.visible)
    }
}
