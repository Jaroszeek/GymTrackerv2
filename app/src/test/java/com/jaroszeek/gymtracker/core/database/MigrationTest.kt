package com.jaroszeek.gymtracker.core.database

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.data.workout.WorkoutRepository
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.RoutineDraft
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import com.jaroszeek.gymtracker.domain.workout.HealthLink
import com.jaroszeek.gymtracker.domain.workout.WorkoutVariant
import com.jaroszeek.gymtracker.testing.seeder
import java.io.File
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Builds each old version from its exported schema (what `MigrationTestHelper` does), adds data,
 * then opens it with the current database: Room runs the migrations and validates the schema.
 * Runs on the JVM, so every migration is checked in CI.
 */
class MigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "migration-test.db") }
    private var database: GtDatabase? = null

    @After
    fun close() {
        database?.close()
    }

    @Test
    fun version1MigratesKeepingCustomExercises() = runTest {
        createFromSchema(version = 1).run {
            execSQL(
                """INSERT INTO exercises (id, external_id, name, aliases_pl, category, instructions, images, source)
                   VALUES (1, NULL, 'Landmine Press', '[]', 'strength', '[]', '[]', 'custom')""",
            )
            execSQL("INSERT INTO exercise_muscles (exercise_id, muscle, is_primary) VALUES (1, 'shoulders', 1)")
            close()
        }

        val current = Room.databaseBuilder<GtDatabase>(file.path).setDriver(BundledSQLiteDriver()).build().also { database = it }
        val exercises = ExerciseRepository(current, seeder(current), Dispatchers.IO)
        val landmine = exercises.observe(1).first()!!
        assertEquals("Landmine Press", landmine.name)

        // The tables added in version 2 work
        val routines = RoutineRepository(current, exercises, Clock.systemUTC(), Dispatchers.IO)
        val routineId = routines.save(null, RoutineDraft(name = "Shoulders").addBlock(landmine))
        assertEquals(listOf(landmine), routines.observe(routineId).first()!!.blocks.map { it.variants.single().exercise })
    }

    @Test
    fun version2MigratesKeepingRoutines() = runTest {
        createFromSchema(version = 2).run {
            execSQL(
                """INSERT INTO exercises (id, external_id, name, aliases_pl, category, instructions, images, source)
                   VALUES (1, NULL, 'Landmine Press', '[]', 'strength', '[]', '[]', 'custom')""",
            )
            execSQL("INSERT INTO exercise_muscles (exercise_id, muscle, is_primary) VALUES (1, 'shoulders', 1)")
            execSQL("INSERT INTO routines (id, name, created_at, updated_at) VALUES (1, 'Shoulders', 0, 0)")
            execSQL("INSERT INTO routine_blocks (id, routine_id, position) VALUES (1, 1, 0)")
            execSQL(
                """INSERT INTO routine_variants (id, block_id, exercise_id, position, target_sets, reps_min, reps_max, target_weight_kg, rest_seconds)
                   VALUES (1, 1, 1, 0, 4, 6, 10, 30.0, 90)""",
            )
            close()
        }

        val current = Room.databaseBuilder<GtDatabase>(file.path).setDriver(BundledSQLiteDriver()).build().also { database = it }
        val exercises = ExerciseRepository(current, seeder(current), Dispatchers.IO)
        val routine = RoutineRepository(current, exercises, Clock.systemUTC(), Dispatchers.IO).observe(1).first()!!
        assertEquals(RoutineTargets(4, 6, 10, 30.0, 90), routine.blocks.single().variants.single().targets)

        // The tables added in version 3 work and link to the routine
        val variant = routine.blocks.single().variants.single()
        var workout = ActiveWorkout(routine.id, routine.name, 0, 0).addExercise(listOf(WorkoutVariant(1, variant.id, variant.targets)))
        workout.exercises.single().let { exercise -> workout = workout.updateSet(exercise.id, exercise.sets[0].id) { it.copy(done = true) } }
        val workouts = WorkoutRepository(current, Dispatchers.IO)
        workouts.save(workout, endedAt = 1)
        assertEquals(listOf(PerformedSet(30.0, 6)), workouts.lastSession(1))
    }

    @Test
    fun version3MigratesKeepingWorkouts() = runTest {
        createFromSchema(version = 3).run {
            execSQL(
                """INSERT INTO exercises (id, external_id, name, aliases_pl, category, instructions, images, source)
                   VALUES (1, NULL, 'Landmine Press', '[]', 'strength', '[]', '[]', 'custom')""",
            )
            execSQL("INSERT INTO exercise_muscles (exercise_id, muscle, is_primary) VALUES (1, 'shoulders', 1)")
            execSQL("INSERT INTO routines (id, name, created_at, updated_at) VALUES (1, 'Shoulders', 0, 0)")
            execSQL("INSERT INTO routine_blocks (id, routine_id, position) VALUES (1, 1, 0)")
            execSQL(
                """INSERT INTO routine_variants (id, block_id, exercise_id, position, target_sets, reps_min, reps_max, target_weight_kg, rest_seconds)
                   VALUES (1, 1, 1, 0, 4, 6, 10, 30.0, 90)""",
            )
            execSQL("INSERT INTO workouts (id, routine_id, name, started_at, ended_at) VALUES (1, 1, 'Shoulders', 1000, 2000)")
            execSQL("INSERT INTO workout_exercises (id, workout_id, exercise_id, position, routine_variant_id) VALUES (1, 1, 1, 0, 1)")
            execSQL("INSERT INTO workout_sets (id, workout_exercise_id, position, type, weight_kg, reps) VALUES (1, 1, 0, 'working', 30.0, 8)")
            close()
        }

        val current = Room.databaseBuilder<GtDatabase>(file.path).setDriver(BundledSQLiteDriver()).build().also { database = it }
        val workouts = WorkoutRepository(current, Dispatchers.IO)
        assertEquals(listOf(PerformedSet(30.0, 8)), workouts.lastSession(1))
        val migrated = workouts.forHealth(1)!!
        assertEquals(
            listOf(1L, "Shoulders", 1_000L, 2_000L, null, false),
            listOf(migrated.routineId, migrated.name, migrated.startedAt, migrated.endedAt, migrated.hcSessionId, migrated.hcMatchDismissed),
        )

        // The columns added in version 4 work
        workouts.linkHealthSession(1, "hc-session")
        workouts.dismissHealthMatch(1)
        assertEquals(HealthLink(1_000, 2_000, "hc-session", dismissed = true), workouts.observeHealthLink(1).first())
        assertEquals(listOf("hc-session"), workouts.observeHistory().first().map { it.healthSessionId })
    }

    private fun createFromSchema(version: Int): SQLiteConnection {
        val schema = Json.parseToJsonElement(File("schemas/${GtDatabase::class.qualifiedName}/$version.json").readText())
            .jsonObject.getValue("database").jsonObject
        val connection = BundledSQLiteDriver().open(file.path)
        schema.getValue("entities").jsonArray.forEach { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            connection.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
        connection.execSQL("PRAGMA user_version = $version")
        return connection
    }
}
