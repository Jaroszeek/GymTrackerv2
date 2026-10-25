package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtAlpha
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAccessor
import java.util.Locale

private const val MAX_HOUR = 23.0
private const val MAX_MINUTE = 59.0
private const val MINUTE_STEP = 5.0

/**
 * Picks a date and a 24-hour time in [zone]: a month grid with hour and minute fields in a
 * [GlassBottomSheet], an own layer like every overlay here and not an M3 dialog window (D-006, D-019).
 * Days after [max] are disabled and a time after it cannot be confirmed. Times are epoch millis UTC.
 * The picker starts at the [value] it opens with.
 */
@Composable
fun GlassDateTimeSheet(
    visible: Boolean,
    value: Long,
    title: String,
    onConfirm: (Long) -> Unit,
    onDismissRequest: () -> Unit,
    max: Long? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    GlassBottomSheet(visible = visible, onDismissRequest = onDismissRequest) {
        DateTimePicker(value, title, onConfirm, max, zone)
    }
}

/** Matte field like [GlassTextField] showing a date and time; a tap opens its [GlassDateTimeSheet]. */
@Composable
fun GlassDateTimeField(value: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    val interaction = remember { MutableInteractionSource() }
    // One node with the label for TalkBack; the ripple only on the field
    Column(
        modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.xs),
    ) {
        Text(label, color = colors.textSecondary, style = GtTheme.typography.labelSmall)
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = GtSizes.MinTouch)
                .clip(GtShapes.Medium)
                .background(colors.surfaceRaised)
                .specularEdge(GtShapes.Medium, colors)
                .indication(interaction, LocalIndication.current)
                .padding(horizontal = GtSpacing.l, vertical = GtSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GtSpacing.s),
        ) {
            Text(value, Modifier.weight(1f), style = GtTheme.typography.body)
            Icon(painterResource(R.drawable.ic_calendar_month), contentDescription = null, Modifier.size(GtSizes.IconSmall), tint = colors.textSecondary)
        }
    }
}

