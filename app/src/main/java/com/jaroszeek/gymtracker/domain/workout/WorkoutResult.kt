package com.jaroszeek.gymtracker.domain.workout

import com.jaroszeek.gymtracker.domain.progress.PersonalBests
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.progress.e1rmKg
import com.jaroszeek.gymtracker.domain.progress.volumeKg
import com.jaroszeek.gymtracker.domain.progress.weight
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.SetType
import com.jaroszeek.gymtracker.domain.routine.normalized

/** Only ticked-off sets count; volume and the set count leave warm-ups out. */
data class WorkoutSummary(val durationMillis: Long, val volumeKg: Double, val sets: Int, val exercises: Int) {
    /** Nothing was ticked off, so nothing is saved. */
    val isEmpty: Boolean get() = exercises == 0
}

fun ActiveWorkout.summary(endedAt: Long): WorkoutSummary {
    val done = exercises.map { exercise -> exercise.sets.filter { it.done } }.filter { it.isNotEmpty() }
    val work = done.flatten().filter { it.type != SetType.Warmup }
    return WorkoutSummary(
        durationMillis = (endedAt - startedAt).coerceAtLeast(0),
        volumeKg = work.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
        sets = work.size,
        exercises = done.size,
    )
}

/** What the workout did differently from the routine targets of one variant. */
data class TargetChange(val exerciseId: Long, val routineVariantId: Long, val old: RoutineTargets, val new: RoutineTargets)

/**
 * Proposed new targets per routine exercise: the number of ticked-off working sets and the heaviest
 * working weight. The rep range and rest stay. Exercises without a working set propose nothing.
 */
fun ActiveWorkout.targetChanges(): List<TargetChange> = exercises.mapNotNull { exercise ->
    val variant = exercise.variant
    val variantId = variant.routineVariantId ?: return@mapNotNull null
    val old = variant.targets ?: return@mapNotNull null
    val working = exercise.sets.filter { it.done && it.type.isWork }
    if (working.isEmpty()) return@mapNotNull null
    val new = old.copy(sets = working.size, weightKg = working.mapNotNull { it.weightKg }.maxOrNull() ?: old.weightKg).normalized()
    TargetChange(variant.exerciseId, variantId, old, new).takeIf { new != old }
}

/** A record of the workout for the summary: the set that set it and the value; reps records show the set itself. */
data class RecordHit(val exerciseId: Long, val type: RecordType, val set: PerformedSet, val valueKg: Double?)

/** Records per ticked-off set id, against each exercise's bests from before the workout. */
fun ActiveWorkout.records(): Map<Long, Set<RecordType>> {
    val bests = exercises.map { it.variant }.distinctBy { it.exerciseId }.associate { it.exerciseId to it.bests }
    return recordsByExercise(recordEntries()) { bests[it] }
}

/** Per exercise and record type the last set that set it, which is the best one. */
fun ActiveWorkout.recordHits(): List<RecordHit> {
    val records = records()
    return recordEntries().groupBy { it.exerciseId }.flatMap { (exerciseId, entries) ->
        val sessionVolume = entries.filter { it.set.type != SetType.Warmup }.sumOf { it.set.volumeKg }
        RecordType.entries.mapNotNull { type ->
            val entry = entries.lastOrNull { type in records[it.setId].orEmpty() } ?: return@mapNotNull null
            val value = when (type) {
                RecordType.Weight -> entry.set.weight
                RecordType.E1rm -> entry.set.e1rmKg
                RecordType.Reps -> null
                RecordType.SetVolume -> entry.set.volumeKg
                RecordType.SessionVolume -> sessionVolume
            }
            RecordHit(exerciseId, type, entry.set, value)
        }
    }
}

private fun ActiveWorkout.recordEntries() = exercises.flatMap { exercise ->
    exercise.sets.filter { it.done }.map { RecordEntry(exercise.variant.exerciseId, it.id, PerformedSet(it.weightKg, it.reps ?: 0, it.type)) }
}

internal data class RecordEntry(val exerciseId: Long, val setId: Long, val set: PerformedSet)

/** Sets of the same exercise are one session for the records, even when the exercise is in the workout twice. */
internal fun recordsByExercise(entries: List<RecordEntry>, bests: (exerciseId: Long) -> PersonalBests?): Map<Long, Set<RecordType>> =
    entries.groupBy { it.exerciseId }
        .flatMap { (exerciseId, sets) ->
            val before = bests(exerciseId) ?: return@flatMap emptyList()
            sets.zip(before.sessionRecords(sets.map { it.set })) { entry, records -> entry.setId to records }
        }
        .filter { (_, records) -> records.isNotEmpty() }
        .toMap()
