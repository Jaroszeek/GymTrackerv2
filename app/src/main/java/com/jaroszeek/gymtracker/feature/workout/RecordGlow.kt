package com.jaroszeek.gymtracker.feature.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import com.jaroszeek.gymtracker.designsystem.theme.GtAlpha
import com.jaroszeek.gymtracker.designsystem.theme.GtMotion
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme

private const val GLOW_REST = 0.45f
private const val GLOW_REACH = 0.8f

/**
 * Strength of a record's glow: 0 without a record; a new record flares up to 1 and settles, one that
 * already was a record (scrolled back into view) starts settled. Static when animations are off.
 */
@Composable
internal fun rememberRecordGlow(record: Boolean): State<Float> {
    val reducedMotion = GtTheme.reducedMotion
    val glow = remember { Animatable(if (record) GLOW_REST else 0f) }
    LaunchedEffect(record) {
        when {
            !record -> glow.snapTo(0f)
            glow.value == 0f && !reducedMotion -> {
                glow.animateTo(1f, GtMotion.Standard)
                glow.animateTo(GLOW_REST, GtMotion.Reveal)
            }
            else -> glow.snapTo(GLOW_REST)
        }
    }
    return glow.asState()
}

/** A soft accent light from the set number across the row: the record moment, without confetti (A4). */
internal fun Modifier.recordGlow(glow: State<Float>, accent: Color) = drawBehind {
    val strength = glow.value
    if (strength <= 0f) return@drawBehind
    drawOutline(
        GtShapes.Small.createOutline(size, layoutDirection, this),
        Brush.radialGradient(
            listOf(accent.copy(alpha = GtAlpha.RECORD_GLOW * strength), Color.Transparent),
            center = Offset(size.height / 2, size.height / 2),
            radius = size.width * GLOW_REACH,
        ),
    )
}
