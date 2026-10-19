package com.jaroszeek.gymtracker.core.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Small app flags; the settings of Etap 9 join them here. */
@Singleton
class AppPreferences @Inject constructor(private val store: DataStore<Preferences>) {

    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e !is IOException) throw e
        Log.e(TAG, "Reading preferences failed", e)
        emit(emptyPreferences())
    }

    /** Whether the explanation before the notification permission request was shown (first start). */
    val notificationPromptShown: Flow<Boolean> = data.map { it[NOTIFICATION_PROMPT_SHOWN] == true }

    suspend fun setNotificationPromptShown() {
        try {
            store.edit { it[NOTIFICATION_PROMPT_SHOWN] = true }
        } catch (e: IOException) {
            Log.e(TAG, "Saving preferences failed", e)
        }
    }

    private companion object {
        const val TAG = "Preferences"
        val NOTIFICATION_PROMPT_SHOWN = booleanPreferencesKey("notification_prompt_shown")
    }
}
