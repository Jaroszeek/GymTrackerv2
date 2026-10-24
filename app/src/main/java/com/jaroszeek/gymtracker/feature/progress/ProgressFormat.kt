package com.jaroszeek.gymtracker.feature.progress

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.ChartPoint
import com.jaroszeek.gymtracker.designsystem.component.NumberInput
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.progress.Week
import com.jaroszeek.gymtracker.domain.settings.WeightUnit
import com.jaroszeek.gymtracker.feature.routines.LocalWeightUnit
import com.jaroszeek.gymtracker.feature.routines.MASS_DECIMALS
import com.jaroszeek.gymtracker.feature.routines.format
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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

/** "Wtorek, 20 października": today on Start. */
fun todayLabel(date: LocalDate): String = formatter("EEEE, d MMMM").format(date).replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** "P", "W", … in [TextStyle.NARROW_STANDALONE], "pon.", … in [TextStyle.SHORT_STANDALONE]. */
fun weekdayLabel(day: DayOfWeek, style: TextStyle): String = day.getDisplayName(style, Locale.getDefault())

/** Whole numbers with the locale's grouping: "12 400". */
fun countLabel(value: Long): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

/** "Październik 2026". */
fun monthLabel(month: YearMonth): String = formatter("LLLL yyyy").format(month).replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** "13–19 paź", or "27 paź – 2 lis" across months. */
@Composable
@ReadOnlyComposable
fun weekLabel(week: Week, zone: ZoneId = ZoneId.systemDefault()): String {
    val start = week.monday
    val end = start.plusDays(6)
    val last = formatter(if (end.thisYear(zone)) "d MMM" else "d MMM yyyy").format(end)
    return if (start.month == end.month) {
        stringResource(R.string.week_range_days, start.dayOfMonth, last)
    } else {
        stringResource(R.string.week_range, formatter("d MMM").format(start), last)
    }
}

/** Estimates get one decimal: "116,7". */
fun e1rmLabel(kg: Double): String = NumberInput.format(kg, decimals = 1, DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator)

/** "102,5 kg" for a chart value already in the shown unit ([shownIn]); charts call it outside composition. 0 [decimals] for volumes. */
@Composable
fun rememberMassLabel(decimals: Int = MASS_DECIMALS): (Double) -> String {
    val resources = LocalResources.current
    val unit = LocalWeightUnit.current
    return remember(resources, unit, decimals) { { value -> unit.format(resources, value, decimals) } }
}

/** Masses in kg to the shown unit, so a chart's round ticks are round in that unit. */
fun List<ChartPoint>.shownIn(unit: WeightUnit): List<ChartPoint> = if (unit == WeightUnit.Kg) this else map { it.copy(value = unit.fromKg(it.value)) }

@StringRes
fun RecordType.label(): Int = when (this) {
    RecordType.Weight -> R.string.record_type_weight
    RecordType.E1rm -> R.string.record_type_e1rm
    RecordType.Reps -> R.string.record_type_reps
    RecordType.SetVolume -> R.string.record_type_set_volume
    RecordType.SessionVolume -> R.string.record_type_session_volume
}
