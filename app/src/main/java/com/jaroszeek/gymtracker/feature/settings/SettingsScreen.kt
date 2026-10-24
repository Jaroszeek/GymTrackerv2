package com.jaroszeek.gymtracker.feature.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.BuildConfig
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.data.backup.BackupSummary
import com.jaroszeek.gymtracker.data.backup.Rejection
import com.jaroszeek.gymtracker.data.backup.Skipped
import com.jaroszeek.gymtracker.data.backup.SkippedKind
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassDialog
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.GlassSegmentedControl
import com.jaroszeek.gymtracker.designsystem.component.GlassSwatch
import com.jaroszeek.gymtracker.designsystem.component.GlassSwitch
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtPalette
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.designsystem.theme.ThemeMode
import com.jaroszeek.gymtracker.designsystem.theme.ThemeSettings
import com.jaroszeek.gymtracker.designsystem.theme.gtColors
import com.jaroszeek.gymtracker.domain.settings.RestDefaults
import com.jaroszeek.gymtracker.domain.settings.WeightUnit
import com.jaroszeek.gymtracker.feature.progress.shortDate
import com.jaroszeek.gymtracker.feature.progress.timeLabel
import com.jaroszeek.gymtracker.feature.routines.label
import com.jaroszeek.gymtracker.feature.routines.restLabel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onHealthConnect: () -> Unit,
    onDiagnostics: () -> Unit,
    onLicenses: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri -> uri?.let(viewModel::onExport) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::onImport) }
    LaunchedEffect(state.backup) {
        if (state.backup == BackupState.Restarting) activity?.restart()
    }
    SettingsScreen(
        state = state,
        version = BuildConfig.VERSION_NAME,
        actions = SettingsActions(
            onBack = onBack,
            onTheme = viewModel::onTheme,
            onUnit = viewModel::onUnit,
            onRestSeconds = viewModel::onRestSeconds,
            onRestSound = viewModel::onRestSound,
            onRestVibration = viewModel::onRestVibration,
            onHealthConnect = onHealthConnect,
            onDiagnostics = onDiagnostics,
            onExport = { export.launch(context.getString(R.string.backup_file_name, FILE_STAMP.format(LocalDateTime.now()))) },
            // Backups from file managers and clouds come with any type, often application/octet-stream
            onImport = { import.launch(arrayOf("*/*")) },
            onConfirmImport = viewModel::onConfirmImport,
            onDismissBackup = viewModel::onDismissBackup,
            onLicenses = onLicenses,
        ),
    )
}

/** A new process opens the swapped-in database (A5.10). */
private fun Activity.restart() {
    startActivity(Intent.makeRestartActivityTask(componentName))
    Runtime.getRuntime().exit(0)
}

class SettingsActions(
    val onBack: () -> Unit = {},
    val onTheme: (ThemeSettings) -> Unit = {},
    val onUnit: (WeightUnit) -> Unit = {},
    val onRestSeconds: (Int) -> Unit = {},
    val onRestSound: (Boolean) -> Unit = {},
    val onRestVibration: (Boolean) -> Unit = {},
    val onHealthConnect: () -> Unit = {},
    val onDiagnostics: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onConfirmImport: () -> Unit = {},
    val onDismissBackup: () -> Unit = {},
    val onLicenses: () -> Unit = {},
)

