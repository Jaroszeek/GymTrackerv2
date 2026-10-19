package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtElevation
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

/** Matte content card: opaque surface, no blur, so text stays readable over the aurora. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(GtSpacing.xl),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = GtTheme.colors
    Column(
        modifier
            .softShadow(GtShapes.Card, colors, GtElevation.Card)
            .clip(GtShapes.Card)
            .background(colors.surface)
            .specularEdge(GtShapes.Card, colors)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.s),
        content = content,
    )
}

/** Floating glass panel; blurs the content behind it when placed in the chrome layer. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(GtSpacing.xl),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier, shape = GtShapes.Card) {
        Column(
            Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(GtSpacing.s),
            content = content,
        )
    }
}

@Preview
@Composable
private fun GlassCardPreview() {
    GymTrackerTheme {
        GlassScaffold {
            Column(Modifier.padding(GtSpacing.xl), verticalArrangement = Arrangement.spacedBy(GtSpacing.l)) {
                GlassCard { Text(stringResource(R.string.app_name), style = GtTheme.typography.title) }
                GlassPanel { Text(stringResource(R.string.app_name), style = GtTheme.typography.title) }
            }
        }
    }
}
