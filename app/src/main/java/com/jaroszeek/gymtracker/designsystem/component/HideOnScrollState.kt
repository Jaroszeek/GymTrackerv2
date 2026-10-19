package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Hides bars while scrolling down and shows them when scrolling up. Scroll accumulates in one
 * direction and flips the state only after [threshold] pixels, so small moves do not flicker the bars.
 * Ported from vynox (A3).
 */
@Stable
class HideOnScrollState(private val threshold: Float) {
    var visible by mutableStateOf(true)
        private set

    private var accumulated = 0f

    /** Negative [deltaY] = content moves up, i.e. the user scrolls down. */
    fun onScroll(deltaY: Float) {
        if (accumulated * deltaY < 0f) accumulated = 0f
        accumulated += deltaY
        when {
            accumulated <= -threshold -> changeVisibility(false)
            accumulated >= threshold -> changeVisibility(true)
        }
    }

    fun show() = changeVisibility(true)

    private fun changeVisibility(value: Boolean) {
        visible = value
        accumulated = 0f
    }

    // Observes scrolling only, consumes nothing
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            onScroll(available.y)
            return Offset.Zero
        }
    }
}
