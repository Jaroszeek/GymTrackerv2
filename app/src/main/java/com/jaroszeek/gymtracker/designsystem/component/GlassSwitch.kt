package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val ThumbInset = 4.dp

/**
 * On/off switch whose thumb travels as a liquid droplet (same springs as the navbar indicator).
 * With [onCheckedChange] null it only shows the state, for a row that toggles as a whole ([GlassSwitchRow]).
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GtTheme.colors
    val haptics = LocalHapticFeedback.current
    val position = if (checked) 1f else 0f
    val thumb = remember { LiquidSpan(position, position) }
    LaunchedEffect(checked) { thumb.animateTo(position, position) }
    val track by animateColorAsState(if (checked) colors.accent else colors.surfaceRaised, label = "switchTrack")
    val knob by animateColorAsState(if (checked) colors.onAccent else colors.textSecondary, label = "switchThumb")
    val outline = colors.hairline
    Box(
        modifier
            .size(GtSizes.MinTouch * 1.25f, GtSizes.MinTouch)
            .then(
                if (onCheckedChange == null) {
                    Modifier
                } else {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = {
                            haptics.toggle(it)
                            onCheckedChange(it)
                        },
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(TrackWidth, TrackHeight)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = radius)
                    drawRoundRect(outline, cornerRadius = radius, style = Stroke(GtSizes.Hairline.toPx()))
                    val inset = ThumbInset.toPx()
                    val diameter = size.height - inset * 2
                    val travel = size.width - inset * 2 - diameter
                    // Leading and trailing edges lag differently, so the thumb stretches in motion
                    val left = inset + travel * minOf(thumb.start, thumb.end)
                    val right = inset + travel * maxOf(thumb.start, thumb.end) + diameter
                    drawRoundRect(knob, Offset(left, inset), Size(right - left, diameter), CornerRadius(diameter / 2))
                },
        )
    }
}

/** A title (and an optional explanation) with a switch; the whole row toggles, and TalkBack reads it as one switch. */
@Composable
fun GlassSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    enabled: Boolean = true,
    titleStyle: TextStyle = GtTheme.typography.label,
) {
    val colors = GtTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch) {
            haptics.toggle(it)
            onCheckedChange(it)
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GtSpacing.m),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
            Text(title, color = if (enabled) colors.textPrimary else colors.textTertiary, style = titleStyle)
            if (text != null) Text(text, color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        }
        GlassSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private fun HapticFeedback.toggle(on: Boolean) = performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

@Preview
@Composable
private fun GlassSwitchPreview() {
    GymTrackerTheme {
        GlassScaffold {
            Column(Modifier.padding(GtSpacing.xl)) {
                Row {
                    GlassSwitch(checked = true, onCheckedChange = {})
                    GlassSwitch(checked = false, onCheckedChange = {})
                }
                GlassSwitchRow("AMOLED", checked = true, onCheckedChange = {}, text = "Czysta czerń")
            }
        }
    }
}
