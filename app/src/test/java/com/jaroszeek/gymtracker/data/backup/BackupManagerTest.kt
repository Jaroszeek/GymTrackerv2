package com.jaroszeek.gymtracker.data.backup

import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.jaroszeek.gymtracker.core.database.BodyMeasurementEntity
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.database.RoutineBlockEntity
import com.jaroszeek.gymtracker.core.database.RoutineEntity
import com.jaroszeek.gymtracker.core.database.RoutineVariantEntity
import com.jaroszeek.gymtracker.core.database.WorkoutEntity
import com.jaroszeek.gymtracker.core.database.WorkoutExerciseEntity
import com.jaroszeek.gymtracker.core.database.WorkoutSetEntity
import com.jaroszeek.gymtracker.testing.seeder
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var files: BackupFiles
    private lateinit var live: GtDatabase
    private val opened = mutableListOf<GtDatabase>()

    private fun open(file: File): GtDatabase = Room.databaseBuilder<GtDatabase>(file.path).setDriver(BundledSQLiteDriver()).build().also { opened += it }

    private fun manager(database: GtDatabase = live) =
        BackupManager(database, files, BundledSQLiteDriver(), ::open, { seeder(it).ensureSeeded() }, "Trening", Dispatchers.IO)

    @Before
    fun setUp() = runTest {
        files = BackupFiles(File(folder.root, "databases/gymtracker.db").apply { parentFile!!.mkdirs() }, File(folder.root, "no_backup/backup"))
        live = open(files.database)
        seeder(live).ensureSeeded()
        val squat = live.exerciseDao().bundled().first { it.externalId == "Barbell_Squat" }.id
        val routine = live.routineDao().insertRoutine(RoutineEntity(name = "Legs", createdAt = 1_000, updatedAt = 2_000))
        val block = live.routineDao().insertBlock(RoutineBlockEntity(routineId = routine, position = 0))
        val variant = live.routineDao().insertVariant(RoutineVariantEntity(blockId = block, exerciseId = squat, position = 0, targetSets = 3, repsMin = 5, repsMax = 8, targetWeightKg = 100.0, restSeconds = 180))
        listOf(START, START + DAY).forEach { start ->
            val workout = live.workoutDao().insertWorkout(WorkoutEntity(routineId = routine, name = "Legs", startedAt = start, endedAt = start + HOUR))
            val exercise = live.workoutDao().insertExercise(WorkoutExerciseEntity(workoutId = workout, exerciseId = squat, position = 0, routineVariantId = variant))
            live.workoutDao().insertSets(listOf(WorkoutSetEntity(workoutExerciseId = exercise, position = 0, type = "working", weightKg = 100.0, reps = 5)))
        }
        live.bodyDao().insert(BodyMeasurementEntity(type = "weight", time = START, value = 82.5))
    }

    @After
    fun close() = opened.forEach { it.close() }

    @Test
    fun exportThenImportGivesIdenticalData() = runTest {
        val backup = export()
        val before = dump(live)
        // Changed after the export: the import brings the exported state back
        live.bodyDao().insert(BodyMeasurementEntity(type = "weight", time = START + DAY, value = 83.0))

        val inspection = manager().inspect(backup.inputStream())

        assertEquals(Inspection.Ready(BackupSummary(routines = 1, workouts = 2, measurements = 1, customExercises = 0, firstWorkout = START, lastWorkout = START + DAY)), inspection)
        manager().confirm()
        live.close()
        assertTrue(BackupManager.applyPending(files, Instant.ofEpochMilli(START)))
        assertEquals(before, dump(open(files.database)))
        // The replaced database stays as a copy with a timestamp
        assertTrue(File(files.previous, "gymtracker-20231114-221320.db").exists())
    }

    @Test
    fun aV1BackupImportsAsConverted() = runTest {
        val v1 = V1Fixture.write(folder.newFile("GymTracker-backup-2026-10-01-1200.db"))

        val inspection = manager().inspect(v1.inputStream()) as Inspection.Ready

        assertTrue(inspection.summary.fromV1)
        assertEquals(listOf(2, 2, 4, 3), with(inspection.summary) { listOf(routines, workouts, measurements, customExercises) })
        assertEquals(1993, inspection.summary.birthYear)
        manager().confirm()
        live.close()
        assertTrue(BackupManager.applyPending(files, Instant.ofEpochMilli(START)))
        val imported = open(files.database)
        assertEquals(listOf("Legacy", "Push"), imported.routineDao().observeAll().first().map { it.routine.name })
    }

    @Test
    fun randomBytesAreNotABackup() = runTest {
        assertEquals(Inspection.Rejected(Rejection.NotABackup), manager().inspect(Random(1).nextBytes(8_192).inputStream()))
    }

    @Test
    fun aTruncatedBackupIsDamaged() = runTest {
        val backup = export()
        assertEquals(Inspection.Rejected(Rejection.Damaged), manager().inspect(backup.copyOf(backup.size / 2).inputStream()))
    }

    @Test
    fun aBackupWithOverwrittenPagesIsDamaged() = runTest {
        val backup = export()
        // The page header right after the first page
        Random(2).nextBytes(backup, PAGE_SIZE, PAGE_SIZE + 64)
        assertEquals(Inspection.Rejected(Rejection.Damaged), manager().inspect(backup.inputStream()))
    }

    @Test
    fun aBackupFromANewerVersionIsRejected() = runTest {
        val file = folder.newFile("newer.db").apply { writeBytes(export()) }
        BundledSQLiteDriver().open(file.path).use { it.execSQL("PRAGMA user_version = ${GtDatabase.VERSION + 1}") }
        assertEquals(Inspection.Rejected(Rejection.Newer), manager().inspect(file.inputStream()))
    }

    @Test
    fun anotherSqliteDatabaseIsNotABackup() = runTest {
        val file = folder.newFile("notes.db")
        BundledSQLiteDriver().open(file.path).use { it.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT)") }
        assertEquals(Inspection.Rejected(Rejection.NotABackup), manager().inspect(file.inputStream()))
    }

    @Test
    fun anOlderV1BackupIsRejected() = runTest {
        val v1 = V1Fixture.write(folder.newFile("old-v1.db"), version = 6)
        assertEquals(Inspection.Rejected(Rejection.UnsupportedV1), manager().inspect(v1.inputStream()))
    }

    @Test
    fun nothingChangesUntilConfirmed() = runTest {
        val before = dump(live)
        manager().inspect(export().inputStream())
        manager().discard()

        assertFalse(BackupManager.applyPending(files, Instant.ofEpochMilli(START)))
        assertEquals(before, dump(live))
        assertEquals(emptyList<String>(), files.work.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun onlyTheLastReplacedDatabasesAreKept() = runTest {
        live.close()
        repeat(4) { index ->
            files.pending.apply { parentFile!!.mkdirs() }.writeBytes(files.database.readBytes())
            BackupManager.applyPending(files, Instant.ofEpochMilli(START + index * DAY))
        }
        assertEquals(
            listOf("gymtracker-20231115-221320.db", "gymtracker-20231116-221320.db", "gymtracker-20231117-221320.db"),
            files.previous.list()!!.filter { it.endsWith(".db") }.sorted(),
        )
    }

    private suspend fun export(): ByteArray = ByteArrayOutputStream().also { manager().export(it) }.toByteArray()

    /** Every row of every table, in order. */
    private suspend fun dump(database: GtDatabase): List<String> = database.useReaderConnection { connection ->
        val tables = connection.usePrepared("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name") {
            buildList { while (it.step()) add(it.getText(0)) }
        }
        tables.flatMap { table ->
            connection.usePrepared("SELECT * FROM `$table` ORDER BY rowid") { statement ->
                buildList {
                    while (statement.step()) add("$table: " + List(statement.getColumnCount()) { if (statement.isNull(it)) "null" else statement.getText(it) })
                }
            }
        }
    }

    private companion object {
        // 2023-11-14 22:13:20 UTC
        const val START = 1_700_000_000_000L
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        const val PAGE_SIZE = 4_096
    }
}