@Composable
fun SettingsScreen(state: SettingsUiState, version: String, actions: SettingsActions, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(navBarContentPadding())
            .padding(horizontal = GtSpacing.xl, vertical = GtSpacing.s),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            GlassIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClick = actions.onBack)
            Text(stringResource(R.string.settings_title), style = GtTheme.typography.headline)
        }

        SectionTitle(R.string.settings_appearance)
        AppearanceCard(state.theme, actions.onTheme)

        SectionTitle(R.string.settings_training)
        TrainingCard(state, actions)

        SectionTitle(R.string.health_title)
        Entry(
            R.drawable.ic_favorite,
            stringResource(R.string.health_title),
            state.lastSync?.let { stringResource(R.string.health_sync_last, shortDate(it), timeLabel(it)) } ?: stringResource(R.string.health_entry_text),
            actions.onHealthConnect,
        )
        Entry(R.drawable.ic_timer, stringResource(R.string.diagnostics_title), null, actions.onDiagnostics)

        SectionTitle(R.string.settings_data)
        DataCard(state, actions)

        SectionTitle(R.string.settings_updates)
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_updates_soon), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
            SwitchRow(R.string.settings_updates_auto, null, checked = false, onCheckedChange = {}, enabled = false)
            GlassButton(stringResource(R.string.settings_updates_check), onClick = {}, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary, enabled = false)
        }

        SectionTitle(R.string.settings_about)
        Entry(R.drawable.ic_fitness_center, stringResource(R.string.app_name), stringResource(R.string.settings_version, version), onClick = null)
        Entry(R.drawable.ic_menu_book, stringResource(R.string.settings_licenses), stringResource(R.string.settings_licenses_text), actions.onLicenses)
    }
    BackupDialogs(state.backup, actions)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceCard(theme: ThemeSettings, onTheme: (ThemeSettings) -> Unit) {
    val colors = GtTheme.colors
    val fixed = theme.palette.forcedDark != null
    GlassCard(Modifier.fillMaxWidth()) {
        GlassSegmentedControl(
            options = ThemeMode.entries.map { stringResource(it.label()) },
            selectedIndex = theme.mode.ordinal,
            onSelect = { onTheme(theme.copy(mode = ThemeMode.entries[it])) },
        )
        if (fixed) Text(stringResource(R.string.settings_mode_fixed), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        Text(stringResource(R.string.settings_palette, stringResource(theme.palette.label())), style = GtTheme.typography.label)
        FlowRow {
            GtPalette.entries.forEach { palette ->
                GlassSwatch(
                    color = gtColors(palette, colors.isDark).accent,
                    selected = palette == theme.palette,
                    onClick = { onTheme(theme.copy(palette = palette)) },
                    label = stringResource(palette.label()),
                )
            }
        }
        SwitchRow(R.string.settings_amoled, R.string.settings_amoled_text, theme.amoled, { onTheme(theme.copy(amoled = it)) }, enabled = theme.palette != GtPalette.Porcelain)
        SwitchRow(
            R.string.settings_dynamic,
            if (fixed) R.string.settings_dynamic_mono else R.string.settings_dynamic_text,
            theme.dynamicColor && !fixed,
            { onTheme(theme.copy(dynamicColor = it)) },
            enabled = !fixed,
        )
        SwitchRow(R.string.settings_reduce_transparency, R.string.settings_reduce_transparency_text, theme.reduceTransparency, {
            onTheme(theme.copy(reduceTransparency = it))
        })
    }
}

@Composable
private fun TrainingCard(state: SettingsUiState, actions: SettingsActions) {
    val colors = GtTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_unit), style = GtTheme.typography.label)
        GlassSegmentedControl(
            options = WeightUnit.entries.map { stringResource(it.label()) },
            selectedIndex = state.unit.ordinal,
            onSelect = { actions.onUnit(WeightUnit.entries[it]) },
        )
        Text(stringResource(R.string.settings_unit_text), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
                Text(stringResource(R.string.settings_rest), style = GtTheme.typography.label)
                Text(stringResource(R.string.settings_rest_text), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
            }
            val label = stringResource(R.string.settings_rest)
            GlassIconButton(
                R.drawable.ic_remove,
                stringResource(R.string.number_decrease, label),
                onClick = { actions.onRestSeconds(state.restSeconds - RestDefaults.STEP_SECONDS) },
                enabled = state.restSeconds > RestDefaults.MIN_SECONDS,
            )
            Text(restLabel(state.restSeconds), style = GtTheme.typography.numberM)
            GlassIconButton(
                R.drawable.ic_add,
                stringResource(R.string.number_increase, label),
                onClick = { actions.onRestSeconds(state.restSeconds + RestDefaults.STEP_SECONDS) },
                enabled = state.restSeconds < RestDefaults.MAX_SECONDS,
            )
        }
        SwitchRow(R.string.settings_rest_vibration, null, state.restAlert.vibration, actions.onRestVibration)
        SwitchRow(R.string.settings_rest_sound, R.string.settings_rest_sound_text, state.restAlert.sound, actions.onRestSound)
    }
}

