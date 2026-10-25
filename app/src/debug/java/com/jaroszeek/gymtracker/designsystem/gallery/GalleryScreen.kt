package com.jaroszeek.gymtracker.designsystem.gallery

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.BadgeTone
import com.jaroszeek.gymtracker.designsystem.component.ChartPoint
import com.jaroszeek.gymtracker.designsystem.component.ChartRange
import com.jaroszeek.gymtracker.designsystem.component.ChartRangeSelector
import com.jaroszeek.gymtracker.designsystem.component.GlassBadge
import com.jaroszeek.gymtracker.designsystem.component.GlassBar
import com.jaroszeek.gymtracker.designsystem.component.GlassBarChart
import com.jaroszeek.gymtracker.designsystem.component.GlassBottomSheet
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassChip
import com.jaroszeek.gymtracker.designsystem.component.GlassDialog
import com.jaroszeek.gymtracker.designsystem.component.GlassFullScreenOverlay
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassLineChart
import com.jaroszeek.gymtracker.designsystem.component.GlassMiniBar
import com.jaroszeek.gymtracker.designsystem.component.GlassNavBar
import com.jaroszeek.gymtracker.designsystem.component.GlassNavItem
import com.jaroszeek.gymtracker.designsystem.component.GlassNumberField
import com.jaroszeek.gymtracker.designsystem.component.GlassPanel
import com.jaroszeek.gymtracker.designsystem.component.GlassProgressRing
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.GlassSearchField
import com.jaroszeek.gymtracker.designsystem.component.GlassSegmentedControl
import com.jaroszeek.gymtracker.designsystem.component.GlassSparkline
import com.jaroszeek.gymtracker.designsystem.component.GlassSwatch
import com.jaroszeek.gymtracker.designsystem.component.GlassSwitchRow
import com.jaroszeek.gymtracker.designsystem.component.GlassTextField
import com.jaroszeek.gymtracker.designsystem.component.GlassTopBar
import com.jaroszeek.gymtracker.designsystem.component.HideOnScrollState
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.component.within
import com.jaroszeek.gymtracker.designsystem.theme.GtPalette
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.ThemeMode
import com.jaroszeek.gymtracker.designsystem.theme.ThemeSettings
import com.jaroszeek.gymtracker.designsystem.theme.gtColors
import com.jaroszeek.gymtracker.feature.progress.weekdayLabel
import com.jaroszeek.gymtracker.feature.routines.weightLabel
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

private val NavItems = listOf(
    GlassNavItem(R.string.nav_home, R.drawable.ic_home, R.drawable.ic_home_filled),
    GlassNavItem(R.string.nav_exercises, R.drawable.ic_menu_book, R.drawable.ic_menu_book_filled),
    GlassNavItem(R.string.nav_workout, R.drawable.ic_exercise, R.drawable.ic_exercise_filled),
    GlassNavItem(R.string.nav_progress, R.drawable.ic_monitoring, R.drawable.ic_monitoring_filled),
    GlassNavItem(R.string.nav_profile, R.drawable.ic_person, R.drawable.ic_person_filled),
)

