package com.jaroszeek.gymtracker.feature.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.designsystem.component.GlassMiniBar
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The running workout for the mini bar above the navbar. */
@HiltViewModel
class WorkoutBarViewModel @Inject constructor(manager: ActiveWorkoutManager) : ViewModel() {
    val workout: StateFlow<ActiveWorkout?> = manager.workout
}

/** Name, session clock (or the rest countdown) and set progress; leads back to the workout. */
@Composable
fun WorkoutMiniBar(workout: ActiveWorkout, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val rest = workout.rest
    GlassMiniBar(
        title = workout.name,
        subtitle = stringResource(
            R.string.workout_meta,
            if (rest != null) stringResource(R.string.rest_chip, countdownLabel(rest.remainingMillis(now))) else durationLabel(now - workout.startedAt),
            stringResource(R.string.workout_progress, workout.doneSets, workout.totalSets),
        ),
        progress = if (workout.totalSets == 0) 0f else workout.doneSets.toFloat() / workout.totalSets,
        onClick = onClick,
        onClickLabel = stringResource(R.string.workout_open),
        modifier = modifier,
    )
}

@Preview
@Composable
private fun WorkoutMiniBarPreview() {
    GymTrackerTheme {
        GlassScaffold(chrome = { WorkoutMiniBar(ActiveWorkout(1, "Push", System.currentTimeMillis(), 0), onClick = {}) }) {}
    }
}
