package com.jaroszeek.gymtracker.feature.workout

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.feature.routines.massLabel
import com.jaroszeek.gymtracker.feature.routines.repsLabel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/** "4:05" under an hour, otherwise "1:04:05". */
fun durationLabel(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / MILLIS_PER_SECOND
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds % SECONDS_PER_MINUTE)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds % SECONDS_PER_MINUTE)
    }
}

/** A countdown rounds up, so it reads 0:01 until the very end. */
fun countdownLabel(millis: Long): String = durationLabel((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND * MILLIS_PER_SECOND)

/** Whole numbers with the locale's grouping: "8 450". */
fun volumeLabel(kg: Double): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(kg.roundToLong())

@Composable
@ReadOnlyComposable
fun setValueLabel(weightKg: Double?, reps: Int): String =
    if (weightKg == null) stringResource(R.string.workout_set_value_bodyweight, reps) else stringResource(R.string.workout_set_value, massLabel(weightKg), reps)

@StringRes
fun SetType.label(): Int = when (this) {
    SetType.Warmup -> R.string.set_type_warmup
    SetType.Working -> R.string.set_type_working
    SetType.DropSet -> R.string.set_type_drop
    SetType.Failure -> R.string.set_type_failure
}

/** "3 × 8–12 • 60 kg", without the rest. */
@Composable
@ReadOnlyComposable
fun RoutineTargets.compact(): String = listOfNotNull(
    stringResource(R.string.routine_target_sets_reps, sets, repsLabel()),
    weightKg?.let { massLabel(it) },
).joinToString(" • ")

/** Wall-clock time refreshed on every [periodMillis] boundary, so all clocks on screen tick together. Read it in small composables. */
@Composable
fun rememberNow(periodMillis: Long = MILLIS_PER_SECOND): Long {
    val now by produceState(System.currentTimeMillis(), periodMillis) {
        while (true) {
            delay(periodMillis - System.currentTimeMillis() % periodMillis)
            value = System.currentTimeMillis()
        }
    }
    return now
}
