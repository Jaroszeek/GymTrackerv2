package com.jaroszeek.gymtracker.feature.exercises

import android.net.Uri
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.BadgeTone
import com.jaroszeek.gymtracker.designsystem.component.GlassBadge
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassDialog
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.exercise.ExerciseCategory
import com.jaroszeek.gymtracker.domain.exercise.ExerciseSource
import com.jaroszeek.gymtracker.domain.exercise.Level
import com.jaroszeek.gymtracker.domain.exercise.Mechanic
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import com.jaroszeek.gymtracker.navigation.sharedElement
import kotlinx.coroutines.delay

private const val PHOTO_ASPECT_RATIO = 3f / 2f
private const val PHOTO_SWITCH_MILLIS = 1_500L
private const val YOUTUBE_SEARCH = "https://www.youtube.com/results?search_query="

@Composable
fun ExerciseDetailRoute(viewModel: ExerciseDetailViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == ExerciseDetailUiState.Deleted) onBack() }
    ExerciseDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onDeleteRequest = viewModel::onDeleteRequest,
        onDeleteDismiss = viewModel::onDeleteDismiss,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        onInUseDismiss = viewModel::onInUseDismiss,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onInUseDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercise = (state as? ExerciseDetailUiState.Content)?.exercise
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(navBarContentPadding())
                .padding(horizontal = GtSpacing.xl, vertical = GtSpacing.s),
            verticalArrangement = Arrangement.spacedBy(GtSpacing.l),
        ) {
            // Room for the floating back button
            Box(Modifier.padding(top = GtSpacing.huge))
            when (state) {
                is ExerciseDetailUiState.Content -> ExerciseDetails(state.exercise)
                ExerciseDetailUiState.NotFound, ExerciseDetailUiState.Deleted -> Message(stringResource(R.string.detail_not_found))
                ExerciseDetailUiState.Error -> Message(stringResource(R.string.exercises_error))
                ExerciseDetailUiState.Loading -> Unit
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = GtSpacing.m, vertical = GtSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(GtSpacing.s),
        ) {
            GlassIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClick = onBack)
            Box(Modifier.weight(1f))
            if (exercise?.isCustom == true) {
                GlassIconButton(R.drawable.ic_edit, stringResource(R.string.action_edit), onClick = { onEdit(exercise.id) })
                GlassIconButton(R.drawable.ic_delete, stringResource(R.string.action_delete), onClick = onDeleteRequest)
            }
        }
    }
    if (exercise != null) {
        GlassDialog(
            visible = state.confirmDelete,
            onDismissRequest = onDeleteDismiss,
            title = stringResource(R.string.delete_exercise_title),
            text = stringResource(R.string.delete_exercise_text, exercise.name),
            confirmButton = { GlassButton(stringResource(R.string.action_delete), onClick = onDeleteConfirm) },
            dismissButton = { GlassButton(stringResource(R.string.action_cancel), onClick = onDeleteDismiss, style = GlassButtonStyle.Ghost) },
        )
        GlassDialog(
            visible = state.inUse,
            onDismissRequest = onInUseDismiss,
            title = stringResource(R.string.delete_exercise_in_use_title),
            text = stringResource(R.string.delete_exercise_in_use_text),
            confirmButton = { GlassButton(stringResource(R.string.action_ok), onClick = onInUseDismiss) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseDetails(exercise: Exercise) {
    val colors = GtTheme.colors
    val uriHandler = LocalUriHandler.current
    val videoQuery = stringResource(R.string.detail_video_query, exercise.name)
    ExercisePhotos(exercise)
    Text(exercise.name, style = GtTheme.typography.headline)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s), verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        exercise.equipment?.let { GlassBadge(stringResource(it.label)) }
        exercise.level?.let { GlassBadge(stringResource(it.label)) }
        exercise.mechanic?.let { GlassBadge(stringResource(it.label)) }
        if (exercise.category != ExerciseCategory.Strength) GlassBadge(stringResource(exercise.category.label))
        if (exercise.isCustom) GlassBadge(stringResource(R.string.badge_custom), tone = BadgeTone.Accent)
    }
    GlassButton(
        text = stringResource(R.string.detail_video),
        icon = R.drawable.ic_smart_display,
        style = GlassButtonStyle.Secondary,
        onClick = {
            try {
                uriHandler.openUri(YOUTUBE_SEARCH + Uri.encode(videoQuery))
            } catch (e: IllegalArgumentException) {
                Log.w("Exercises", "No app to open the video search", e)
            }
        },
    )
    MuscleSection(stringResource(R.string.detail_primary), exercise.primaryMuscles, BadgeTone.Accent)
    if (exercise.secondaryMuscles.isNotEmpty()) MuscleSection(stringResource(R.string.detail_secondary), exercise.secondaryMuscles, BadgeTone.Neutral)
    if (exercise.instructions.isNotEmpty()) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.detail_instructions), style = GtTheme.typography.title)
            exercise.instructions.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                    Text("${index + 1}", color = colors.accentText, style = GtTheme.typography.label)
                    Text(step, color = colors.textSecondary, style = GtTheme.typography.bodySmall)
                }
            }
        }
    }
    // Filled in Etap 6 (charts and records)
    PlaceholderCard(stringResource(R.string.detail_chart_title), stringResource(R.string.detail_chart_empty))
    PlaceholderCard(stringResource(R.string.detail_records_title), stringResource(R.string.detail_records_empty))
    Text(
        stringResource(R.string.detail_source, stringResource(exercise.source.label)),
        color = colors.textTertiary,
        style = GtTheme.typography.labelSmall,
    )
}

