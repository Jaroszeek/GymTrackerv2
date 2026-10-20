package com.jaroszeek.gymtracker.feature.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.core.health.HealthAvailability
import com.jaroszeek.gymtracker.core.health.HealthConnect
import com.jaroszeek.gymtracker.core.health.HealthFeature
import com.jaroszeek.gymtracker.core.health.openHealthConnect
import com.jaroszeek.gymtracker.data.health.SyncProblem
import com.jaroszeek.gymtracker.data.health.SyncStatus
import com.jaroszeek.gymtracker.designsystem.component.BadgeTone
import com.jaroszeek.gymtracker.designsystem.component.GlassBadge
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassDialog
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassNumberField
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.GlassSwitch
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.health.ImportSummary
import com.jaroszeek.gymtracker.feature.progress.shortDate
import com.jaroszeek.gymtracker.feature.progress.timeLabel
import kotlin.math.roundToInt

@Composable
fun HealthConnectRoute(viewModel: HealthConnectViewModel, onBack: () -> Unit, onPrivacy: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = rememberLauncherForActivityResult(HealthConnect.permissionContract, viewModel::onPermissionsResult)
    LaunchedEffect(state.request) {
        val feature = state.request ?: return@LaunchedEffect
        viewModel.onRequestLaunched()
        permissions.launch(feature.permissions)
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onRefresh()
        onPauseOrDispose {}
    }
    HealthConnectScreen(
        state = state,
        onBack = onBack,
        onConnect = viewModel::onConnect,
        onRetry = viewModel::onRefresh,
        onSyncNow = viewModel::onSyncNow,
        onSyncOnOpen = viewModel::onSyncOnOpen,
        onBackgroundSync = viewModel::onBackgroundSync,
        onWriteWorkouts = viewModel::onWriteWorkouts,
        onWriteConfirm = viewModel::onWriteConfirm,
        onWriteDismiss = viewModel::onWriteDismiss,
        onAge = viewModel::onAge,
        onMaxHeartRate = viewModel::onMaxHeartRate,
        onManage = context::openHealthConnect,
        onPrivacy = onPrivacy,
        onDisconnectRequest = viewModel::onDisconnectRequest,
        onDisconnectDismiss = viewModel::onDisconnectDismiss,
        onDisconnectConfirm = viewModel::onDisconnectConfirm,
    )
}

/** Permissions per feature with what each one is for (A6), the sync, and the settings of A5.9. */
@Composable
fun HealthConnectScreen(
    state: HealthConnectUiState,
    onBack: () -> Unit,
    onConnect: (HealthFeature) -> Unit,
    onRetry: () -> Unit,
    onSyncNow: () -> Unit,
    onSyncOnOpen: (Boolean) -> Unit,
    onBackgroundSync: (Boolean) -> Unit,
    onWriteWorkouts: (Boolean) -> Unit,
    onWriteConfirm: () -> Unit,
    onWriteDismiss: () -> Unit,
    onAge: (Int?) -> Unit,
    onMaxHeartRate: (Int?) -> Unit,
    onManage: () -> Unit,
    onPrivacy: () -> Unit,
    onDisconnectRequest: () -> Unit,
    onDisconnectDismiss: () -> Unit,
    onDisconnectConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GtTheme.colors
    val access = state.access
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
                Text(stringResource(R.string.health_title), style = GtTheme.typography.headline)
            }
        }
        item(key = "intro") {
            Text(stringResource(R.string.health_intro), color = colors.textSecondary, style = GtTheme.typography.body)
        }
        when {
            state.loading -> Unit
            access.availability != HealthAvailability.Available -> item(key = "unavailable") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.health_unavailable), style = GtTheme.typography.body)
                }
            }
            access.error -> item(key = "error") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.health_error), style = GtTheme.typography.body)
                    GlassButton(stringResource(R.string.action_retry), onClick = onRetry, style = GlassButtonStyle.Secondary)
                }
            }
            // Connecting again before the permissions are gone would only lose them later
            access.revoked -> item(key = "revoked") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.health_revoked), style = GtTheme.typography.body)
                }
            }
            else -> {
                if (access.reads) {
                    item(key = "sync") { SyncCard(state, onSyncNow, onSyncOnOpen, onBackgroundSync) }
                }
                DATA_FEATURES.forEach { feature ->
                    item(key = feature.name) {
                        FeatureCard(feature, access, denied = state.denied == feature, onConnect = { onConnect(feature) })
                    }
                }
                if (access.reads && access.historySupported) {
                    item(key = "history") {
                        FeatureCard(HealthFeature.History, access, denied = state.denied == HealthFeature.History, onConnect = { onConnect(HealthFeature.History) })
                    }
                }
                item(key = "write") {
                    SwitchCard(
                        title = R.string.health_write,
                        text = R.string.health_write_text,
                        checked = state.writeWorkouts,
                        onCheckedChange = onWriteWorkouts,
                        denied = state.denied == HealthFeature.WriteWorkouts,
                    )
                }
                item(key = "zones") { ZonesCard(state, onAge, onMaxHeartRate) }
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                if (access.availability == HealthAvailability.Available) {
                    GlassButton(
                        stringResource(R.string.health_manage),
                        onClick = onManage,
                        style = GlassButtonStyle.Secondary,
                        icon = R.drawable.ic_open_in_new,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                GlassButton(stringResource(R.string.privacy_title), onClick = onPrivacy, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
                if (access.granted.isNotEmpty() || state.summary != ImportSummary()) {
                    GlassButton(stringResource(R.string.health_disconnect), onClick = onDisconnectRequest, style = GlassButtonStyle.Ghost, modifier = Modifier.fillMaxWidth())
                }
                if (state.disconnectFailed) {
                    Text(stringResource(R.string.health_disconnect_failed), color = colors.danger, style = GtTheme.typography.bodySmall)
                }
            }
        }
    }
    GlassDialog(
        visible = state.confirmWrite,
        onDismissRequest = onWriteDismiss,
        title = stringResource(R.string.health_write_confirm_title),
        text = stringResource(R.string.health_write_confirm_text),
        confirmButton = { GlassButton(stringResource(R.string.health_write_enable), onClick = onWriteConfirm) },
        dismissButton = { GlassButton(stringResource(R.string.action_cancel), onClick = onWriteDismiss, style = GlassButtonStyle.Ghost) },
    )
    GlassDialog(
        visible = state.confirmDisconnect,
        onDismissRequest = onDisconnectDismiss,
        title = stringResource(R.string.health_disconnect_title),
        text = stringResource(R.string.health_disconnect_text),
        confirmButton = { GlassButton(stringResource(R.string.health_disconnect_confirm), onClick = onDisconnectConfirm) },
        dismissButton = { GlassButton(stringResource(R.string.action_cancel), onClick = onDisconnectDismiss, style = GlassButtonStyle.Ghost) },
    )
}

