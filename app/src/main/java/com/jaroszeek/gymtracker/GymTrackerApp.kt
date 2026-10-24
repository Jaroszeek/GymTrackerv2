package com.jaroszeek.gymtracker

import android.app.Application
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
        healthWriter.start()
    }
}
