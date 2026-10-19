package com.jaroszeek.gymtracker.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.jaroszeek.gymtracker.core.database.ExerciseDao
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.database.HealthDao
import com.jaroszeek.gymtracker.core.database.HealthDatabase
import com.jaroszeek.gymtracker.core.database.RoutineDao
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Lives as long as the process: work that must outlast screens, e.g. the active workout. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GtDatabase =
        Room.databaseBuilder<GtDatabase>(context, GtDatabase.NAME)
            .setDriver(AndroidSQLiteDriver())
            .build()

    @Provides
    fun exerciseDao(database: GtDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun routineDao(database: GtDatabase): RoutineDao = database.routineDao()

    @Provides
    @Singleton
    fun healthDatabase(@ApplicationContext context: Context): HealthDatabase =
        Room.databaseBuilder<HealthDatabase>(context, HealthDatabase.NAME)
            .setDriver(AndroidSQLiteDriver())
            .build()

    @Provides
    fun healthDao(database: HealthDatabase): HealthDao = database.healthDao()

    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    fun activeWorkoutStore(@ApplicationContext context: Context): ActiveWorkoutStore =
        ActiveWorkoutStore(File(context.filesDir, ACTIVE_WORKOUT_FILE))

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + io)) { context.preferencesDataStoreFile(PREFERENCES_FILE) }

    private const val ACTIVE_WORKOUT_FILE = "active_workout.json"
    private const val PREFERENCES_FILE = "preferences"
}
