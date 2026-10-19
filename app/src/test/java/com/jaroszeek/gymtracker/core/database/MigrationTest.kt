package com.jaroszeek.gymtracker.core.database

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.domain.routine.RoutineDraft
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