private const val FILLER_CARDS = 12
private const val SHEET_ITEMS = 30
private const val SAMPLE_SESSIONS = 120
private const val SAMPLE_GAP_MILLIS = 3 * 86_400_000L
private const val SPARKLINE_POINTS = 30
private const val HR_SETS = 4
private const val HR_CYCLE = 30
private const val HR_SET_STEPS = 6
private const val HR_STEP_MILLIS = 5_000L

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GalleryScreen(settings: ThemeSettings, onSettingsChange: (ThemeSettings) -> Unit) {
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val density = LocalDensity.current
    val hideOnScroll = remember(density) { HideOnScrollState(with(density) { GtSizes.HideBarThreshold.toPx() }) }
    var navIndex by remember { mutableIntStateOf(2) }
    var dialog by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + GtSizes.TopBarHeight

    GlassScaffold(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .nestedScroll(hideOnScroll.connection),
        chrome = {
            GlassTopBar(stringResource(R.string.gallery_title), scrolled = scrolled)
            AnimatedVisibility(
                visible = hideOnScroll.visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                GlassNavBar(NavItems, navIndex, onSelect = { navIndex = it })
            }
            GlassDialog(
                visible = dialog,
                onDismissRequest = { dialog = false },
                title = stringResource(R.string.gallery_dialog_title),
                text = stringResource(R.string.gallery_dialog_text),
                confirmButton = { GlassButton(stringResource(R.string.gallery_dialog_confirm), onClick = { dialog = false }) },
                dismissButton = {
                    GlassButton(stringResource(R.string.gallery_dialog_dismiss), onClick = { dialog = false }, style = GlassButtonStyle.Ghost)
                },
            )
            GlassBottomSheet(visible = sheet, onDismissRequest = { sheet = false }) {
                Text(stringResource(R.string.gallery_sheet_title), style = GtTheme.typography.headline)
                Text(stringResource(R.string.gallery_sheet_text), color = GtTheme.colors.textSecondary)
                // Taller than the screen, so it scrolls; dragged down at its top it moves the sheet
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(GtSpacing.m),
                ) {
                    repeat(SHEET_ITEMS) { Text(stringResource(R.string.gallery_filler_title, it + 1)) }
                }
                GlassButton(stringResource(R.string.action_close), onClick = { sheet = false }, modifier = Modifier.fillMaxWidth())
            }
            GlassFullScreenOverlay(visible = fullScreen, onDismissRequest = { fullScreen = false }) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(GtSpacing.xl),
                ) {
                    GlassProgressRing(0.65f, Modifier.size(GtSizes.RestRing)) {
                        Text(stringResource(R.string.gallery_sample_timer), style = GtTheme.typography.numberXL)
                    }
                    GlassButton(stringResource(R.string.action_close), onClick = { fullScreen = false })
                }
            }
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.testTag("gallery_list"),
            contentPadding = PaddingValues(
                start = GtSpacing.xl,
                end = GtSpacing.xl,
                top = top + GtSpacing.s,
                bottom = navBarContentPadding().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(GtSpacing.xl),
        ) {
            section(R.string.gallery_section_icon) { AppIcon() }
            section(R.string.gallery_section_theme) { ThemeControls(settings, onSettingsChange) }
            section(R.string.gallery_section_colors) { Swatches() }
            section(R.string.gallery_section_type) { TypeSamples() }
            section(R.string.gallery_section_buttons) { Buttons() }
            section(R.string.gallery_section_chips) { ChipsAndBadges() }
            section(R.string.gallery_section_inputs) { Inputs() }
            section(R.string.gallery_section_surfaces) { Surfaces() }
            section(R.string.gallery_section_overlays) {
                Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
                    GlassButton(stringResource(R.string.gallery_show_dialog), onClick = { dialog = true }, style = GlassButtonStyle.Secondary)
                    GlassButton(stringResource(R.string.gallery_show_sheet), onClick = { sheet = true }, style = GlassButtonStyle.Secondary)
                    GlassButton(stringResource(R.string.gallery_show_fullscreen), onClick = { fullScreen = true }, style = GlassButtonStyle.Secondary)
                }
            }
            section(R.string.gallery_section_progress) {
                Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.l), verticalAlignment = Alignment.CenterVertically) {
                        GlassProgressRing(0.3f, Modifier.size(GtSizes.MiniRing), strokeWidth = GtSizes.MiniRingStroke)
                        GlassProgressRing(0.75f, Modifier.size(GtSizes.Thumbnail)) {
                            Text(stringResource(R.string.gallery_sample_reps), style = GtTheme.typography.label)
                        }
                    }
                    GlassMiniBar(stringResource(R.string.nav_workout), stringResource(R.string.gallery_minibar_subtitle), 0.28f, onClick = {})
                }
            }
            section(R.string.gallery_section_charts) { Charts() }
            section(R.string.gallery_section_live_update) { LiveUpdateProbeCard() }
            section(R.string.gallery_section_scroll) {}
            items(FILLER_CARDS) { index -> FillerCard(index + 1) }
        }
    }
}

