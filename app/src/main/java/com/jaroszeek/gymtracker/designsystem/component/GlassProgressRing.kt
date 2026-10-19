package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtAlpha
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

private const val FULL_CIRCLE = 360f
private const val TOP = -90f

/** Accent arc on a faint accent track, clockwise from the top; [content] sits in the middle. */
@Composable
fun GlassProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = GtSizes.RingStroke,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val accent = GtTheme.colors.accent
    val value = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }
            .drawBehind {
                val stroke = strokeWidth.toPx()
                val topLeft = Offset(stroke / 2, stroke / 2)
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(accent.copy(alpha = GtAlpha.ACCENT_WASH), 0f, FULL_CIRCLE, false, topLeft, arc, style = Stroke(stroke))
                drawArc(accent, TOP, FULL_CIRCLE * value, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Preview
@Composable
private fun GlassProgressRingPreview() {
    GymTrackerTheme {
        GlassScaffold {
            GlassProgressRing(0.65f, Modifier.padding(GtSpacing.xl).size(GtSizes.RestRing)) {
                Text(stringResource(R.string.rest_title), style = GtTheme.typography.title)
            }
        }
    }
}
