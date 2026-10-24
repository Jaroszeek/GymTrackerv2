package com.jaroszeek.gymtracker.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.GlassBottomSheet
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassChip
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.workout.RecordHit
import com.jaroszeek.gymtracker.domain.workout.TargetChange
import com.jaroszeek.gymtracker.domain.workout.WorkoutSummary
import com.jaroszeek.gymtracker.feature.progress.label
import com.jaroszeek.gymtracker.feature.routines.massLabel

/** After the workout: time, volume and sets, then the sheet with changes against the routine. */
@Composable
fun WorkoutSummaryScreen(
    state: FinishState,
    exercises: Map<Long, Exercise>,
    onChangeChoice: (routineVariantId: Long, apply: Boolean) -> Unit,
    onChangesDone: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    val summary = state.summary
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = GtSpacing.xl, end = GtSpacing.xl, top = insets.calculateTopPadding() + GtSpacing.xxl)
            .padding(navBarContentPadding()),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.l),
    ) {
        Text(stringResource(R.string.summary_title), Modifier.semantics { heading() }, style = GtTheme.typography.display)
        Text(state.name, color = GtTheme.colors.textSecondary, style = GtTheme.typography.title)
        SummaryStats(summary)
        if (state.records.isNotEmpty()) RecordsSummary(state.records, exercises)
        GlassButton(stringResource(R.string.action_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }

    GlassBottomSheet(visible = state.changesVisible, onDismissRequest = onChangesDone) {
        TargetChanges(state.changes, state.apply, exercises, onChangeChoice, onChangesDone)
    }
}

/** The records of the workout per exercise: "ciężar: 102,5 kg • e1RM: 116,7 kg". */
@Composable
private fun RecordsSummary(records: List<RecordHit>, exercises: Map<Long, Exercise>) {
    val colors = GtTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            Icon(painterResource(R.drawable.ic_trophy), contentDescription = null, tint = colors.accentText)
            Text(stringResource(R.string.summary_records), style = GtTheme.typography.title)
        }
        records.groupBy { it.exerciseId }.forEach { (exerciseId, hits) ->
            val values = hits.map { hit ->
                val value = when (hit.type) {
                    RecordType.Reps -> setValueLabel(hit.set.weightKg, hit.set.reps)
                    RecordType.Weight -> hit.valueKg?.let { massLabel(it) }.orEmpty()
                    RecordType.E1rm -> hit.valueKg?.let { massLabel(it, decimals = 1) }.orEmpty()
                    RecordType.SetVolume, RecordType.SessionVolume -> hit.valueKg?.let { massLabel(it, decimals = 0) }.orEmpty()
                }
                stringResource(R.string.summary_record_value, stringResource(hit.type.label()), value)
            }
            Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.xxs)) {
                Text(exercises[exerciseId]?.name.orEmpty(), style = GtTheme.typography.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(values.joinToString(" • "), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
            }
        }
    }
}

/** Duration, volume, working sets and exercises as four tiles. */
@Composable
internal fun SummaryStats(summary: WorkoutSummary, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            Stat(stringResource(R.string.summary_duration), durationLabel(summary.durationMillis), Modifier.weight(1f))
            Stat(stringResource(R.string.summary_volume), massLabel(summary.volumeKg, decimals = 0), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            Stat(stringResource(R.string.summary_sets), "${summary.sets}", Modifier.weight(1f))
            Stat(stringResource(R.string.summary_exercises), "${summary.exercises}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Text(label, color = GtTheme.colors.textSecondary, style = GtTheme.typography.labelSmall)
        Text(value, style = GtTheme.typography.numberM, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Per exercise: "Zastosuj jako nowe cele" or "Zostaw stare"; nothing changes in the routine until "Gotowe". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TargetChanges(
    changes: List<TargetChange>,
    apply: Set<Long>,
    exercises: Map<Long, Exercise>,
    onChoice: (Long, Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val colors = GtTheme.colors
    Text(stringResource(R.string.changes_title), style = GtTheme.typography.headline)
    Text(stringResource(R.string.changes_text), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.l),
    ) {
        changes.forEach { change ->
            val applied = change.routineVariantId in apply
            Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
                Text(exercises[change.exerciseId]?.name.orEmpty(), style = GtTheme.typography.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.changes_value, change.old.compact(), change.new.compact()), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                    GlassChip(
                        stringResource(R.string.change_apply),
                        selected = applied,
                        onClick = { onChoice(change.routineVariantId, true) },
                        role = Role.RadioButton,
                    )
                    GlassChip(
                        stringResource(R.string.change_keep),
                        selected = !applied,
                        onClick = { onChoice(change.routineVariantId, false) },
                        role = Role.RadioButton,
                    )
                }
            }
        }
    }
    GlassButton(stringResource(R.string.action_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
}

@Preview
@Composable
private fun WorkoutSummaryScreenPreview() {
    val targets = RoutineTargets(3, 8, 12, 60.0)
    GymTrackerTheme {
        GlassScaffold {
            WorkoutSummaryScreen(
                state = FinishState(
                    name = "Push",
                    summary = WorkoutSummary(durationMillis = 3_125_000, volumeKg = 8_450.0, sets = 18, exercises = 6),
                    changes = listOf(TargetChange(1, 1, targets, targets.copy(sets = 4, weightKg = 62.5))),
                ),
                exercises = emptyMap(),
                onChangeChoice = { _, _ -> },
                onChangesDone = {},
                onDone = {},
            )
        }
    }
}
