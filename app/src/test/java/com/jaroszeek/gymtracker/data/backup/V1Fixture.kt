package com.jaroszeek.gymtracker.data.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File

/**
 * A synthetic GymTracker v1 backup: the tables of its Room database version 7, as its backup file has
 * them, with data covering every rule of the mapping (D-076). Never the owner's real backup.
 */
object V1Fixture {
    const val START = 1_700_000_000_000L
    const val HOUR = 3_600_000L

    private val SCHEMA = listOf(
        "CREATE TABLE exercises (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, " +
            "imageUri TEXT, link TEXT, bodyParts TEXT NOT NULL, equipment TEXT NOT NULL, isCustom INTEGER NOT NULL)",
        "CREATE TABLE routines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, note TEXT NOT NULL, " +
            "createdAt INTEGER NOT NULL, structure TEXT NOT NULL DEFAULT '')",
        "CREATE TABLE routine_exercises (routineId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, position INTEGER NOT NULL, " +
            "targetWeightKg REAL, targetReps INTEGER, targetSets INTEGER NOT NULL, PRIMARY KEY(routineId, exerciseId))",
        "CREATE TABLE workout_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, routineId INTEGER, routineName TEXT NOT NULL, " +
            "startTime INTEGER NOT NULL, endTime INTEGER, note TEXT NOT NULL, externalId TEXT, externalType INTEGER, linkedSessionId INTEGER)",
        "CREATE TABLE set_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, " +
            "exerciseName TEXT NOT NULL, weightKg REAL NOT NULL, reps INTEGER NOT NULL, setNumber INTEGER NOT NULL, " +
            "type TEXT NOT NULL DEFAULT 'NORMAL', timestamp INTEGER NOT NULL)",
        "CREATE TABLE body_metrics (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date INTEGER NOT NULL, weightKg REAL, heightCm REAL, " +
            "age INTEGER, bodyFatPct REAL, fatMassKg REAL, skeletalMuscleKg REAL)",
        "CREATE TABLE daily_steps (dayStart INTEGER NOT NULL PRIMARY KEY, steps INTEGER NOT NULL)",
        "CREATE TABLE daily_calories (dayStart INTEGER NOT NULL PRIMARY KEY, activeKcal REAL, totalKcal REAL)",
        "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
        "INSERT INTO room_master_table VALUES (42, 'v1-identity')",
    )

    private const val SQUAT_IMAGE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Squat/0.jpg"

