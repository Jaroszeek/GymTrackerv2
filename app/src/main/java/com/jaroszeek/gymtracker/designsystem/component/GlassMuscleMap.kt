package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtColors
import com.jaroszeek.gymtracker.designsystem.theme.GtPalette
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.designsystem.theme.ThemeMode
import com.jaroszeek.gymtracker.designsystem.theme.ThemeSettings
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import com.jaroszeek.gymtracker.domain.workout.MuscleIntensity
import com.jaroszeek.gymtracker.domain.workout.MuscleLoad
import com.jaroszeek.gymtracker.feature.exercises.label
import kotlin.math.min

private data class ParsedBodyPart(
    val muscle: Muscle?,
    val cosmetic: CosmeticPart?,
    val overlay: Boolean,
    val paths: List<Path>,
)

private object ParsedMuscleMap {
    val front: List<ParsedBodyPart> by lazy { MuscleMapPaths.front.map { it.parse() } }
    val back: List<ParsedBodyPart> by lazy { MuscleMapPaths.back.map { it.parse() } }

    private fun BodyPartSvg.parse() = ParsedBodyPart(
        muscle = muscle,
        cosmetic = cosmetic,
        overlay = overlay,
        paths = paths.map { PathParser().parsePathString(it).toPath() },
    )
}

/**
 * Full post-workout muscle engagement card: front and back anatomical diagrams with a single-hue
 * intensity scale, legend, and compact per-muscle summary pills.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassMuscleMapCard(
    workload: List<MuscleLoad>,
    modifier: Modifier = Modifier,
) {
    if (workload.isEmpty()) return
    val colors = GtTheme.colors
    val intensities = remember(workload) { workload.associate { it.muscle to it.intensity } }

    GlassCard(modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(GtSpacing.xs),
        ) {
            Text(
                stringResource(R.string.muscle_map_title),
                Modifier.semantics { heading() },
                style = GtTheme.typography.title,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(GtSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendItem(stringResource(R.string.muscle_map_high), colors.muscleColor(MuscleIntensity.High))
                LegendItem(stringResource(R.string.muscle_map_medium), colors.muscleColor(MuscleIntensity.Medium))
                LegendItem(stringResource(R.string.muscle_map_low), colors.muscleColor(MuscleIntensity.Low))
            }
        }

        GlassMuscleMap(intensities = intensities, modifier = Modifier.fillMaxWidth())

        Box(
            Modifier
                .fillMaxWidth()
                .height(GtSizes.Hairline)
                .background(colors.hairline),
        )

        Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.s)) {
            workload.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GtSpacing.s),
                ) {
                    pair.forEach { load ->
                        MuscleLoadChip(load, Modifier.weight(1f))
                    }
                    if (pair.size == 1) {
                        Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Side-by-side front and back anatomical muscle map rendered from vector paths. */
