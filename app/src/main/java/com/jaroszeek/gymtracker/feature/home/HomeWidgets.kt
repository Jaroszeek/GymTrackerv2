package com.jaroszeek.gymtracker.feature.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.BadgeTone
import com.jaroszeek.gymtracker.designsystem.component.ChartPoint
import com.jaroszeek.gymtracker.designsystem.component.GlassBadge
import com.jaroszeek.gymtracker.designsystem.component.GlassBarChart
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.GlassSparkline
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.body.BmiCategory
import com.jaroszeek.gymtracker.domain.body.BodyMeasurement
import com.jaroszeek.gymtracker.domain.body.BodyMetric
import com.jaroszeek.gymtracker.domain.body.BodySummary
import com.jaroszeek.gymtracker.domain.body.bmiCategory
import com.jaroszeek.gymtracker.domain.home.WeekStats
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.routine.Routine
import com.jaroszeek.gymtracker.domain.workout.ExerciseRecords
import com.jaroszeek.gymtracker.domain.workout.WorkoutListItem
import com.jaroszeek.gymtracker.domain.workout.WorkoutSummary
import com.jaroszeek.gymtracker.feature.profile.bodyValueLabel
import com.jaroszeek.gymtracker.feature.profile.decimalLabel
import com.jaroszeek.gymtracker.feature.profile.label
import com.jaroszeek.gymtracker.feature.progress.RecordBadges
import com.jaroszeek.gymtracker.feature.progress.StatColumn
import com.jaroszeek.gymtracker.feature.progress.SummaryRow
import com.jaroszeek.gymtracker.feature.progress.countLabel
import com.jaroszeek.gymtracker.feature.progress.dayLabel
import com.jaroszeek.gymtracker.feature.progress.monthLabel
import com.jaroszeek.gymtracker.feature.progress.shortDate
import com.jaroszeek.gymtracker.feature.progress.timeLabel
import com.jaroszeek.gymtracker.feature.progress.weekdayLabel
import com.jaroszeek.gymtracker.feature.workout.durationLabel
import com.jaroszeek.gymtracker.feature.workout.volumeLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

// The widgets of Start (A5.1): stateless cards, laid out by HomeScreen

/** A widget's card: an icon and title row, then [content]; [trailing] goes at the end of the title row. */
@Composable
private fun WidgetCard(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(GtSizes.IconSmall), tint = GtTheme.colors.accentText)
            Text(title, Modifier.weight(1f), style = GtTheme.typography.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing()
        }
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = GtTheme.colors.textSecondary, style = GtTheme.typography.bodySmall)
}

@Composable
fun QuickStartCard(
    quickStart: QuickStart,
    onStart: (Routine) -> Unit,
    onStartEmpty: () -> Unit,
    onOpenWorkout: () -> Unit,
    onChooseRoutine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GtTheme.colors
    WidgetCard(R.drawable.ic_play_arrow, stringResource(R.string.home_quick_title), modifier) {
        when (quickStart) {
            is QuickStart.Running -> {
                Text(quickStart.name, style = GtTheme.typography.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.routine_meta, stringResource(R.string.home_quick_running), timeLabel(quickStart.startedAt)),
                    color = colors.textSecondary,
                    style = GtTheme.typography.label,
                )
                GlassButton(stringResource(R.string.action_open_workout), onClick = onOpenWorkout, icon = R.drawable.ic_play_arrow)
            }
            is QuickStart.Proposed -> {
                val routine = quickStart.routine
                Text(routine.name, style = GtTheme.typography.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(
                        R.string.routine_meta,
                        stringResource(R.string.home_quick_proposed),
                        pluralStringResource(R.plurals.routine_exercises, routine.blocks.size, routine.blocks.size),
                    ),
                    color = colors.textSecondary,
                    style = GtTheme.typography.label,
                )
                Buttons {
                    GlassButton(stringResource(R.string.action_start), onClick = { onStart(routine) }, icon = R.drawable.ic_play_arrow)
                    GlassButton(stringResource(R.string.home_quick_other), onClick = onChooseRoutine, style = GlassButtonStyle.Secondary)
                }
            }
            QuickStart.NoRoutines -> {
                Hint(stringResource(R.string.home_quick_empty))
                Buttons {
                    GlassButton(stringResource(R.string.routines_start_empty), onClick = onStartEmpty, icon = R.drawable.ic_play_arrow)
                    GlassButton(stringResource(R.string.home_quick_create), onClick = onChooseRoutine, style = GlassButtonStyle.Secondary)
                }
            }
        }
    }
}