    private val DATA = listOf(
        // 1 by its free-exercise-db image, 2 by name, 3 by an alias, 4 custom, 5 unused, 6 used and unmatched
        "INSERT INTO exercises VALUES (1, 'Squat (v1 name)', '', '$SQUAT_IMAGE', NULL, 'LEGS,GLUTES', 'FREE_WEIGHT', 0)",
        "INSERT INTO exercises VALUES (2, 'pullups', '', NULL, NULL, 'BACK', 'BODYWEIGHT', 0)",
        "INSERT INTO exercises VALUES (3, 'Pec Deck', '', NULL, NULL, 'CHEST', 'MACHINE', 0)",
        "INSERT INTO exercises VALUES (4, 'Landmine Press', 'Brace\n\n  Press up  ', 'content://photo/1', 'https://example.com', " +
            "'SHOULDERS,TRICEPS,FULL_BODY', 'FREE_WEIGHT', 1)",
        "INSERT INTO exercises VALUES (5, 'Unused Builtin', '', NULL, NULL, 'ABS', 'OTHER', 0)",
        "INSERT INTO exercises VALUES (6, 'Mystery Move', '', NULL, NULL, 'CARDIO', 'KETTLEBELL', 0)",

        """INSERT INTO routines VALUES (1, 'Push', 'heavy day', ${START - HOUR}, '[
            {"type":"SINGLE","exercises":[{"exerciseId":1,"sets":[{"t":"WARMUP","w":40.0,"r":10},{"t":"NORMAL","w":100.0,"r":5},{"t":"NORMAL","w":100.0,"r":8}]}]},
            {"type":"FLEX","exercises":[{"exerciseId":3,"sets":[]},{"exerciseId":4,"sets":[{"t":"NORMAL","r":12}]}]},
            {"type":"SUPERSET","exercises":[{"exerciseId":2,"sets":[{"t":"FAILURE","r":10}]},{"exerciseId":99,"sets":[]}]}
        ]')""",
        "INSERT INTO routines VALUES (2, 'Legacy', '', ${START - 2 * HOUR}, '')",
        "INSERT INTO routine_exercises VALUES (2, 1, 0, 60.0, 10, 4)",

        // 1 done from a routine, 2 unfinished, 3 from Health Connect, 4 without sets, 5 without a name
        "INSERT INTO workout_sessions VALUES (1, 1, 'Push', $START, ${START + HOUR}, 'felt good', NULL, NULL, NULL)",
        "INSERT INTO workout_sessions VALUES (2, 1, 'Push', ${START + 24 * HOUR}, NULL, '', NULL, NULL, NULL)",
        "INSERT INTO workout_sessions VALUES (3, NULL, 'Walk', ${START + 48 * HOUR}, ${START + 49 * HOUR}, '', 'hc-1', 79, NULL)",
        "INSERT INTO workout_sessions VALUES (4, NULL, 'Empty', ${START + 72 * HOUR}, ${START + 73 * HOUR}, '', NULL, NULL, NULL)",
        "INSERT INTO workout_sessions VALUES (5, NULL, '  ', ${START + 96 * HOUR}, ${START + 97 * HOUR}, '', NULL, NULL, NULL)",
        "INSERT INTO set_entries VALUES (1, 1, 1, 'Squat (v1 name)', 100.0, 5, 1, 'WARMUP', $START)",
        "INSERT INTO set_entries VALUES (2, 1, 6, 'Mystery Move', 0.0, 12, 1, 'NORMAL', $START)",
        "INSERT INTO set_entries VALUES (3, 1, 1, 'Squat (v1 name)', 62.3, 8, 2, 'MYOREPS', $START)",
        "INSERT INTO set_entries VALUES (4, 1, 77, 'landmine press', 20.0, 10, 1, 'DROPSET', $START)",
        "INSERT INTO set_entries VALUES (5, 1, 88, 'Gone Exercise', 10.0, 15, 1, 'FAILURE', $START)",
        "INSERT INTO set_entries VALUES (6, 2, 1, 'Squat (v1 name)', 100.0, 5, 1, 'NORMAL', ${START + 24 * HOUR})",
        "INSERT INTO set_entries VALUES (7, 3, 1, 'Squat (v1 name)', 100.0, 5, 1, 'NORMAL', ${START + 48 * HOUR})",
        "INSERT INTO set_entries VALUES (8, 5, 2, 'pullups', 0.0, 10, 1, 'NORMAL', ${START + 96 * HOUR})",

        // 2023-11-14, age 30: born 1993
        "INSERT INTO body_metrics VALUES (1, $START, 80.5, 180.4, 30, 15.5, 12.4, 35.2)",
        "INSERT INTO body_metrics VALUES (2, ${START + 24 * HOUR}, 500.0, NULL, NULL, NULL, NULL, NULL)",
        "INSERT INTO daily_steps VALUES (1, 100)",
        "INSERT INTO daily_steps VALUES (2, 200)",
        "INSERT INTO daily_calories VALUES (2, 10.0, 20.0)",
        "INSERT INTO daily_calories VALUES (3, 10.0, 20.0)",
    )

    /** Writes the backup to [file]; [version] other than 7 makes one of an older v1. */
    fun write(file: File, version: Int = V1Import.VERSION): File {
        BundledSQLiteDriver().open(file.path).use { connection ->
            (SCHEMA + DATA).forEach { connection.execSQL(it) }
            connection.execSQL("PRAGMA user_version = $version")
        }
        return file
    }
}
