package com.jaroszeek.gymtracker.feature.exercises

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassChip
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.GlassTextField
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.exercise.BodyPart
import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.exercise.ExerciseDraft
import com.jaroszeek.gymtracker.domain.exercise.Level
import com.jaroszeek.gymtracker.domain.exercise.Mechanic
import com.jaroszeek.gymtracker.domain.exercise.Muscle

private const val NOTES_MIN_LINES = 4

@Composable
fun ExerciseEditorRoute(viewModel: ExerciseEditorViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    // The system photo picker needs no permission
    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia(ExerciseDraft.MAX_PHOTOS), viewModel::onPhotosPicked)
    ExerciseEditorScreen(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onAddPhotos = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
        onRemovePhoto = viewModel::onRemovePhoto,
        onEquipmentChange = viewModel::onEquipmentChange,
        onMechanicChange = viewModel::onMechanicChange,
        onLevelChange = viewModel::onLevelChange,
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
    onAddPhotos: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onEquipmentChange: (Equipment?) -> Unit,
    onMechanicChange: (Mechanic?) -> Unit,
    onLevelChange: (Level?) -> Unit,
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
        Photos(state, onAddPhotos, onRemovePhoto)
        Section(stringResource(R.string.label_equipment)) {
            GlassChip(
                stringResource(R.string.editor_no_equipment),
                selected = draft.equipment == null,
                onClick = { onEquipmentChange(null) },
                role = Role.RadioButton,
            )
            Equipment.entries.forEach { equipment ->
                GlassChip(
                    stringResource(equipment.label),
                    selected = draft.equipment == equipment,
                    onClick = { onEquipmentChange(equipment) },
                    role = Role.RadioButton,
                )
            }
        }
        ChoiceSection(stringResource(R.string.label_mechanic), Mechanic.entries, draft.mechanic, { stringResource(it.label) }, onMechanicChange)
        ChoiceSection(stringResource(R.string.label_level), Level.entries, draft.level, { stringResource(it.label) }, onLevelChange)
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
            enabled = !state.saving && !state.importingPhotos,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Thumbnails in the order they play, each with its remove button under it. */
@Composable
private fun Photos(state: ExerciseEditorUiState, onAddPhotos: () -> Unit, onRemovePhoto: (Int) -> Unit) {
    val colors = GtTheme.colors
    val images = state.draft.images
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        Text(stringResource(R.string.label_photos), color = colors.textSecondary, style = GtTheme.typography.labelSmall)
        if (images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                images.forEachIndexed { index, url ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ExerciseImage(url = url, contentDescription = null, shape = GtShapes.Small, modifier = Modifier.size(GtSizes.Thumbnail))
                        GlassIconButton(
                            R.drawable.ic_close,
                            stringResource(R.string.editor_photo_remove, index + 1),
                            onClick = { onRemovePhoto(index) },
                            style = GlassButtonStyle.Ghost,
                        )
                    }
                }
            }
        }
        if (images.size < ExerciseDraft.MAX_PHOTOS) {
            GlassButton(
                text = stringResource(R.string.editor_photos_add),
                onClick = onAddPhotos,
                style = GlassButtonStyle.Secondary,
                icon = R.drawable.ic_add,
                enabled = !state.importingPhotos,
            )
        }
        Text(stringResource(R.string.editor_photos_hint), color = colors.textTertiary, style = GtTheme.typography.labelSmall)
        if (state.photosFailed) Text(stringResource(R.string.editor_photos_failed), color = colors.danger, style = GtTheme.typography.labelSmall)
    }
}

/** One of [options] or none. */
@Composable
private fun <T> ChoiceSection(title: String, options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T?) -> Unit) {
    Section(title) {
        GlassChip(stringResource(R.string.editor_not_set), selected = selected == null, onClick = { onSelect(null) }, role = Role.RadioButton)
        options.forEach { option ->
            GlassChip(label(option), selected = selected == option, onClick = { onSelect(option) }, role = Role.RadioButton)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, error: String? = null, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        Text(title, color = GtTheme.colors.textSecondary, style = GtTheme.typography.labelSmall)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) { chips() }
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
                    draft = ExerciseDraft(equipment = Equipment.Barbell, mechanic = Mechanic.Compound, primaryMuscles = setOf(Muscle.Shoulders)),
                    showErrors = true,
                ),
                onBack = {}, onNameChange = {}, onAddPhotos = {}, onRemovePhoto = {}, onEquipmentChange = {}, onMechanicChange = {}, onLevelChange = {},
                onTogglePrimary = {}, onToggleSecondary = {}, onNotesChange = {}, onSave = {},
            )
        }
    }
}