/** Buttons side by side, wrapping under large fonts. */
@Composable
private fun Buttons(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.padding(top = GtSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(GtSpacing.s),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.s),
    ) { content() }
}

/** The weekly streak and this week's totals. */
@Composable
fun HeroCard(streak: Int, week: WeekStats, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    WidgetCard(R.drawable.ic_local_fire_department, stringResource(R.string.home_streak_title), modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            Text("$streak", color = colors.accentText, style = GtTheme.typography.numberXL)
            Text(
                pluralStringResource(R.plurals.home_streak_weeks, streak),
                Modifier.padding(bottom = GtSpacing.s),
                style = GtTheme.typography.title,
            )
        }
        Hint(
            stringResource(
                when {
                    week.workouts > 0 -> R.string.home_streak_done
                    streak > 0 -> R.string.home_streak_pending
                    else -> R.string.home_streak_none
                },
            ),
        )
        Text(stringResource(R.string.home_week_title), Modifier.padding(top = GtSpacing.s), color = colors.textSecondary, style = GtTheme.typography.label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            StatColumn("${week.workouts}", stringResource(R.string.home_week_workouts), Modifier.weight(1f))
            StatColumn(durationLabel(week.durationMillis), stringResource(R.string.summary_duration), Modifier.weight(1f))
            StatColumn("${week.sets}", stringResource(R.string.progress_sets), Modifier.weight(1f))
            StatColumn(stringResource(R.string.summary_volume_value, volumeLabel(week.volumeKg)), stringResource(R.string.summary_volume), Modifier.weight(1.3f))
        }
    }
}

@Composable
fun LastWorkoutCard(workout: WorkoutListItem?, onOpen: (Long) -> Unit, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    WidgetCard(R.drawable.ic_fitness_center, stringResource(R.string.home_last_title), modifier, onClick = workout?.let { { onOpen(it.id) } }) {
        if (workout == null) {
            Hint(stringResource(R.string.home_last_empty))
            GlassButton(stringResource(R.string.home_last_start), onClick = onStart, style = GlassButtonStyle.Secondary, icon = R.drawable.ic_play_arrow)
            return@WidgetCard
        }
        Text(workout.name, style = GtTheme.typography.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.routine_meta, dayLabel(workout.startedAt), timeLabel(workout.startedAt)),
            color = colors.textSecondary,
            style = GtTheme.typography.label,
        )
        SummaryRow(workout.summary, Modifier.padding(top = GtSpacing.xs))
    }
}

