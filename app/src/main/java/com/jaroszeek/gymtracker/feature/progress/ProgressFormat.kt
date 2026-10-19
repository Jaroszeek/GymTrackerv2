package com.jaroszeek.gymtracker.feature.progress

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.NumberInput
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.progress.Week
import com.jaroszeek.gymtracker.feature.routines.weightLabel
import com.jaroszeek.gymtracker.feature.workout.volumeLabel
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Times are stored in UTC and shown in the device zone

private fun formatter(pattern: String) = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())

private fun Long.date(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

private fun LocalDate.thisYear(zone: ZoneId) = year == LocalDate.now(zone).year

/** "15 paź", with the year outside this year. */
fun shortDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = millis.date(zone)
    return formatter(if (date.thisYear(zone)) "d MMM" else "d MMM yyyy").format(date)
}

/** "śr., 15 października", with the year outside this year. */
fun dayLabel(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = millis.date(zone)
    return formatter(if (date.thisYear(zone)) "EEE, d MMMM" else "EEE, d MMMM yyyy").format(date)
}

/** "środa, 15 października 2026". */
fun fullDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = formatter("EEEE, d MMMM yyyy").format(millis.date(zone))

fun timeLabel(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = formatter("HH:mm").format(Instant.ofEpochMilli(millis).atZone(zone))

/** "Październik 2026". */
fun monthLabel(month: YearMonth): String = formatter("LLLL yyyy").format(month).replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** "13–19 paź", or "27 paź – 2 lis" across months. */
fun weekLabel(week: Week, zone: ZoneId = ZoneId.systemDefault()): String {
    val start = week.monday
    val end = start.plusDays(6)
    val year = if (end.thisYear(zone)) "" else " yyyy"
    return if (start.month == end.month) {
        "${start.dayOfMonth}–${formatter("d MMM$year").format(end)}"
    } else {
        "${formatter("d MMM").format(start)} – ${formatter("d MMM$year").format(end)}"
    }
}

/** Estimates get one decimal: "116,7". */
fun e1rmLabel(kg: Double): String = NumberInput.format(kg, decimals = 1, DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator)

/** "102,5 kg" for chart headers and records, which run outside composition. */
@Composable
fun rememberKgLabel(decimals: Int = 2): (Double) -> String {
    val resources = LocalResources.current
    return remember(resources, decimals) {
        { kg -> resources.getString(R.string.value_kg, if (decimals == 1) e1rmLabel(kg) else weightLabel(kg)) }
    }
}

/** "12 500 kg": volumes are whole and grouped. */
@Composable
fun rememberVolumeLabel(): (Double) -> String {
    val resources = LocalResources.current
    return remember(resources) { { kg -> resources.getString(R.string.summary_volume_value, volumeLabel(kg)) } }
}

@StringRes
fun RecordType.label(): Int = when (this) {
    RecordType.Weight -> R.string.record_type_weight
    RecordType.E1rm -> R.string.record_type_e1rm
    RecordType.Reps -> R.string.record_type_reps
    RecordType.SetVolume -> R.string.record_type_set_volume
    RecordType.SessionVolume -> R.string.record_type_session_volume
}
