package com.jaroszeek.gymtracker.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassChip
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.GlassTextField
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.exercise.BodyPart
import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.exercise.ExerciseDraft
import com.jaroszeek.gymtracker.domain.exercise.Muscle

private const val NOTES_MIN_LINES = 4

@Composable
fun ExerciseEditorRoute(viewModel: ExerciseEditorViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    ExerciseEditorScreen(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onEquipmentChange = viewModel::onEquipmentChange,
        onTogglePrimary = viewModel::onTogglePrimary,
        onToggleSecondary = viewModel::onToggleSecondary,
        onNotesChange = viewModel::onNotesChange,
        onSave = viewModel::onSave,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditorScreen(
    state: ExerciseEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onEquipmentChange: (Equipment?) -> Unit,
    onTogglePrimary: (Muscle) -> Unit,
    onToggleSecondary: (Muscle) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    val colors = GtTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .imePadding()
            .padding(navBarContentPadding())
            .padding(horizontal = GtSpacing.xl, vertical = GtSpacing.s),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            GlassIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClick = onBack)
            Text(
                stringResource(if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit),
                style = GtTheme.typography.headline,
            )
        }
        if (state.loading) return@Column
        GlassTextField(
            value = draft.name,
            onValueChange = onNameChange,
            label = stringResource(R.string.label_name),
            placeholder = stringResource(R.string.editor_name_placeholder),
            error = stringResource(R.string.editor_name_error).takeIf { state.showErrors && draft.name.isBlank() },
        )
        Section(stringResource(R.string.label_equipment)) {
            GlassChip(stringResource(R.string.editor_no_equipment), selected = draft.equipment == null, onClick = { onEquipmentChange(null) })
            Equipment.entries.forEach { equipment ->
                GlassChip(stringResource(equipment.label), selected = draft.equipment == equipment, onClick = { onEquipmentChange(equipment) })
            }
        }
        Section(
            title = stringResource(R.string.detail_primary),
            error = stringResource(R.string.editor_primary_error).takeIf { state.showErrors && draft.primaryMuscles.isEmpty() },
        ) {
            BodyPart.orderedMuscles.forEach { muscle ->
                GlassChip(stringResource(muscle.label), selected = muscle in draft.primaryMuscles, onClick = { onTogglePrimary(muscle) })
            }
        }
        Section(stringResource(R.string.detail_secondary)) {
            // A primary muscle cannot also be secondary
            (BodyPart.orderedMuscles - draft.primaryMuscles).forEach { muscle ->
                GlassChip(stringResource(muscle.label), selected = muscle in draft.secondaryMuscles, onClick = { onToggleSecondary(muscle) })
            }
        }
        GlassTextField(
            value = draft.notes,
            onValueChange = onNotesChange,
            label = stringResource(R.string.label_notes),
            placeholder = stringResource(R.string.editor_notes_placeholder),
            minLines = NOTES_MIN_LINES,
        )
        if (state.saveFailed) Text(stringResource(R.string.editor_save_failed), color = colors.danger, style = GtTheme.typography.bodySmall)
        GlassButton(
            text = stringResource(R.string.action_save),
            onClick = onSave,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, error: String? = null, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        Text(title, color = GtTheme.colors.textSecondary, style = GtTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) { chips() }
        if (error != null) Text(error, color = GtTheme.colors.danger, style = GtTheme.typography.labelSmall)
    }
}

@Preview
@Composable
private fun ExerciseEditorScreenPreview() {
    GymTrackerTheme {
        GlassScaffold {
            ExerciseEditorScreen(
                state = ExerciseEditorUiState(
                    isNew = true,
                    loading = false,
                    draft = ExerciseDraft(equipment = Equipment.Barbell, primaryMuscles = setOf(Muscle.Shoulders)),
                    showErrors = true,
                ),
                onBack = {}, onNameChange = {}, onEquipmentChange = {}, onTogglePrimary = {}, onToggleSecondary = {}, onNotesChange = {}, onSave = {},
            )
        }
    }
}