private fun LazyListScope.section(title: Int, content: @Composable () -> Unit) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            Text(stringResource(title), color = GtTheme.colors.textSecondary, style = GtTheme.typography.label)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeControls(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        FlowRow {
            GtPalette.entries.forEach { palette ->
                GlassSwatch(
                    gtColors(palette, GtTheme.colors.isDark).accent,
                    selected = settings.palette == palette,
                    onClick = { onChange(settings.copy(palette = palette)) },
                    label = palette.name,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            ThemeMode.entries.forEach { mode ->
                GlassChip(
                    stringResource(mode.label()),
                    selected = settings.mode == mode,
                    onClick = { onChange(settings.copy(mode = mode)) },
                )
            }
        }
        SwitchRow(stringResource(R.string.gallery_amoled), settings.amoled) { onChange(settings.copy(amoled = it)) }
        SwitchRow(stringResource(R.string.gallery_dynamic), settings.dynamicColor) { onChange(settings.copy(dynamicColor = it)) }
        SwitchRow(stringResource(R.string.gallery_reduce_transparency), settings.reduceTransparency) {
            onChange(settings.copy(reduceTransparency = it))
        }
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.System -> R.string.gallery_mode_system
    ThemeMode.Light -> R.string.gallery_mode_light
    ThemeMode.Dark -> R.string.gallery_mode_dark
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    GlassSwitchRow(label, checked, onChange, Modifier.fillMaxWidth(), titleStyle = GtTheme.typography.body)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches() {
    val c = GtTheme.colors
    val swatches = listOf(
        "accent" to c.accent, "accentText" to c.accentText, "background" to c.background, "surface" to c.surface,
        "raised" to c.surfaceRaised, "text" to c.textPrimary, "text2" to c.textSecondary, "text3" to c.textTertiary,
        "danger" to c.danger, "success" to c.success,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m), verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        swatches.forEach { (name, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(GtSizes.MinTouch)
                        .clip(GtShapes.Pill)
                        .background(color)
                        .border(GtSizes.Hairline, c.hairline, GtShapes.Pill),
                )
                Text(name, color = c.textSecondary, style = GtTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TypeSamples() {
    val t = GtTheme.typography
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.gallery_type_display), style = t.display)
        Text(stringResource(R.string.gallery_type_headline), style = t.headline)
        Text(stringResource(R.string.gallery_type_title), style = t.title)
        Text(stringResource(R.string.gallery_type_body), style = t.body)
        Text(stringResource(R.string.gallery_type_body), color = GtTheme.colors.textSecondary, style = t.bodySmall)
        Text(stringResource(R.string.gallery_type_label), style = t.label)
        Text(stringResource(R.string.gallery_sample_weight), style = t.numberXL)
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.xl), verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.gallery_sample_reps), style = t.numberL)
            Text(stringResource(R.string.gallery_sample_timer), style = t.numberM)
            Text(stringResource(R.string.gallery_sample_digits), color = GtTheme.colors.textSecondary, style = t.numberM)
        }
    }
}

