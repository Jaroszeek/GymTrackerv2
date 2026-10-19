package com.jaroszeek.gymtracker.feature.progress

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.BadgeTone
import com.jaroszeek.gymtracker.designsystem.component.GlassBadge
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.progress.RecordType

@StringRes
private fun RecordType.badge(): Int = when (this) {
    RecordType.Weight -> R.string.record_badge_weight
    RecordType.E1rm -> R.string.record_badge_e1rm
    RecordType.Reps -> R.string.record_badge_reps
    RecordType.SetVolume -> R.string.record_badge_set_volume
    RecordType.SessionVolume -> R.string.record_badge_session_volume
}

/** A badge per record of a set, wrapping when they do not fit beside the set. */
@Composable
fun RecordBadges(records: Set<RecordType>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(GtSpacing.xs), verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
        RecordType.entries.filter { it in records }.forEach { GlassBadge(stringResource(it.badge()), tone = BadgeTone.Accent) }
    }
}

@Preview
@Composable
private fun RecordBadgesPreview() {
    GymTrackerTheme {
        GlassScaffold {
            GlassCard(Modifier.padding(GtSpacing.xl)) {
                RecordBadges(RecordType.entries.toSet())
            }
        }
    }
}
