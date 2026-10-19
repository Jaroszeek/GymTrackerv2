package com.jaroszeek.gymtracker.domain.progress

import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.SetType

/**
 * Estimated one-rep max (A5.7). Epley is the one shown, Brzycki is there to compare. Only sets of
 * 1–[MAX_REPS] reps with a weight count; a single is its own 1RM. The SQL in `WorkoutDao` repeats Epley.
 */
object OneRepMax {
    const val MAX_REPS = 12
    const val EPLEY_DIVISOR = 30.0
    private const val BRZYCKI_NUMERATOR = 36.0

    fun epley(weightKg: Double, reps: Int): Double? = when {
        !counts(weightKg, reps) -> null
        reps == 1 -> weightKg
        else -> weightKg * (1 + reps / EPLEY_DIVISOR)
    }

    fun brzycki(weightKg: Double, reps: Int): Double? =
        if (counts(weightKg, reps)) weightKg * BRZYCKI_NUMERATOR / (BRZYCKI_NUMERATOR + 1 - reps) else null

    private fun counts(weightKg: Double, reps: Int) = weightKg > 0 && reps in 1..MAX_REPS
}

/** Epley e1RM of the set; null for warm-ups, bodyweight and sets over [OneRepMax.MAX_REPS] reps. */
val PerformedSet.e1rmKg: Double? get() = if (type == SetType.Warmup) null else weightKg?.let { OneRepMax.epley(it, reps) }
