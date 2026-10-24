package com.jaroszeek.gymtracker.data.backup

import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.jaroszeek.gymtracker.core.database.BodyMeasurementEntity
import com.jaroszeek.gymtracker.core.database.ExerciseEntity
import com.jaroszeek.gymtracker.core.database.ExerciseMuscleEntity
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.database.RoutineBlockEntity
import com.jaroszeek.gymtracker.core.database.RoutineEntity
import com.jaroszeek.gymtracker.core.database.RoutineVariantEntity
import com.jaroszeek.gymtracker.core.database.WorkoutEntity
import com.jaroszeek.gymtracker.core.database.WorkoutExerciseEntity
import com.jaroszeek.gymtracker.core.database.WorkoutSetEntity
import com.jaroszeek.gymtracker.domain.body.BodyMetric
import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.exercise.ExerciseCategory
import com.jaroszeek.gymtracker.domain.exercise.ExerciseSource
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.SetType
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** What a v1 backup holds that did not come along, or came changed; [count] of what [kind] names. */
data class Skipped(val kind: SkippedKind, val count: Int)

enum class SkippedKind {
    /** Workouts without an end, never saved as done. */
    UnfinishedWorkouts,

    /** Workouts without a single set. */
    EmptyWorkouts,

    /** Sessions read from Health Connect; they come back with its sync. */
    HealthConnectSessions,

    /** Days of steps and calories from Health Connect; they come back with its sync. */
    DailyActivity,

    /** Myo-reps, negatives, back-off and partial sets, saved as working sets. */
    SetTypes,

    /** Warm-up sets planned in routines; targets here count working sets only. */
    RoutineWarmups,

    /** Supersets, whose exercises follow one another as separate blocks. */
    Supersets,

    /** Notes of routines and workouts. */
    Notes,

    /** Fat mass, which follows from weight and body fat. */
    FatMass,

    /** Body values outside the ranges the profile accepts. */
    OutOfRange,

    /** Exercises a routine names but the backup no longer has. */
    MissingExercises,

    /** Images and links of custom exercises. */
    ExerciseMedia,
}

data class V1Result(val skipped: List<Skipped>, val birthYear: Int?)

/**
 * Converts a GymTracker v1 backup ([v1], a Room database of [VERSION]) into [database], which already
 * holds the bundled exercises. Only the file format of v1 is used: its exercises are matched to the
 * bundled ones by the free-exercise-db id in their image address, then by name; the rest become custom
 * exercises. The mapping is in docs/DECISIONS.md (D-076).
 */
