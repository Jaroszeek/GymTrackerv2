package com.jaroszeek.gymtracker.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassFullScreenOverlay
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassProgressRing
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.workout.RestTimer

private const val MINUS_SECONDS = -15
private const val PLUS_SECONDS = 30
private const val RING_TICK_MILLIS = 100L

/** Full-screen rest timer; it closes by itself when the rest ends. Closing it keeps the rest running. */
@Composable
fun RestOverlay(
    rest: RestTimer?,
    visible: Boolean,
    next: String,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
    onHide: () -> Unit,
) {
    GlassFullScreenOverlay(visible = visible, onDismissRequest = onHide) {
        RestContent(rest, next, onAdjust, onSkip, onHide)
    }
}

@Composable
private fun RestContent(rest: RestTimer?, next: String, onAdjust: (Int) -> Unit, onSkip: () -> Unit, onHide: () -> Unit) {
    val colors = GtTheme.colors
    Box(Modifier.fillMaxSize()) {
        GlassIconButton(
            R.drawable.ic_close,
            stringResource(R.string.action_close),
            onClick = onHide,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(GtSpacing.l),
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = GtSpacing.xxl)
                .widthIn(max = GtSizes.DialogMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GtSpacing.xl),
        ) {
            Text(stringResource(R.string.rest_title), color = colors.textSecondary, style = GtTheme.typography.title)
            RestRing(rest)
            Text(next, color = colors.textSecondary, style = GtTheme.typography.body, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                GlassButton(stringResource(R.string.rest_minus), onClick = { onAdjust(MINUS_SECONDS) }, style = GlassButtonStyle.Secondary)
                GlassButton(stringResource(R.string.rest_plus), onClick = { onAdjust(PLUS_SECONDS) }, style = GlassButtonStyle.Secondary)
            }
            GlassButton(stringResource(R.string.rest_skip), onClick = onSkip, modifier = Modifier.fillMaxWidth())
        }
    }
}

// Its own composable: only the ring redraws ten times a second. A finished rest (null) reads 0:00 while the overlay fades out.
@Composable
private fun RestRing(rest: RestTimer?) {
    val now = rememberNow(RING_TICK_MILLIS)
    GlassProgressRing(rest?.fraction(now) ?: 0f, Modifier.size(GtSizes.RestRing)) {
        Text(countdownLabel(rest?.remainingMillis(now) ?: 0), style = GtTheme.typography.numberXL)
    }
}

@Preview
@Composable
private fun RestContentPreview() {
    GymTrackerTheme {
        GlassScaffold {
            val now = System.currentTimeMillis()
            RestContent(RestTimer(now - 30_000, now + 60_000, 1, 1), stringResource(R.string.rest_next, "Bench Press", 2), {}, {}, {})
        }
    }
}
