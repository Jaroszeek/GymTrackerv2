package com.jaroszeek.gymtracker.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** How much was imported, for the Health Connect screen. */
data class HealthCounts(val sessions: Int, val measurements: Int, val nights: Int, val days: Int)

data class HeartRateRow(val time: Long, val bpm: Int)

@Dao
interface HealthDao {
    // Sessions

    @Query("SELECT * FROM sessions WHERE id IN (:ids)")
    suspend fun sessions(ids: List<String>): List<HealthSessionEntity>

    @Upsert
    suspend fun upsertSessions(sessions: List<HealthSessionEntity>)

    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteSessions(ids: List<String>)

    @Query("SELECT id FROM sessions")
    suspend fun sessionIds(): List<String>

    @Query("UPDATE sessions SET stale = 1 WHERE start_time < :end AND end_time > :start")
    suspend fun markSessionsStale(start: Long, end: Long)

    @Query("UPDATE sessions SET stale = 1 WHERE end_time > :since")
    suspend fun markSessionsStaleSince(since: Long)

    @Query("UPDATE sessions SET stale = 1 WHERE id IN (SELECT session_id FROM heart_rate WHERE record_id IN (:recordIds))")
    suspend fun markStaleByHeartRate(recordIds: List<String>)

    /** Newest first: the ones the user most likely opens. */
    @Query("SELECT * FROM sessions WHERE stale = 1 ORDER BY start_time DESC LIMIT :limit")
    suspend fun staleSessions(limit: Int): List<HealthSessionEntity>

    @Query("UPDATE sessions SET active_kcal = :activeKcal, total_kcal = :totalKcal, stale = 0 WHERE id = :id")
    suspend fun setRefreshed(id: String, activeKcal: Double?, totalKcal: Double?)

    @Query("DELETE FROM heart_rate WHERE session_id = :sessionId")
    suspend fun deleteHeartRate(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartRate(samples: List<HeartRateSampleEntity>)

    @Query("SELECT * FROM sessions ORDER BY start_time DESC")
    fun observeSessions(): Flow<List<HealthSessionEntity>>

    @Query("SELECT * FROM sessions WHERE start_time BETWEEN :from AND :to ORDER BY start_time")
    fun observeSessionsStartedBetween(from: Long, to: Long): Flow<List<HealthSessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: String): Flow<HealthSessionEntity?>

    @Query("SELECT time, bpm FROM heart_rate WHERE session_id = :sessionId ORDER BY time")
    fun observeHeartRate(sessionId: String): Flow<List<HeartRateRow>>

    // Measurements and sleep

    @Upsert
    suspend fun upsertMeasurements(measurements: List<MeasurementEntity>)

    @Query("DELETE FROM measurements WHERE id IN (:ids)")
    suspend fun deleteMeasurements(ids: List<String>)

    @Query("SELECT id FROM measurements WHERE type = :type")
    suspend fun measurementIds(type: String): List<String>

    @Query("SELECT * FROM measurements WHERE type IN (:types) AND time >= :from ORDER BY time")
    fun observeMeasurements(types: List<String>, from: Long): Flow<List<MeasurementEntity>>

    @Upsert
    suspend fun upsertSleep(sleep: List<SleepEntity>)

    @Query("DELETE FROM sleep WHERE id IN (:ids)")
    suspend fun deleteSleep(ids: List<String>)

    @Query("SELECT id FROM sleep")
    suspend fun sleepIds(): List<String>

    @Query("SELECT * FROM sleep WHERE end_time >= :from ORDER BY end_time")
    fun observeSleep(from: Long): Flow<List<SleepEntity>>

    // Daily totals

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markDirty(days: List<DirtyDayEntity>)

    @Query("SELECT day FROM dirty_days ORDER BY day")
    suspend fun dirtyDays(): List<Long>

    @Query("DELETE FROM dirty_days WHERE day BETWEEN :from AND :to")
    suspend fun clearDirty(from: Long, to: Long)

    @Upsert
    suspend fun upsertDays(days: List<DailyActivityEntity>)

    @Query("DELETE FROM daily_activity WHERE day BETWEEN :from AND :to")
    suspend fun deleteDays(from: Long, to: Long)

    @Query("SELECT * FROM daily_activity WHERE day BETWEEN :from AND :to ORDER BY day")
    fun observeDays(from: Long, to: Long): Flow<List<DailyActivityEntity>>

    // Sync

    @Query("SELECT token FROM sync_tokens WHERE record_type = :recordType")
    suspend fun token(recordType: String): String?

    @Upsert
    suspend fun saveToken(token: SyncTokenEntity)

    @Query("DELETE FROM sync_tokens WHERE record_type = :recordType")
    suspend fun deleteToken(recordType: String)

    @Query("DELETE FROM sync_tokens")
    suspend fun deleteTokens()

    @Query(
        "SELECT (SELECT COUNT(*) FROM sessions) AS sessions, (SELECT COUNT(*) FROM measurements) AS measurements, " +
            "(SELECT COUNT(*) FROM sleep) AS nights, (SELECT COUNT(*) FROM daily_activity) AS days",
    )
    fun observeCounts(): Flow<HealthCounts>

    // Disconnecting removes everything (heart rate goes with the sessions)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM measurements")
    suspend fun deleteAllMeasurements()

    @Query("DELETE FROM sleep")
    suspend fun deleteAllSleep()

    @Query("DELETE FROM daily_activity")
    suspend fun deleteAllDays()

    @Query("DELETE FROM dirty_days")
    suspend fun deleteAllDirtyDays()
}
