package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.designsystem.theme.GtMotion
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Blob anchors (fraction of width/height), radius (fraction of the longer side) and drift amplitude
private class Blob(val x: Float, val y: Float, val radius: Float, val drift: Float, val speed: Int)

private val Blobs = listOf(
    Blob(x = 0.15f, y = 0.08f, radius = 0.55f, drift = 0.08f, speed = 1),
    Blob(x = 0.95f, y = 0.35f, radius = 0.45f, drift = 0.10f, speed = 2),
    Blob(x = 0.30f, y = 0.85f, radius = 0.50f, drift = 0.07f, speed = 1),
)

// ponytail: phase advances at ~30 fps; motion per tick is sub-pixel, and it keeps the blurred layer cheap
private const val TICK_MILLIS = 33L

/** Near-black base with 2–3 slowly drifting accent-toned blobs; static when system animations are off. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    val reducedMotion = GtTheme.reducedMotion
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        while (true) {
            // The frame clock pauses in the background, so the drift stops with it
            val now = withFrameMillis { it }
            phase = (now % GtMotion.AURORA_PERIOD_MILLIS) / GtMotion.AURORA_PERIOD_MILLIS.toFloat()
            delay(TICK_MILLIS)
        }
    }
    Box(
        modifier.drawBehind {
            drawRect(colors.background)
            Blobs.forEachIndexed { index, blob ->
                val tone = colors.aurora[index % colors.aurora.size]
                drawBlob(blob, tone.copy(alpha = colors.auroraAlpha), phase)
            }
        },
    )
}

private fun DrawScope.drawBlob(blob: Blob, color: Color, phase: Float) {
    val angle = 2 * PI.toFloat() * phase * blob.speed
    val longest = maxOf(size.width, size.height)
    val center = Offset(
        x = size.width * blob.x + longest * blob.drift * cos(angle),
        y = size.height * blob.y + longest * blob.drift * sin(angle),
    )
    val radius = longest * blob.radius
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
}

@Preview
@Composable
private fun AuroraBackgroundPreview() {
    GymTrackerTheme { AuroraBackground(Modifier.fillMaxSize()) }
}
