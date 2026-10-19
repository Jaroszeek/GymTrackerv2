package com.jaroszeek.gymtracker.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

// Copies of Health Connect records from other apps; ids are Health Connect's, times epoch millis UTC

/** An exercise session, with its calories summed by Health Connect for its span. */
@Entity(tableName = "sessions", indices = [Index(value = ["start_time"]), Index(value = ["stale"])])
data class HealthSessionEntity(
    @PrimaryKey val id: String,
    /** Package of the app that wrote it. */
    val origin: String,
    @ColumnInfo(name = "exercise_type") val exerciseType: Int,
    val title: String?,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "active_kcal") val activeKcal: Double? = null,
    @ColumnInfo(name = "total_kcal") val totalKcal: Double? = null,
    /** Its heart rate and calories need reading again. */
    val stale: Boolean = true,
)

/** Heart rate during a session, at most every 5 s. */
@Entity(
    tableName = "heart_rate",
    primaryKeys = ["session_id", "time"],
    foreignKeys = [ForeignKey(entity = HealthSessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["record_id"])],
)
data class HeartRateSampleEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    val time: Long,
    val bpm: Int,
    /** The heart-rate record it came from: when that one is deleted, the session is read again. */
    @ColumnInfo(name = "record_id") val recordId: String,
)

/** A body or recovery value; [type] is [com.jaroszeek.gymtracker.domain.health.HealthMetric.key]. */
@Entity(tableName = "measurements", indices = [Index(value = ["type", "time"])])
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val type: String,
    val time: Long,
    val value: Double,
    val origin: String,
)

@Entity(tableName = "sleep", indices = [Index(value = ["end_time"])])
data class SleepEntity(
    @PrimaryKey val id: String,
    val origin: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    /** Without awake stages; the whole span when the app wrote no stages. */
    @ColumnInfo(name = "asleep_minutes") val asleepMinutes: Int,
)

/** Totals of a local day ([day] is its epoch day), summed by Health Connect across apps. */
@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey val day: Long,
    val steps: Long?,
    @ColumnInfo(name = "distance_m") val distanceMeters: Double?,
    @ColumnInfo(name = "active_kcal") val activeKcal: Double?,
    @ColumnInfo(name = "total_kcal") val totalKcal: Double?,
)

/** A day whose totals changed in Health Connect and are summed again on the next sync. */
@Entity(tableName = "dirty_days")
data class DirtyDayEntity(@PrimaryKey val day: Long)

/** Where the incremental sync of a record type goes on from. */
@Entity(tableName = "sync_tokens")
data class SyncTokenEntity(@PrimaryKey @ColumnInfo(name = "record_type") val recordType: String, val token: String)
