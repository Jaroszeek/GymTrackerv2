package com.jaroszeek.gymtracker.data.exercise

import com.jaroszeek.gymtracker.domain.exercise.ExerciseSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs against the real bundled seed files, so a bad edit to them fails here, not on a phone. */
class ExerciseSeedParserTest {

    private val seed = ExerciseSeedParser.parse(
        freeExerciseDb = asset(ExerciseSeedParser.FREE_EXERCISE_DB),
        nippard = asset(ExerciseSeedParser.NIPPARD),
        aliasesPl = asset(ExerciseSeedParser.ALIASES_PL),
    )

    @Test
    fun containsBothSources() {
        assertEquals(FREE_EXERCISE_DB_COUNT + NIPPARD_COUNT, seed.size)
        assertEquals(NIPPARD_COUNT, seed.count { it.entity.source == ExerciseSource.Nippard.key })
    }

    @Test
    fun everyExerciseHasAPrimaryMuscleAndNoDuplicateMuscles() {
        seed.forEach { exercise ->
            val muscles = exercise.muscles(exerciseId = 1)
            assertTrue(exercise.entity.name, muscles.any { it.isPrimary })
            assertEquals(exercise.entity.name, muscles.size, muscles.map { it.muscle }.toSet().size)
        }
    }

    @Test
    fun polishAliasesAreAttached() {
        val deadlift = seed.single { it.entity.name == "Barbell Deadlift" }
        assertTrue("martwy ciąg" in deadlift.entity.aliasesPl)
        assertTrue(seed.count { it.entity.aliasesPl.isNotEmpty() } >= 100)
    }

    @Test(expected = NoSuchElementException::class)
    fun unknownMuscleFailsFast() {
        val broken = """[{"id":"x","name":"X","primaryMuscles":["wings"],"secondaryMuscles":[],"instructions":[],"category":"strength","images":[]}]"""
        ExerciseSeedParser.parse(freeExerciseDb = broken, nippard = "[]", aliasesPl = "{}")
    }

    private fun asset(path: String) = File("src/main/assets/$path").readText()

    private companion object {
        const val FREE_EXERCISE_DB_COUNT = 876
        const val NIPPARD_COUNT = 26
    }
}
