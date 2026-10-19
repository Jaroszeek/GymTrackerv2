package com.jaroszeek.gymtracker.feature.exercises

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.AuroraBackground
import com.jaroszeek.gymtracker.domain.exercise.Exercise

/**
 * The exercise list in picker mode, drawn over the current screen (routine editor, workout).
 * Place it after the screen's own back handlers, so back closes the picker first.
 */
@Composable
fun ExercisePicker(viewModel: ExercisesViewModel, onPick: (Exercise) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.fillMaxSize())
        ExercisesScreen(
            state = state,
            query = viewModel.query,
            onQueryChange = viewModel::onQueryChange,
            onToggleBodyPart = viewModel::onToggleBodyPart,
            onToggleEquipment = viewModel::onToggleEquipment,
            onClearFilters = viewModel::onClearFilters,
            onRetry = viewModel::onRetry,
            onOpen = { id -> (state as? ExercisesUiState.Content)?.exercises?.firstOrNull { it.id == id }?.let(onPick) },
            onAdd = null,
            pickerTitle = stringResource(R.string.picker_title),
            onBack = onDismiss,
        )
    }
}
