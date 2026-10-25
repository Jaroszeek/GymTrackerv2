package com.jaroszeek.gymtracker.data.exercise

import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.exercise.ExerciseDraft
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import com.jaroszeek.gymtracker.testing.asset
import com.jaroszeek.gymtracker.testing.bundledSeed
import com.jaroszeek.gymtracker.testing.inMemoryDatabase
import com.jaroszeek.gymtracker.testing.seeder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DAO, seed and repository on an in-memory database with the bundled SQLite driver (runs on the JVM and in CI). */
class ExerciseRepositoryTest {

    private val database = inMemoryDatabase()

    private fun seeder() = seeder(database)

    private fun repository() = ExerciseRepository(database, seeder(), Dispatchers.IO)

    @After
    fun close() = database.close()

    @Test
    fun seedRunsOnceAcrossRestarts() = runTest {
        seeder().ensureSeeded()
        // A new seeder instance stands in for the next app start
        seeder().ensureSeeded()
        assertEquals(bundledSeed.size, database.exerciseDao().countSeeded())
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

        assertEquals(DeleteResult.Deleted, repository.deleteCustom(id))
        assertNull(repository.observe(id).first())
    }

    @Test
    fun builtInExercisesCannotBeDeleted() = runTest {
        val repository = repository()
        val bench = repository.observeAll().first().first { !it.isCustom }
        repository.deleteCustom(bench.id)
        assertEquals(bench, repository.observe(bench.id).first())
    }

    @Test
    fun builtInExercisesShowTheirPolishInstructions() = runTest {
        val translations = ExerciseTranslations { mapOf("Barbell_Deadlift" to listOf("Podejdź do sztangi.")) }
        val repository = ExerciseRepository(database, seeder(), Dispatchers.IO, translations)
        val all = repository.observeAll().first()
        val deadlift = all.single { it.name == "Barbell Deadlift" }
        assertEquals(listOf("Podejdź do sztangi."), repository.observe(deadlift.id).first()!!.instructions)
        // Without a translation the source text stays
        val other = all.first { !it.isCustom && it.name != "Barbell Deadlift" }
        assertEquals(other.instructions, repository.observe(other.id).first()!!.instructions)
        val custom = repository.saveCustom(null, ExerciseDraft(name = "Landmine Press", notes = "Stand tall"))
        assertEquals(listOf("Stand tall"), repository.observe(custom).first()!!.instructions)
    }

    @Test
    fun instructionsFollowTheAppLanguage() = runTest {
        val polish = MutableStateFlow(true)
        val translations = ExerciseTranslations(polish) { mapOf("Barbell_Deadlift" to listOf("Podejdź do sztangi.")) }
        val repository = ExerciseRepository(database, seeder(), Dispatchers.IO, translations)
        val deadlift = repository.observeAll().first().single { it.name == "Barbell Deadlift" }
        val shown = repository.observe(deadlift.id).map { it!!.instructions }
        assertEquals(listOf("Podejdź do sztangi."), shown.first())
        // The English app keeps the source text, also on a screen open while the language changes
        polish.value = false
        assertEquals(deadlift.instructions, shown.first())
        assertTrue(deadlift.instructions.isNotEmpty())
    }

    @Test
    fun polishInstructionsMatchTheBundledSeed() {
        val polish = Json.decodeFromString<Map<String, List<String>>>(asset("exercises/instructions_pl.json"))
        val seeded = bundledSeed.associate { it.entity.externalId to it.entity }
        polish.forEach { (id, steps) ->
            val source = checkNotNull(seeded[id]) { "unknown id $id" }
            assertEquals(id, source.instructions.size, steps.size)
            assertTrue(id, steps.none { it.isBlank() })
        }
        assertEquals(seeded.values.count { it.instructions.isNotEmpty() }, polish.size)
    }
}
