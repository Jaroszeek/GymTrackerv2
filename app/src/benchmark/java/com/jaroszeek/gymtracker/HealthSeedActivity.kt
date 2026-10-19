package com.jaroszeek.gymtracker

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.lifecycle.lifecycleScope
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Benchmark build only (Etap 7 emulator tests): writes Health Connect data as a watch app would, under
 * another package than the debug build, which then imports it. A strength session with heart rate and
 * calories over [EXTRA_START]–[EXTRA_END] (epoch millis; default the last 50 minutes), a walk 3 h before,
 * and 9 days of sleep, resting heart rate, HRV, steps, distance and weight. Fixed client ids: seeding
 * the same span again changes nothing.
 *
 * adb shell am start -n com.jaroszeek.gymtracker.benchmark/com.jaroszeek.gymtracker.HealthSeedActivity --el start <ms> --el end <ms>
 */
class HealthSeedActivity : ComponentActivity() {

    private val zone = ZoneId.systemDefault()
    private val watch = Device(type = Device.TYPE_WATCH, manufacturer = "Samsung", model = "Galaxy Watch")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val now = Instant.now()
        val end = intent.getLongExtra(EXTRA_END, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli) ?: now.minusSeconds(60)
        val start = intent.getLongExtra(EXTRA_START, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli) ?: end.minus(Duration.ofMinutes(50))
        lifecycleScope.launch {
            try {
                val records = workout(start, end) + walk(start.minus(Duration.ofHours(3))) + days(now)
                HealthConnectClient.getOrCreate(this@HealthSeedActivity).insertRecords(records)
                Log.i(TAG, "Inserted ${records.size} records")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Seeding failed", e)
            }
            finish()
        }
    }

    private fun offset(time: Instant): ZoneOffset = zone.rules.getOffset(time)

    private fun meta(id: String) = Metadata.autoRecorded(watch, id, 1)

    private fun heartRate(start: Instant, end: Instant, base: Int, id: String): HeartRateRecord {
        val samples = generateSequence(start) { it.plusSeconds(SAMPLE_SECONDS) }.takeWhile { it < end }
            .mapIndexed { index, time -> HeartRateRecord.Sample(time, (base + HR_SWING * sin(index / HR_PERIOD)).toLong()) }
            .toList()
        return HeartRateRecord(start, offset(start), end, offset(end), samples, meta(id))
    }

    private fun workout(start: Instant, end: Instant): List<Record> {
        val id = "workout-${start.toEpochMilli()}"
        val minutes = Duration.between(start, end).toMinutes().coerceAtLeast(1)
        return listOf(
            ExerciseSessionRecord(
                start, offset(start), end, offset(end), meta(id),
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                title = "Siłownia",
            ),
            heartRate(start, end, base = 120, id = "$id-hr"),
            ActiveCaloriesBurnedRecord(start, offset(start), end, offset(end), Energy.kilocalories(minutes * 6.5), meta("$id-active")),
            TotalCaloriesBurnedRecord(start, offset(start), end, offset(end), Energy.kilocalories(minutes * 8.5), meta("$id-total")),
        )
    }

    private fun walk(start: Instant): List<Record> {
        val end = start.plus(Duration.ofMinutes(30))
        val id = "walk-${start.toEpochMilli()}"
        return listOf(
            ExerciseSessionRecord(start, offset(start), end, offset(end), meta(id), exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING),
            heartRate(start, end, base = 95, id = "$id-hr"),
        )
    }

    /** Today is a little worse than the 8 days before it, so the readiness has something to say. */
    private fun days(now: Instant): List<Record> {
        val today = LocalDate.now(zone)
        return (0L..8L).flatMap { back ->
            val day = today.minusDays(back)
            val worse = back == 0L
            buildList {
                val bed = day.minusDays(1).atTime(23, 0).atZone(zone).toInstant()
                val up = day.atTime(7, 0).atZone(zone).toInstant().minus(Duration.ofMinutes(if (worse) 70 else back % 3 * 10))
                if (up < now) add(sleep(bed, up, "sleep-$day"))
                val morning = day.atTime(7, 30).atZone(zone).toInstant()
                if (morning < now) {
                    add(RestingHeartRateRecord(morning, offset(morning), if (worse) 58 else 52 + back % 3, meta("rhr-$day")))
                    add(HeartRateVariabilityRmssdRecord(morning, offset(morning), if (worse) 44.0 else 55.0 - back % 4, meta("hrv-$day")))
                    if (back % 3 == 0L) add(WeightRecord(morning, offset(morning), Mass.kilograms(82.4 + back * 0.1), meta("weight-$day")))
                }
                val walkStart = day.atTime(LocalTime.of(8, 0)).atZone(zone).toInstant()
                val walkEnd = minOf(day.atTime(20, 0).atZone(zone).toInstant(), now.minusSeconds(60))
                if (walkEnd > walkStart) {
                    val steps = 6_000L + back * 700
                    add(StepsRecord(walkStart, offset(walkStart), walkEnd, offset(walkEnd), steps, meta("steps-$day")))
                    add(DistanceRecord(walkStart, offset(walkStart), walkEnd, offset(walkEnd), Length.meters(steps * 0.75), meta("distance-$day")))
                }
            }
        }
    }

    private fun sleep(bed: Instant, up: Instant, id: String): SleepSessionRecord {
        fun at(hours: Double) = bed.plusSeconds((hours * 3_600).toLong())
        val stages = listOf(
            SleepSessionRecord.Stage(bed, at(2.0), SleepSessionRecord.STAGE_TYPE_LIGHT),
            SleepSessionRecord.Stage(at(2.0), at(3.5), SleepSessionRecord.STAGE_TYPE_DEEP),
            SleepSessionRecord.Stage(at(3.5), at(4.5), SleepSessionRecord.STAGE_TYPE_REM),
            SleepSessionRecord.Stage(at(4.5), at(4.75), SleepSessionRecord.STAGE_TYPE_AWAKE),
            SleepSessionRecord.Stage(at(4.75), up, SleepSessionRecord.STAGE_TYPE_LIGHT),
        )
        return SleepSessionRecord(bed, offset(bed), up, offset(up), meta(id), stages = stages)
    }

    private companion object {
        const val TAG = "HealthSeed"
        const val EXTRA_START = "start"
        const val EXTRA_END = "end"
        const val SAMPLE_SECONDS = 5L
        const val HR_SWING = 30
        const val HR_PERIOD = 12.0
    }
}
