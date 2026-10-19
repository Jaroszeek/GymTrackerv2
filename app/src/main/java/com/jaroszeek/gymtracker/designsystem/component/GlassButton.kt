package com.jaroszeek.gymtracker.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtAlpha
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

enum class GlassButtonStyle { Primary, Secondary, Ghost }

/**
 * Pill button. Primary is the accent fill (with refraction when floating in the chrome layer),
 * Secondary is glass, Ghost is accent text only.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val colors = GtTheme.colors
    val content = when (style) {
        GlassButtonStyle.Primary -> colors.onAccent
        GlassButtonStyle.Secondary -> colors.textPrimary
        GlassButtonStyle.Ghost -> colors.accentText
    }
    PressableGlass(
        onClick = onClick,
        enabled = enabled,
        style = style,
        modifier = modifier.defaultMinSize(minHeight = GtSizes.ButtonHeight),
    ) {
        Row(
            Modifier.padding(horizontal = GtSpacing.xxl, vertical = GtSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(GtSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, Modifier.size(GtSizes.IconSmall), tint = content)
            Text(text, color = content, style = GtTheme.typography.label)
        }
    }
}

/** Round icon button; at least 48 dp touch target. */
@Composable
fun GlassIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Secondary,
    enabled: Boolean = true,
) {
    val colors = GtTheme.colors
    val tint = when (style) {
        GlassButtonStyle.Primary -> colors.onAccent
        GlassButtonStyle.Secondary -> colors.textPrimary
        GlassButtonStyle.Ghost -> colors.textSecondary
    }
    PressableGlass(
        onClick = onClick,
        enabled = enabled,
        style = style,
        modifier = modifier.size(GtSizes.MinTouch),
    ) {
        Icon(painterResource(icon), contentDescription, Modifier.size(GtSizes.Icon), tint = tint)
    }
}

@Composable
private fun PressableGlass(
    onClick: () -> Unit,
    enabled: Boolean,
    style: GlassButtonStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val pressable = modifier
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            alpha = if (enabled) 1f else GtAlpha.DISABLED
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    when (style) {
        GlassButtonStyle.Ghost -> Box(pressable, contentAlignment = Alignment.Center) { content() }
        else -> GlassSurface(
            modifier = pressable,
            shape = GtShapes.Pill,
            refraction = style == GlassButtonStyle.Primary,
            fill = if (style == GlassButtonStyle.Primary) GtTheme.colors.accent else Color.Unspecified,
            shadow = style == GlassButtonStyle.Primary,
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Preview
@Composable
private fun GlassButtonPreview() {
    GymTrackerTheme {
        GlassScaffold {
            Column(Modifier.padding(GtSpacing.xl), verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                GlassButton(stringResource(R.string.app_name), onClick = {}, icon = R.drawable.ic_add)
                GlassButton(stringResource(R.string.app_name), onClick = {}, style = GlassButtonStyle.Secondary)
                GlassButton(stringResource(R.string.app_name), onClick = {}, style = GlassButtonStyle.Ghost)
                GlassIconButton(R.drawable.ic_settings, contentDescription = null, onClick = {})
            }
        }
    }
}
