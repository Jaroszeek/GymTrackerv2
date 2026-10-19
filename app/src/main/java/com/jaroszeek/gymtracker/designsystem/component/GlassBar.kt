package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.designsystem.theme.GtMotion
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

/** Horizontal meter with [fraction] of the track in the accent; like the ring, drawn in the text accent for contrast. */
@Composable
fun GlassBar(fraction: Float, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    val target = fraction.coerceIn(0f, 1f)
    val value by animateFloatAsState(target, GtMotion.Standard, label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(GtSizes.BarHeight)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) }
            .clip(GtShapes.Pill)
            .background(colors.surfaceRaised)
            .drawBehind {
                val width = size.width * value.coerceIn(0f, 1f)
                if (width > 0f) drawRoundRect(colors.accentText, size = Size(width, size.height), cornerRadius = CornerRadius(size.height / 2))
            },
    )
}

@Preview
@Composable
private fun GlassBarPreview() {
    GymTrackerTheme {
        GlassScaffold {
            Column(Modifier.padding(GtSpacing.xl), verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                GlassBar(0f)
                GlassBar(0.35f)
                GlassBar(1f)
            }
        }
    }
}
