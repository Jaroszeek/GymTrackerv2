package com.jaroszeek.gymtracker.domain.workout

import com.jaroszeek.gymtracker.domain.progress.PersonalBests
import com.jaroszeek.gymtracker.domain.progress.RecordType
import com.jaroszeek.gymtracker.domain.progress.volumeKg
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.SetType

/** A set of a finished workout; 0 reps only while it is being edited. */
data class LoggedSet(val id: Long, val type: SetType, val weightKg: Double?, val reps: Int) {
    val performed: PerformedSet get() = PerformedSet(weightKg, reps, type)
}

/** [id] is the entry in the workout, [exerciseId] the exercise done. */
data class LoggedExercise(val id: Long, val exerciseId: Long, val routineVariantId: Long?, val sets: List<LoggedSet>)

/** A row of the history list; [healthSessionId] is the linked watch session (Etap 7). */
data class WorkoutListItem(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val summary: WorkoutSummary,
    val healthSessionId: String? = null,
    val routineId: Long? = null,
)

/** A workout's link to a watch session in Health Connect; [dismissed] = no session is proposed any more. */
data class HealthLink(val startedAt: Long, val endedAt: Long, val sessionId: String?, val dismissed: Boolean)

/**
 * A finished workout from history, also the draft its editor works on. Entry ids are unique among the
 * entries and set ids among all sets of the workout. Times are epoch millis UTC.
 */
data class LoggedWorkout(
    val id: Long,
    val routineId: Long?,
    val name: String,
    val startedAt: Long,
    val endedAt: Long,
    val exercises: List<LoggedExercise>,
) {
    val durationMillis: Long get() = (endedAt - startedAt).coerceAtLeast(0)

    /** Every set needs reps, and a workout without sets is deleted instead. */
    val canSave: Boolean get() = name.isNotBlank() && exercises.isNotEmpty() && exercises.all { e -> e.sets.all { it.reps > 0 } }

    /** Same rules as the summary after a workout: warm-ups leave the volume and the set count. */
    fun summary(): WorkoutSummary {
        val work = exercises.flatMap { it.sets }.filter { it.type != SetType.Warmup }
        return WorkoutSummary(durationMillis, work.sumOf { it.performed.volumeKg }, work.size, exercises.size)
    }

    /** Records per set id, given each exercise's bests before this workout. */
    fun records(bests: Map<Long, PersonalBests>): Map<Long, Set<RecordType>> = recordsByExercise(recordEntries()) { bests[it] }

    /** As after a workout: per exercise and record type the set that set it. */
    fun recordHits(bests: Map<Long, PersonalBests>): List<RecordHit> = recordHitsOf(recordEntries(), records(bests))

    private fun recordEntries() = exercises.flatMap { exercise -> exercise.sets.map { RecordEntry(exercise.exerciseId, it.id, it.performed) } }

    fun updateSet(exerciseId: Long, setId: Long, change: (LoggedSet) -> LoggedSet): LoggedWorkout = updateExercise(exerciseId) { exercise ->
        exercise.copy(sets = exercise.sets.map { if (it.id == setId) change(it) else it })
    }

    /** A new set copies the last one. */
    fun addSet(exerciseId: Long): LoggedWorkout {
        val id = nextId()
        return updateExercise(exerciseId) { exercise ->
            val last = exercise.sets.lastOrNull()
            exercise.copy(sets = exercise.sets + LoggedSet(id, last?.type ?: SetType.Working, last?.weightKg, last?.reps ?: 0))
        }
    }

    /** Removing the last set removes the exercise. */
    fun removeSet(exerciseId: Long, setId: Long): LoggedWorkout = copy(
        exercises = exercises.mapNotNull { exercise ->
            if (exercise.id != exerciseId) return@mapNotNull exercise
            exercise.copy(sets = exercise.sets.filterNot { it.id == setId }).takeIf { it.sets.isNotEmpty() }
        },
    )

    fun removeExercise(exerciseId: Long): LoggedWorkout = copy(exercises = exercises.filterNot { it.id == exerciseId })

    /** An added exercise starts with one set to fill in. */
    fun addExercise(exerciseId: Long): LoggedWorkout {
        val id = nextId()
        return copy(exercises = exercises + LoggedExercise(id, exerciseId, null, listOf(LoggedSet(id + 1, SetType.Working, null, 0))))
    }

    fun withDuration(minutes: Int): LoggedWorkout = copy(endedAt = startedAt + minutes.coerceAtLeast(1) * MILLIS_PER_MINUTE)

    private fun updateExercise(exerciseId: Long, change: (LoggedExercise) -> LoggedExercise) =
        copy(exercises = exercises.map { if (it.id == exerciseId) change(it) else it })

    private fun nextId(): Long = (exercises.flatMap { exercise -> exercise.sets.map { it.id } + exercise.id }.maxOrNull() ?: 0L) + 1

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