@Composable
private fun ColumnScope.DateTimePicker(value: Long, title: String, onConfirm: (Long) -> Unit, max: Long?, zone: ZoneId) {
    val colors = GtTheme.colors
    // Not keyed on value: the sheet keeps what it opened with while it animates out
    val start = remember { Instant.ofEpochMilli(value).atZone(zone) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var month by remember { mutableStateOf(YearMonth.from(start)) }
    var hour by remember { mutableIntStateOf(start.hour) }
    var minute by remember { mutableIntStateOf(start.minute) }
    val today = remember { LocalDate.now(zone) }
    val lastDay = max?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val picked = DateTimeInput.millis(date, hour, minute, zone)
    val future = max != null && picked > max

    Text(title, style = GtTheme.typography.headline)
    // Scrolls with a large font or in landscape, so the button stays on screen
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.m),
    ) {
        Text(format(DateTimeInput.PICKED_PATTERN, Instant.ofEpochMilli(picked).atZone(zone)), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(R.drawable.ic_chevron_left, stringResource(R.string.date_time_previous_month), onClick = { month = month.minusMonths(1) }, style = GlassButtonStyle.Ghost)
            Text(
                monthLabel(month),
                Modifier.weight(1f),
                style = GtTheme.typography.title,
                textAlign = TextAlign.Center,
            )
            GlassIconButton(
                R.drawable.ic_chevron_right,
                stringResource(R.string.date_time_next_month),
                onClick = { month = month.plusMonths(1) },
                style = GlassButtonStyle.Ghost,
                enabled = lastDay == null || month < YearMonth.from(lastDay),
            )
        }
        MonthGrid(month, selected = date, today = today, lastDay = lastDay, onSelect = { date = it })
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            // An emptied field keeps the last value
            GlassNumberField(
                value = hour.toDouble(),
                onValueChange = { it?.let { v -> hour = v.toInt() } },
                label = stringResource(R.string.date_time_hour),
                modifier = Modifier.weight(1f),
                range = 0.0..MAX_HOUR,
            )
            GlassNumberField(
                value = minute.toDouble(),
                onValueChange = { it?.let { v -> minute = v.toInt() } },
                label = stringResource(R.string.date_time_minute),
                modifier = Modifier.weight(1f),
                step = MINUTE_STEP,
                range = 0.0..MAX_MINUTE,
                minDigits = 2,
            )
        }
    }
    if (future) Text(stringResource(R.string.date_time_future), color = colors.danger, style = GtTheme.typography.bodySmall)
    GlassButton(
        stringResource(R.string.action_done),
        // Unchanged gives back the value itself, seconds included, so confirming changes nothing
        onClick = { onConfirm(if (date == start.toLocalDate() && hour == start.hour && minute == start.minute) value else picked) },
        enabled = !future,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate, today: LocalDate, lastDay: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val colors = GtTheme.colors
    Column(Modifier.selectableGroup()) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Text(
                    weekdayLabel(it),
                    Modifier.weight(1f),
                    color = colors.textTertiary,
                    style = GtTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Always six rows, so the arrows stay put from month to month
        DateTimeInput.weeks(month).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(day, selected = day == selected, today = day == today, enabled = lastDay == null || (day != null && day <= lastDay), onClick = { day?.let(onSelect) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate?, selected: Boolean, today: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = GtTheme.colors
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        if (day == null) return@Box
        val description = format(DateTimeInput.DAY_PATTERN, day)
        Box(
            Modifier
                .fillMaxSize()
                .padding(GtSpacing.xxs)
                .clip(GtShapes.Pill)
                .then(if (selected) Modifier.background(colors.accent) else Modifier)
                .then(if (today && !selected) Modifier.border(GtSizes.TodayOutline, colors.accentText, GtShapes.Pill) else Modifier)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(if (enabled) 1f else GtAlpha.DISABLED),
                color = when {
                    selected -> colors.onAccent
                    today -> colors.accentText
                    else -> colors.textPrimary
                },
                style = GtTheme.typography.label,
            )
        }
    }
}

// Outside composables, like the app's other date labels: the locale changes only with a new activity
private fun format(pattern: String, value: TemporalAccessor): String = DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(value)

/** "Październik 2026". */
private fun monthLabel(month: YearMonth): String = format(DateTimeInput.MONTH_PATTERN, month).replaceFirstChar { it.titlecase(Locale.getDefault()) }

private fun weekdayLabel(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault())

/** Date arithmetic behind [GlassDateTimeSheet]; pure, so it is unit tested. */
internal object DateTimeInput {
    const val PICKED_PATTERN = "EEEE, d MMMM yyyy, HH:mm"
    const val DAY_PATTERN = "EEEE, d MMMM yyyy"
    const val MONTH_PATTERN = "LLLL yyyy"
    private const val WEEKS = 6

    /** A time that does not exist on that day (the clock jumping forward) moves forward with the clock. */
    fun millis(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** Six Monday-first weeks of [month], with nulls before its first and after its last day. */
    fun weeks(month: YearMonth): List<List<LocalDate?>> {
        val days = DayOfWeek.entries.size
        val lead = month.atDay(1).dayOfWeek.value - 1
        return List(WEEKS * days) { index -> (index - lead + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay) }.chunked(days)
    }
}

// The sheet's content alone: the sheet itself animates in, so a static preview would show it closed
@Preview
@Composable
private fun DateTimePickerPreview() {
    val zone = ZoneId.of("Europe/Warsaw")
    val value = 1_792_859_400_000 // 24 Oct 2026, 18:30
    GymTrackerTheme {
        GlassScaffold {
            Column(Modifier.padding(GtSpacing.xxl), verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                GlassDateTimeField(format(DateTimeInput.PICKED_PATTERN, Instant.ofEpochMilli(value).atZone(zone)), stringResource(R.string.date_time_title), onClick = {})
                DateTimePicker(value, stringResource(R.string.date_time_title), onConfirm = {}, max = value + 86_400_000, zone = zone)
            }
        }
    }
}
