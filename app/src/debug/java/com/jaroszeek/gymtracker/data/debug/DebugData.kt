package com.jaroszeek.gymtracker.data.debug

import android.content.Context
import android.util.Log
import androidx.room3.withWriteTransaction
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.database.WorkoutEntity
import com.jaroszeek.gymtracker.core.database.WorkoutExerciseEntity
import com.jaroszeek.gymtracker.core.database.WorkoutSetEntity
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.data.routine.TemplateRepository
import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.routine.SetType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * Debug only: copies the push/pull/legs template and adds [count] finished workouts of it, every other
 * day up to now, with slowly growing weights, to check history and charts with lots of data. It only
 * adds rows, so real data stays; clearing app data removes everything.
 * `adb shell am start -n com.jaroszeek.gymtracker.debug/com.jaroszeek.gymtracker.designsystem.gallery.GalleryActivity --ei seedWorkouts 300`
 */
object DebugData {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun database(): GtDatabase

        fun templates(): TemplateRepository

        fun routines(): RoutineRepository
    }

    suspend fun seed(context: Context, count: Int) {
        val deps = EntryPointAccessors.fromApplication(context, Dependencies::class.java)
        val templates = deps.templates()
        val template = templates.templates().first { it.id == TEMPLATE }
        val routines = templates.copy(template).map { checkNotNull(deps.routines().observe(it).first()) }
        val dao = deps.database().workoutDao()
        val random = Random(SEED)
        val now = System.currentTimeMillis()
        deps.database().withWriteTransaction {
            repeat(count) { i ->
                val routine = routines[i % routines.size]
                val progress = i.toDouble() / count
                val startedAt = now - (count - i) * GAP_MILLIS + random.nextLong(HOUR_MILLIS)
                val endedAt = startedAt + MIN_DURATION_MILLIS + random.nextLong(HOUR_MILLIS / 2)
                val workoutId = dao.insertWorkout(WorkoutEntity(routineId = routine.id, name = routine.name, startedAt = startedAt, endedAt = endedAt))
                routine.blocks.forEachIndexed { position, block ->
                    // Now and then the alternative variant
                    val variant = block.variants[if (block.variants.size > 1 && random.nextInt(4) == 0) 1 else 0]
                    val rowId = dao.insertExercise(WorkoutExerciseEntity(workoutId = workoutId, exerciseId = variant.exercise.id, position = position, routineVariantId = variant.id))
                    val weight = if (variant.exercise.equipment == Equipment.BodyOnly) null else weight(variant.exercise.id, progress, random)
                    val targets = variant.targets
                    val sets = buildList {
                        if (position == 0 && weight != null) add(WorkoutSetEntity(0, rowId, 0, SetType.Warmup.key, plates(weight / 2), WARMUP_REPS))
                        repeat(targets.sets) { add(WorkoutSetEntity(0, rowId, size, SetType.Working.key, weight, random.nextInt(targets.repsMin, targets.repsMax + 1))) }
                    }
                    dao.insertSets(sets)
                }
            }
        }
        Log.i(TAG, "Added $count workouts of ${template.name}")
    }

    /** Up to a third heavier by the end, with a wobble, in 2.5 kg steps. */
    private fun weight(exerciseId: Long, progress: Double, random: Random): Double {
        val base = BASE_KG + exerciseId % BASE_SPREAD * STEP_KG * 4
        return plates(base * (1 + GROWTH * progress) + random.nextDouble(-STEP_KG, STEP_KG))
    }

    private fun plates(kg: Double) = (kg / STEP_KG).roundToInt().coerceAtLeast(1) * STEP_KG

    private const val TAG = "DebugData"
    private const val TEMPLATE = "ppl"
    private const val SEED = 42
    private const val HOUR_MILLIS = 3_600_000L
    private const val GAP_MILLIS = 48 * HOUR_MILLIS
    private const val MIN_DURATION_MILLIS = HOUR_MILLIS
    private const val WARMUP_REPS = 10
    private const val BASE_KG = 20.0
    private const val BASE_SPREAD = 6
    private const val STEP_KG = 2.5
    private const val GROWTH = 0.33
}
