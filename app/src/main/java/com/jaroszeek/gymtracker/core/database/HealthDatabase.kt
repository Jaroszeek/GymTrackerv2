package com.jaroszeek.gymtracker.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * Data read from Health Connect, in its own file so the Android backup can leave it out: health data
 * never leaves the phone (D-060). Same rules as [GtDatabase]: migrations and their tests, exported schemas.
 */
@Database(
    entities = [
        HealthSessionEntity::class,
        HeartRateSampleEntity::class,
        MeasurementEntity::class,
        SleepEntity::class,
        DailyActivityEntity::class,
        DirtyDayEntity::class,
        SyncTokenEntity::class,
    ],
    version = 1,
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao

    companion object {
        const val NAME = "health.db"
    }
}
