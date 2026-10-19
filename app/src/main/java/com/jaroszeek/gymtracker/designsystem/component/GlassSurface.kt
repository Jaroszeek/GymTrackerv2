package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtBlur
import com.jaroszeek.gymtracker.designsystem.theme.GtColors
import com.jaroszeek.gymtracker.designsystem.theme.GtElevation
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow as BackdropShadow

/**
 * Base of every floating glass element and the only place that uses the Backdrop library (D-006).
 *
 * In the chrome layer of [GlassScaffold] it blurs and saturates what is behind it; [refraction]
 * adds lens refraction at the edges (navbar, mini bar, primary button only). Inside content there
 * is nothing to sample, so it draws the translucent tint alone. With "Ogranicz przezroczystość"
 * it is opaque and never blurs.
 *
 * @param fill replaces the theme glass tint, e.g. the accent for a primary button
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GtShapes.Card,
    refraction: Boolean = false,
    blurRadius: Dp = GtBlur.Panel,
    fill: Color = Color.Unspecified,
    shadow: Boolean = true,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = GtTheme.colors
    val opaque = GtTheme.reduceTransparency
    val backdrop = LocalBackdrop.current.takeUnless { opaque }
    val tint = when {
        fill.isSpecified -> fill
        opaque -> colors.surfaceRaised
        else -> colors.glassTint
    }
    val surface = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurRadius.toPx())
                if (refraction) {
                    lens(
                        refractionHeight = GtBlur.RefractionHeight.toPx(),
                        refractionAmount = GtBlur.RefractionAmount.toPx(),
                        depthEffect = true,
                    )
                }
            },
            highlight = null,
            shadow = if (shadow) ({ BackdropShadow(radius = GtElevation.Floating, color = colors.shadow) }) else null,
            onDrawSurface = {
                drawRect(tint)
                drawSheen(colors)
            },
        )
    } else {
        Modifier
            .then(if (shadow) Modifier.softShadow(shape, colors, GtElevation.Floating) else Modifier)
            .clip(shape)
            .background(tint)
            .drawWithContent {
                drawSheen(colors)
                drawContent()
            }
    }
    Box(
        modifier
            .then(surface)
            .specularEdge(shape, colors),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** 1 px specular hairline, brighter at the top as if lit from above. */
internal fun Modifier.specularEdge(shape: Shape, colors: GtColors) = border(
    width = GtSizes.Hairline,
    brush = Brush.verticalGradient(listOf(colors.specularTop, colors.specularBottom)),
    shape = shape,
)

internal fun Modifier.softShadow(shape: Shape, colors: GtColors, radius: Dp) = dropShadow(
    shape = shape,
    shadow = Shadow(radius = radius, color = colors.shadow, offset = DpOffset(0.dp, radius / 4)),
)

// Soft inner gloss along the top edge
private fun DrawScope.drawSheen(colors: GtColors) {
    drawRect(Brush.verticalGradient(0f to colors.sheen, 0.45f to Color.Transparent))
}

@Preview
@Composable
private fun GlassSurfacePreview() {
    GymTrackerTheme {
        GlassScaffold {
            GlassSurface(Modifier.padding(24.dp).size(220.dp, 120.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.app_name))
            }
        }
    }
}
