package com.jaroszeek.gymtracker.feature.health

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.data.health.HealthRepository
import com.jaroszeek.gymtracker.data.health.observeMaxHeartRate
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.health.HealthSession
import com.jaroszeek.gymtracker.domain.health.HeartRateSample
import com.jaroszeek.gymtracker.feature.progress.fullDate
import com.jaroszeek.gymtracker.feature.progress.timeLabel
import com.jaroszeek.gymtracker.feature.workout.durationLabel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface ImportedSessionUiState {
    data object Loading : ImportedSessionUiState

    /** Deleted in Health Connect, or everything imported was removed. */
    data object NotFound : ImportedSessionUiState

    data object Error : ImportedSessionUiState

    data class Content(val session: HealthSession, val heartRate: List<HeartRateSample>, val maxHeartRate: Int?) : ImportedSessionUiState
}

@HiltViewModel(assistedFactory = ImportedSessionViewModel.Factory::class)
class ImportedSessionViewModel @AssistedInject constructor(
    @Assisted id: String,
    repository: HealthRepository,
    preferences: AppPreferences,
    clock: Clock,
) : ViewModel() {

    val state: StateFlow<ImportedSessionUiState> =
        combine(repository.observeSession(id), repository.observeHeartRate(id), preferences.observeMaxHeartRate(clock)) { session, heartRate, maxHeartRate ->
            if (session == null) ImportedSessionUiState.NotFound else ImportedSessionUiState.Content(session, heartRate, maxHeartRate)
        }
            .catch { e ->
                Log.e(TAG, "Loading session $id failed", e)
                emit(ImportedSessionUiState.Error)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ImportedSessionUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(id: String): ImportedSessionViewModel
    }

    private companion object {
        const val TAG = "HealthConnect"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun ImportedSessionRoute(viewModel: ImportedSessionViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ImportedSessionScreen(state, onBack)
}

/** A session from history that another app wrote; read-only, it is changed in that app. */
@Composable
fun ImportedSessionScreen(state: ImportedSessionUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
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
                is ImportedSessionUiState.Content -> {
                    val session = state.session
                    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
                        Text(session.label(), style = GtTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        session.subtitle()?.let { Text(it, style = GtTheme.typography.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        Text(
                            stringResource(
                                R.string.routine_meta,
                                fullDate(session.startTime),
                                stringResource(R.string.workout_detail_time, timeLabel(session.startTime), timeLabel(session.endTime)),
                            ),
                            color = colors.textSecondary,
                            style = GtTheme.typography.label,
                        )
                        Text(
                            stringResource(R.string.routine_meta, durationLabel(session.durationMillis), sourceLabel(session.origin)),
                            color = colors.textSecondary,
                            style = GtTheme.typography.label,
                        )
                    }
                    HeartRateCard(session, state.heartRate, state.maxHeartRate)
                    Text(stringResource(R.string.session_imported_text), color = colors.textTertiary, style = GtTheme.typography.bodySmall)
                }
                ImportedSessionUiState.NotFound -> Message(stringResource(R.string.session_not_found))
                ImportedSessionUiState.Error -> Message(stringResource(R.string.progress_error))
                ImportedSessionUiState.Loading -> Unit
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = GtSpacing.m, vertical = GtSpacing.xs),
        ) {
            GlassIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClick = onBack)
        }
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
private fun ImportedSessionScreenPreview() {
    val start = 1_760_000_000_000
    val session = HealthSession("1", "com.sec.android.app.shealth", 56, "Bieg poranny", start, start + 2_700_000, activeKcal = 380.0, totalKcal = 455.0)
    val samples = List(180) { HeartRateSample(start + it * 15_000L, 120 + (it % 30)) }
    GymTrackerTheme {
        GlassScaffold { ImportedSessionScreen(ImportedSessionUiState.Content(session, samples, 188), onBack = {}) }
    }
}
