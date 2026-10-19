package com.jaroszeek.gymtracker.feature.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtAlpha
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.navigation.TopLevelTab

@StringRes
private fun TopLevelTab.placeholderText(): Int = when (this) {
    TopLevelTab.Home -> R.string.placeholder_home
    TopLevelTab.Exercises -> R.string.placeholder_exercises
    TopLevelTab.Workout -> R.string.placeholder_workout
    TopLevelTab.Progress -> R.string.placeholder_progress
    TopLevelTab.Profile -> R.string.placeholder_profile
}

/** Stub of a tab until its stage (Etap 2–8) replaces it. */
@Composable
fun PlaceholderScreen(tab: TopLevelTab, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(navBarContentPadding())
            .padding(horizontal = GtSpacing.xl, vertical = GtSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.xxl),
    ) {
        Text(stringResource(tab.label), style = GtTheme.typography.display)
        GlassCard(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(GtSizes.MinTouch)
                    .clip(GtShapes.Pill)
                    .background(colors.accent.copy(alpha = GtAlpha.ACCENT_WASH)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(tab.selectedIcon), contentDescription = null, tint = colors.accentText)
            }
            Text(stringResource(R.string.placeholder_title), style = GtTheme.typography.title)
            Text(stringResource(tab.placeholderText()), color = colors.textSecondary, style = GtTheme.typography.body)
        }
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    GymTrackerTheme {
        GlassScaffold { PlaceholderScreen(TopLevelTab.Workout) }
    }
}
