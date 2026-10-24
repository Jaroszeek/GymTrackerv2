package com.jaroszeek.gymtracker.feature.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.data.backup.BackupManager
import com.jaroszeek.gymtracker.data.backup.BackupSummary
import com.jaroszeek.gymtracker.data.backup.Inspection
import com.jaroszeek.gymtracker.data.backup.Rejection
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.designsystem.theme.ThemeSettings
import com.jaroszeek.gymtracker.domain.settings.RestAlert
import com.jaroszeek.gymtracker.domain.settings.RestDefaults
import com.jaroszeek.gymtracker.domain.settings.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where export and import of a backup are (A5.10). */
sealed interface BackupState {
    data object Idle : BackupState

    data object Exporting : BackupState

    data object Exported : BackupState

    data object ExportFailed : BackupState

    data object Checking : BackupState

    data class Rejected(val reason: Rejection) : BackupState

    /** Checked and ready; replaces the data after confirmation. */
    data class Confirm(val summary: BackupSummary) : BackupState

    /** Confirmed: the app starts again and the backup becomes the data. */
    data object Restarting : BackupState
}

data class SettingsUiState(
    val theme: ThemeSettings = ThemeSettings(),
    val unit: WeightUnit = WeightUnit.Kg,
    val restSeconds: Int = RestDefaults.SECONDS,
    val restAlert: RestAlert = RestAlert(),
    val lastSync: Long? = null,
    /** An import would replace the data under the workout in progress, so it waits until the end. */
    val workoutActive: Boolean = false,
    val backup: BackupState = BackupState.Idle,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val backup: BackupManager,
    workout: ActiveWorkoutManager,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val backupState = MutableStateFlow<BackupState>(BackupState.Idle)

    private val training = combine(preferences.weightUnit, preferences.defaultRestSeconds, preferences.restAlert, ::Triple)

    val state: StateFlow<SettingsUiState> =
        combine(preferences.theme, training, preferences.healthLastSync, workout.workout, backupState) { theme, (unit, rest, alert), lastSync, active, backup ->
            SettingsUiState(theme, unit, rest, alert, lastSync, workoutActive = active != null, backup = backup)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun onTheme(theme: ThemeSettings) {
        viewModelScope.launch { preferences.setTheme(theme) }
    }

    fun onUnit(unit: WeightUnit) {
        viewModelScope.launch { preferences.setWeightUnit(unit) }
    }

    fun onRestSeconds(seconds: Int) {
        viewModelScope.launch { preferences.setDefaultRestSeconds(seconds) }
    }

    fun onRestSound(enabled: Boolean) {
        viewModelScope.launch { preferences.setRestSound(enabled) }
    }

    fun onRestVibration(enabled: Boolean) {
        viewModelScope.launch { preferences.setRestVibration(enabled) }
    }

    /** [uri] from the system's "save as" picker. */
    fun onExport(uri: Uri) {
        backupState.value = BackupState.Exporting
        viewModelScope.launch {
            backupState.value = try {
                withContext(io) {
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No stream for $uri")
                    out.use { backup.export(it) }
                }
                BackupState.Exported
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                BackupState.ExportFailed
            }
        }
    }

    /** [uri] from the system's file picker. */
    fun onImport(uri: Uri) {
        if (state.value.workoutActive) return
        backupState.value = BackupState.Checking
        viewModelScope.launch {
            val inspection = try {
                withContext(io) { context.contentResolver.openInputStream(uri)?.use { backup.inspect(it) } }
                    ?: Inspection.Rejected(Rejection.NotABackup)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read $uri", e)
                Inspection.Rejected(Rejection.NotABackup)
            }
            backupState.value = when (inspection) {
                is Inspection.Ready -> BackupState.Confirm(inspection.summary)
                is Inspection.Rejected -> BackupState.Rejected(inspection.reason)
            }
        }
    }

    fun onConfirmImport() {
        val summary = (backupState.value as? BackupState.Confirm)?.summary ?: return
        viewModelScope.launch {
            backupState.value = try {
                backup.confirm()
                // The profile keeps an age already entered
                summary.birthYear?.let { if (preferences.birthYear.first() == null) preferences.setBirthYear(it) }
                BackupState.Restarting
            } catch (e: IOException) {
                Log.e(TAG, "Could not stage the backup", e)
                BackupState.Rejected(Rejection.Damaged)
            }
        }
    }

    fun onDismissBackup() {
        if (backupState.value is BackupState.Confirm) viewModelScope.launch(io) { backup.discard() }
        backupState.value = BackupState.Idle
    }

    private companion object {
        const val TAG = "Settings"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
