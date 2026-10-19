package com.jaroszeek.gymtracker.feature.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.BadgeTone
import com.jaroszeek.gymtracker.designsystem.component.GlassBadge
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassChip
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.GlassSearchField
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.exercise.BodyPart
import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.exercise.EquipmentGroup
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.exercise.ExerciseCategory
import com.jaroszeek.gymtracker.domain.exercise.ExerciseFilter
import com.jaroszeek.gymtracker.domain.exercise.ExerciseSource
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import com.jaroszeek.gymtracker.navigation.sharedElement

@Composable
fun ExercisesRoute(onOpen: (Long) -> Unit, onAdd: () -> Unit, viewModel: ExercisesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExercisesScreen(
        state = state,
        query = viewModel.query,
        onQueryChange = viewModel::onQueryChange,
        onToggleBodyPart = viewModel::onToggleBodyPart,
        onToggleEquipment = viewModel::onToggleEquipment,
        onClearFilters = viewModel::onClearFilters,
        onRetry = viewModel::onRetry,
        onOpen = onOpen,
        onAdd = onAdd,
    )
}

@Composable
fun ExercisesScreen(
    state: ExercisesUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleBodyPart: (BodyPart) -> Unit,
    onToggleEquipment: (EquipmentGroup) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Picker mode (routine editor): a back button and the given title instead of the large header. */
    pickerTitle: String? = null,
    onBack: (() -> Unit)? = null,
) {
    val filter = (state as? ExercisesUiState.Content)?.filter ?: ExerciseFilter()
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding() + GtSpacing.xxl,
                bottom = navBarContentPadding().calculateBottomPadding() + GtSizes.MinTouch,
            ),
            verticalArrangement = Arrangement.spacedBy(GtSpacing.s),
        ) {
            item(key = "header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GtSpacing.xl),
                    verticalAlignment = if (pickerTitle != null) Alignment.CenterVertically else Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(GtSpacing.m),
                ) {
                    if (pickerTitle != null) {
                        if (onBack != null) GlassIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClick = onBack)
                        Text(pickerTitle, Modifier.weight(1f), style = GtTheme.typography.headline)
                    } else {
                        Text(stringResource(R.string.nav_exercises), Modifier.weight(1f), style = GtTheme.typography.display)
                    }
                    if (state is ExercisesUiState.Content) {
                        Text(
                            pluralStringResource(R.plurals.exercises_count, state.exercises.size, state.exercises.size),
                            color = GtTheme.colors.textSecondary,
                            style = GtTheme.typography.labelSmall,
                        )
                    }
                }
            }
            item(key = "search") {
                GlassSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.exercises_search),
                    modifier = Modifier.padding(horizontal = GtSpacing.xl, vertical = GtSpacing.s),
                )
            }
            item(key = "bodyParts") {
                ChipRow(BodyPart.entries, filter.bodyParts, { it.label }, onToggleBodyPart)
            }
            item(key = "equipment") {
                ChipRow(EquipmentGroup.entries, filter.equipment, { it.label }, onToggleEquipment)
            }
            when (state) {
                ExercisesUiState.Loading -> item(key = "loading") { LoadingRows() }
                ExercisesUiState.Error -> item(key = "error") {
                    MessageCard(stringResource(R.string.exercises_error), stringResource(R.string.action_retry), onRetry)
                }
                is ExercisesUiState.Content -> if (state.exercises.isEmpty()) {
                    item(key = "empty") {
                        MessageCard(
                            text = stringResource(R.string.exercises_empty_text),
                            action = stringResource(R.string.exercises_clear_filters),
                            onAction = onClearFilters,
                            title = stringResource(R.string.exercises_empty_title),
                        )
                    }
                } else {
                    items(state.exercises, key = { it.id }, contentType = { "exercise" }) { exercise ->
                        ExerciseRow(exercise, onClick = { onOpen(exercise.id) }, Modifier.padding(horizontal = GtSpacing.xl))
                    }
                }
            }
        }
        if (onAdd != null) {
            GlassIconButton(
                icon = R.drawable.ic_add,
                contentDescription = stringResource(R.string.exercises_add),
                onClick = onAdd,
                style = GlassButtonStyle.Primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(navBarContentPadding())
                    .padding(end = GtSpacing.xl),
            )
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: Set<T>, label: (T) -> Int, onToggle: (T) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = GtSpacing.xl),
        horizontalArrangement = Arrangement.spacedBy(GtSpacing.s),
    ) {
        items(options) { option ->
            GlassChip(stringResource(label(option)), selected = option in selected, onClick = { onToggle(option) })
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(GtShapes.Medium)
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(GtSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GtSpacing.m),
    ) {
        ExerciseImage(
            url = exercise.images.firstOrNull(),
            contentDescription = null,
            shape = GtShapes.Small,
            modifier = Modifier
                .size(GtSizes.Thumbnail)
                .sharedElement(exerciseImageKey(exercise.id)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GtSpacing.xxs)) {
            Text(exercise.name, style = GtTheme.typography.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            exercise.primaryMuscles.firstOrNull()?.let { muscle ->
                Text(stringResource(muscle.label), color = colors.textSecondary, style = GtTheme.typography.bodySmall, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
            exercise.equipment?.let { GlassBadge(stringResource(it.label)) }
            if (exercise.isCustom) GlassBadge(stringResource(R.string.badge_custom), tone = BadgeTone.Accent)
        }
    }
}

@Composable
private fun LoadingRows() {
    Column(Modifier.padding(horizontal = GtSpacing.xl), verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        repeat(LOADING_ROWS) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(GtSizes.Thumbnail + GtSpacing.l)
                    .clip(GtShapes.Medium)
                    .background(GtTheme.colors.surface),
            )
        }
    }
}

@Composable
private fun MessageCard(text: String, action: String, onAction: () -> Unit, title: String? = null) {
    GlassCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = GtSpacing.xl, vertical = GtSpacing.s),
    ) {
        if (title != null) Text(title, style = GtTheme.typography.title)
        Text(text, color = GtTheme.colors.textSecondary, style = GtTheme.typography.body)
        GlassButton(action, onClick = onAction, style = GlassButtonStyle.Secondary, modifier = Modifier.padding(top = GtSpacing.s))
    }
}

internal fun exerciseImageKey(id: Long) = "exercise-image-$id"

private const val LOADING_ROWS = 6

@Preview
@Composable
private fun ExercisesScreenPreview() {
    val sample = listOf(
        Exercise(
            id = 1, name = "Barbell Bench Press - Medium Grip", aliases = emptyList(), category = ExerciseCategory.Strength,
            equipment = Equipment.Barbell, level = null, mechanic = null, primaryMuscles = setOf(Muscle.Chest),
            secondaryMuscles = emptySet(), instructions = emptyList(), images = emptyList(), source = ExerciseSource.Builtin,
        ),
        Exercise(
            id = 2, name = "Landmine Press", aliases = emptyList(), category = ExerciseCategory.Strength,
            equipment = Equipment.Barbell, level = null, mechanic = null, primaryMuscles = setOf(Muscle.Shoulders),
            secondaryMuscles = emptySet(), instructions = emptyList(), images = emptyList(), source = ExerciseSource.Custom,
        ),
    )
    GymTrackerTheme {
        GlassScaffold {
            ExercisesScreen(
                state = ExercisesUiState.Content(sample, ExerciseFilter(bodyParts = setOf(BodyPart.Chest))),
                query = "",
                onQueryChange = {}, onToggleBodyPart = {}, onToggleEquipment = {}, onClearFilters = {}, onRetry = {}, onOpen = {}, onAdd = {},
            )
        }
    }
}
