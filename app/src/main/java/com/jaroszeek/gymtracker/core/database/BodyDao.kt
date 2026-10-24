package com.jaroszeek.gymtracker.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyDao {
    @Insert
    suspend fun insert(measurement: BodyMeasurementEntity): Long

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM body_measurements ORDER BY time")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>
}
