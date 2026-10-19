package com.jaroszeek.gymtracker.domain.progress

/** One session's results of an exercise, without warm-ups; weights are null when it was all bodyweight. */
data class ExerciseSession(
    val workoutId: Long,
    /** Workout start, epoch millis UTC. */
    val time: Long,
    val maxWeightKg: Double?,
    val e1rmKg: Double?,
    val bestSetVolumeKg: Double?,
    val volumeKg: Double,
    val maxReps: Int,
)

/** A weight and reps pair done with an exercise, and when it was first done. */
data class RepsDone(val best: RepBest, val firstTime: Long)

data class Dated<T>(val value: T, val time: Long)

/** The record list of an exercise (A5.7), each with the date it was first reached. */
data class ExerciseRecords(
    val weight: Dated<Double>?,
    val e1rm: Dated<Double>?,
    /** The set behind [e1rm], for the Brzycki comparison. */
    val e1rmFrom: RepBest?,
    val setVolume: Dated<Double>?,
    val sessionVolume: Dated<Double>?,
    val reps: List<Dated<RepBest>>,
) {
    val isEmpty: Boolean get() = weight == null && reps.isEmpty()
}

/** History of one exercise: [sessions] in time order and every weight and reps pair done. */
data class ExerciseProgress(val sessions: List<ExerciseSession>, val repsDone: List<RepsDone>) {

    fun records(): ExerciseRecords {
        // maxByOrNull keeps the first of equal values, which is when the record was reached
        fun best(value: (ExerciseSession) -> Double?) =
            sessions.mapNotNull { session -> value(session)?.let { Dated(it, session.time) } }.maxByOrNull { it.value }
        val first = repsDone.groupBy { it.best }.mapValues { (_, done) -> done.minOf { it.firstTime } }
        // The best estimate, first reached earliest; the same value as the sessions' best e1RM
        val e1rm = first.mapNotNull { (best, time) -> best.weightKg?.let { OneRepMax.epley(it, best.reps) }?.let { Triple(best, it, time) } }
            .minWithOrNull(compareByDescending<Triple<RepBest, Double, Long>> { it.second }.thenBy { it.third })
        return ExerciseRecords(
            weight = best { it.maxWeightKg },
            e1rm = e1rm?.let { Dated(it.second, it.third) },
            e1rmFrom = e1rm?.first,
            setVolume = best { it.bestSetVolumeKg },
            sessionVolume = best { session -> session.volumeKg.takeIf { it > 0 } },
            reps = first.keys.toList().frontier().map { Dated(it, first.getValue(it)) },
        )
    }

    /** Bests from the sessions before [time], which the sets of a session at [time] are compared with. */
    fun bestsBefore(time: Long): PersonalBests {
        val before = sessions.filter { it.time < time }
        return PersonalBests(
            weightKg = before.mapNotNull { it.maxWeightKg }.maxOrNull(),
            e1rmKg = before.mapNotNull { it.e1rmKg }.maxOrNull(),
            setVolumeKg = before.mapNotNull { it.bestSetVolumeKg }.maxOrNull(),
            sessionVolumeKg = before.map { it.volumeKg }.filter { it > 0 }.maxOrNull(),
            reps = repsDone.filter { it.firstTime < time }
                .groupBy { it.best.weightKg }
                .map { (weight, done) -> RepBest(weight, done.maxOf { it.best.reps }) },
        )
    }
}

/** An exercise in the Postępy list; [maxReps] stands in for the weights of bodyweight exercises. */
data class ExerciseHistory(
    val exerciseId: Long,
    val name: String,
    val sessions: Int,
    val lastTime: Long,
    val e1rmKg: Double?,
    val maxWeightKg: Double?,
    val maxReps: Int,
)

data class RoutineHistory(val routineId: Long, val name: String, val sessions: Int, val lastTime: Long)

/** A session of a routine for its charts: the volume and the heaviest working set. */
data class RoutineSession(val workoutId: Long, val time: Long, val volumeKg: Double, val maxWeightKg: Double?)
