package com.jaroszeek.gymtracker.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope

/** Preferences in [folder], kept by [scope] (e.g. `backgroundScope`). */
fun testPreferences(scope: CoroutineScope, folder: File): AppPreferences =
    AppPreferences(PreferenceDataStoreFactory.create(scope = scope) { File(folder, "test.preferences_pb") })
