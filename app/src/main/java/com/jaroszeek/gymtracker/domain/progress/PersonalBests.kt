package com.jaroszeek.gymtracker.domain.progress

import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.SetType
import kotlinx.serialization.Serializable

/** The automatic records of A5.7. */
enum class RecordType { Weight, E1rm, Reps, SetVolume, SessionVolume }

/** The most reps done with one weight; null weight = bodyweight. */
@Serializable
data class RepBest(val weightKg: Double?, val reps: Int)

/**
 * Best results of one exercise so far. Warm-ups never count, and a weight of 0 counts as bodyweight.
 * A record needs a previous best to beat, so the first session with an exercise only sets the baseline.
 */
@Serializable
data class PersonalBests(
    val weightKg: Double? = null,
    val e1rmKg: Double? = null,
    val setVolumeKg: Double? = null,
    val sessionVolumeKg: Double? = null,
    /** Per weight the most reps done with it. */
    val reps: List<RepBest> = emptyList(),
) {
    /**
     * Records the set beats, except the session volume (see [sessionRecords]). More reps is a record
     * only against sets with at least the same weight: a heavier weight than ever is a weight record.
     */
    fun recordsOf(set: PerformedSet): Set<RecordType> {
        if (set.type == SetType.Warmup) return emptySet()
        val weight = set.weight
        return buildSet {
            if (weight != null) {
                if (weightKg != null && weight > weightKg) add(RecordType.Weight)
                val e1rm = OneRepMax.epley(weight, set.reps)
                if (e1rm != null && e1rmKg != null && e1rm > e1rmKg) add(RecordType.E1rm)
                if (setVolumeKg != null && weight * set.reps > setVolumeKg) add(RecordType.SetVolume)
            }
            val most = mostReps(weight)
            if (most != null && set.reps > most) add(RecordType.Reps)
        }
    }

    /** Most reps with at least [weightKg], or with bodyweight when null; null when no such set was done. */
    fun mostReps(weightKg: Double?): Int? = reps
        .filter { if (weightKg == null) it.weightKg == null else it.weightKg != null && it.weightKg >= weightKg }
        .maxOfOrNull { it.reps }

    /** The bests once [set] is done; the session volume is left to [sessionRecords]. */
    fun plus(set: PerformedSet): PersonalBests {
        if (set.type == SetType.Warmup) return this
        val weight = set.weight
        val same = reps.firstOrNull { it.weightKg == weight }
        return copy(
            weightKg = maxOrNull(weightKg, weight),
            e1rmKg = maxOrNull(e1rmKg, weight?.let { OneRepMax.epley(it, set.reps) }),
            setVolumeKg = maxOrNull(setVolumeKg, weight?.let { it * set.reps }),
            reps = when {
                same == null -> reps + RepBest(weight, set.reps)
                set.reps > same.reps -> reps.map { if (it == same) RepBest(weight, set.reps) else it }
                else -> reps
            },
        )
    }

    /**
     * Records of each set of one exercise in one session, in order. Every set is compared with the bests
     * before the session and with the sets before it; the session volume record goes to the set that
     * passes the old best.
     */
    fun sessionRecords(sets: List<PerformedSet>): List<Set<RecordType>> {
        // Every set done adds to reps, so none means no history: the first session only sets the baseline
        if (reps.isEmpty()) return sets.map { emptySet() }
        var bests = this
        var volume = 0.0
        return sets.map { set ->
            val records = bests.recordsOf(set)
            bests = bests.plus(set)
            val before = volume
            if (set.type != SetType.Warmup) volume += set.volumeKg
            if (sessionVolumeKg != null && before <= sessionVolumeKg && volume > sessionVolumeKg) records + RecordType.SessionVolume else records
        }
    }
}

/** Weight that counts for records and volume: null for bodyweight, including a weight of 0. */
val PerformedSet.weight: Double? get() = weightKg?.takeIf { it > 0 }

/** Weight × reps; 0 for bodyweight. */
val PerformedSet.volumeKg: Double get() = (weight ?: 0.0) * reps

/**
 * Rep records worth listing: per weight the most reps, leaving out weights where a heavier one was done
 * for as many reps. Heaviest first, bodyweight last.
 */
fun List<RepBest>.frontier(): List<RepBest> {
    val weighted = mutableListOf<RepBest>()
    filter { it.weightKg != null }
        .groupBy { it.weightKg }
        .values
        .map { bests -> bests.maxBy { it.reps } }
        .sortedByDescending { it.weightKg }
        // Kept entries have ever more reps, so the last one is the most reps with a heavier weight
        .forEach { if (it.reps > (weighted.lastOrNull()?.reps ?: 0)) weighted += it }
    return weighted + listOfNotNull(filter { it.weightKg == null }.maxByOrNull { it.reps })
}

private fun maxOrNull(a: Double?, b: Double?): Double? = if (a == null) b else if (b == null) a else maxOf(a, b)