@Composable
private fun Buttons() {
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        GlassButton(stringResource(R.string.gallery_start_workout), onClick = {}, icon = R.drawable.ic_add, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            GlassButton(stringResource(R.string.gallery_secondary), onClick = {}, style = GlassButtonStyle.Secondary)
            GlassButton(stringResource(R.string.gallery_ghost), onClick = {}, style = GlassButtonStyle.Ghost)
            GlassButton(stringResource(R.string.gallery_disabled), onClick = {}, enabled = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            GlassIconButton(R.drawable.ic_add, stringResource(R.string.gallery_add), onClick = {}, style = GlassButtonStyle.Primary)
            GlassIconButton(R.drawable.ic_settings, stringResource(R.string.gallery_settings), onClick = {})
            GlassIconButton(R.drawable.ic_search, stringResource(R.string.gallery_search), onClick = {}, style = GlassButtonStyle.Ghost)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsAndBadges() {
    val muscles = listOf(R.string.gallery_chip_chest, R.string.gallery_chip_back, R.string.gallery_chip_legs, R.string.gallery_chip_shoulders)
    var selected by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            muscles.forEachIndexed { index, label ->
                GlassChip(stringResource(label), selected = index == selected, onClick = { selected = index })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            GlassBadge(stringResource(R.string.gallery_badge_barbell))
            GlassBadge(stringResource(R.string.gallery_badge_custom), tone = BadgeTone.Accent)
        }
    }
}

@Composable
private fun Inputs() {
    var weight by remember { mutableDoubleStateOf(82.5) }
    var reps by remember { mutableDoubleStateOf(8.0) }
    var switch by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        GlassSearchField(query = query, onQueryChange = { query = it }, placeholder = stringResource(R.string.exercises_search))
        GlassTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.label_name),
            placeholder = stringResource(R.string.editor_name_placeholder),
            error = stringResource(R.string.editor_name_error).takeIf { name.isBlank() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
            GlassNumberField(
                value = weight,
                onValueChange = { weight = it ?: 0.0 },
                label = stringResource(R.string.label_weight),
                modifier = Modifier.weight(1f),
                step = 2.5,
                decimals = 2,
                unit = stringResource(R.string.unit_kg),
            )
            GlassNumberField(
                value = reps,
                onValueChange = { reps = it ?: 0.0 },
                label = stringResource(R.string.label_reps),
                modifier = Modifier.weight(1f),
            )
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SwitchRow(stringResource(R.string.gallery_switch_label), switch) { switch = it }
        }
    }
}

@Composable
private fun Charts() {
    val now = remember { Instant.now() }
    // A year of sessions every three days: steady growth with a wobble
    val points = remember(now) {
        List(SAMPLE_SESSIONS) { i ->
            ChartPoint(now.toEpochMilli() - (SAMPLE_SESSIONS - 1 - i) * SAMPLE_GAP_MILLIS, 92.5 + i * 0.22 + sin(i / 5.0) * 2.5)
        }
    }
    var range by remember { mutableStateOf(ChartRange.ThreeMonths) }
    var tab by remember { mutableIntStateOf(0) }
    val zone = ZoneId.systemDefault()
    val dates = remember(zone) { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).withZone(zone) }
    val resources = LocalResources.current
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        GlassSegmentedControl(
            options = listOf(R.string.progress_history, R.string.progress_exercises, R.string.progress_routines, R.string.progress_muscles).map { stringResource(it) },
            selectedIndex = tab,
            onSelect = { tab = it },
        )
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.gallery_chart_title), style = GtTheme.typography.title)
            GlassLineChart(
                points = points.within(range, now, zone),
                valueLabel = { resources.getString(R.string.value_kg, weightLabel(it)) },
                dateLabel = { dates.format(Instant.ofEpochMilli(it)) },
                emptyText = stringResource(R.string.chart_range_empty),
                axisLabel = { weightLabel(it) },
            )
            ChartRangeSelector(range, onSelect = { range = it })
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.gallery_chart_markers_title), style = GtTheme.typography.title)
            // Four sets: the heart rate climbs in each set and falls in the rest; a marker where each set was ticked off
            val start = now.toEpochMilli()
            val heartRate = remember(start) {
                List(HR_CYCLE * HR_SETS) { i ->
                    val step = i % HR_CYCLE
                    ChartPoint(start + i * HR_STEP_MILLIS, if (step >= HR_CYCLE - HR_SET_STEPS) 118.0 + (step - HR_CYCLE + HR_SET_STEPS) * 7 else 160.0 - step * 1.7)
                }
            }
            val times = remember(zone) { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).withZone(zone) }
            GlassLineChart(
                points = heartRate,
                valueLabel = { resources.getString(R.string.value_bpm, it.roundToInt()) },
                dateLabel = { times.format(Instant.ofEpochMilli(it)) },
                emptyText = "",
                axisLabel = { "${it.roundToInt()}" },
                markers = List(HR_SETS) { start + (it * HR_CYCLE + HR_CYCLE - 1) * HR_STEP_MILLIS },
                height = GtSizes.ChartCompactHeight,
            )
        }
        GlassCard(Modifier.fillMaxWidth()) {
            listOf(0.15f, 0.6f, 1f).forEach { GlassBar(it) }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            GlassBarChart(
                values = listOf(6_200.0, 9_800.0, 4_100.0, 0.0, 12_400.0, 8_000.0, 3_500.0),
                labels = DayOfWeek.entries.map { weekdayLabel(it, TextStyle.SHORT_STANDALONE) },
                description = stringResource(R.string.gallery_chart_title),
            )
            GlassSparkline(points.takeLast(SPARKLINE_POINTS), description = stringResource(R.string.gallery_chart_title))
        }
    }
}

