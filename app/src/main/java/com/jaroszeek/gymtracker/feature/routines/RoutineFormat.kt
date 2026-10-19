package com.jaroszeek.gymtracker.feature.routines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.NumberInput
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import java.text.DecimalFormatSymbols
import java.util.Locale

/** "8–12" or "8" for a fixed count. */
fun RoutineTargets.repsLabel(): String = if (repsMin == repsMax) "$repsMin" else "$repsMin–$repsMax"

/** "45 s" under a minute, otherwise "1:30". */
fun restLabel(seconds: Int): String =
    if (seconds < SECONDS_PER_MINUTE) "$seconds s" else "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

fun weightLabel(kg: Double): String = NumberInput.format(kg, decimals = 2, DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator)

/** "3 × 8–12 • 60 kg • przerwa 1:30" */
@Composable
@ReadOnlyComposable
fun RoutineTargets.summary(): String = listOfNotNull(
    stringResource(R.string.routine_target_sets_reps, sets, repsLabel()),
    weightKg?.let { stringResource(R.string.routine_target_weight, weightLabel(it)) },
    restSeconds?.let { stringResource(R.string.routine_target_rest, restLabel(it)) } ?: stringResource(R.string.routine_target_rest_default),
).joinToString(SEPARATOR)

private const val SECONDS_PER_MINUTE = 60
private const val SEPARATOR = " • "
