package com.jaroszeek.gymtracker.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

/** What already has a screen; the settings of A5.9 come with Etap 9. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onHealthConnect: () -> Unit, onDiagnostics: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(navBarContentPadding())
            .padding(horizontal = GtSpacing.xl, vertical = GtSpacing.s),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            GlassIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClick = onBack)
            Text(stringResource(R.string.settings_title), style = GtTheme.typography.headline)
        }
        Entry(R.drawable.ic_favorite, stringResource(R.string.health_title), stringResource(R.string.health_entry_text), onHealthConnect)
        Entry(R.drawable.ic_timer, stringResource(R.string.diagnostics_title), null, onDiagnostics)
        Text(stringResource(R.string.settings_soon), color = GtTheme.colors.textTertiary, style = GtTheme.typography.bodySmall)
    }
}

@Composable
private fun Entry(@DrawableRes icon: Int, title: String, text: String?, onClick: () -> Unit) {
    val colors = GtTheme.colors
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.accentText)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
                Text(title, style = GtTheme.typography.title)
                text?.let { Text(it, color = colors.textSecondary, style = GtTheme.typography.bodySmall) }
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.textTertiary)
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    GymTrackerTheme {
        GlassScaffold { SettingsScreen(onBack = {}, onHealthConnect = {}, onDiagnostics = {}) }
    }
}
