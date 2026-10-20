package com.jaroszeek.gymtracker.feature.health

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.core.health.HealthConnect
import com.jaroszeek.gymtracker.core.health.HealthFeature
import com.jaroszeek.gymtracker.data.health.HealthRepository
import com.jaroszeek.gymtracker.designsystem.component.BadgeTone
import com.jaroszeek.gymtracker.designsystem.component.GlassBadge
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.health.Readiness
import com.jaroszeek.gymtracker.domain.health.ReadinessLevel
import com.jaroszeek.gymtracker.domain.health.ReadinessMetric
import com.jaroszeek.gymtracker.domain.health.readiness
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReadinessUiState(
    val loading: Boolean = true,
    /** Recovery is connected, or was and its data is still here. */
    val connected: Boolean = false,
    val readiness: Readiness? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReadinessViewModel @Inject constructor(
    private val health: HealthConnect,
    repository: HealthRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val today = MutableStateFlow(today())
    private val permitted = MutableStateFlow<Boolean?>(null)

    val state: StateFlow<ReadinessUiState> = today.flatMapLatest { day ->
        combine(permitted, repository.observeRecovery(day, zone)) { permitted, days ->
            ReadinessUiState(loading = permitted == null, connected = permitted == true || days.isNotEmpty(), readiness = readiness(day, days))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReadinessUiState())

    /** On every return to the screen: a new day, or permissions changed in Health Connect. */
    fun onRefresh() {
        today.value = today()
        viewModelScope.launch {
            permitted.value = withContext(io) {
                try {
                    HealthFeature.Recovery.permissions.any { it in health.granted() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Reading permissions failed", e)
                    false
                }
            }
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(zone))

    private companion object {
        const val TAG = "HealthConnect"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun ReadinessRoute(onConnect: () -> Unit, viewModel: ReadinessViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onRefresh()
        onPauseOrDispose {}
    }
    if (!state.loading) ReadinessCard(state, onConnect)
}

/** Today's sleep, resting heart rate and HRV against the last 7 days; a rough guide, said so on the card (A6). */
@Composable
fun ReadinessCard(state: ReadinessUiState, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    val readiness = state.readiness
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            Icon(painterResource(R.drawable.ic_favorite), contentDescription = null, Modifier.size(GtSizes.IconSmall), tint = colors.accentText)
            Text(stringResource(R.string.readiness_title), Modifier.weight(1f), style = GtTheme.typography.title)
            readiness?.level?.let { GlassBadge(stringResource(it.label()), tone = if (it == ReadinessLevel.Good) BadgeTone.Accent else BadgeTone.Neutral) }
        }
        when {
            !state.connected -> {
                Text(stringResource(R.string.readiness_connect), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
                GlassButton(stringResource(R.string.health_connect), onClick = onConnect, style = GlassButtonStyle.Secondary, icon = R.drawable.ic_link)
            }
            readiness?.level == null -> Text(stringResource(R.string.readiness_collecting), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
            else -> {
                MetricRow(R.string.readiness_sleep, readiness.sleep) { sleepLabel(it) }
                MetricRow(R.string.readiness_resting_hr, readiness.restingHeartRate) { bpmLabel(it.roundToInt()) }
                MetricRow(R.string.readiness_hrv, readiness.hrv) { stringResource(R.string.value_ms, it.roundToInt()) }
            }
        }
        Text(stringResource(R.string.readiness_note), Modifier.padding(top = GtSpacing.xs), color = colors.textTertiary, style = GtTheme.typography.labelSmall)
    }
}

@Composable
private fun MetricRow(label: Int, metric: ReadinessMetric?, format: @Composable (Double) -> String) {
    val colors = GtTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        Text(stringResource(label), Modifier.weight(1f), style = GtTheme.typography.label)
        if (metric == null) {
            Text(stringResource(R.string.readiness_no_data), color = colors.textTertiary, style = GtTheme.typography.label)
        } else {
            Text(format(metric.today), style = GtTheme.typography.label)
            metric.baseline?.let {
                Text(stringResource(R.string.readiness_baseline, format(it)), color = colors.textSecondary, style = GtTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun sleepLabel(minutes: Double): String {
    val total = minutes.roundToInt()
    return stringResource(R.string.value_sleep, total / MINUTES_PER_HOUR, total % MINUTES_PER_HOUR)
}

private const val MINUTES_PER_HOUR = 60

private fun ReadinessLevel.label(): Int = when (this) {
    ReadinessLevel.Good -> R.string.readiness_good
    ReadinessLevel.Moderate -> R.string.readiness_moderate
    ReadinessLevel.Low -> R.string.readiness_low
}

@Preview
@Composable
private fun ReadinessCardPreview() {
    val readiness = Readiness(
        level = ReadinessLevel.Moderate,
        sleep = ReadinessMetric(392.0, 445.0, 1),
        restingHeartRate = ReadinessMetric(55.0, 53.0, 2),
        hrv = null,
    )
    GymTrackerTheme {
        GlassScaffold {
            ReadinessCard(ReadinessUiState(loading = false, connected = true, readiness = readiness), onConnect = {}, Modifier.padding(GtSpacing.xl))
        }
    }
}
