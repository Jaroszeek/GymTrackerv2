package com.jaroszeek.gymtracker

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList
import com.jaroszeek.gymtracker.data.backup.BackupFiles
import com.jaroszeek.gymtracker.data.backup.BackupManager
import com.jaroszeek.gymtracker.data.health.HealthWriter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GymTrackerApp : Application() {

    @Inject lateinit var healthWriter: HealthWriter

    override fun onCreate() {
        // A confirmed import becomes the database before anything opens it (A5.10)
        BackupManager.applyPending(BackupFiles(this))
        super.onCreate()
        // The texts are Polish only, and plurals follow the app's locale, not the strings' (D-078)
        getSystemService(LocaleManager::class.java).run {
            if (applicationLocales.isEmpty) applicationLocales = LocaleList.forLanguageTags(APP_LOCALE)
        }
        healthWriter.start()
    }

    private companion object {
        const val APP_LOCALE = "pl-PL"
    }
}
