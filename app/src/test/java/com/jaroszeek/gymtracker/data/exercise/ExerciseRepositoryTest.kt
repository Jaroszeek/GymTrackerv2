package com.jaroszeek.gymtracker.data.exercise

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.exercise.ExerciseDraft
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DAO, seed and repository on an in-memory database with the bundled SQLite driver (runs on the JVM and in CI). */
class ExerciseRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder<GtDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    private val seed by lazy {
        fun asset(path: String) = File("src/main/assets/$path").readText()
        ExerciseSeedParser.parse(
            asset(ExerciseSeedParser.FREE_EXERCISE_DB),
            asset(ExerciseSeedParser.NIPPARD),
            asset(ExerciseSeedParser.ALIASES_PL),
        )
    }

    private fun seeder() = ExerciseSeeder(database, Dispatchers.IO) { seed }

    private fun repository() = ExerciseRepository(database, seeder(), Dispatchers.IO)

    @After
    fun close() = database.close()

    @Test
    fun seedRunsOnceAcrossRestarts() = runTest {
        seeder().ensureSeeded()
        // A new seeder instance stands in for the next app start
        seeder().ensureSeeded()
        assertEquals(seed.size, database.exerciseDao().countSeeded())
    }

    @Test
    fun exercisesComeWithTheirMuscles() = runTest {
        val deadlift = repository().observeAll().first().single { it.name == "Barbell Deadlift" }
        assertTrue(deadlift.primaryMuscles.isNotEmpty())
        assertTrue(deadlift.images.all { it.startsWith("https://") })
    }

    @Test
    fun customExerciseCanBeAddedEditedAndDeleted() = runTest {
        val repository = repository()
        val draft = ExerciseDraft(
            name = "  Landmine Press ",
            equipment = Equipment.Barbell,
            primaryMuscles = setOf(Muscle.Shoulders),
            secondaryMuscles = setOf(Muscle.Shoulders, Muscle.Triceps),
            notes = "Stand tall\n\nPress up and forward",
        )
        val id = repository.saveCustom(null, draft)
        val saved = repository.observe(id).first()!!
        assertEquals("Landmine Press", saved.name)
        assertTrue(saved.isCustom)
        // A muscle chosen as both primary and secondary stays primary only
        assertEquals(setOf(Muscle.Triceps), saved.secondaryMuscles)
        assertEquals(listOf("Stand tall", "Press up and forward"), saved.instructions)

        repository.saveCustom(id, draft.copy(name = "Landmine Press (half-kneeling)", primaryMuscles = setOf(Muscle.Chest)))
        val edited = repository.observe(id).first()!!
        assertEquals("Landmine Press (half-kneeling)", edited.name)
        assertEquals(setOf(Muscle.Chest), edited.primaryMuscles)

        repository.deleteCustom(id)
        assertNull(repository.observe(id).first())
    }

    @Test
    fun builtInExercisesCannotBeDeleted() = runTest {
        val repository = repository()
        val bench = repository.observeAll().first().first { !it.isCustom }
        repository.deleteCustom(bench.id)
        assertEquals(bench, repository.observe(bench.id).first())
    }
}