/** Start and end position photos alternate, which reads like a slow animation of the movement. */
@Composable
private fun ExercisePhotos(exercise: Exercise) {
    var index by remember { mutableIntStateOf(0) }
    val animate = exercise.images.size > 1 && !GtTheme.reducedMotion
    LaunchedEffect(animate) {
        while (animate) {
            delay(PHOTO_SWITCH_MILLIS)
            index = (index + 1) % exercise.images.size
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(PHOTO_ASPECT_RATIO)
            .sharedElement(exerciseImageKey(exercise.id)),
    ) {
        Crossfade(targetState = exercise.images.getOrNull(index), label = "exercisePhoto") { url ->
            ExerciseImage(
                url = url,
                contentDescription = stringResource(R.string.detail_photo, exercise.name),
                shape = GtShapes.Card,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleSection(title: String, muscles: Set<Muscle>, tone: BadgeTone) {
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        Text(title, color = GtTheme.colors.textSecondary, style = GtTheme.typography.label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s), verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            muscles.forEach { GlassBadge(stringResource(it.label), tone = tone) }
        }
    }
}

@Composable
private fun PlaceholderCard(title: String, text: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, style = GtTheme.typography.title)
        Text(text, color = GtTheme.colors.textSecondary, style = GtTheme.typography.bodySmall)
    }
}

@Composable
private fun Message(text: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(text, color = GtTheme.colors.textSecondary, style = GtTheme.typography.body)
    }
}

@Preview
@Composable
private fun ExerciseDetailScreenPreview() {
    val exercise = Exercise(
        id = 1, name = "Bayesian Cable Curl", aliases = emptyList(), category = ExerciseCategory.Strength,
        equipment = Equipment.Cable, level = Level.Intermediate, mechanic = Mechanic.Isolation,
        primaryMuscles = setOf(Muscle.Biceps), secondaryMuscles = setOf(Muscle.Forearms),
        instructions = listOf("Face away from the stack.", "Curl without moving the elbow."), images = emptyList(),
        source = ExerciseSource.Nippard,
    )
    GymTrackerTheme {
        GlassScaffold {
            ExerciseDetailScreen(ExerciseDetailUiState.Content(exercise), {}, {}, {}, {}, {}, {})
        }
    }
}