@Composable
fun RecordsCard(records: List<ExerciseRecords>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    WidgetCard(R.drawable.ic_trophy, stringResource(R.string.home_records_title), modifier) {
        if (records.isEmpty()) {
            Hint(stringResource(R.string.home_records_empty))
            return@WidgetCard
        }
        records.take(MAX_RECORDS).forEach { record ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(GtShapes.Small)
                    .clickable { onOpen(record.workoutId) }
                    .padding(vertical = GtSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(GtSpacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                    Text(record.exerciseName, Modifier.weight(1f), style = GtTheme.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(shortDate(record.startedAt), color = colors.textTertiary, style = GtTheme.typography.labelSmall)
                }
                RecordBadges(record.types)
            }
        }
        val more = records.size - MAX_RECORDS
        if (more > 0) Text(pluralStringResource(R.plurals.home_records_more, more, more), color = colors.textSecondary, style = GtTheme.typography.labelSmall)
    }
}

private const val MAX_RECORDS = 5

/** A month with the workout days filled and today ringed; the arrows go back in time, up to this month. */
@Composable
fun ActivityCalendarCard(workoutDays: Set<LocalDate>, today: LocalDate, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    val current = YearMonth.from(today)
    var month by rememberSaveable { mutableStateOf(current) }
    WidgetCard(
        R.drawable.ic_calendar_month,
        monthLabel(month),
        modifier,
        trailing = {
            GlassIconButton(R.drawable.ic_chevron_left, stringResource(R.string.home_calendar_previous), onClick = { month = month.minusMonths(1) }, style = GlassButtonStyle.Ghost)
            GlassIconButton(
                R.drawable.ic_chevron_right,
                stringResource(R.string.home_calendar_next),
                onClick = { month = month.plusMonths(1) },
                style = GlassButtonStyle.Ghost,
                enabled = month < current,
            )
        },
    ) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Text(
                    weekdayLabel(it, TextStyle.NARROW_STANDALONE),
                    Modifier.weight(1f),
                    color = colors.textTertiary,
                    style = GtTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
        val first = month.atDay(1)
        val cells = List(first.dayOfWeek.value - 1) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(DayOfWeek.entries.size).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day -> DayCell(day, worked = day in workoutDays, today = day == today, Modifier.weight(1f)) }
                repeat(DayOfWeek.entries.size - week.size) { Box(Modifier.weight(1f)) }
            }
        }
        val count = workoutDays.count { YearMonth.from(it) == month }
        Text(pluralStringResource(R.plurals.home_calendar_workouts, count, count), color = colors.textSecondary, style = GtTheme.typography.labelSmall)
    }
}