@Composable
private fun SyncCard(state: HealthConnectUiState, onSyncNow: () -> Unit, onSyncOnOpen: (Boolean) -> Unit, onBackgroundSync: (Boolean) -> Unit) {
    val colors = GtTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.health_sync), style = GtTheme.typography.title)
        Text(syncLine(state.status, state.lastSync), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        when (state.status.problem.takeUnless { state.status.running }) {
            SyncProblem.RateLimited -> Text(stringResource(R.string.health_sync_rate_limited), style = GtTheme.typography.bodySmall)
            SyncProblem.Failed -> Text(stringResource(R.string.health_sync_failed), color = colors.danger, style = GtTheme.typography.bodySmall)
            null -> Unit
        }
        Text(importedLine(state.summary), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        GlassButton(
            stringResource(R.string.health_sync_now),
            onClick = onSyncNow,
            style = GlassButtonStyle.Secondary,
            icon = R.drawable.ic_sync,
            enabled = !state.status.running,
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(R.string.health_sync_on_open, null, state.syncOnOpen, onSyncOnOpen)
        if (state.access.backgroundSupported) {
            SwitchRow(R.string.health_sync_background, R.string.health_sync_background_text, state.backgroundSync, onBackgroundSync)
            if (state.denied == HealthFeature.Background) Denied()
        }
    }
}

@Composable
private fun syncLine(status: SyncStatus, lastSync: Long?): String = when {
    status.running -> stringResource(R.string.health_sync_running)
    lastSync == null -> stringResource(R.string.health_sync_never)
    else -> stringResource(R.string.health_sync_last, shortDate(lastSync), timeLabel(lastSync))
}

@Composable
private fun importedLine(summary: ImportSummary): String {
    val parts = listOf(
        R.plurals.health_sessions to summary.sessions,
        R.plurals.health_measurements to summary.measurements,
        R.plurals.health_nights to summary.nights,
        R.plurals.health_days to summary.days,
    ).filter { it.second > 0 }.map { (plural, count) -> pluralStringResource(plural, count, count) }
    return if (parts.isEmpty()) stringResource(R.string.health_imported_none) else stringResource(R.string.health_imported, parts.joinToString(" · "))
}

@StringRes
private fun HealthFeature.title(): Int = when (this) {
    HealthFeature.Workouts -> R.string.health_workouts
    HealthFeature.Body -> R.string.health_body
    HealthFeature.Activity -> R.string.health_activity
    HealthFeature.Recovery -> R.string.health_recovery
    HealthFeature.History -> R.string.health_history
    HealthFeature.Background -> R.string.health_sync_background
    HealthFeature.WriteWorkouts -> R.string.health_write
}

@StringRes
private fun HealthFeature.text(): Int = when (this) {
    HealthFeature.Workouts -> R.string.health_workouts_text
    HealthFeature.Body -> R.string.health_body_text
    HealthFeature.Activity -> R.string.health_activity_text
    HealthFeature.Recovery -> R.string.health_recovery_text
    HealthFeature.History -> R.string.health_history_text
    HealthFeature.Background -> R.string.health_sync_background_text
    HealthFeature.WriteWorkouts -> R.string.health_write_text
}

@Composable
private fun FeatureCard(feature: HealthFeature, access: HealthAccess, denied: Boolean, onConnect: () -> Unit) {
    val connected = access.connected(feature)
    val partly = access.partly(feature)
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            Text(stringResource(feature.title()), Modifier.weight(1f), style = GtTheme.typography.title)
            when {
                connected -> GlassBadge(stringResource(R.string.health_connected), tone = BadgeTone.Accent)
                partly -> GlassBadge(stringResource(R.string.health_partly))
            }
        }
        Text(stringResource(feature.text()), color = GtTheme.colors.textSecondary, style = GtTheme.typography.bodySmall)
        if (!connected) {
            if (denied) Denied()
            GlassButton(
                stringResource(if (partly) R.string.health_complete else R.string.health_connect),
                onClick = onConnect,
                style = GlassButtonStyle.Secondary,
                icon = R.drawable.ic_link,
            )
        }
    }
}