@Composable
fun GlassMuscleMap(
    intensities: Map<Muscle, MuscleIntensity>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GtSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MuscleFigureColumn(
            label = stringResource(R.string.muscle_map_front),
            parts = ParsedMuscleMap.front,
            originX = MuscleMapPaths.FRONT_ORIGIN_X,
            intensities = intensities,
            modifier = Modifier.weight(1f),
        )
        MuscleFigureColumn(
            label = stringResource(R.string.muscle_map_back),
            parts = ParsedMuscleMap.back,
            originX = MuscleMapPaths.BACK_ORIGIN_X,
            intensities = intensities,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MuscleFigureColumn(
    label: String,
    parts: List<ParsedBodyPart>,
    originX: Float,
    intensities: Map<Muscle, MuscleIntensity>,
    modifier: Modifier = Modifier,
) {
    val colors = GtTheme.colors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GtSpacing.xs),
    ) {
        Text(
            label,
            color = colors.textTertiary,
            style = GtTheme.typography.labelSmall,
        )
        Canvas(
            Modifier
                .height(GtSizes.MuscleMapHeight)
                .aspectRatio(MuscleMapPaths.VIEW_WIDTH / MuscleMapPaths.VIEW_HEIGHT),
        ) {
            val scale = min(
                size.width / MuscleMapPaths.VIEW_WIDTH,
                size.height / MuscleMapPaths.VIEW_HEIGHT,
            )
            val offsetX = (size.width - MuscleMapPaths.VIEW_WIDTH * scale) / 2f - originX * scale
            val offsetY = (size.height - MuscleMapPaths.VIEW_HEIGHT * scale) / 2f - MuscleMapPaths.ORIGIN_Y * scale

            withTransform({
                translate(left = offsetX, top = offsetY)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            }) {
                for (part in parts) {
                    val intensity = part.muscle?.let { intensities[it] } ?: MuscleIntensity.None
                    if (part.overlay && intensity == MuscleIntensity.None) continue
                    val fillColor = when {
                        part.cosmetic == CosmeticPart.Hair -> colors.hairPartColor()
                        part.cosmetic == CosmeticPart.Extremity -> colors.extremityPartColor()
                        else -> colors.muscleColor(intensity)
                    }
                    for (path in part.paths) {
                        drawPath(path = path, color = fillColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GtSpacing.xs),
    ) {
        Box(
            Modifier
                .size(GtSizes.MuscleLegendDot)
                .clip(GtShapes.Pill)
                .background(color),
        )
        Text(
            label,
            color = GtTheme.colors.textSecondary,
            style = GtTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MuscleLoadChip(
    load: MuscleLoad,
    modifier: Modifier = Modifier,
) {
    val colors = GtTheme.colors
    val tierLabel = when (load.intensity) {
        MuscleIntensity.High -> stringResource(R.string.muscle_map_high)
        MuscleIntensity.Medium -> stringResource(R.string.muscle_map_medium)
        MuscleIntensity.Low, MuscleIntensity.None -> stringResource(R.string.muscle_map_low)
    }
    val meta = when {
        load.primarySets > 0 && load.secondarySets > 0 -> stringResource(
            R.string.muscle_map_primary_and_secondary,
            pluralStringResource(R.plurals.muscle_map_sets, load.primarySets, load.primarySets),
            load.secondarySets,
        )
        load.primarySets > 0 -> stringResource(
            R.string.muscle_map_primary_only,
            pluralStringResource(R.plurals.muscle_map_sets, load.primarySets, load.primarySets),
            tierLabel,
        )
        else -> stringResource(
            R.string.muscle_map_secondary_only,
            load.secondarySets,
            tierLabel,
        )
    }

    Row(
        modifier
            .clip(GtShapes.Small)
            .background(colors.surfaceRaised)
            .border(GtSizes.Hairline, colors.hairline, GtShapes.Small)
            .padding(horizontal = GtSpacing.m, vertical = GtSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GtSpacing.s),
    ) {
        Box(
            Modifier
                .width(GtSizes.MuscleChipBarWidth)
                .height(GtSizes.MuscleChipBarHeight)
                .clip(GtShapes.Pill)
                .background(colors.muscleColor(load.intensity)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(load.muscle.label),
                color = colors.textPrimary,
                style = GtTheme.typography.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                color = colors.textSecondary,
                style = GtTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun GtColors.unworkedMuscleColor(): Color =
    lerp(surfaceRaised, textTertiary, if (isDark) 0.22f else 0.18f)

private fun GtColors.extremityPartColor(): Color =
    lerp(surfaceRaised, textTertiary, if (isDark) 0.14f else 0.12f)

private fun GtColors.hairPartColor(): Color =
    lerp(surface, textTertiary, if (isDark) 0.08f else 0.26f)

private fun GtColors.muscleColor(intensity: MuscleIntensity): Color {
    val base = unworkedMuscleColor()
    return when (intensity) {
        MuscleIntensity.None -> base
        MuscleIntensity.Low -> lerp(base, accent, if (isDark) 0.36f else 0.38f)
        MuscleIntensity.Medium -> lerp(base, accent, if (isDark) 0.68f else 0.70f)
        MuscleIntensity.High -> accent
    }
}

@Preview
@Composable
private fun GlassMuscleMapCardDarkPreview() {
    val sample = listOf(
        MuscleLoad(Muscle.Chest, primarySets = 9, secondarySets = 0),
        MuscleLoad(Muscle.Shoulders, primarySets = 6, secondarySets = 3),
        MuscleLoad(Muscle.Triceps, primarySets = 4, secondarySets = 6),
        MuscleLoad(Muscle.Quadriceps, primarySets = 3, secondarySets = 0),
        MuscleLoad(Muscle.Abdominals, primarySets = 0, secondarySets = 4),
        MuscleLoad(Muscle.Traps, primarySets = 0, secondarySets = 3),
        MuscleLoad(Muscle.Forearms, primarySets = 0, secondarySets = 3),
    )
    GymTrackerTheme(ThemeSettings(mode = ThemeMode.Dark, palette = GtPalette.Volt)) {
        GlassScaffold {
            Column(Modifier.padding(GtSpacing.xl)) {
                GlassMuscleMapCard(workload = sample)
            }
        }
    }
}

@Preview
@Composable
private fun GlassMuscleMapCardLightPreview() {
    val sample = listOf(
        MuscleLoad(Muscle.Lats, primarySets = 6, secondarySets = 0),
        MuscleLoad(Muscle.MiddleBack, primarySets = 4, secondarySets = 2),
        MuscleLoad(Muscle.Biceps, primarySets = 3, secondarySets = 4),
        MuscleLoad(Muscle.Hamstrings, primarySets = 2, secondarySets = 0),
        MuscleLoad(Muscle.Forearms, primarySets = 0, secondarySets = 6),
    )
    GymTrackerTheme(ThemeSettings(mode = ThemeMode.Light, palette = GtPalette.Volt)) {
        GlassScaffold {
            Column(Modifier.padding(GtSpacing.xl)) {
                GlassMuscleMapCard(workload = sample)
            }
        }
    }
}
