package com.jaroszeek.gymtracker.feature.diagnostics

import android.app.NotificationManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.core.di.DefaultDispatcher
import com.jaroszeek.gymtracker.core.notifications.LiveUpdateChecks
import com.jaroszeek.gymtracker.core.notifications.LiveUpdateStatus
import com.jaroszeek.gymtracker.core.notifications.SystemSettings
import com.jaroszeek.gymtracker.core.notifications.openSettings
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LiveUpdateDiagnosticsViewModel @Inject constructor(
    private val checks: LiveUpdateChecks,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow<LiveUpdateStatus?>(null)

    /** Null while loading. */
    val state: StateFlow<LiveUpdateStatus?> = _state.asStateFlow()

    /** Checks again, e.g. after coming back from the system settings. */
    fun onRefresh() {
        viewModelScope.launch { _state.value = withContext(dispatcher) { checks.status() } }
    }
}

@Composable
fun LiveUpdateDiagnosticsRoute(viewModel: LiveUpdateDiagnosticsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onRefresh()
        onPauseOrDispose {}
    }
    LiveUpdateDiagnosticsScreen(state, onBack, onRefresh = viewModel::onRefresh, onOpen = context::openSettings)
}

private enum class Verdict { Ok, Problem, Info }

private data class Check(val label: Int, val value: String, val verdict: Verdict)

@Composable
fun LiveUpdateDiagnosticsScreen(
    status: LiveUpdateStatus?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (SystemSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GtTheme.colors
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = GtSpacing.xl,
            end = GtSpacing.xl,
            top = insets.calculateTopPadding() + GtSpacing.s,
            bottom = navBarContentPadding().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.l),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                GlassIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClick = onBack)
                Text(stringResource(R.string.diagnostics_title), style = GtTheme.typography.headline)
            }
        }
        item(key = "intro") {
            Text(stringResource(R.string.diagnostics_intro), color = colors.textSecondary, style = GtTheme.typography.body)
        }
        if (status != null) {
            item(key = "checks") {
                GlassCard(Modifier.fillMaxWidth()) {
                    checks(status).forEach { CheckRow(it) }
                }
            }
            if (status.samsung) {
                item(key = "samsung") {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.diagnostics_samsung_title), style = GtTheme.typography.title)
                        Text(stringResource(R.string.diagnostics_samsung_text), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
                    }
                }
            }
        }
        item(key = "settings") {
            Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                listOf(
                    SystemSettings.Notifications to R.string.diagnostics_open_notifications,
                    SystemSettings.LiveUpdates to R.string.diagnostics_open_live_updates,
                    SystemSettings.Battery to R.string.diagnostics_open_battery,
                    SystemSettings.ExactAlarms to R.string.diagnostics_open_exact_alarms,
                    SystemSettings.Developer to R.string.diagnostics_open_developer,
                ).forEach { (screen, label) ->
                    GlassButton(stringResource(label), onClick = { onOpen(screen) }, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
                }
                GlassButton(stringResource(R.string.diagnostics_refresh), onClick = onRefresh, style = GlassButtonStyle.Ghost, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CheckRow(check: Check) {
    val colors = GtTheme.colors
    val color: Color = when (check.verdict) {
        Verdict.Ok -> colors.success
        Verdict.Problem -> colors.danger
        Verdict.Info -> colors.textSecondary
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        Text(stringResource(check.label), Modifier.weight(1f), style = GtTheme.typography.label)
        Text(check.value, Modifier.weight(1f), color = color, style = GtTheme.typography.label)
    }
}

@Composable
private fun checks(status: LiveUpdateStatus): List<Check> {
    val yes = stringResource(R.string.diagnostics_yes)
    val no = stringResource(R.string.diagnostics_no)
    val notAvailable = stringResource(R.string.diagnostics_not_available)
    fun flag(label: Int, value: Boolean?, missing: String = notAvailable) = when (value) {
        true -> Check(label, yes, Verdict.Ok)
        false -> Check(label, no, Verdict.Problem)
        null -> Check(label, missing, Verdict.Info)
    }
    return listOf(
        Check(R.string.diagnostics_android, status.android, Verdict.Info),
        Check(R.string.diagnostics_device, status.device, Verdict.Info),
        Check(
            R.string.diagnostics_notifications,
            stringResource(if (status.notificationsEnabled) R.string.diagnostics_enabled else R.string.diagnostics_disabled),
            if (status.notificationsEnabled) Verdict.Ok else Verdict.Problem,
        ),
        channel(status.channelImportance),
        flag(R.string.diagnostics_can_post_promoted, status.canPostPromoted),
        flag(R.string.diagnostics_promotable, status.promotable),
        flag(R.string.diagnostics_promoted, status.promoted, if (status.canPostPromoted == null) notAvailable else stringResource(R.string.diagnostics_no_workout)),
        Check(
            R.string.diagnostics_exact_alarms,
            stringResource(if (status.exactAlarms) R.string.diagnostics_allowed else R.string.diagnostics_exact_alarms_missing),
            if (status.exactAlarms) Verdict.Ok else Verdict.Problem,
        ),
        when {
            status.backgroundRestricted -> Check(R.string.diagnostics_battery, stringResource(R.string.diagnostics_battery_restricted), Verdict.Problem)
            status.ignoringBatteryOptimizations -> Check(R.string.diagnostics_battery, stringResource(R.string.diagnostics_battery_unrestricted), Verdict.Ok)
            else -> Check(R.string.diagnostics_battery, stringResource(R.string.diagnostics_battery_optimized), Verdict.Problem)
        },
    )
}

@Composable
private fun channel(importance: Int?): Check = when (importance) {
    null -> Check(R.string.diagnostics_channel, stringResource(R.string.diagnostics_channel_missing), Verdict.Info)
    NotificationManager.IMPORTANCE_NONE -> Check(R.string.diagnostics_channel, stringResource(R.string.diagnostics_disabled), Verdict.Problem)
    NotificationManager.IMPORTANCE_MIN -> Check(R.string.diagnostics_channel, stringResource(R.string.diagnostics_channel_min), Verdict.Problem)
    else -> Check(R.string.diagnostics_channel, stringResource(R.string.diagnostics_enabled), Verdict.Ok)
}

@Preview
@Composable
private fun LiveUpdateDiagnosticsPreview() {
    GymTrackerTheme {
        GlassScaffold {
            LiveUpdateDiagnosticsScreen(
                status = LiveUpdateStatus(
                    android = "16 (36.1)",
                    device = "samsung SM-S938B",
                    samsung = true,
                    notificationsEnabled = true,
                    channelImportance = NotificationManager.IMPORTANCE_LOW,
                    canPostPromoted = true,
                    promotable = true,
                    promoted = false,
                    exactAlarms = false,
                    ignoringBatteryOptimizations = false,
                    backgroundRestricted = false,
                ),
                onBack = {},
                onRefresh = {},
                onOpen = {},
            )
        }
    }
}
