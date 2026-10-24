package com.jaroszeek.gymtracker.data.backup

import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.testing.inMemoryDatabase
import com.jaroszeek.gymtracker.testing.seeder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class V1ImportTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: GtDatabase
    private lateinit var result: V1Result

    @Before
    fun import() = runTest {
        database = inMemoryDatabase()
        seeder(database).ensureSeeded()
        val v1 = V1Fixture.write(folder.newFile("v1.db"))
        result = BundledSQLiteDriver().open(v1.path).use { V1Import(it, database, "Trening").run() }
    }

    @After
    fun close() = database.close()

    @Test
    fun exercisesMatchTheBundledOnesOrBecomeCustom() = runTest {
        assertEquals(
            listOf(
                listOf("Landmine Press", "strength", null, """["Brace","Press up"]"""),
                listOf("Mystery Move", "cardio", "kettlebells", "[]"),
                listOf("Gone Exercise", "strength", null, "[]"),
            ),
            rows("SELECT name, category, equipment, instructions FROM exercises WHERE source = 'custom' ORDER BY id"),
        )
        assertEquals(
            listOf(listOf("shoulders", "1"), listOf("triceps", "1")),
            rows("SELECT muscle, is_primary FROM exercise_muscles JOIN exercises ON exercises.id = exercise_id WHERE name = 'Landmine Press' ORDER BY muscle"),
        )
    }

    @Test
    fun routinesKeepBlocksVariantsAndTargets() = runTest {
        assertEquals(
            listOf(
                // Rebuilt from routine_exercises: no structure
                listOf("Legacy", "0", "0", "Barbell Squat", "4", "10", "10", "60.0"),
                listOf("Push", "0", "0", "Barbell Squat", "2", "5", "8", "100.0"),
                // A flex block: a choice of variants
                listOf("Push", "1", "0", "Butterfly", "3", "8", "12", null),
                listOf("Push", "1", "1", "Landmine Press", "1", "12", "12", null),
                // A superset: one block per exercise, without the exercise v1 no longer has
                listOf("Push", "2", "0", "Pullups", "1", "10", "10", null),
            ),
            rows(
                """SELECT r.name, b.position, v.position, e.name, v.target_sets, v.reps_min, v.reps_max, v.target_weight_kg
                   FROM routine_variants v JOIN routine_blocks b ON b.id = v.block_id JOIN routines r ON r.id = b.routine_id
                   JOIN exercises e ON e.id = v.exercise_id ORDER BY r.id, b.position, v.position""",
            ),
        )
        assertEquals(listOf(listOf("Legacy", "${V1Fixture.START - 2 * V1Fixture.HOUR}"), listOf("Push", "${V1Fixture.START - V1Fixture.HOUR}")), rows("SELECT name, created_at FROM routines ORDER BY id"))
    }

    @Test
    fun finishedWorkoutsKeepTheirSetsInOrder() = runTest {
        assertEquals(
            listOf(
                listOf("Push", "Push", "${V1Fixture.START}", "${V1Fixture.START + V1Fixture.HOUR}"),
                listOf("Trening", null, "${V1Fixture.START + 96 * V1Fixture.HOUR}", "${V1Fixture.START + 97 * V1Fixture.HOUR}"),
            ),
            rows("SELECT w.name, r.name, w.started_at, w.ended_at FROM workouts w LEFT JOIN routines r ON r.id = w.routine_id ORDER BY w.started_at"),
        )
        assertEquals(
            listOf(
                listOf("0", "Barbell Squat", "0", "warmup", "100.0", "5"),
                listOf("0", "Barbell Squat", "1", "working", "62.3", "8"),
                listOf("1", "Mystery Move", "0", "working", null, "12"),
                // Sets whose exercise v1 no longer had: found by name, or created
                listOf("2", "Landmine Press", "0", "drop", "20.0", "10"),
                listOf("3", "Gone Exercise", "0", "failure", "10.0", "15"),
            ),
            rows(
                """SELECT we.position, e.name, s.position, s.type, s.weight_kg, s.reps FROM workout_sets s
                   JOIN workout_exercises we ON we.id = s.workout_exercise_id JOIN exercises e ON e.id = we.exercise_id
                   JOIN workouts w ON w.id = we.workout_id WHERE w.name = 'Push' ORDER BY we.position, s.position""",
            ),
        )
    }

    @Test
    fun bodyValuesInRangeComeAlong() = runTest {
        assertEquals(
            listOf(listOf("weight", "80.5"), listOf("height", "1.804"), listOf("body_fat", "15.5"), listOf("skeletal_muscle", "35.2")),
            rows("SELECT type, value FROM body_measurements ORDER BY id"),
        )
        assertEquals(1993, result.birthYear)
    }

    @Test
    fun whatDidNotComeAlongIsCounted() {
        assertEquals(
            listOf(
                Skipped(SkippedKind.UnfinishedWorkouts, 1),
                Skipped(SkippedKind.EmptyWorkouts, 1),
                Skipped(SkippedKind.HealthConnectSessions, 1),
                Skipped(SkippedKind.DailyActivity, 3),
                Skipped(SkippedKind.SetTypes, 1),
                Skipped(SkippedKind.RoutineWarmups, 1),
                Skipped(SkippedKind.Supersets, 1),
                Skipped(SkippedKind.Notes, 2),
                Skipped(SkippedKind.FatMass, 1),
                Skipped(SkippedKind.OutOfRange, 1),
                Skipped(SkippedKind.MissingExercises, 1),
                Skipped(SkippedKind.ExerciseMedia, 1),
            ),
            result.skipped,
        )
    }

    private suspend fun rows(sql: String): List<List<String?>> = database.useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            buildList {
                while (statement.step()) add(List(statement.getColumnCount()) { if (statement.isNull(it)) null else statement.getText(it) })
            }
        }
    }
}
