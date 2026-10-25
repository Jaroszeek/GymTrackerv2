package com.jaroszeek.gymtracker.feature.update

import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.core.update.UpdateError
import com.jaroszeek.gymtracker.core.update.UpdateState
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassDialog
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.GlassSwitchRow
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.update.AppRelease
import com.jaroszeek.gymtracker.domain.update.AppVersion
import com.jaroszeek.gymtracker.feature.progress.shortDate
import com.jaroszeek.gymtracker.feature.progress.timeLabel
import kotlin.math.roundToInt

/** The update dialog over the whole app: new version, install permission, download, install, failure. */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Back from the system's "install unknown apps" switch: install() checks the permission again
    val unknownSources = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.onInstall() }
    UpdateDialog(
        update = ui.update,
        onInstall = viewModel::onInstall,
        onPostpone = viewModel::onPostpone,
        onAllowInstalls = {
            unknownSources.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
        },
    )
}

@Composable
fun UpdateDialog(update: UpdateState, onInstall: () -> Unit, onPostpone: () -> Unit, onAllowInstalls: () -> Unit) {
    val visible = when (update) {
        is UpdateState.Available -> update.prompt
        is UpdateState.NeedsInstallPermission, is UpdateState.Downloading, is UpdateState.Installing -> true
        // A failed manual check shows in Settings, where it was started
        is UpdateState.Failed -> update.release != null
        else -> false
    }
    // The dialog keeps its last content while it fades out
    val last = remember { mutableStateOf(update) }
    SideEffect { if (visible) last.value = update }
    val shown = if (visible) update else last.value
    val release = shown.release
    val version = release?.version?.toString().orEmpty()
    val busy = shown is UpdateState.Downloading || shown is UpdateState.Installing
    val retry = shown is UpdateState.Failed && shown.error.retryable
    GlassDialog(
        visible = visible,
        // Download and install go on in the background either way; the dialog stays until they end
        onDismissRequest = { if (!busy) onPostpone() },
        title = when (shown) {
            is UpdateState.NeedsInstallPermission -> stringResource(R.string.update_permission_title)
            is UpdateState.Downloading -> stringResource(R.string.update_downloading_title, version)
            is UpdateState.Installing -> stringResource(R.string.update_installing_title, version)
            is UpdateState.Failed -> stringResource(R.string.update_failed_title)
            else -> stringResource(R.string.update_title, version)
        },
        text = when (shown) {
            is UpdateState.NeedsInstallPermission -> stringResource(R.string.update_permission_text)
            is UpdateState.Downloading -> stringResource(R.string.update_downloading_text, (shown.progress * PERCENT).roundToInt(), fileSize(shown.release.apkSize))
            is UpdateState.Installing -> stringResource(R.string.update_installing_text)
            is UpdateState.Failed -> stringResource(shown.error.message())
            else -> release?.let { listOf(it.notes, stringResource(R.string.update_size, fileSize(it.apkSize))).filter(String::isNotBlank).joinToString("\n\n") }
        },
        confirmButton = {
            when {
                busy -> Unit
                shown is UpdateState.NeedsInstallPermission -> GlassButton(stringResource(R.string.update_permission_open), onAllowInstalls)
                shown is UpdateState.Failed && !retry -> GlassButton(stringResource(R.string.action_close), onPostpone)
                shown is UpdateState.Failed -> GlassButton(stringResource(R.string.action_retry), onInstall)
                else -> GlassButton(stringResource(R.string.update_action_install), onInstall)
            }
        },
        dismissButton = when {
            busy || (shown is UpdateState.Failed && !retry) -> null
            shown is UpdateState.Failed -> ({ GlassButton(stringResource(R.string.action_close), onPostpone, style = GlassButtonStyle.Ghost) })
            else -> ({ GlassButton(stringResource(R.string.update_action_later), onPostpone, style = GlassButtonStyle.Ghost) })
        },
    )
}

