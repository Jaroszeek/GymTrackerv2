package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import com.jaroszeek.gymtracker.designsystem.theme.GtMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A span (navbar indicator, switch thumb) whose edges ride separate springs: the leading edge
 * is fast, the trailing one lags. While moving it stretches like a droplet, then settles.
 * Ported from vynox (A3).
 */
@Stable
internal class LiquidSpan(start: Float, end: Float) {
    private val startEdge = Animatable(start)
    private val endEdge = Animatable(end)

    val start: Float get() = startEdge.value
    val end: Float get() = endEdge.value

    /** Relative stretch: 0 at rest, grows while moving. */
    fun stretch(restLength: Float): Float = ((end - start) / restLength - 1f).coerceAtLeast(0f)

    suspend fun animateTo(start: Float, end: Float) = coroutineScope {
        val forward = start > startEdge.value
        launch { startEdge.animateTo(start, if (forward) GtMotion.LiquidTrailing else GtMotion.LiquidLeading) }
        launch { endEdge.animateTo(end, if (forward) GtMotion.LiquidLeading else GtMotion.LiquidTrailing) }
    }
}

/** Springy press of a tappable element; read the value in `graphicsLayer` to skip recomposition. */
@Composable
internal fun rememberPressScale(interaction: InteractionSource, pressedScale: Float = GtMotion.PRESSED_SCALE): State<Float> {
    val pressed by interaction.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = GtMotion.Press,
        label = "pressScale",
    )
}