@Composable
private fun DataCard(state: SettingsUiState, actions: SettingsActions) {
    val colors = GtTheme.colors
    val busy = state.backup == BackupState.Exporting || state.backup == BackupState.Checking || state.backup == BackupState.Restarting
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_data_text), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            GlassButton(stringResource(R.string.settings_export), actions.onExport, Modifier.weight(1f), style = GlassButtonStyle.Secondary, enabled = !busy)
            GlassButton(
                stringResource(R.string.settings_import),
                actions.onImport,
                Modifier.weight(1f),
                style = GlassButtonStyle.Secondary,
                enabled = !busy && !state.workoutActive,
            )
        }
        val status = when {
            state.backup == BackupState.Exporting -> R.string.settings_exporting
            state.backup == BackupState.Checking -> R.string.settings_checking
            state.backup == BackupState.Exported -> R.string.settings_exported
            state.backup == BackupState.ExportFailed -> R.string.settings_export_failed
            state.backup == BackupState.Restarting -> R.string.backup_restarting
            state.workoutActive -> R.string.settings_import_blocked
            else -> null
        }
        status?.let {
            Text(
                stringResource(it),
                color = if (state.backup == BackupState.ExportFailed) colors.danger else colors.textSecondary,
                style = GtTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BackupDialogs(backup: BackupState, actions: SettingsActions) {
    GlassDialog(
        visible = backup is BackupState.Rejected,
        onDismissRequest = actions.onDismissBackup,
        title = stringResource(R.string.settings_import),
        text = (backup as? BackupState.Rejected)?.let { stringResource(it.reason.message()) },
        confirmButton = { GlassButton(stringResource(R.string.action_ok), actions.onDismissBackup) },
    )
    val summary = (backup as? BackupState.Confirm)?.summary
    GlassDialog(
        visible = summary != null,
        onDismissRequest = actions.onDismissBackup,
        title = stringResource(if (summary?.fromV1 == true) R.string.backup_confirm_title_v1 else R.string.backup_confirm_title),
        text = summary?.let { summaryText(it) },
        confirmButton = { GlassButton(stringResource(R.string.backup_confirm), actions.onConfirmImport) },
        dismissButton = { GlassButton(stringResource(R.string.action_cancel), actions.onDismissBackup, style = GlassButtonStyle.Ghost) },
    )
}

/** What the backup holds, what did not come along from v1, and what confirming does. */
@Composable
private fun summaryText(summary: BackupSummary): String {
    val counts = listOf(
        pluralStringResource(R.plurals.backup_routines, summary.routines, summary.routines),
        pluralStringResource(R.plurals.home_calendar_workouts, summary.workouts, summary.workouts),
        pluralStringResource(R.plurals.health_measurements, summary.measurements, summary.measurements),
        pluralStringResource(R.plurals.backup_custom_exercises, summary.customExercises, summary.customExercises),
    ).joinToString(", ")
    val range = if (summary.firstWorkout != null && summary.lastWorkout != null) {
        stringResource(R.string.backup_confirm_range, shortDate(summary.firstWorkout), shortDate(summary.lastWorkout))
    } else {
        null
    }
    val skipped = summary.skipped.takeIf { it.isNotEmpty() }?.let { list ->
        (listOf(stringResource(R.string.backup_skipped_title)) + list.map { "• ${skippedLabel(it)}" }).joinToString("\n")
    }
    return listOfNotNull(listOfNotNull(counts, range).joinToString(". "), skipped, stringResource(R.string.backup_confirm_text)).joinToString("\n\n")
}

@Composable
private fun skippedLabel(skipped: Skipped): String {
    val plural = when (skipped.kind) {
        SkippedKind.UnfinishedWorkouts -> R.plurals.backup_skipped_unfinished
        SkippedKind.EmptyWorkouts -> R.plurals.backup_skipped_empty
        SkippedKind.HealthConnectSessions -> R.plurals.backup_skipped_health_sessions
        SkippedKind.DailyActivity -> R.plurals.backup_skipped_daily
        SkippedKind.SetTypes -> R.plurals.backup_skipped_set_types
        SkippedKind.RoutineWarmups -> R.plurals.backup_skipped_routine_warmups
        SkippedKind.Supersets -> R.plurals.backup_skipped_supersets
        SkippedKind.Notes -> R.plurals.backup_skipped_notes
        SkippedKind.FatMass -> R.plurals.backup_skipped_fat_mass
        SkippedKind.OutOfRange -> R.plurals.backup_skipped_out_of_range
        SkippedKind.MissingExercises -> R.plurals.backup_skipped_missing_exercises
        SkippedKind.ExerciseMedia -> R.plurals.backup_skipped_media
    }
    return pluralStringResource(plural, skipped.count, skipped.count)
}

@StringRes
private fun Rejection.message(): Int = when (this) {
    Rejection.NotABackup -> R.string.backup_rejected_not_backup
    Rejection.Damaged -> R.string.backup_rejected_damaged
    Rejection.Newer -> R.string.backup_rejected_newer
    Rejection.UnsupportedV1 -> R.string.backup_rejected_old_v1
}

@StringRes
private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.System -> R.string.settings_mode_system
    ThemeMode.Light -> R.string.settings_mode_light
    ThemeMode.Dark -> R.string.settings_mode_dark
}

@StringRes
private fun GtPalette.label(): Int = when (this) {
    GtPalette.Volt -> R.string.palette_volt
    GtPalette.Ember -> R.string.palette_ember
    GtPalette.Rose -> R.string.palette_rose
    GtPalette.Aqua -> R.string.palette_aqua
    GtPalette.Azure -> R.string.palette_azure
    GtPalette.Amber -> R.string.palette_amber
    GtPalette.Onyx -> R.string.palette_onyx
    GtPalette.Porcelain -> R.string.palette_porcelain
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(stringResource(title), Modifier.padding(top = GtSpacing.s), color = GtTheme.colors.textSecondary, style = GtTheme.typography.label)
}

@Composable
private fun SwitchRow(@StringRes title: Int, @StringRes text: Int?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val colors = GtTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
            Text(stringResource(title), color = if (enabled) colors.textPrimary else colors.textTertiary, style = GtTheme.typography.label)
            if (text != null) Text(stringResource(text), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        }
        GlassSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun Entry(@DrawableRes icon: Int, title: String, text: String?, onClick: (() -> Unit)?) {
    val colors = GtTheme.colors
    GlassCard(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.accentText)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
                Text(title, style = GtTheme.typography.title)
                text?.let { Text(it, color = colors.textSecondary, style = GtTheme.typography.bodySmall) }
            }
            if (onClick != null) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.textTertiary)
        }
    }
}

private const val BACKUP_MIME_TYPE = "application/octet-stream"
private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

@Preview
@Composable
private fun SettingsScreenPreview() {
    GymTrackerTheme {
        GlassScaffold { SettingsScreen(SettingsUiState(lastSync = 1_760_000_000_000), version = "0.1.0", actions = SettingsActions()) }
    }
}

@Preview
@Composable
private fun SettingsImportPreview() {
    val summary = BackupSummary(
        routines = 4,
        workouts = 126,
        measurements = 31,
        customExercises = 2,
        firstWorkout = 1_720_000_000_000,
        lastWorkout = 1_760_000_000_000,
        fromV1 = true,
        skipped = listOf(Skipped(SkippedKind.UnfinishedWorkouts, 1), Skipped(SkippedKind.DailyActivity, 210)),
    )
    GymTrackerTheme {
        GlassScaffold { SettingsScreen(SettingsUiState(backup = BackupState.Confirm(summary)), version = "0.1.0", actions = SettingsActions()) }
    }
}
