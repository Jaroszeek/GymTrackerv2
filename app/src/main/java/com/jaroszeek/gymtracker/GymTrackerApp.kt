package com.jaroszeek.gymtracker

import android.app.Application
import com.jaroszeek.gymtracker.data.health.HealthWriter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GymTrackerApp : Application() {

    @Inject lateinit var healthWriter: HealthWriter

    override fun onCreate() {
        super.onCreate()
        healthWriter.start()
    }
}
