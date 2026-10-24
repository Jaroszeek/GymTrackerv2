package com.jaroszeek.gymtracker.feature.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

private val Sections = listOf(
    R.string.privacy_read_title to R.string.privacy_read_text,
    R.string.privacy_why_title to R.string.privacy_why_text,
    R.string.privacy_write_title to R.string.privacy_write_text,
    R.string.privacy_where_title to R.string.privacy_where_text,
    R.string.privacy_control_title to R.string.privacy_control_text,
)

/** What the app does with health data; Health Connect opens it from its permission screens too (A6). */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = GtSpacing.xl,
            end = GtSpacing.xl,
            top = insets.calculateTopPadding() + GtSpacing.s,
            bottom = navBarContentPadding().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.l),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                GlassIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClick = onBack)
                Text(stringResource(R.string.privacy_title), Modifier.semantics { heading() }, style = GtTheme.typography.headline)
            }
        }
        item(key = "intro") {
            Text(stringResource(R.string.privacy_intro), color = GtTheme.colors.textSecondary, style = GtTheme.typography.body)
        }
        items(Sections, key = { it.first }) { (title, text) ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(title), style = GtTheme.typography.title)
                Text(stringResource(text), color = GtTheme.colors.textSecondary, style = GtTheme.typography.body)
            }
        }
    }
}

@Preview
@Composable
private fun PrivacyPolicyScreenPreview() {
    GymTrackerTheme {
        GlassScaffold { PrivacyPolicyScreen(onBack = {}) }
    }
}
