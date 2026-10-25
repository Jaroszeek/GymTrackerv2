package com.jaroszeek.gymtracker.feature.settings

import android.app.LocaleManager
import android.content.Context
import android.net.Uri
import android.os.LocaleList
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
import com.jaroszeek.gymtracker.domain.settings.AppLanguage
import com.jaroszeek.gymtracker.domain.settings.RestAlert
import com.jaroszeek.gymtracker.domain.settings.RestDefaults
import com.jaroszeek.gymtracker.domain.settings.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
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

    /** Confirmed: the backup is staged, the app starts again and it becomes the data. */
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
    val language: AppLanguage = AppLanguage.System,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val backup: BackupManager,
    private val workout: ActiveWorkoutManager,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val backupState = MutableStateFlow<BackupState>(BackupState.Idle)

    // Android keeps the app's language (D-105)
    private val locales = context.getSystemService(LocaleManager::class.java)
    private val language = MutableStateFlow(AppLanguage.fromTag(locales.applicationLocales[0]?.language))

    private val training = combine(preferences.weightUnit, preferences.defaultRestSeconds, preferences.restAlert, ::Triple)

    /** Null until the settings are read, so nothing is changed from the defaults. */
    val state: StateFlow<SettingsUiState?> =
        combine(preferences.theme, training, preferences.healthLastSync, workout.workout, combine(backupState, language, ::Pair)) {
                theme, (unit, rest, alert), lastSync, active, (backup, language) ->
            SettingsUiState(theme, unit, rest, alert, lastSync, workoutActive = active != null, backup = backup, language = language)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun onTheme(theme: ThemeSettings) {
        viewModelScope.launch { preferences.setTheme(theme) }
    }

    /** The screens are created again in the new language. */
    fun onLanguage(language: AppLanguage) {
        this.language.value = language
        locales.applicationLocales = language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
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
        backupState.value = BackupState.Checking
        viewModelScope.launch {
            // In a process started again for the picked file, the workout may not be read back yet
            workout.ready.first { it }
            if (workout.workout.value != null) {
                backupState.value = BackupState.Idle
                return@launch
            }
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
        // At once: a second tap or a dismissal must not touch the backup being staged
        backupState.value = BackupState.Restarting
        viewModelScope.launch {
            // Leaving the screen now must not leave a staged backup without the restart that swaps it in
            withContext(NonCancellable) {
                try {
                    backup.confirm()
                } catch (e: IOException) {
                    Log.e(TAG, "Could not stage the backup", e)
                    backupState.value = BackupState.Rejected(Rejection.Damaged)
                    return@withContext
                }
                // The profile keeps an age already entered
                summary.birthYear?.let { if (preferences.birthYear.first() == null) preferences.setBirthYear(it) }
                backup.restart()
            }
        }
    }

    fun onDismissBackup() {
        if (backupState.value == BackupState.Restarting) return
        if (backupState.value is BackupState.Confirm) viewModelScope.launch(io) { backup.discard() }
        backupState.value = BackupState.Idle
    }

    private companion object {
        const val TAG = "Settings"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
