package com.jaroszeek.gymtracker.domain.routine

import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.exercise.ExerciseCategory
import com.jaroszeek.gymtracker.domain.exercise.ExerciseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDraftTest {

    private fun exercise(id: Long, name: String) = Exercise(
        id = id, name = name, aliases = emptyList(), category = ExerciseCategory.Strength, equipment = null,
        level = null, mechanic = null, primaryMuscles = emptySet(), secondaryMuscles = emptySet(),
        instructions = emptyList(), images = emptyList(), source = ExerciseSource.Builtin,
    )

    private val bench = exercise(1, "Bench Press")
    private val dumbbell = exercise(2, "Dumbbell Bench Press")
    private val row = exercise(3, "Barbell Row")

    @Test
    fun newDraftIsInvalidUntilNamedAndFilled() {
        assertFalse(RoutineDraft().isValid)
        assertFalse(RoutineDraft(name = "Push").isValid)
        assertFalse(RoutineDraft().addBlock(bench).isValid)
        assertTrue(RoutineDraft(name = "Push").addBlock(bench).isValid)
        assertFalse(RoutineDraft(name = "  ").addBlock(bench).isValid)
    }

    @Test
    fun temporaryIdsAreUniqueAndNegative() {
        val draft = RoutineDraft().addBlock(bench).addBlock(row)
        val draftWithVariant = draft.addVariant(draft.blocks.first().id, dumbbell)
        val ids = draftWithVariant.blocks.flatMap { block -> block.variants.map { it.id } + block.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it < 0 })
    }

    @Test
    fun newVariantCopiesTheTargetsOfTheBlock() {
        val heavy = RoutineTargets(sets = 4, repsMin = 6, repsMax = 8, weightKg = 80.0, restSeconds = 180)
        var draft = RoutineDraft().addBlock(bench)
        val block = draft.blocks.single()
        draft = draft.updateTargets(block.id, block.variants.single().id, heavy).addVariant(block.id, dumbbell)
        assertEquals(listOf(heavy, heavy), draft.blocks.single().variants.map { it.targets })
    }

    @Test
    fun removingTheLastVariantRemovesTheBlock() {
        var draft = RoutineDraft().addBlock(bench).addBlock(row)
        val first = draft.blocks.first()
        draft = draft.addVariant(first.id, dumbbell)
        draft = draft.removeVariant(first.id, first.variants.single().id)
        assertEquals(listOf(dumbbell), draft.blocks.first().variants.map { it.exercise })
        draft = draft.removeVariant(first.id, draft.blocks.first().variants.single().id)
        assertEquals(listOf(row), draft.blocks.map { it.variants.single().exercise })
    }

    @Test
    fun moveBlockReorders() {
        val draft = RoutineDraft().addBlock(bench).addBlock(row).addBlock(dumbbell)
        assertEquals(listOf(dumbbell, bench, row), draft.moveBlock(2, 0).blocks.map { it.variants.single().exercise })
        assertEquals(draft, draft.moveBlock(0, 5))
    }

    @Test
    fun targetsAreNormalized() {
        val raw = RoutineTargets(sets = 0, repsMin = 12, repsMax = 8, weightKg = 0.0, restSeconds = 0)
        assertEquals(RoutineTargets(sets = 1, repsMin = 8, repsMax = 12, weightKg = null, restSeconds = null), raw.normalized())
    }
}