@Composable
private fun DayCell(day: LocalDate?, worked: Boolean, today: Boolean, modifier: Modifier) {
    val colors = GtTheme.colors
    Box(modifier.aspectRatio(1f).padding(GtSpacing.xxs), contentAlignment = Alignment.Center) {
        if (day == null) return@Box
        val description = when {
            worked -> stringResource(R.string.home_calendar_day, day.dayOfMonth.toString())
            today -> stringResource(R.string.home_calendar_today)
            else -> null
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(GtShapes.Pill)
                .then(if (worked) Modifier.background(colors.accent) else Modifier)
                .then(if (today) Modifier.border(GtSizes.TodayOutline, colors.accentText, GtShapes.Pill) else Modifier)
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                color = if (worked) colors.onAccent else if (today) colors.accentText else colors.textPrimary,
                style = GtTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun StepsCard(steps: List<Long>?, today: LocalDate, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    WidgetCard(R.drawable.ic_directions_walk, stringResource(R.string.home_steps_title), modifier) {
        if (steps == null) {
            Hint(stringResource(R.string.home_steps_connect))
            GlassButton(stringResource(R.string.health_connect), onClick = onConnect, style = GlassButtonStyle.Secondary, icon = R.drawable.ic_link)
            return@WidgetCard
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            Text(countLabel(steps.last()), style = GtTheme.typography.numberL)
            Text(stringResource(R.string.home_steps_today), Modifier.padding(bottom = GtSpacing.xs), color = colors.textSecondary, style = GtTheme.typography.label)
        }
        GlassBarChart(
            values = steps.map { it.toDouble() },
            labels = steps.indices.map { weekdayLabel(today.minusDays((steps.lastIndex - it).toLong()).dayOfWeek, TextStyle.SHORT_STANDALONE) },
            description = stringResource(R.string.home_steps_description, steps.joinToString { countLabel(it) }),
        )
    }
}

@Composable
fun BodyCard(body: BodySummary, weights: List<BodyMeasurement>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    WidgetCard(R.drawable.ic_monitor_weight, stringResource(R.string.home_body_title), modifier, onClick = onOpen) {
        val weight = body.weight
        if (weight == null) {
            Hint(stringResource(R.string.home_body_empty))
            GlassButton(stringResource(R.string.home_body_add), onClick = onOpen, style = GlassButtonStyle.Secondary, icon = R.drawable.ic_add)
            return@WidgetCard
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(GtSpacing.l)) {
            Column(Modifier.weight(1f)) {
                Text(bodyValueLabel(BodyMetric.Weight, weight.value), style = GtTheme.typography.numberL)
                Text(shortDate(weight.time), color = colors.textTertiary, style = GtTheme.typography.labelSmall)
            }
            val bmi = body.bmi
            if (bmi == null) {
                Text(stringResource(R.string.home_body_no_height), Modifier.weight(1f), color = colors.textSecondary, style = GtTheme.typography.bodySmall)
            } else {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
                    Text(stringResource(R.string.routine_meta, stringResource(R.string.bmi_label), decimalLabel(bmi)), style = GtTheme.typography.title)
                    val category = bmiCategory(bmi)
                    GlassBadge(stringResource(category.label()), tone = if (category == BmiCategory.Normal) BadgeTone.Accent else BadgeTone.Neutral)
                }
            }
        }
        if (weights.size >= 2) {
            GlassSparkline(
                points = weights.map { ChartPoint(it.time, it.value) },
                description = stringResource(
                    R.string.home_body_trend,
                    bodyValueLabel(BodyMetric.Weight, weights.first().value),
                    bodyValueLabel(BodyMetric.Weight, weights.last().value),
                ),
            )
        }
    }
}

@Preview
@Composable
private fun HomeWidgetsPreview() {
    val now = 1_760_000_000_000
    val day = 86_400_000L
    val today = LocalDate.of(2026, 10, 20)
    GymTrackerTheme {
        GlassScaffold {
            Column(Modifier.padding(GtSpacing.xl), verticalArrangement = Arrangement.spacedBy(GtSpacing.l)) {
                HeroCard(streak = 6, week = WeekStats(2, 6_300_000, 12_450.0, 34), onClick = {})
                LastWorkoutCard(WorkoutListItem(1, "Push", now, WorkoutSummary(3_600_000, 8_450.0, 18, 6)), onOpen = {}, onStart = {})
                RecordsCard(listOf(ExerciseRecords(1, now, 1, "Barbell Bench Press", setOf(RecordType.Weight, RecordType.E1rm))), onOpen = {})
                ActivityCalendarCard(setOf(today, today.minusDays(2), today.minusDays(5)), today)
                StepsCard(listOf(6_200, 9_800, 4_100, 0, 12_400, 8_000, 3_512), today, onConnect = {})
                BodyCard(
                    BodySummary(
                        weight = BodyMeasurement(BodyMetric.Weight, now, 82.4, manualId = 1),
                        height = BodyMeasurement(BodyMetric.Height, now, 1.82, manualId = 2),
                    ),
                    weights = List(12) { BodyMeasurement(BodyMetric.Weight, now - (12 - it) * 7 * day, 84.0 - it * 0.15, manualId = it.toLong()) },
                    onOpen = {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun HomeWidgetsEmptyPreview() {
    GymTrackerTheme {
        GlassScaffold {
            Column(Modifier.padding(GtSpacing.xl), verticalArrangement = Arrangement.spacedBy(GtSpacing.l)) {
                QuickStartCard(QuickStart.NoRoutines, onStart = {}, onStartEmpty = {}, onOpenWorkout = {}, onChooseRoutine = {})
                HeroCard(streak = 0, week = WeekStats(), onClick = {})
                LastWorkoutCard(null, onOpen = {}, onStart = {})
                StepsCard(null, LocalDate.of(2026, 10, 20), onConnect = {})
                BodyCard(BodySummary(), emptyList(), onOpen = {})
            }
        }
    }
}
