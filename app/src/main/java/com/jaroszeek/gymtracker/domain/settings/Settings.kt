package com.jaroszeek.gymtracker.domain.settings

import kotlin.math.roundToLong

/** The unit masses are shown and entered in (A5.9); they are always stored in kg. [key] is stored. */
enum class WeightUnit(val key: String) {
    Kg("kg"),
    Lb("lb"),
    ;

    fun fromKg(kg: Double): Double = if (this == Kg) kg else kg / KG_PER_LB

    fun toKg(value: Double): Double = if (this == Kg) value else value * KG_PER_LB

    /** The smallest usual jump on the bar, in this unit: 2.5 kg or 5 lb. */
    val step: Double get() = if (this == Kg) KG_STEP else LB_STEP

    /** [step] in kg, e.g. for the progression suggestion. */
    val stepKg: Double get() = toKg(step)

    /** [kg] rounded to hundredths of this unit: the same value as when typed into a field. */
    fun roundKg(kg: Double): Double = toKg((fromKg(kg) * HUNDRED).roundToLong() / HUNDRED)

    companion object {
        const val KG_PER_LB = 0.45359237
        private const val KG_STEP = 2.5
        private const val LB_STEP = 5.0
        private const val HUNDRED = 100.0

        fun fromKey(key: String?): WeightUnit = entries.firstOrNull { it.key == key } ?: Kg
    }
}

/** What happens when a rest ends; the screen and the notification change either way. */
data class RestAlert(val sound: Boolean = false, val vibration: Boolean = true)

object RestDefaults {
    /** Rest after a set whose routine has no rest of its own. */
    const val SECONDS = 90
    const val MIN_SECONDS = 15
    const val MAX_SECONDS = 600
    const val STEP_SECONDS = 15

    fun coerce(seconds: Int): Int = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
}
