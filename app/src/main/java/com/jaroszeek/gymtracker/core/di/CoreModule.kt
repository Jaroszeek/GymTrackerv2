package com.jaroszeek.gymtracker.core.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.jaroszeek.gymtracker.core.database.ExerciseDao
import com.jaroszeek.gymtracker.core.database.GtDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GtDatabase =
        Room.databaseBuilder<GtDatabase>(context, GtDatabase.NAME)
            .setDriver(AndroidSQLiteDriver())
            .build()

    @Provides
    fun exerciseDao(database: GtDatabase): ExerciseDao = database.exerciseDao()
}
