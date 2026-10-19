package com.jaroszeek.gymtracker.testing

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.data.exercise.ExerciseSeedParser
import com.jaroszeek.gymtracker.data.exercise.ExerciseSeeder
import java.io.File
import kotlinx.coroutines.Dispatchers

/** In-memory database on the bundled SQLite driver: Room tests run on the JVM and in CI. */
fun inMemoryDatabase(): GtDatabase = Room.inMemoryDatabaseBuilder<GtDatabase>()
    .setDriver(BundledSQLiteDriver())
    .build()

/** Unit tests run with the module directory as working directory. */
fun asset(path: String): String = File("src/main/assets/$path").readText()

/** The real bundled seed, parsed once per test run. */
val bundledSeed by lazy {
    ExerciseSeedParser.parse(
        asset(ExerciseSeedParser.FREE_EXERCISE_DB),
        asset(ExerciseSeedParser.NIPPARD),
        asset(ExerciseSeedParser.ALIASES_PL),
    )
}

fun seeder(database: GtDatabase) = ExerciseSeeder(database, Dispatchers.IO) { bundledSeed }
