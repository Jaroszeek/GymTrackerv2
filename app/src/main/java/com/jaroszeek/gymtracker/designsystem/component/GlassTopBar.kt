package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtBlur
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

/**
 * Top bar for the chrome layer: transparent at the top of the content, glass once [scrolled].
 * [navigationIcon] and [actions] usually hold [GlassIconButton]s.
 */
@Composable
fun GlassTopBar(
    title: String,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth()) {
        AnimatedVisibility(scrolled, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
            GlassSurface(shape = RectangleShape, blurRadius = GtBlur.Bar, shadow = false)
        }
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(GtSizes.TopBarHeight)
                .fillMaxWidth()
                .padding(horizontal = GtSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GtSpacing.xs),
        ) {
            if (navigationIcon != null) navigationIcon() else Box(Modifier.padding(start = GtSpacing.m))
            Text(
                title,
                Modifier.weight(1f),
                style = GtTheme.typography.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
    }
}

@Preview
@Composable
private fun GlassTopBarPreview() {
    GymTrackerTheme {
        GlassScaffold(chrome = {
            GlassTopBar(
                title = stringResource(R.string.app_name),
                scrolled = true,
                navigationIcon = { GlassIconButton(R.drawable.ic_arrow_back, null, onClick = {}, style = GlassButtonStyle.Ghost) },
                actions = { GlassIconButton(R.drawable.ic_settings, null, onClick = {}, style = GlassButtonStyle.Ghost) },
            )
        }) {}
    }
}