internal class V1Import(
    private val v1: SQLiteConnection,
    private val database: GtDatabase,
    /** The name of a workout saved without one. */
    private val emptyWorkoutName: String,
) {
    private val skipped = mutableMapOf<SkippedKind, Int>()
    private val exercises = database.exerciseDao()

    private lateinit var v1Exercises: Map<Long, V1Exercise>
    private lateinit var bundledByExternalId: Map<String, Long>
    private lateinit var bundledByName: Map<String, Long>

    /** v1 exercise id to the exercise here, filled as they are needed. */
    private val exerciseIds = mutableMapOf<Long, Long>()

    /** Custom exercises created here, by lowercase name, for sets whose exercise v1 no longer has. */
    private val customByName = mutableMapOf<String, Long>()

    suspend fun run(): V1Result {
        v1Exercises = query("SELECT id, name, description, imageUri, link, bodyParts, equipment, isCustom FROM exercises") {
            V1Exercise(it.getLong(0), it.text(1), it.text(2), it.text(3), it.text(4), it.text(5), it.text(6), it.getLong(7) != 0L)
        }.associateBy { it.id }
        val routines = query("SELECT id, name, note, createdAt, structure FROM routines ORDER BY createdAt, id") {
            V1Routine(it.getLong(0), it.text(1), it.text(2), it.getLong(3), it.text(4))
        }
        val routineExercises = query("SELECT routineId, exerciseId, targetWeightKg, targetReps, targetSets FROM routine_exercises ORDER BY routineId, position") {
            it.getLong(0) to V1BlockExercise(it.getLong(1), List(it.getLong(4).toInt().coerceIn(0, MAX_SETS)) { _ -> V1Set(V1_NORMAL, it.doubleOrNull(2), it.intOrNull(3)) })
        }.groupBy({ it.first }, { it.second })
        val sessions = query("SELECT id, routineId, routineName, startTime, endTime, note, externalId FROM workout_sessions ORDER BY startTime, id") {
            V1Session(it.getLong(0), it.longOrNull(1), it.text(2), it.getLong(3), it.longOrNull(4), it.text(5), it.textOrNull(6))
        }
        val sets = query("SELECT sessionId, exerciseId, exerciseName, weightKg, reps, type FROM set_entries ORDER BY sessionId, id") {
            V1SetEntry(it.getLong(0), it.getLong(1), it.text(2), it.getDouble(3), it.getLong(4).toInt(), it.text(5))
        }.groupBy { it.sessionId }
        val metrics = query("SELECT date, weightKg, heightCm, age, bodyFatPct, fatMassKg, skeletalMuscleKg FROM body_metrics ORDER BY date, id") {
            V1Metrics(it.getLong(0), it.doubleOrNull(1), it.doubleOrNull(2), it.intOrNull(3), it.doubleOrNull(4), it.doubleOrNull(5), it.doubleOrNull(6))
        }
        count(SkippedKind.DailyActivity, query("SELECT COUNT(*) FROM (SELECT dayStart FROM daily_steps UNION SELECT dayStart FROM daily_calories)") { it.getLong(0).toInt() }.single())

        database.withWriteTransaction {
            val bundled = exercises.bundled()
            bundledByExternalId = bundled.associate { it.externalId to it.id }
            bundledByName = bundled.associate { it.name.lowercase() to it.id }
            // Custom exercises come along even when nothing uses them
            v1Exercises.values.filter { it.isCustom }.forEach { exerciseFor(it.id, it.name) }
            val routineIds = routines.associate { it.id to importRoutine(it, routineExercises[it.id].orEmpty()) }
            sessions.forEach { importSession(it, sets[it.id].orEmpty(), routineIds) }
            metrics.forEach { importMetrics(it) }
        }
        return V1Result(
            skipped = SkippedKind.entries.mapNotNull { kind -> skipped[kind]?.takeIf { it > 0 }?.let { Skipped(kind, it) } },
            birthYear = metrics.lastOrNull { (it.age ?: 0) in 1..MAX_AGE }?.let { Instant.ofEpochMilli(it.date).atZone(ZoneOffset.UTC).year - it.age!! },
        )
    }

    private suspend fun importRoutine(routine: V1Routine, fallback: List<V1BlockExercise>): Long {
        if (routine.note.isNotBlank()) count(SkippedKind.Notes)
        val routineId = database.routineDao().insertRoutine(RoutineEntity(name = routine.name, createdAt = routine.createdAt, updatedAt = routine.createdAt))
        // A routine saved before blocks existed has its exercises only in routine_exercises
        val blocks = parseStructure(routine.structure).ifEmpty { fallback.map { V1Block(V1_SINGLE, listOf(it)) } }
        val v2Blocks = blocks.flatMap { block ->
            when (block.type) {
                V1_FLEX -> listOf(block.exercises)
                else -> {
                    if (block.type == V1_SUPERSET) count(SkippedKind.Supersets)
                    block.exercises.map { listOf(it) }
                }
            }
        }
        var position = 0
        v2Blocks.forEach { variants ->
            val present = variants.filter { it.exerciseId in v1Exercises }
            count(SkippedKind.MissingExercises, variants.size - present.size)
            if (present.isEmpty()) return@forEach
            val blockId = database.routineDao().insertBlock(RoutineBlockEntity(routineId = routineId, position = position++))
            present.forEachIndexed { index, variant ->
                val exercise = v1Exercises.getValue(variant.exerciseId)
                database.routineDao().insertVariant(targets(variant, blockId, exerciseFor(exercise.id, exercise.name), index))
            }
        }
        return routineId
    }

    /** Targets from the planned sets: working sets count, the reps span their range, the first weight set. */
    private fun targets(variant: V1BlockExercise, blockId: Long, exerciseId: Long, position: Int): RoutineVariantEntity {
        val (warmups, work) = variant.sets.partition { it.type == V1_WARMUP }
        count(SkippedKind.RoutineWarmups, warmups.size)
        val reps = work.mapNotNull { it.reps }.filter { it > 0 }
        return RoutineVariantEntity(
            blockId = blockId,
            exerciseId = exerciseId,
            position = position,
            targetSets = if (work.isEmpty()) RoutineTargets.DEFAULT_SETS else work.size.coerceAtMost(MAX_SETS),
            repsMin = reps.minOrNull()?.coerceAtMost(MAX_REPS) ?: RoutineTargets.DEFAULT_REPS_MIN,
            repsMax = reps.maxOrNull()?.coerceAtMost(MAX_REPS) ?: RoutineTargets.DEFAULT_REPS_MAX,
            targetWeightKg = work.firstNotNullOfOrNull { set -> set.weightKg?.takeIf { it > 0 } }?.let { round(it, 2) },
            restSeconds = null,
        )
    }

    private suspend fun importSession(session: V1Session, sets: List<V1SetEntry>, routineIds: Map<Long, Long>) {
        when {
            session.externalId != null -> return count(SkippedKind.HealthConnectSessions)
            session.endTime == null -> return count(SkippedKind.UnfinishedWorkouts)
            sets.isEmpty() -> return count(SkippedKind.EmptyWorkouts)
        }
        if (session.note.isNotBlank()) count(SkippedKind.Notes)
        val dao = database.workoutDao()
        val workoutId = dao.insertWorkout(
            WorkoutEntity(
                routineId = session.routineId?.let(routineIds::get),
                name = session.routineName.trim().ifEmpty { emptyWorkoutName },
                startedAt = session.startTime,
                endedAt = maxOf(session.startTime, session.endTime!!),
            ),
        )
        // Sets in the order they were done, gathered by exercise in the order the exercises began
        // Two v1 exercises can become the same one here
        sets.groupBy { exerciseFor(it.exerciseId, it.exerciseName) }.entries.forEachIndexed { position, (exerciseId, exerciseSets) ->
            val workoutExerciseId = dao.insertExercise(WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exerciseId, position = position, routineVariantId = null))
            dao.insertSets(
                exerciseSets.mapIndexed { index, set ->
                    WorkoutSetEntity(
                        workoutExerciseId = workoutExerciseId,
                        position = index,
                        type = setType(set.type).key,
                        weightKg = set.weightKg.takeIf { it > 0 }?.let { round(it, 2) },
                        reps = set.reps.coerceAtLeast(0),
                    )
                },
            )
        }
    }

    private fun setType(v1: String): SetType = when (v1) {
        V1_NORMAL -> SetType.Working
        V1_WARMUP -> SetType.Warmup
        "DROPSET" -> SetType.DropSet
        "FAILURE" -> SetType.Failure
        else -> SetType.Working.also { count(SkippedKind.SetTypes) }
    }

    private suspend fun importMetrics(metrics: V1Metrics) {
        if (metrics.fatMassKg != null) count(SkippedKind.FatMass)
        listOf(
            BodyMetric.Weight to metrics.weightKg?.let { round(it, 2) },
            BodyMetric.Height to metrics.heightCm?.let { round(it / CM_PER_M, 3) },
            BodyMetric.BodyFat to metrics.bodyFatPct?.let { round(it, 2) },
            BodyMetric.SkeletalMuscle to metrics.skeletalMuscleKg?.let { round(it, 2) },
        ).forEach { (metric, value) ->
            when {
                value == null -> Unit
                value in metric.range -> database.bodyDao().insert(BodyMeasurementEntity(type = metric.key, time = metrics.date, value = value))
                else -> count(SkippedKind.OutOfRange)
            }
        }
    }

    /** The exercise here for v1's [v1Id]; one v1 no longer has is found or created by [name]. */
    private suspend fun exerciseFor(v1Id: Long, name: String): Long = exerciseIds.getOrPut(v1Id) {
        val exercise = v1Exercises[v1Id]
        if (exercise == null) {
            val named = v1Exercises.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (named != null) {
                exerciseFor(named.id, named.name)
            } else {
                bundledByName[name.lowercase()] ?: customByName.getOrPut(name.lowercase()) { createCustom(V1Exercise(v1Id, name)) }
            }
        } else {
            (if (exercise.isCustom) null else bundledFor(exercise)) ?: createCustom(exercise)
        }
    }

    private fun bundledFor(exercise: V1Exercise): Long? =
        FREE_EXERCISE_DB_IMAGE.find(exercise.imageUri)?.let { bundledByExternalId[it.groupValues[1]] }
            ?: bundledByName[exercise.name.lowercase()]
            ?: ALIASES[exercise.name]?.let(bundledByExternalId::get)

    private suspend fun createCustom(exercise: V1Exercise): Long {
        if (exercise.imageUri.isNotBlank() || exercise.link.isNotBlank()) count(SkippedKind.ExerciseMedia)
        val parts = exercise.bodyParts.split(',').map { it.trim() }
        val id = exercises.insert(
            ExerciseEntity(
                externalId = null,
                name = exercise.name.trim(),
                aliasesPl = emptyList(),
                category = when {
                    "CARDIO" in parts -> ExerciseCategory.Cardio
                    "STRETCH" in parts -> ExerciseCategory.Stretching
                    else -> ExerciseCategory.Strength
                }.key,
                equipment = EQUIPMENT[exercise.equipment]?.key,
                level = null,
                mechanic = null,
                force = null,
                instructions = exercise.description.lines().map { it.trim() }.filter { it.isNotEmpty() },
                images = emptyList(),
                source = ExerciseSource.Custom.key,
            ),
        )
        exercises.insertMuscles(parts.mapNotNull { MUSCLES[it] }.distinct().map { ExerciseMuscleEntity(id, it.key, isPrimary = true) })
        return id
    }

    /** The blocks of a routine's structure JSON; empty when there is none or it does not parse. */
    private fun parseStructure(structure: String): List<V1Block> {
        if (structure.isBlank()) return emptyList()
        return runCatching {
            (Json.parseToJsonElement(structure) as JsonArray).map { block ->
                block as JsonObject
                V1Block(
                    type = block["type"]?.jsonPrimitive?.content ?: V1_SINGLE,
                    exercises = (block["exercises"] as? JsonArray).orEmpty().map { exercise ->
                        exercise as JsonObject
                        V1BlockExercise(
                            exerciseId = exercise["exerciseId"]?.jsonPrimitive?.longOrNull ?: 0,
                            sets = (exercise["sets"] as? JsonArray).orEmpty().map { set ->
                                set as JsonObject
                                V1Set(
                                    type = set["t"]?.jsonPrimitive?.content ?: V1_NORMAL,
                                    weightKg = set["w"]?.jsonPrimitive?.doubleOrNull,
                                    reps = set["r"]?.jsonPrimitive?.intOrNull,
                                )
                            },
                        )
                    },
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun count(kind: SkippedKind, n: Int = 1) {
        if (n > 0) skipped[kind] = (skipped[kind] ?: 0) + n
    }

    private fun <T> query(sql: String, row: (SQLiteStatement) -> T): List<T> = v1.prepare(sql).use { statement ->
        buildList { while (statement.step()) add(row(statement)) }
    }

    private fun SQLiteStatement.text(index: Int) = if (isNull(index)) "" else getText(index)
    private fun SQLiteStatement.textOrNull(index: Int) = if (isNull(index)) null else getText(index)
    private fun SQLiteStatement.longOrNull(index: Int) = if (isNull(index)) null else getLong(index)
    private fun SQLiteStatement.intOrNull(index: Int) = if (isNull(index)) null else getLong(index).toInt()
    private fun SQLiteStatement.doubleOrNull(index: Int) = if (isNull(index)) null else getDouble(index)

    private data class V1Exercise(
        val id: Long,
        val name: String,
        val description: String = "",
        val imageUri: String = "",
        val link: String = "",
        val bodyParts: String = "",
        val equipment: String = "",
        val isCustom: Boolean = true,
    )

    private data class V1Routine(val id: Long, val name: String, val note: String, val createdAt: Long, val structure: String)
    private data class V1Block(val type: String, val exercises: List<V1BlockExercise>)
    private data class V1BlockExercise(val exerciseId: Long, val sets: List<V1Set>)
    private data class V1Set(val type: String, val weightKg: Double?, val reps: Int?)
    private data class V1Session(val id: Long, val routineId: Long?, val routineName: String, val startTime: Long, val endTime: Long?, val note: String, val externalId: String?)
    private data class V1SetEntry(val sessionId: Long, val exerciseId: Long, val exerciseName: String, val weightKg: Double, val reps: Int, val type: String)
    private data class V1Metrics(
        val date: Long,
        val weightKg: Double?,
        val heightCm: Double?,
        val age: Int?,
        val bodyFatPct: Double?,
        val fatMassKg: Double?,
        val skeletalMuscleKg: Double?,
    )

    companion object {
        /** The Room version of every v1 release that makes backups. */
        const val VERSION = 7

        /** Tables that tell a v1 backup apart. */
        val TABLES = setOf("exercises", "routines", "routine_exercises", "workout_sessions", "set_entries", "body_metrics")

        private const val V1_SINGLE = "SINGLE"
        private const val V1_SUPERSET = "SUPERSET"
        private const val V1_FLEX = "FLEX"
        private const val V1_NORMAL = "NORMAL"
        private const val V1_WARMUP = "WARMUP"
        private const val MAX_SETS = 20
        private const val MAX_REPS = 100
        private const val CM_PER_M = 100.0
        private const val MAX_AGE = 120

        private val FREE_EXERCISE_DB_IMAGE = Regex("^https://raw\\.githubusercontent\\.com/yuhonas/free-exercise-db/main/exercises/([^/]+)/")

        /** v1 exercises without a free-exercise-db image whose bundled match has another name. */
        private val ALIASES = mapOf(
            "Nordic Hamstring Curl" to "Nordic_Ham_Curl",
            "Reverse Nordic Curl" to "Reverse_Nordic",
            "Pec Deck" to "Butterfly",
        )

        private val EQUIPMENT = mapOf(
            "MACHINE" to Equipment.Machine,
            "CABLE" to Equipment.Cable,
            "KETTLEBELL" to Equipment.Kettlebells,
            "BAND" to Equipment.Bands,
            "BODYWEIGHT" to Equipment.BodyOnly,
            "OTHER" to Equipment.Other,
        )

        private val MUSCLES = mapOf(
            "CHEST" to Muscle.Chest,
            "BACK" to Muscle.Lats,
            "LEGS" to Muscle.Quadriceps,
            "SHOULDERS" to Muscle.Shoulders,
            "BICEPS" to Muscle.Biceps,
            "TRICEPS" to Muscle.Triceps,
            "ABS" to Muscle.Abdominals,
            "GLUTES" to Muscle.Glutes,
            "FOREARMS" to Muscle.Forearms,
            "CALVES" to Muscle.Calves,
        )

        /** v1 kept values as floats: 62.3 comes as 62.29999923706055. */
        private fun round(value: Double, decimals: Int): Double = 10.0.pow(decimals).let { (value * it).roundToLong() / it }
    }
}
