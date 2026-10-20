package com.jaroszeek.gymtracker.core.database

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A body value entered in the app (A5.8); [type] is [com.jaroszeek.gymtracker.domain.body.BodyMetric.key],
 * [time] epoch millis UTC. Values from Health Connect stay in the health database.
 */
@Entity(tableName = "body_measurements", indices = [Index(value = ["type", "time"])])
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val time: Long,
    val value: Double,
)
