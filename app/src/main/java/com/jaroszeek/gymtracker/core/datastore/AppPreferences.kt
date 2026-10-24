package com.jaroszeek.gymtracker.core.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jaroszeek.gymtracker.designsystem.theme.GtPalette
import com.jaroszeek.gymtracker.designsystem.theme.ThemeMode
import com.jaroszeek.gymtracker.designsystem.theme.ThemeSettings
import com.jaroszeek.gymtracker.domain.settings.RestAlert
import com.jaroszeek.gymtracker.domain.settings.RestDefaults
import com.jaroszeek.gymtracker.domain.settings.WeightUnit
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** App flags and the settings (A5.9). */
@Singleton
class AppPreferences @Inject constructor(private val store: DataStore<Preferences>) {

    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e !is IOException) throw e
        Log.e(TAG, "Reading preferences failed", e)
        emit(emptyPreferences())
    }

    /** Whether the explanation before the notification permission request was shown (first start). */
    val notificationPromptShown: Flow<Boolean> = data.map { it[NOTIFICATION_PROMPT_SHOWN] == true }

    suspend fun setNotificationPromptShown() = set(NOTIFICATION_PROMPT_SHOWN, true)

    /** [com.jaroszeek.gymtracker.feature.workout.BackgroundIssues.keys] when the user hid the "Trening w tle" card. */
    val hiddenBackgroundIssues: Flow<Set<String>> = data.map { it[HIDDEN_BACKGROUND_ISSUES].orEmpty() }.distinctUntilChanged()

    suspend fun setHiddenBackgroundIssues(value: Set<String>) = set(HIDDEN_BACKGROUND_ISSUES, value)

    // Settings (A5.9)

    val theme: Flow<ThemeSettings> = data.map {
        ThemeSettings(
            mode = ThemeMode.entries.firstOrNull { mode -> mode.name == it[THEME_MODE] } ?: ThemeMode.System,
            palette = GtPalette.entries.firstOrNull { palette -> palette.name == it[THEME_PALETTE] } ?: GtPalette.Volt,
            amoled = it[THEME_AMOLED] == true,
            dynamicColor = it[THEME_DYNAMIC_COLOR] == true,
            reduceTransparency = it[THEME_REDUCE_TRANSPARENCY] == true,
        )
    }.distinctUntilChanged()

    suspend fun setTheme(value: ThemeSettings) = edit {
        it[THEME_MODE] = value.mode.name
        it[THEME_PALETTE] = value.palette.name
        it[THEME_AMOLED] = value.amoled
        it[THEME_DYNAMIC_COLOR] = value.dynamicColor
        it[THEME_REDUCE_TRANSPARENCY] = value.reduceTransparency
    }

    val weightUnit: Flow<WeightUnit> = data.map { WeightUnit.fromKey(it[WEIGHT_UNIT]) }.distinctUntilChanged()

    suspend fun setWeightUnit(value: WeightUnit) = set(WEIGHT_UNIT, value.key)

    /** Rest after a set whose routine sets none. */
    val defaultRestSeconds: Flow<Int> = data.map { RestDefaults.coerce(it[DEFAULT_REST_SECONDS] ?: RestDefaults.SECONDS) }.distinctUntilChanged()

    suspend fun setDefaultRestSeconds(value: Int) = set(DEFAULT_REST_SECONDS, RestDefaults.coerce(value))

    val restAlert: Flow<RestAlert> = data.map {
        RestAlert(sound = it[REST_SOUND] ?: RestAlert().sound, vibration = it[REST_VIBRATION] ?: RestAlert().vibration)
    }.distinctUntilChanged()

    suspend fun setRestSound(value: Boolean) = set(REST_SOUND, value)

    suspend fun setRestVibration(value: Boolean) = set(REST_VIBRATION, value)

    // Health Connect (A6)

    /** Sync when the app opens; on unless turned off. */
    val healthSyncOnOpen: Flow<Boolean> = flag(HEALTH_SYNC_ON_OPEN, default = true)

    val healthBackgroundSync: Flow<Boolean> = flag(HEALTH_BACKGROUND_SYNC, default = false)

    val healthWriteWorkouts: Flow<Boolean> = flag(HEALTH_WRITE_WORKOUTS, default = false)

    /** Epoch millis of the last finished sync. */
    val healthLastSync: Flow<Long?> = data.map { it[HEALTH_LAST_SYNC] }.distinctUntilChanged()

    /** Whether the data older than 30 days was imported, which the history permission allows. */
    val healthHistoryImported: Flow<Boolean> = flag(HEALTH_HISTORY_IMPORTED, default = false)

    /** For the age-based maximum heart rate; the age the user enters, kept as a year so it grows. */
    val birthYear: Flow<Int?> = data.map { it[BIRTH_YEAR] }.distinctUntilChanged()

    /** Entered by the user; overrides the age-based estimate. */
    val maxHeartRate: Flow<Int?> = data.map { it[MAX_HEART_RATE] }.distinctUntilChanged()

    /** Start's widgets as [com.jaroszeek.gymtracker.domain.home.HomeLayout.encode] stores them; null is the default layout. */
    val homeLayout: Flow<String?> = data.map { it[HOME_LAYOUT] }.distinctUntilChanged()

    suspend fun setHomeLayout(value: String?) = set(HOME_LAYOUT, value)

    suspend fun setHealthSyncOnOpen(value: Boolean) = set(HEALTH_SYNC_ON_OPEN, value)

    suspend fun setHealthBackgroundSync(value: Boolean) = set(HEALTH_BACKGROUND_SYNC, value)

    suspend fun setHealthWriteWorkouts(value: Boolean) = set(HEALTH_WRITE_WORKOUTS, value)

    suspend fun setHealthLastSync(value: Long?) = set(HEALTH_LAST_SYNC, value)

    suspend fun setHealthHistoryImported(value: Boolean) = set(HEALTH_HISTORY_IMPORTED, value)

    suspend fun setBirthYear(value: Int?) = set(BIRTH_YEAR, value)

    suspend fun setMaxHeartRate(value: Int?) = set(MAX_HEART_RATE, value)

    // Updates (A5.11)

    /** Check for a new version when the app opens, at most once a day; on unless turned off. */
    val updateAutoCheck: Flow<Boolean> = flag(UPDATE_AUTO_CHECK, default = true)

    /** Epoch millis of the last successful update check. */
    val updateLastCheck: Flow<Long?> = data.map { it[UPDATE_LAST_CHECK] }.distinctUntilChanged()

    suspend fun setUpdateAutoCheck(value: Boolean) = set(UPDATE_AUTO_CHECK, value)

    suspend fun setUpdateLastCheck(value: Long?) = set(UPDATE_LAST_CHECK, value)

    private fun flag(key: Preferences.Key<Boolean>, default: Boolean) = data.map { it[key] ?: default }.distinctUntilChanged()

    /** Null removes the value. */
    private suspend fun <T> set(key: Preferences.Key<T>, value: T?) = edit { if (value == null) it.remove(key) else it[key] = value }

    private suspend fun edit(change: (MutablePreferences) -> Unit) {
        try {
            store.edit(change)
        } catch (e: IOException) {
            Log.e(TAG, "Saving preferences failed", e)
        }
    }

    private companion object {
        const val TAG = "Preferences"
        val NOTIFICATION_PROMPT_SHOWN = booleanPreferencesKey("notification_prompt_shown")
        val HIDDEN_BACKGROUND_ISSUES = stringSetPreferencesKey("hidden_background_issues")
        val HEALTH_SYNC_ON_OPEN = booleanPreferencesKey("health_sync_on_open")
        val HEALTH_BACKGROUND_SYNC = booleanPreferencesKey("health_background_sync")
        val HEALTH_WRITE_WORKOUTS = booleanPreferencesKey("health_write_workouts")
        val HEALTH_LAST_SYNC = longPreferencesKey("health_last_sync")
        val HEALTH_HISTORY_IMPORTED = booleanPreferencesKey("health_history_imported")
        val BIRTH_YEAR = intPreferencesKey("birth_year")
        val MAX_HEART_RATE = intPreferencesKey("max_heart_rate")
        val HOME_LAYOUT = stringPreferencesKey("home_layout")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val THEME_AMOLED = booleanPreferencesKey("theme_amoled")
        val THEME_DYNAMIC_COLOR = booleanPreferencesKey("theme_dynamic_color")
        val THEME_REDUCE_TRANSPARENCY = booleanPreferencesKey("theme_reduce_transparency")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val DEFAULT_REST_SECONDS = intPreferencesKey("default_rest_seconds")
        val REST_SOUND = booleanPreferencesKey("rest_sound")
        val REST_VIBRATION = booleanPreferencesKey("rest_vibration")
        val UPDATE_AUTO_CHECK = booleanPreferencesKey("update_auto_check")
        val UPDATE_LAST_CHECK = longPreferencesKey("update_last_check")
    }
}