/** The "Aktualizacje" card in Settings: status, automatic checks and a manual check or the install. */
@Composable
fun UpdatesCard(ui: UpdateUiState, onCheck: () -> Unit, onInstall: () -> Unit, onAutoCheck: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    GlassCard(modifier.fillMaxWidth()) {
        if (!ui.enabled) {
            Text(stringResource(R.string.update_disabled), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        } else {
            val update = ui.update
            val release = update.release
            val checkFailed = update is UpdateState.Failed && release == null
            val status = when {
                update is UpdateState.Checking -> stringResource(R.string.update_checking)
                release != null -> stringResource(R.string.update_available, release.version.toString())
                checkFailed -> stringResource(R.string.update_check_failed)
                ui.lastCheck != null -> stringResource(R.string.update_up_to_date, shortDate(ui.lastCheck), timeLabel(ui.lastCheck))
                else -> stringResource(R.string.update_never_checked)
            }
            Text(
                status,
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = if (checkFailed) colors.danger else colors.textSecondary,
                style = GtTheme.typography.bodySmall,
            )
            GlassSwitchRow(
                stringResource(R.string.settings_updates_auto),
                ui.autoCheck,
                onAutoCheck,
                text = stringResource(R.string.settings_updates_auto_text),
            )
            if (release != null) {
                GlassButton(
                    stringResource(R.string.update_install, release.version.toString()),
                    onInstall,
                    Modifier.fillMaxWidth(),
                    enabled = update is UpdateState.Available || update is UpdateState.Failed,
                )
            } else {
                GlassButton(
                    stringResource(R.string.settings_updates_check),
                    onCheck,
                    Modifier.fillMaxWidth(),
                    style = GlassButtonStyle.Secondary,
                    enabled = update !is UpdateState.Checking,
                )
            }
        }
    }
}

private val UpdateState.release: AppRelease?
    get() = when (this) {
        is UpdateState.Available -> release
        is UpdateState.NeedsInstallPermission -> release
        is UpdateState.Downloading -> release
        is UpdateState.Installing -> release
        is UpdateState.Failed -> release
        else -> null
    }

/** Trying again cannot help a release without an APK or one signed with another key. */
private val UpdateError.retryable: Boolean
    get() = this != UpdateError.NoApk && this != UpdateError.SignatureMismatch

@StringRes
private fun UpdateError.message(): Int = when (this) {
    UpdateError.Network -> R.string.update_failed_network
    UpdateError.NoApk -> R.string.update_failed_no_apk
    UpdateError.SignatureMismatch -> R.string.update_failed_signature
    UpdateError.InstallFailed -> R.string.update_failed_install
    UpdateError.Cancelled -> R.string.update_failed_cancelled
}

@Composable
private fun fileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

private const val PERCENT = 100

private val PreviewRelease = AppRelease(
    version = AppVersion(1, 0, 1),
    notes = "• Szybszy start aplikacji\n• Poprawki w historii treningów",
    apkUrl = "",
    apkSize = 9_800_000,
    pageUrl = "",
)

@Preview
@Composable
private fun UpdateDialogPreview() {
    GymTrackerTheme {
        GlassScaffold { UpdateDialog(UpdateState.Available(PreviewRelease), onInstall = {}, onPostpone = {}, onAllowInstalls = {}) }
    }
}

@Preview
@Composable
private fun UpdateDownloadingPreview() {
    GymTrackerTheme {
        GlassScaffold { UpdateDialog(UpdateState.Downloading(PreviewRelease, 0.42f), onInstall = {}, onPostpone = {}, onAllowInstalls = {}) }
    }
}

@Preview
@Composable
private fun UpdatesCardPreview() {
    GymTrackerTheme {
        UpdatesCard(UpdateUiState(enabled = true, lastCheck = 1_760_000_000_000), onCheck = {}, onInstall = {}, onAutoCheck = {})
    }
}