@Composable
private fun SwitchCard(@StringRes title: Int, @StringRes text: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit, denied: Boolean) {
    GlassCard(Modifier.fillMaxWidth()) {
        SwitchRow(title, text, checked, onCheckedChange, titleStyle = true)
        if (denied && !checked) Denied()
    }
}

@Composable
private fun SwitchRow(
    @StringRes title: Int,
    @StringRes text: Int?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    titleStyle: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
            Text(stringResource(title), style = if (titleStyle) GtTheme.typography.title else GtTheme.typography.label)
            if (text != null) Text(stringResource(text), color = GtTheme.colors.textSecondary, style = GtTheme.typography.bodySmall)
        }
        GlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Denied() {
    Text(stringResource(R.string.health_denied), color = GtTheme.colors.textSecondary, style = GtTheme.typography.bodySmall)
}

@Composable
private fun ZonesCard(state: HealthConnectUiState, onAge: (Int?) -> Unit, onMaxHeartRate: (Int?) -> Unit) {
    val colors = GtTheme.colors
    val bpm = stringResource(R.string.unit_bpm)
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.health_zones), style = GtTheme.typography.title)
        Text(stringResource(R.string.health_zones_text), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            // Wide ranges: the fields clamp every keystroke, so "1" on the way to "18" must fit
            GlassNumberField(
                value = state.age?.toDouble(),
                onValueChange = { onAge(it?.roundToInt()) },
                label = stringResource(R.string.health_age),
                modifier = Modifier.weight(1f),
                range = 0.0..MAX_AGE,
                placeholder = stringResource(R.string.number_empty),
            )
            GlassNumberField(
                value = state.manualMaxHeartRate?.toDouble(),
                onValueChange = { onMaxHeartRate(it?.roundToInt()) },
                label = stringResource(R.string.health_max_hr),
                modifier = Modifier.weight(1f),
                unit = bpm,
                range = 0.0..MAX_BPM,
                placeholder = state.estimatedMaxHeartRate?.toString() ?: stringResource(R.string.number_empty),
            )
        }
        state.maxHeartRate?.let {
            Text(stringResource(R.string.health_zones_using, bpmLabel(it)), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        }
    }
}

/** The way in from Profil. */
@Composable
fun HealthConnectEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    GlassCard(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            Icon(painterResource(R.drawable.ic_favorite), contentDescription = null, tint = colors.accentText)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
                Text(stringResource(R.string.health_title), style = GtTheme.typography.title)
                Text(stringResource(R.string.health_entry_text), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.textTertiary)
        }
    }
}

private const val MAX_AGE = 120.0
private const val MAX_BPM = 250.0

@Preview
@Composable
private fun HealthConnectScreenPreview() {
    GymTrackerTheme {
        GlassScaffold {
            HealthConnectScreen(
                state = HealthConnectUiState(
                    loading = false,
                    access = HealthAccess(
                        granted = HealthFeature.Workouts.permissions + HealthFeature.Recovery.permissions.take(1),
                        historySupported = true,
                        backgroundSupported = true,
                    ),
                    lastSync = 1_760_000_000_000,
                    summary = ImportSummary(sessions = 12, measurements = 40, nights = 0, days = 30),
                    age = 30,
                ),
                onBack = {},
                onConnect = {},
                onRetry = {},
                onSyncNow = {},
                onSyncOnOpen = {},
                onBackgroundSync = {},
                onWriteWorkouts = {},
                onWriteConfirm = {},
                onWriteDismiss = {},
                onAge = {},
                onMaxHeartRate = {},
                onManage = {},
                onPrivacy = {},
                onDisconnectRequest = {},
                onDisconnectDismiss = {},
                onDisconnectConfirm = {},
            )
        }
    }
}