@Composable
private fun Surfaces() {
    Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.gallery_card_overline), color = GtTheme.colors.textSecondary, style = GtTheme.typography.labelSmall)
            Text(stringResource(R.string.gallery_card_title), style = GtTheme.typography.headline)
            Text(stringResource(R.string.gallery_card_meta), color = GtTheme.colors.textSecondary, style = GtTheme.typography.bodySmall)
        }
        GlassPanel(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.gallery_panel_title), style = GtTheme.typography.title)
            Text(stringResource(R.string.gallery_panel_text), color = GtTheme.colors.textSecondary, style = GtTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveUpdateProbeCard() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(LiveUpdateProbe.state(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        LiveUpdateProbe.post(context)
        state = LiveUpdateProbe.state(context)
    }
    GlassCard(Modifier.fillMaxWidth()) {
        if (!LiveUpdateProbe.supported) {
            Text(stringResource(R.string.probe_requires_android16), color = GtTheme.colors.textSecondary)
        }
        ProbeRow(stringResource(R.string.probe_notifications_enabled), state.notificationsEnabled)
        ProbeRow(stringResource(R.string.probe_can_post_promoted), state.canPostPromoted)
        ProbeRow(stringResource(R.string.probe_promotable), state.hasPromotableCharacteristics)
        ProbeRow(stringResource(R.string.probe_promoted), state.promoted)
        ProbeRow(stringResource(R.string.probe_battery_unrestricted), state.ignoringBatteryOptimizations)
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.s), modifier = Modifier.padding(top = GtSpacing.s)) {
            GlassButton(stringResource(R.string.probe_post), onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) })
            GlassButton(stringResource(R.string.probe_refresh), onClick = { state = LiveUpdateProbe.state(context) }, style = GlassButtonStyle.Secondary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
            if (LiveUpdateProbe.supported) {
                GlassButton(
                    stringResource(R.string.probe_open_promoted_settings),
                    onClick = { LiveUpdateProbe.openSettings(context, LiveUpdateProbe.promotedSettingsIntent(context)) },
                    style = GlassButtonStyle.Ghost,
                )
            }
            GlassButton(
                stringResource(R.string.probe_open_notification_settings),
                onClick = { LiveUpdateProbe.openSettings(context, LiveUpdateProbe.notificationSettingsIntent(context)) },
                style = GlassButtonStyle.Ghost,
            )
            GlassButton(
                stringResource(R.string.probe_open_battery_settings),
                onClick = { LiveUpdateProbe.openSettings(context, LiveUpdateProbe.batterySettingsIntent(context)) },
                style = GlassButtonStyle.Ghost,
            )
            GlassButton(
                stringResource(R.string.probe_cancel),
                onClick = {
                    LiveUpdateProbe.cancel(context)
                    state = LiveUpdateProbe.state(context)
                },
                style = GlassButtonStyle.Ghost,
            )
        }
    }
}

@Composable
private fun ProbeRow(label: String, value: Boolean?) {
    val colors = GtTheme.colors
    val (text, color) = when (value) {
        true -> stringResource(R.string.probe_yes) to colors.success
        false -> stringResource(R.string.probe_no) to colors.danger
        null -> stringResource(R.string.probe_unknown) to colors.textTertiary
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = GtTheme.typography.bodySmall)
        Text(text, color = color, style = GtTheme.typography.label)
    }
}

@Composable
private fun FillerCard(number: Int) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.gallery_filler_title, number), style = GtTheme.typography.title)
        Text(
            pluralStringResource(R.plurals.gallery_filler_sets, number, number),
            color = GtTheme.colors.textSecondary,
            style = GtTheme.typography.bodySmall,
        )
    }
}

