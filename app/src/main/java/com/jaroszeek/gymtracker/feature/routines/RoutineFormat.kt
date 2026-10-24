package com.jaroszeek.gymtracker.feature.routines

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.NumberInput
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.settings.WeightUnit
import com.jaroszeek.gymtracker.feature.workout.volumeLabel
import java.text.DecimalFormatSymbols
import java.util.Locale

/** "8–12" or "8" for a fixed count. */
fun RoutineTargets.repsLabel(): String = if (repsMin == repsMax) "$repsMin" else "$repsMin–$repsMax"

/** "45 s" under a minute, otherwise "1:30". */
fun restLabel(seconds: Int): String =
    if (seconds < SECONDS_PER_MINUTE) "$seconds s" else "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

/** The unit masses are shown and entered in, from the settings; they are stored in kg and converted only in the UI. */
val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.Kg }

/** Decimals of a mass on screen and in its field. */
const val MASS_DECIMALS = 2

@StringRes
fun WeightUnit.label(): Int = if (this == WeightUnit.Kg) R.string.unit_kg else R.string.unit_lb

/** "62,5 kg" for [value] already in this unit, e.g. a chart point; 0 [decimals] groups a volume: "12 500 kg". */
fun WeightUnit.format(resources: Resources, value: Double, decimals: Int = MASS_DECIMALS): String = resources.getString(
    if (this == WeightUnit.Kg) R.string.value_kg else R.string.value_lb,
    if (decimals == 0) volumeLabel(value) else NumberInput.format(value, decimals, DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator),
)

/** "62,5 kg" or "137,79 lb" for [kg] in the chosen unit; 1 decimal for estimates, 0 for volumes. */
@Composable
@ReadOnlyComposable
fun massLabel(kg: Double, decimals: Int = MASS_DECIMALS): String = LocalWeightUnit.current.let { it.format(LocalResources.current, it.fromKg(kg), decimals) }

/** The number of a mass already in the shown unit, without the unit: chart axes. */
fun weightLabel(value: Double): String = NumberInput.format(value, MASS_DECIMALS, DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator)

/** A mass field's value: [kg] in this unit, rounded as the field shows it, so the user's typing is not replaced. */
fun WeightUnit.input(kg: Double?): Double? = kg?.let { NumberInput.round(fromKg(it), MASS_DECIMALS) }

/** "3 × 8–12 • 60 kg • przerwa 1:30" */
@Composable
@ReadOnlyComposable
fun RoutineTargets.summary(): String = listOfNotNull(
    stringResource(R.string.routine_target_sets_reps, sets, repsLabel()),
    weightKg?.let { massLabel(it) },
    restSeconds?.let { stringResource(R.string.routine_target_rest, restLabel(it)) } ?: stringResource(R.string.routine_target_rest_default),
).joinToString(SEPARATOR)

private const val SECONDS_PER_MINUTE = 60
private const val SEPARATOR = " • "
