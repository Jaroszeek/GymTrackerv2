package com.jaroszeek.gymtracker

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList
import com.jaroszeek.gymtracker.data.backup.BackupFiles
import com.jaroszeek.gymtracker.data.backup.BackupManager
import com.jaroszeek.gymtracker.data.health.HealthWriter
import com.jaroszeek.gymtracker.data.health.WatchLinker
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class GymTrackerApp : Application() {

    @Inject lateinit var healthWriter: HealthWriter

    @Inject lateinit var watchLinker: WatchLinker

    override fun onCreate() {
        // A confirmed import becomes the database before anything opens it (A5.10)
        BackupManager.applyPending(BackupFiles(this))
        super.onCreate()
        // The texts are Polish only, and plurals follow the app's locale, not the strings' (D-078)
        getSystemService(LocaleManager::class.java).run {
            if (applicationLocales.isEmpty) {
                applicationLocales = LocaleList.forLanguageTags(APP_LOCALE)
                // This first process started in the system's locale, which dates and numbers follow
                Locale.setDefault(Locale.forLanguageTag(APP_LOCALE))
            }
        }
        healthWriter.start()
        // After the writer, so it hears of each workout linked here and removes its Health Connect copy
        watchLinker.start()
    }
}

/** The texts are Polish only (D-078). */
const val APP_LOCALE = "pl-PL"

