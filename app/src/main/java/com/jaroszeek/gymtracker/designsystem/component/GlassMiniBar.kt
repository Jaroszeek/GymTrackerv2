package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtBlur
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

/** Floating glass pill above the navbar for something running in the background (the workout), with its progress. */
@Composable
fun GlassMiniBar(
    title: String,
    subtitle: String,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    GlassSurface(
        modifier = modifier
            .padding(horizontal = GtSizes.NavBarMargin)
            .widthIn(max = GtSizes.NavBarMaxWidth)
            .fillMaxWidth()
            .heightIn(min = GtSizes.MiniBarHeight)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = onClickLabel, onClick = onClick),
        shape = GtShapes.Pill,
        refraction = true,
        blurRadius = GtBlur.Bar,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = GtSizes.MiniBarHeight)
                .padding(horizontal = GtSpacing.xl, vertical = GtSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GtSpacing.m),
        ) {
            GlassProgressRing(progress, Modifier.size(GtSizes.MiniRing), strokeWidth = GtSizes.MiniRingStroke)
            Column(Modifier.weight(1f)) {
                Text(title, style = GtTheme.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = GtTheme.colors.textSecondary, style = GtTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Preview
@Composable
private fun GlassMiniBarPreview() {
    GymTrackerTheme {
        GlassScaffold(chrome = { GlassMiniBar(stringResource(R.string.nav_workout), stringResource(R.string.rest_title), 0.4f, onClick = {}) }) {}
    }
}
