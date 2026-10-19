package com.jaroszeek.gymtracker.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtAlpha
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

/** Selectable filter chip; selected = accent fill. */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val colors = GtTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val fill by animateColorAsState(if (selected) colors.accent else colors.surfaceRaised, label = "chipFill")
    val content by animateColorAsState(if (selected) colors.onAccent else colors.textSecondary, label = "chipContent")
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            // Visual height stays compact; the touch target still reaches 48 dp
            .defaultMinSize(minHeight = GtSizes.MinTouch)
            .selectable(selected, interaction, indication = null, role = Role.Checkbox, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(GtSizes.ChipHeight)
                .clip(GtShapes.Pill)
                .background(fill)
                .specularEdge(GtShapes.Pill, colors)
                .padding(horizontal = GtSpacing.l),
            horizontalArrangement = Arrangement.spacedBy(GtSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, Modifier.size(GtSizes.IconSmall), tint = content)
            Text(text, color = content, style = GtTheme.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

enum class BadgeTone { Neutral, Accent }

/** Small non-interactive label, e.g. equipment or "własne". */
@Composable
fun GlassBadge(text: String, modifier: Modifier = Modifier, tone: BadgeTone = BadgeTone.Neutral) {
    val colors = GtTheme.colors
    val (fill, content) = when (tone) {
        BadgeTone.Neutral -> colors.surfaceRaised to colors.textSecondary
        BadgeTone.Accent -> colors.accent.copy(alpha = GtAlpha.ACCENT_WASH) to colors.accentText
    }
    Text(
        text,
        modifier
            .height(GtSizes.BadgeHeight)
            .clip(GtShapes.Pill)
            .background(fill)
            .padding(horizontal = GtSpacing.s, vertical = GtSpacing.xxs),
        color = content,
        style = GtTheme.typography.labelSmall,
        maxLines = 1,
    )
}

@Preview
@Composable
private fun GlassChipPreview() {
    GymTrackerTheme {
        GlassScaffold {
            Row(Modifier.padding(GtSpacing.xl), horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                GlassChip(stringResource(R.string.nav_home), selected = true, onClick = {})
                GlassChip(stringResource(R.string.nav_profile), selected = false, onClick = {})
                GlassBadge(stringResource(R.string.nav_workout), tone = BadgeTone.Accent)
            }
        }
    }
}
