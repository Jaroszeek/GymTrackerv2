package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtPalette
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.designsystem.theme.contentColorFor
import com.jaroszeek.gymtracker.designsystem.theme.gtColors

/** A color to pick, e.g. a palette's accent; the selected one has a ring and a check. */
@Composable
fun GlassSwatch(color: Color, selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val ring by animateDpAsState(if (selected) GtSpacing.xxs else GtSizes.Hairline, label = "swatchRing")
    Box(
        modifier
            .size(GtSizes.MinTouch)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .semantics { contentDescription = label }
            .selectable(selected, interaction, indication = null, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(GtSizes.ChipHeight)
                .border(ring, if (selected) colors.textPrimary else colors.hairline, GtShapes.Pill)
                .padding(ring + GtSpacing.xxs)
                .clip(GtShapes.Pill)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(GtSizes.IconSmall), tint = contentColorFor(color))
        }
    }
}

@Preview
@Composable
private fun GlassSwatchPreview() {
    GymTrackerTheme {
        GlassScaffold {
            Row(Modifier.padding(GtSpacing.xl), horizontalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
                GtPalette.entries.forEach { palette ->
                    GlassSwatch(gtColors(palette, dark = true).accent, selected = palette == GtPalette.Volt, onClick = {}, label = palette.name)
                }
            }
        }
    }
}
