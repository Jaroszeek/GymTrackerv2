package com.jaroszeek.gymtracker.feature.profile

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.NumberInput
import com.jaroszeek.gymtracker.domain.body.BmiCategory
import com.jaroszeek.gymtracker.domain.body.BodyMetric
import com.jaroszeek.gymtracker.domain.settings.WeightUnit
import com.jaroszeek.gymtracker.feature.routines.label
import com.jaroszeek.gymtracker.feature.routines.massLabel
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Up to [decimals] decimals with the locale's separator: "82,5", "180". */
fun decimalLabel(value: Double, decimals: Int = 1): String =
    NumberInput.format(value, decimals, DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator)

val BodyMetric.isMass: Boolean get() = this == BodyMetric.Weight || this == BodyMetric.SkeletalMuscle

/** Heights are stored in metres and entered in centimetres, masses in kg and entered in [unit]. */
fun BodyMetric.toInput(value: Double, unit: WeightUnit): Double = when {
    this == BodyMetric.Height -> value * CM_PER_M
    isMass -> unit.fromKg(value)
    else -> value
}

fun BodyMetric.fromInput(value: Double, unit: WeightUnit): Double = when {
    this == BodyMetric.Height -> value / CM_PER_M
    isMass -> unit.toKg(value)
    else -> value
}

/** "82,5 kg", "180 cm", "18,5 %". */
@Composable
@ReadOnlyComposable
fun bodyValueLabel(metric: BodyMetric, value: Double): String = when (metric) {
    BodyMetric.Weight, BodyMetric.SkeletalMuscle -> massLabel(value, decimals = 1)
    BodyMetric.Height -> stringResource(R.string.value_cm, decimalLabel(value * CM_PER_M, decimals = 0))
    BodyMetric.BodyFat -> stringResource(R.string.value_percent, decimalLabel(value))
}

@StringRes
fun BodyMetric.unit(unit: WeightUnit): Int = when (this) {
    BodyMetric.Weight, BodyMetric.SkeletalMuscle -> unit.label()
    BodyMetric.Height -> R.string.unit_cm
    BodyMetric.BodyFat -> R.string.unit_percent
}

@StringRes
fun BodyMetric.label(): Int = when (this) {
    BodyMetric.Weight -> R.string.profile_weight
    BodyMetric.Height -> R.string.profile_height
    BodyMetric.BodyFat -> R.string.profile_body_fat
    BodyMetric.SkeletalMuscle -> R.string.profile_skeletal_muscle
}

@StringRes
fun BmiCategory.label(): Int = when (this) {
    BmiCategory.Underweight -> R.string.bmi_underweight
    BmiCategory.Normal -> R.string.bmi_normal
    BmiCategory.Overweight -> R.string.bmi_overweight
    BmiCategory.Obese -> R.string.bmi_obese
}

private const val CM_PER_M = 100.0
